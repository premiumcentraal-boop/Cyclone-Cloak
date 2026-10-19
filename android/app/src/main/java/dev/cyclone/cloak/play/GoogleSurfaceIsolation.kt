package dev.cyclone.cloak.play

import dev.cyclone.cloak.data.EngineMetadataStore
import dev.cyclone.cloak.forge.PlaySurfaceContract
import org.json.JSONObject

enum class IsolationState { READY, NEEDS_ACCOUNT, NEEDS_PACKAGE_REVIEW, SHARED_ACCOUNT_CONFIRMED, CONFLICT, UNKNOWN }
enum class IsolationCheckState { PASS, FAIL, UNKNOWN }
enum class PackageRole { SURFACE, CONTROL }
enum class PackageRequirement { REQUIRED, OPTIONAL, FORBIDDEN }

data class PackageRule(val packageName: String, val role: PackageRole, val requirement: PackageRequirement)

/** Immutable expectation snapshot. It never contains accounts, tokens or a mutable profile body. */
data class GoogleSurfaceRecord(
    val profileId: String,
    val androidUserId: Int?,
    val profileHash: String,
    val surfaceJson: String,
    val packages: List<PackageRule>,
    val active: Boolean = true,
) {
    val surface: JSONObject get() = PlaySurfaceContract.parseSurface(surfaceJson)
    val account: JSONObject get() = surface.getJSONObject("account")
    val slotId: String? get() = account.takeUnless { it.isNull("slot_id") }?.getString("slot_id")

    companion object {
        /** Older profiles without this optional section stay untouched and have no assignment. */
        fun fromProfile(id: String, user: Int?, profile: JSONObject, packages: List<PackageRule>): GoogleSurfaceRecord? {
            if (!profile.has("play_surface")) return null
            return GoogleSurfaceRecord(id, user, EngineMetadataStore.digest(profile),
                PlaySurfaceContract.encodeSurface(profile.getJSONObject("play_surface")), packages)
                .also(GoogleSurfaceCodec::validate)
        }
    }
}

/** Supplied local evidence, not a Google verdict. Null means not observed, never passed. */
data class GoogleSurfaceObservation(
    val profileId: String,
    val androidUserId: Int,
    val profileHash: String,
    val source: String,
    val checkedAt: java.time.Instant,
    val accountMode: String? = null,
    val accountSlotId: String? = null,
    val backupSync: String? = null,
    val packageStates: Map<String, String> = emptyMap(),
    val privateDataBoundary: Boolean? = null,
    val packageVersions: Map<String, String> = emptyMap(),
)

data class IsolationCheck(val id: String, val state: IsolationCheckState, val reason: String) {
    val message: String get() = when (reason) {
        "GS_MATCH" -> "Supplied local observation matches the selected configuration."
        "GS_MISSING_USER" -> "Assign an Android user before reviewing isolation."
        "GS_PROFILE_CHANGED" -> "The saved profile changed; review its Google surface again."
        "GS_FOREIGN_EVIDENCE" -> "The observation belongs to another profile or Android user."
        "GS_NO_EVIDENCE" -> "No matching local observation is available."
        "GS_ACCOUNT_REQUIRED" -> "Review the account assignment and complete local setup."
        "GS_ACCOUNT_CONFLICT" -> "The account association does not match the approved slot and mode."
        "GS_SHARED_REVIEW" -> "This slot is used by active profiles; explicitly confirm the current shared assignment."
        "GS_PACKAGE_REVIEW" -> "Review package scope and required, optional or forbidden package states."
        "GS_BACKUP_CONFLICT" -> "Observed backup or sync posture differs from the expectation."
        "GS_DATA_BOUNDARY" -> "The supplied private-data boundary check failed."
        "GS_INVENTORY_UNREADABLE" -> "Assignment storage is unreadable; restore or review it before assigning a slot."
        "GS_MALFORMED_EVIDENCE" -> "The supplied local observation is malformed; collect it again."
        "GS_MALFORMED_RECORD" -> "Local assignment records are malformed; review them before checking isolation."
        "GS_INACTIVE" -> "This assignment is inactive; review it before activation."
        else -> "Local isolation evidence is incomplete."
    }
}
data class IsolationReview(val state: IsolationState, val checks: List<IsolationCheck>)

/** Pure evaluation: callers must supply the current binding, inventory and observed facts. */
object GoogleSurfaceIsolation {
    fun evaluate(
        record: GoogleSurfaceRecord?,
        currentProfileId: String,
        currentAndroidUserId: Int?,
        currentProfileHash: String,
        inventory: List<GoogleSurfaceRecord>,
        observation: GoogleSurfaceObservation? = null,
        sharedReviewConfirmed: Boolean = false,
        inventoryReadable: Boolean = true,
    ): IsolationReview {
        fun single(state: IsolationState, id: String, reason: String) = IsolationReview(state,
            listOf(IsolationCheck(id, if (state == IsolationState.CONFLICT) IsolationCheckState.FAIL else IsolationCheckState.UNKNOWN, reason)))
        if (!inventoryReadable) return single(IsolationState.UNKNOWN, "inventory", "GS_INVENTORY_UNREADABLE")
        if (record == null) return single(IsolationState.UNKNOWN, "configuration", "GS_NO_EVIDENCE")
        try {
            require(inventory.size <= 512 && inventory.map { it.profileId }.distinct().size == inventory.size)
            GoogleSurfaceCodec.validate(record)
            inventory.forEach(GoogleSurfaceCodec::validate)
            require(inventory.any { it.profileId == record.profileId &&
                EngineMetadataStore.digest(JSONObject(GoogleSurfaceCodec.encode(it))) ==
                EngineMetadataStore.digest(JSONObject(GoogleSurfaceCodec.encode(record))) })
        } catch (_: Exception) { return single(IsolationState.UNKNOWN, "inventory", "GS_MALFORMED_RECORD") }
        if (record.profileId != currentProfileId) return single(IsolationState.CONFLICT, "profile_hash", "GS_FOREIGN_EVIDENCE")
        if (record.profileHash != currentProfileHash) return single(IsolationState.CONFLICT, "profile_hash", "GS_PROFILE_CHANGED")
        if (record.androidUserId == null || currentAndroidUserId == null) return single(IsolationState.UNKNOWN, "android_user", "GS_MISSING_USER")
        if (record.androidUserId != currentAndroidUserId) return single(IsolationState.CONFLICT, "android_user", "GS_FOREIGN_EVIDENCE")
        val account = record.account
        val peers = inventory.filter { it.active && it.profileId != record.profileId && record.slotId != null && it.slotId == record.slotId }
        if (record.active && peers.isNotEmpty() && !sharedReviewConfirmed)
            return single(IsolationState.CONFLICT, "account_association", "GS_SHARED_REVIEW")
        if (observation != null && (!GoogleSurfaceCodec.id.matches(observation.source) ||
                !GoogleSurfaceCodec.id.matches(observation.profileId) || observation.androidUserId !in 0..21474 ||
                !GoogleSurfaceCodec.digest.matches(observation.profileHash) ||
                (observation.accountMode != null && observation.accountMode !in PlaySurfaceContract.accountModes) ||
                (observation.accountSlotId != null && !GoogleSurfaceCodec.id.matches(observation.accountSlotId)) ||
                (observation.accountMode == "none" && observation.accountSlotId != null) ||
                (observation.accountMode in setOf("dedicated", "managed") && observation.accountSlotId == null) ||
                (observation.backupSync != null && observation.backupSync !in setOf("enabled", "disabled", "managed", "unknown")) ||
                observation.packageStates.size > 256 || observation.packageStates.any { (p, s) ->
                    p.length > 200 || !GoogleSurfaceCodec.packageName.matches(p) || s !in setOf("enabled", "disabled", "missing", "unknown")
                } || observation.packageVersions.size > 256 || observation.packageVersions.any { (p, v) ->
                    p.length > 200 || !GoogleSurfaceCodec.packageName.matches(p) || v.isBlank() || v.length > 128 || v.any { it.isISOControl() }
                })) return single(IsolationState.UNKNOWN, "evidence", "GS_MALFORMED_EVIDENCE")
        if (observation != null && (observation.profileId != record.profileId || observation.androidUserId != record.androidUserId || observation.profileHash != record.profileHash))
            return single(IsolationState.CONFLICT, "evidence_binding", "GS_FOREIGN_EVIDENCE")
        val checks = mutableListOf<IsolationCheck>()
        fun add(id: String, match: Boolean?, failure: String) { checks += IsolationCheck(id,
            when (match) { true -> IsolationCheckState.PASS; false -> IsolationCheckState.FAIL; null -> IsolationCheckState.UNKNOWN },
            when (match) { true -> "GS_MATCH"; false -> failure; null -> "GS_NO_EVIDENCE" }) }
        val mode = account.getString("mode")
        val provisioning = account.getString("provisioning_state")
        val needsAccount = provisioning == "blocked" || provisioning == "needs_review" ||
            (mode in setOf("dedicated", "managed") && provisioning != "ready")
        add("account_association", when {
            mode == "unknown" || observation?.accountMode == null || observation.accountMode == "unknown" -> null
            else -> mode == observation.accountMode && record.slotId == observation.accountSlotId
        }, "GS_ACCOUNT_CONFLICT")
        if (needsAccount) checks += IsolationCheck("account_setup", IsolationCheckState.FAIL, "GS_ACCOUNT_REQUIRED")
        val backup = record.surface.getJSONObject("services").getString("backup_sync")
        add("backup_sync", if (backup == "unknown" || observation?.backupSync !in setOf("enabled", "disabled", "managed")) null
            else backup == observation?.backupSync, "GS_BACKUP_CONFLICT")
        val allowed = account.getJSONArray("allowed_packages").let { a -> (0 until a.length()).map(a::getString).toSet() }
        val surfaces = record.packages.filter { it.role == PackageRole.SURFACE }.map { it.packageName }.toSet()
        // Account scope can include ordinary apps; it is not the Google service package list.
        val scopeMismatch = !record.packages.map { it.packageName }.toSet().containsAll(allowed)
        var packageUnknown = record.packages.isEmpty()
        var packageFailed = scopeMismatch
        val services = record.surface.getJSONObject("services")
        val serviceRules = listOf("play_services", "play_store", "web_provider").map { services.getJSONObject(it) }
        for (rule in record.packages) {
            val actual = observation?.packageStates?.get(rule.packageName)
            val service = serviceRules.firstOrNull { it.optString("package_name") == rule.packageName }
            val expectation = service?.getString("state")
            if ((rule.requirement == PackageRequirement.REQUIRED && expectation in setOf("disabled", "missing")) ||
                (rule.requirement == PackageRequirement.FORBIDDEN && expectation in setOf("enabled", "disabled"))) packageFailed = true
            if (actual !in setOf("enabled", "disabled", "missing")) packageUnknown = true
            else if ((rule.requirement == PackageRequirement.REQUIRED && actual != "enabled") ||
                (rule.requirement == PackageRequirement.FORBIDDEN && actual != "missing") ||
                (expectation != null && expectation != "unknown" && actual != expectation)) packageFailed = true
            if (service?.has("version_name") == true && actual != "missing") {
                val version = observation?.packageVersions?.get(rule.packageName)
                if (version == null) packageUnknown = true
                else if (version != service.getString("version_name")) packageFailed = true
            }
        }
        if (serviceRules.any { s -> !s.has("package_name") || s.getString("state") == "unknown" }) packageUnknown = true
        if (serviceRules.any { s -> s.has("package_name") && s.getString("package_name") !in surfaces }) packageFailed = true
        add("package_visibility", when { packageFailed -> false; packageUnknown -> null; else -> true }, "GS_PACKAGE_REVIEW")
        add("app_data_boundary", observation?.privateDataBoundary, "GS_DATA_BOUNDARY")
        add("android_user", observation?.let { true }, "GS_FOREIGN_EVIDENCE")
        add("profile_hash", observation?.let { true }, "GS_PROFILE_CHANGED")
        if (!record.active) checks += IsolationCheck("configuration", IsolationCheckState.UNKNOWN, "GS_INACTIVE")
        val failures = checks.filter { it.state == IsolationCheckState.FAIL }
        val state = when {
            failures.any { it.reason !in setOf("GS_ACCOUNT_REQUIRED", "GS_PACKAGE_REVIEW") } -> IsolationState.CONFLICT
            needsAccount -> IsolationState.NEEDS_ACCOUNT
            packageFailed -> IsolationState.NEEDS_PACKAGE_REVIEW
            checks.any { it.state == IsolationCheckState.UNKNOWN } || !record.active -> IsolationState.UNKNOWN
            peers.isNotEmpty() -> IsolationState.SHARED_ACCOUNT_CONFIRMED
            else -> IsolationState.READY
        }
        return IsolationReview(state, checks.sortedBy { it.id })
    }
}
