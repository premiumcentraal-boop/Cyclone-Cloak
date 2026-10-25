package dev.cyclone.cloak.ui

import dev.cyclone.cloak.cyclone.*
import dev.cyclone.cloak.data.CloakBinding
import dev.cyclone.cloak.data.CloakBindingStore
import dev.cyclone.cloak.data.CloakSpace
import dev.cyclone.cloak.forge.ProfileUniqueness
import dev.cyclone.cloak.forge.ProfileValidator
import dev.cyclone.cloak.root.RootDoctorResult
import org.json.JSONObject
import java.security.MessageDigest

enum class BindingStep { PROFILE, APP, IDENTITY, REVIEW, RESULT }
data class BindingDraft(
    val step: BindingStep = BindingStep.PROFILE,
    val profileId: String? = null,
    val packageNames: Set<String> = emptySet(),
    val identityId: String? = null,
    val reviewStamp: String? = null,
    val acknowledged: Boolean = false,
    val running: Boolean = false,
    val outcome: BindingOutcome? = null,
    val note: String? = null,
    val action: ProfileAction = ProfileAction.BIND,
    val originalUser: Int? = null,
    val originalBindings: List<CloakBinding> = emptyList(),
)
data class BindingInputs(
    val profiles: List<CycloneProfile>, val identities: List<Pair<String, JSONObject>>,
    val bindings: List<CloakBinding>, val canBind: Boolean, val ownProfileId: String?,
)
data class BindingOutcome(val ready: Boolean, val saved: Boolean, val message: String)

/** UI orchestration only: uses existing validation, store identity and health semantics. */
object BindingFlow {
    fun findings(draft: BindingDraft, input: BindingInputs): List<ProfileValidator.Finding> {
        val out = mutableListOf<ProfileValidator.Finding>()
        out += ProfileOperations.replacementFindings(draft, input)
        fun issue(field: String, code: String, message: String, warning: Boolean = false) {
            out += ProfileValidator.Finding(if (warning) ProfileValidator.WARNING else ProfileValidator.ERROR, code, message, field)
        }
        val profile = input.profiles.firstOrNull { it.id == draft.profileId }
        // A space never reaches Cyclone, so the connector approval is not needed to bind it.
        if (!input.canBind && !isSpaceTarget(draft.profileId)) issue("profile", "CONNECTOR_NOT_READY", "Refresh and approve profile settings in Cyclone → Settings → Connectors.")
        if (profile == null) issue("profile", "PROFILE_MISSING", "Choose a Cyclone profile from the latest synchronized list. Refresh if it is missing.")
        else {
            if (!profile.bindable) issue("profile", "PROFILE_NOT_READY", "This profile is not ready. Restore it or finish setup in Cyclone, then refresh.")
            if (profile.androidUserId == null) issue("profile", "ANDROID_USER_MISSING", "This profile has no Android user assignment. Finish setup in Cyclone, then refresh.")
            if (input.ownProfileId != null && input.ownProfileId != profile.id)
                issue("profile", "PROFILE_OUT_OF_SCOPE", "Bind this profile from Cloak in Main or from Cloak inside that profile.")
        }
        val packages = profile?.packages
        if (draft.packageNames.isEmpty()) issue("app", "APP_REQUIRED", "Choose at least one app or select All apps.")
        draft.packageNames.sorted().forEach { pkg ->
            val field = "app.$pkg"
            if (packages == null || pkg !in packages)
                issue(field, "APP_MISSING", "This app is missing from the current app list. Add it in Cyclone and refresh before binding.")
            val user = profile?.androidUserId
            if (profile != null && user != null &&
                runCatching { CloakBindingStore.validate(profile.id, user, pkg) }.isFailure)
                issue(field, "INVALID_TARGET", "The profile, Android user or app package is invalid. Refresh Cyclone's list.")
            val targets = input.bindings.filter { it.profileId == draft.profileId && it.packageName == pkg }
            if (targets.size > 1) issue(field, "DUPLICATE_BINDING", "Multiple local records exist for this target. Review them in Profiles before binding.")
            if (targets.any { it.origin == CloakBinding.ORIGIN_MAIN })
                issue(field, "MANAGED_BY_MAIN", "This app binding is managed by Cloak in Main. Change it there.")
            if (targets.any { it.cloakProfileId != draft.identityId } && draft.identityId != null)
                issue("identity", "REPLACE_BINDING", "Confirmation replaces $pkg's existing device profile. Go back to keep the current selection.", true)
        }
        val identity = input.identities.firstOrNull { it.first == draft.identityId }?.second
        if (identity == null) issue("identity", "IDENTITY_MISSING", "Choose a readable saved compatibility profile or generate one.")
        else {
            if (identity.optString("id") != draft.identityId) issue("identity", "IDENTITY_REFERENCE_MISMATCH", "The saved profile reference is inconsistent. Review it in Identities before binding.")
            out += ProfileValidator.validateProfileResult(identity).findings.map { it.copy(field = "identity.${it.field.orEmpty()}") }
            out += ProfileUniqueness(input.identities).findings(identity, draft.identityId.orEmpty()).map {
                it.copy(field = "identity.${it.field}", message = "${it.message} Review or regenerate the value in Identities, or acknowledge keeping it.")
            }
            if (input.bindings.any { it.cloakProfileId == draft.identityId &&
                    (it.profileId != draft.profileId || it.packageName !in draft.packageNames) })
                issue("identity", "IDENTITY_ALREADY_BOUND", "This compatibility profile is already bound elsewhere. Choose or generate another profile for separate identities, or acknowledge sharing it.", true)
        }
        return out.sortedWith(compareBy({ if (it.isError) 0 else 1 }, { it.field }, { it.code }))
    }
    fun canAdvance(draft: BindingDraft, input: BindingInputs): Boolean {
        val field = when (draft.step) { BindingStep.PROFILE -> "profile"; BindingStep.APP -> "app"; BindingStep.IDENTITY -> "identity"; else -> "" }
        return !draft.running && draft.step != BindingStep.RESULT && findings(draft, input).none {
            it.isError && (field.isEmpty() || it.field.orEmpty().startsWith(field))
        }
    }
    fun next(draft: BindingDraft, input: BindingInputs): BindingDraft {
        if (draft.step == BindingStep.REVIEW || !canAdvance(draft, input)) return draft
        val step = BindingStep.entries[draft.step.ordinal + 1]
        return draft.copy(step = step, acknowledged = false, reviewStamp = if (step == BindingStep.REVIEW) stamp(draft, input) else null, note = null)
    }
    /** Include reviewed data and warnings; changed local/snapshot facts require a fresh acknowledgement. */
    fun stamp(draft: BindingDraft, input: BindingInputs): String {
        val profile = input.profiles.firstOrNull { it.id == draft.profileId }
        val identity = input.identities.firstOrNull { it.first == draft.identityId }?.second
        val text = listOf(draft.action, draft.originalUser, draft.originalBindings, profile.toString(), draft.packageNames.sorted().joinToString("\n"), identity?.toString(),
            input.bindings.toString(), input.canBind, input.ownProfileId, findings(draft, input).toString()).joinToString("\n")
        return MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
    }
    fun canConfirm(draft: BindingDraft, input: BindingInputs): Boolean =
        draft.step == BindingStep.REVIEW && !draft.running && draft.reviewStamp == stamp(draft, input) &&
            findings(draft, input).none { it.isError } && (draft.acknowledged || findings(draft, input).none { it.severity == ProfileValidator.WARNING })

    fun chooseProfile(draft: BindingDraft, id: String): BindingDraft = draft.copy(
        profileId = id, packageNames = if (id == draft.profileId) draft.packageNames else emptySet(),
        reviewStamp = null, acknowledged = false, note = null)

    fun selectApps(draft: BindingDraft, packages: Set<String>): BindingDraft = draft.copy(
        packageNames = packages.toSet(), reviewStamp = null, acknowledged = false, note = null)

    fun allApps(draft: BindingDraft, input: BindingInputs): Boolean {
        val packages = input.profiles.firstOrNull { it.id == draft.profileId }?.packages.orEmpty().toSet()
        return packages.isNotEmpty() && draft.packageNames == packages
    }

    fun selectAll(draft: BindingDraft, input: BindingInputs, selected: Boolean): BindingDraft =
        selectApps(draft, if (selected) input.profiles.firstOrNull { it.id == draft.profileId }?.packages.orEmpty().toSet() else emptySet())

    fun records(draft: BindingDraft, input: BindingInputs, now: Long): List<CloakBinding> {
        require(canConfirm(draft, input)) { "Review the current selection and resolve blocking findings first." }
        val profile = input.profiles.first { it.id == draft.profileId }
        return draft.packageNames.sorted().map { pkg ->
            val existing = input.bindings.singleOrNull { it.profileId == profile.id && it.packageName == pkg }
            existing?.copy(androidUserId = profile.androidUserId!!, cloakProfileId = draft.identityId!!,
                enabled = if (draft.action == ProfileAction.REPLACE) existing.enabled else true, updatedAt = now, state = "pending") ?: CloakBinding(profile.id, profile.androidUserId!!,
                pkg, draft.identityId!!, 0, true, now, "pending")
        }
    }

    /** Shared by guided app selection and the existing fleet bulk action. Store format stays per app. */
    fun applyRecords(current: List<CloakBinding>, records: List<CloakBinding>): List<CloakBinding> =
        records.fold(current) { list, record -> CloakBindingStore.upsertIn(list, record) }

    fun saved(draft: BindingDraft, records: List<CloakBinding>): Boolean =
        draft.packageNames.isNotEmpty() && draft.packageNames.all { pkg ->
            records.filter { it.profileId == draft.profileId && it.packageName == pkg }.singleOrNull()?.let {
                (if (draft.action == ProfileAction.REPLACE) it.enabled == (draft.originalBindings.singleOrNull { old -> old.packageName == pkg }?.enabled ?: true) &&
                    it.androidUserId == draft.originalUser else it.enabled) && it.cloakProfileId == draft.identityId && it.origin == CloakBinding.ORIGIN_LOCAL
            } == true
        }
    fun isSpaceTarget(profileId: String?): Boolean = profileId != null && CloakSpace.isSpaceId(profileId)

    /** The spaces that can take bindings now, as profile-shaped targets for the same review and validation. */
    fun spaceProfiles(spaces: List<CloakSpace>): List<CycloneProfile> = spaces.filter { it.ready }.map(CycloneProfile::forSpace)

    /**
     * A space has no Cyclone side. Its bindings are complete once Cloak's publication has accepted them and every
     * enabled app reports ready. No connector check applies.
     */
    fun spaceCompletion(draft: BindingDraft, root: RootDoctorResult, records: List<CloakBinding>): BindingOutcome {
        if (!saved(draft, records)) return BindingOutcome(false, false, "The saved target changed or is unavailable. Return to review; retry will not overwrite it.")
        if (!root.published) return BindingOutcome(false, true, "Binding saved locally. ${root.title}: ${root.message}")
        val held = records.filter { it.profileId == draft.profileId && it.packageName in draft.packageNames && it.enabled }
        return if (held.all { CloakHealth.cycloneState(it.state) == CloakHealth.READY }) BindingOutcome(true, true, "Apps are bound. Open them from the space.")
        else BindingOutcome(false, true, "Binding saved and published, but not every app reports ready. Check Health and retry.")
    }

    fun completion(draft: BindingDraft, root: RootDoctorResult, report: SyncReport?, records: List<CloakBinding>): BindingOutcome {
        val held = records.filter { it.profileId == draft.profileId && it.packageName in draft.packageNames }
        val saved = saved(draft, records)
        if (!saved) return BindingOutcome(false, false, "The saved target changed or is unavailable. Return to review; retry will not overwrite it.")
        val reconcile = report?.reconcile
        val issue = draft.packageNames.sorted().mapNotNull { pkg ->
            report?.issues?.get(bindingKey(draft.profileId!!, pkg))?.let { "$pkg: $it" }
        }.takeIf { it.isNotEmpty() }?.joinToString("\n")
        val ready = root.published && report?.headline == "Connected" && report.failureCode == null && report.gate?.bindings == true && reconcile != null &&
            reconcile.stoppedBy == null && issue == null && held.filter { it.enabled }.all { CloakHealth.cycloneState(it.state) == CloakHealth.READY }
        val reason = when {
            !root.published -> "${root.title}: ${root.message}"
            issue != null -> issue
            reconcile?.stoppedBy != null -> "Cyclone stopped the update (${reconcile.stoppedBy}). Reconnect or approve the connector, then retry."
            !ready -> "Readiness is not confirmed. Check Health Doctor and refresh Cyclone before retrying."
            else -> "The binding is ready according to the latest publication and synchronization checks."
        }
        return BindingOutcome(ready, true, if (ready) reason else "Binding saved locally. $reason")
    }
}
