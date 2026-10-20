package dev.cyclone.cloak.ui

import dev.cyclone.cloak.cyclone.CloakHealth
import dev.cyclone.cloak.cyclone.CycloneGate
import dev.cyclone.cloak.root.PublicationReason
import dev.cyclone.cloak.root.RootDoctorCode
import dev.cyclone.cloak.root.RootDoctorResult
import org.json.JSONArray
import org.json.JSONObject

internal enum class DoctorStatus(val label: String) { PASS("Pass"), WARN("Warning"), FAIL("Fail") }
internal enum class Readiness(val label: String) { READY("Ready"), DEGRADED("Degraded"), FAILED("Failed") }
internal enum class DoctorCheckKind(val label: String) {
    ROOT("Root and Zygisk"), VERSION("App/module release match"), CONNECTOR("Cyclone connection"),
    PERMISSIONS("Binding permissions"), STARTUP("Startup provider"), BINDINGS("Enabled bindings"), BINDING("Binding health"),
    PUBLICATION("Profile publication"), SYNC("Binding synchronization"), LOCAL_RECORDS("Local records"),
    ENABLED("Binding enabled"), PROFILE("Cyclone profile reference"), IDENTITY("Identity readability"),
    RECORDS("Binding record consistency"),
}
internal enum class DoctorReason(val message: String) {
    PUBLICATION_STALE_LOCK("A stale publication marker was detected after acquiring the kernel lock. The previous publisher no longer holds it."),
    PUBLICATION_UNKNOWN("Profile publication is not confirmed. Run Check & repair to refresh its status."),
    SYNC_READY("The last synchronization completed without binding issues."),
    SYNC_CHECKING("Cyclone is connected. Binding reconciliation is still running."),
    SYNC_ISSUES("Some bindings were not saved or confirmed by Cyclone. Reconnect to retry existing reconciliation."),
    LOCAL_READY("No unreadable or conflicting local records were found in the current snapshot."),
    LOCAL_UNKNOWN("The local identity library has not finished loading."),
    LOCAL_UNREADABLE("Some local identity files are unreadable. Restore them from an export or review affected bindings; no records were changed."),
    LOCAL_CONFLICT("Saved identities share distinguishing values. Review Local identifier duplicates below."),
    DUPLICATE_BINDING("Multiple local records target the same Cyclone profile and app. Review the conflicting bindings; no records were changed."),
    IDENTITY_ID_MISMATCH("The stored identity ID does not match its file/reference. Review or restore that identity."),
    IDENTITY_UNREADABLE("The referenced local identity file exists but cannot be read as profile JSON. Restore it from an export."),
    IDENTITY_UNKNOWN("Identity readability has not been confirmed yet."),
    IDENTITY_REMOTE("This identity is supplied by Main. Its file is not in this local library, so readability cannot be independently confirmed here."),
    IDENTITY_INVALID("The referenced identity has blocking coherence errors. Review the identity before applying it."),
    IDENTITY_READY("The referenced local identity is readable and coherent."),
    PROFILE_READY("The Cyclone profile, Android user and app membership match this binding."),
    RECORDS_READY("No stale or conflicting records were found for this binding."),
    ENABLED_READY("This binding is enabled."),
    ROOT_READY("Root Doctor reports working root access, module setup and profile publication."),
    ROOT_NOT_CHECKED("Root setup and publication have not been checked yet. Run Root Doctor."),
    ROOT_CHECKING("Root Doctor is still checking. Approve the Magisk prompt if shown."),
    ROOT_APPROVAL("Allow Cyclone Cloak in Magisk, then run the check again."),
    ROOT_TIMEOUT("Root approval timed out. Run the check again and approve the prompt."),
    ROOT_UNAVAILABLE("Magisk root access is unavailable. Check Magisk and its superuser service."),
    MODULE_MISSING("The Cloak module is missing. Install the matching module in Magisk."),
    MODULE_DISABLED("The Cloak module is disabled. Enable it in Magisk and restart when convenient."),
    MODULE_REMOVAL("The module is marked for removal. Reinstall the matching release before using bindings."),
    REBOOT_PENDING("A module change is waiting for a restart. The queued version does not prove which runtime is loaded."),
    MODULE_OUTDATED("The installed module does not match this app release. Use the matching module."),
    MODULE_INVALID("The bundled module did not pass its package or architecture checks."),
    MODULE_INSTALL_FAILED("Magisk could not schedule the matching module. Try the existing repair action again."),
    ZYGISK_DISABLED("Zygisk is disabled. Enable it in Magisk and restart when convenient."),
    ZYGISK_UNKNOWN("Root Doctor could not confirm Zygisk is enabled. Check Magisk settings."),
    ABI_UNSUPPORTED("This module has no library for the device's Android architecture."),
    PUBLICATION_LOCKED("Another publisher holds the publication lock. Retry when it finishes."),
    PUBLICATION_LOCK_UNAVAILABLE("The device publication lock tool is unavailable."),
    PUBLICATION_LOCK_FAILED("The publication lock could not be opened or acquired; this is not publisher contention."),
    PUBLICATION_RECOVERED("Interrupted publication was recovered and the merged tree was published successfully."),
    PUBLICATION_COMPLETED("The merged profile tree was published successfully."),
    PUBLICATION_FAILED("Root access worked, but profile publication failed. Run Root Doctor again."),
    VERSIONS_MATCH("The reported installed app and module release names match. Hook coverage is recorded separately below."),
    VERSIONS_DIFFER("The app and installed module release names differ. Install the matching release."),
    VERSION_UNKNOWN("The installed module or app release could not be confirmed. Run Root Doctor again."),
    VERSION_QUEUED("A module change is queued; its installed/loaded version is not confirmed until after restart and another check."),
    CONNECTED("Cyclone reports an approved connector connection."),
    CONNECTION_UNKNOWN("Cyclone has not been checked yet. Reconnect to load its current status."),
    CONNECTING("Cyclone is being refreshed. Readiness will update when it finishes."),
    CYCLONE_MISSING("Cyclone was not found. Install or open Cyclone, then reconnect."),
    CYCLONE_NOT_RESPONDING("Cyclone is not answering. Open it and reconnect."),
    APPROVAL_REQUIRED("Approve Cyclone Cloak in Cyclone's connector settings, then reconnect."),
    CONNECTION_FAILED("The Cyclone connection failed. Reconnect to try again."),
    PERMISSIONS_READY("Cyclone granted the permissions needed to list profiles/apps and bind configurations."),
    PERMISSIONS_UNKNOWN("Binding permissions have not been confirmed yet."),
    PERMISSIONS_MISSING("Cyclone has not granted all binding permissions. Review connector approval and scopes."),
    CYCLONE_TOO_OLD("This Cyclone connector version cannot support bindings. Update Cyclone."),
    STARTUP_READY("The startup provider is registered for this app process."),
    STARTUP_NOT_READY("The startup provider is not confirmed ready. Reconnect to register it again."),
    BINDINGS_PRESENT("Enabled bindings are included in the readiness verdict."),
    NO_ENABLED_BINDINGS("There are no enabled bindings to assess. Create or enable a binding first."),
    BINDING_DISABLED("This binding is turned off and is excluded from overall readiness."),
    PROFILE_UNKNOWN("Cyclone profile availability is unknown. Reconnect to load the profile list."),
    PROFILE_MISSING("This binding's Cyclone profile is no longer present. Review the binding in Profiles."),
    PROFILE_NOT_READY("The Cyclone profile is setting up or in Recently deleted. Its binding is not ready to apply."),
    USER_CHANGED("The profile's Android user changed. Refresh so existing reconciliation can update the binding."),
    APP_LIST_UNKNOWN("Cyclone has not supplied this profile's app list, so app membership cannot be checked."),
    APP_NOT_LISTED("The app is not in this Cyclone profile's app list. Add it in Cyclone or review the binding."),
    IDENTITY_MISSING("The binding's Cloak configuration is unavailable. Review or replace the binding."),
    BINDING_ISSUE("Cyclone could not save or confirm this binding. Review it in Profiles and reconnect so reconciliation can retry."),
    BINDING_READY("The last reported binding health is ready."),
    BINDING_PENDING("This binding is waiting for publication or a fresh health check."),
    BINDING_DEGRADED("This binding reports degraded health. Check root/module setup and reconnect."),
    BINDING_FAILED("This binding reports failed health and cannot currently apply its configuration."),
}

/** Private display context is intentionally never exported. No identity JSON is accepted here. */
internal data class BindingDoctorFacts(
    val displayContext: String,
    val enabled: Boolean,
    val health: String,
    val profilePresent: Boolean?,
    val profileReady: Boolean?,
    val userMatches: Boolean?,
    val appListed: Boolean?,
    val identityPresent: Boolean,
    val issuePresent: Boolean,
    val identityReadable: Boolean? = identityPresent,
    val identityCoherent: Boolean = true,
    val identityIdMatches: Boolean = true,
    val remoteIdentity: Boolean = false,
    val duplicateRecord: Boolean = false,
    val duplicateIdentifiers: Boolean = false,
)
internal data class DoctorFacts(
    val root: RootDoctorResult,
    val appVersion: String?,
    val connectorHeadline: String?,
    val syncing: Boolean,
    val gate: CycloneGate?,
    val providerState: String,
    val bindings: List<BindingDoctorFacts>,
    val localInventoryKnown: Boolean = true,
    val unreadableIdentities: Boolean = false,
    val conflictingIdentities: Boolean = false,
    val syncIssues: Boolean = false,
    val mismatchedIdentities: Boolean = false,
    val reconciling: Boolean = false,
)
internal data class DoctorCheck(
    val kind: DoctorCheckKind,
    val status: DoctorStatus,
    val reasons: List<DoctorReason>,
    val displayContext: String? = null,
    val bindingOrdinal: Int? = null,
    val affectsReadiness: Boolean = true,
)
internal data class HealthDiagnostics(
    val verdict: Readiness,
    val appVersion: String?,
    val installedModuleVersion: String?,
    val enabledBindings: Int,
    val disabledBindings: Int,
    val checks: List<DoctorCheck>,
) {
    val summary: String get() {
        val applicable = checks.filter { it.affectsReadiness }
        return "${applicable.count { it.status == DoctorStatus.PASS }} pass · ${applicable.count { it.status == DoctorStatus.WARN }} warning · ${applicable.count { it.status == DoctorStatus.FAIL }} fail"
    }
    /** Positive allowlist: never serialize a state object, raw error, or private display context. */
    fun redactedReport(): String = JSONObject()
        .put("format", "cyclone-cloak.readiness-doctor/3")
        .put("scope", "last_reported_enabled_bindings")
        .put("verdict", verdict.name.lowercase())
        .put("app_version", safeVersion(appVersion) ?: JSONObject.NULL)
        .put("installed_module_version", safeVersion(installedModuleVersion) ?: JSONObject.NULL)
        .put("enabled_bindings", enabledBindings)
        .put("disabled_bindings", disabledBindings)
        .put("checks", JSONArray(checks.map { check ->
            JSONObject().put("check", check.kind.name.lowercase()).put("status", if (check.status == DoctorStatus.WARN) "warning" else check.status.name.lowercase())
                .put("binding_number", check.bindingOrdinal ?: JSONObject.NULL)
                .put("affects_readiness", check.affectsReadiness)
                .put("reasons", JSONArray(check.reasons.distinct().map { reason ->
                    JSONObject().put("code", reason.name).put("message", reason.message)
                }))
        })).toString(2)

    companion object {
        /** Only the project's release-name grammar is exportable, never arbitrary property output. */
        private fun safeVersion(value: String?): String? = value?.trim()?.removePrefix("v")?.takeIf {
            Regex("[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}(?:-(?:alpha|beta|rc)\\.[0-9]{1,4})?").matches(it)
        }

        fun assess(facts: DoctorFacts): HealthDiagnostics {
            val checks = mutableListOf<DoctorCheck>()
            fun add(kind: DoctorCheckKind, status: DoctorStatus, reason: DoctorReason) {
                checks += DoctorCheck(kind, status, listOf(reason))
            }
            val rootReason = when (facts.root.code) {
                RootDoctorCode.READY -> DoctorReason.ROOT_READY
                RootDoctorCode.NOT_CHECKED -> DoctorReason.ROOT_NOT_CHECKED
                RootDoctorCode.CHECKING -> DoctorReason.ROOT_CHECKING
                RootDoctorCode.ROOT_REQUIRED -> DoctorReason.ROOT_APPROVAL
                RootDoctorCode.ROOT_TIMEOUT -> DoctorReason.ROOT_TIMEOUT
                RootDoctorCode.ROOT_UNAVAILABLE -> DoctorReason.ROOT_UNAVAILABLE
                RootDoctorCode.MODULE_MISSING -> DoctorReason.MODULE_MISSING
                RootDoctorCode.MODULE_DISABLED -> DoctorReason.MODULE_DISABLED
                RootDoctorCode.MODULE_PENDING_REMOVAL -> DoctorReason.MODULE_REMOVAL
                RootDoctorCode.MODULE_REBOOT_REQUIRED -> DoctorReason.REBOOT_PENDING
                RootDoctorCode.MODULE_OUTDATED -> DoctorReason.MODULE_OUTDATED
                RootDoctorCode.MODULE_BUNDLE_INVALID -> DoctorReason.MODULE_INVALID
                RootDoctorCode.MODULE_INSTALL_FAILED -> DoctorReason.MODULE_INSTALL_FAILED
                RootDoctorCode.ZYGISK_DISABLED -> DoctorReason.ZYGISK_DISABLED
                RootDoctorCode.ZYGISK_STATUS_UNKNOWN -> DoctorReason.ZYGISK_UNKNOWN
                RootDoctorCode.ABI_UNSUPPORTED -> DoctorReason.ABI_UNSUPPORTED
                RootDoctorCode.PUBLISH_FAILED -> DoctorReason.PUBLICATION_FAILED
            }
            val rootStatus = when (facts.root.code) {
                RootDoctorCode.READY -> DoctorStatus.PASS
                RootDoctorCode.NOT_CHECKED, RootDoctorCode.CHECKING, RootDoctorCode.ROOT_TIMEOUT,
                RootDoctorCode.MODULE_REBOOT_REQUIRED, RootDoctorCode.ZYGISK_STATUS_UNKNOWN -> DoctorStatus.WARN
                else -> DoctorStatus.FAIL
            }
            val publicationReason = when (facts.root.publicationReason) {
                PublicationReason.LOCKED -> DoctorReason.PUBLICATION_LOCKED
                PublicationReason.FAILED -> DoctorReason.PUBLICATION_FAILED
                PublicationReason.LOCK_UNAVAILABLE -> DoctorReason.PUBLICATION_LOCK_UNAVAILABLE
                PublicationReason.LOCK_FAILED -> DoctorReason.PUBLICATION_LOCK_FAILED
                PublicationReason.RECOVERED -> DoctorReason.PUBLICATION_RECOVERED
                PublicationReason.COMPLETED -> DoctorReason.PUBLICATION_COMPLETED
                null -> null
            }
            val effectiveRootStatus = if (facts.root.code == RootDoctorCode.PUBLISH_FAILED &&
                facts.root.publicationReason == PublicationReason.LOCKED) DoctorStatus.WARN else rootStatus
            checks += DoctorCheck(DoctorCheckKind.ROOT, effectiveRootStatus,
                listOfNotNull(DoctorReason.PUBLICATION_STALE_LOCK.takeIf { facts.root.staleLockDetected }, if (facts.root.code == RootDoctorCode.PUBLISH_FAILED &&
                    facts.root.publicationReason == PublicationReason.LOCKED) null else rootReason, publicationReason))
            val publicationStatus = when {
                facts.root.publicationReason == PublicationReason.LOCKED -> DoctorStatus.WARN
                facts.root.publicationReason == PublicationReason.FAILED || facts.root.code == RootDoctorCode.PUBLISH_FAILED -> DoctorStatus.FAIL
                facts.root.code == RootDoctorCode.READY -> DoctorStatus.PASS
                else -> DoctorStatus.WARN
            }
            add(DoctorCheckKind.PUBLICATION, publicationStatus, publicationReason ?: when (publicationStatus) {
                DoctorStatus.PASS -> DoctorReason.PUBLICATION_COMPLETED
                DoctorStatus.FAIL -> DoctorReason.PUBLICATION_FAILED
                else -> DoctorReason.PUBLICATION_UNKNOWN
            })
            when {
                facts.reconciling -> add(DoctorCheckKind.SYNC, DoctorStatus.WARN, DoctorReason.SYNC_CHECKING)
                facts.syncing -> add(DoctorCheckKind.SYNC, DoctorStatus.WARN, DoctorReason.CONNECTING)
                facts.connectorHeadline == null -> add(DoctorCheckKind.SYNC, DoctorStatus.WARN, DoctorReason.CONNECTION_UNKNOWN)
                facts.connectorHeadline != "Connected" -> add(DoctorCheckKind.SYNC, DoctorStatus.FAIL, DoctorReason.CONNECTION_FAILED)
                facts.syncIssues || facts.bindings.any { it.enabled && it.issuePresent } -> add(DoctorCheckKind.SYNC, DoctorStatus.WARN, DoctorReason.SYNC_ISSUES)
                else -> add(DoctorCheckKind.SYNC, DoctorStatus.PASS, DoctorReason.SYNC_READY)
            }
            val localReasons = listOfNotNull(
                DoctorReason.LOCAL_UNKNOWN.takeIf { !facts.localInventoryKnown },
                DoctorReason.LOCAL_UNREADABLE.takeIf { facts.unreadableIdentities },
                DoctorReason.LOCAL_CONFLICT.takeIf { facts.conflictingIdentities },
                DoctorReason.DUPLICATE_BINDING.takeIf { facts.bindings.any { it.enabled && it.duplicateRecord } },
                DoctorReason.IDENTITY_ID_MISMATCH.takeIf { facts.mismatchedIdentities || facts.bindings.any { it.enabled && !it.identityIdMatches } })
            checks += DoctorCheck(DoctorCheckKind.LOCAL_RECORDS,
                if (localReasons.isEmpty()) DoctorStatus.PASS else DoctorStatus.WARN,
                localReasons.ifEmpty { listOf(DoctorReason.LOCAL_READY) })
            val app = safeVersion(facts.appVersion)
            val module = if (facts.root.code == RootDoctorCode.MODULE_REBOOT_REQUIRED) null else safeVersion(facts.root.moduleVersion)
            when {
                facts.root.code == RootDoctorCode.MODULE_REBOOT_REQUIRED -> add(DoctorCheckKind.VERSION, DoctorStatus.WARN, DoctorReason.VERSION_QUEUED)
                facts.root.code == RootDoctorCode.MODULE_OUTDATED || (app != null && module != null && app != module) ->
                    add(DoctorCheckKind.VERSION, DoctorStatus.FAIL, DoctorReason.VERSIONS_DIFFER)
                app == null || module == null -> add(DoctorCheckKind.VERSION, DoctorStatus.WARN, DoctorReason.VERSION_UNKNOWN)
                else -> add(DoctorCheckKind.VERSION, DoctorStatus.PASS, DoctorReason.VERSIONS_MATCH)
            }
            when {
                facts.syncing -> add(DoctorCheckKind.CONNECTOR, DoctorStatus.WARN, DoctorReason.CONNECTING)
                facts.connectorHeadline == null -> add(DoctorCheckKind.CONNECTOR, DoctorStatus.WARN, DoctorReason.CONNECTION_UNKNOWN)
                facts.connectorHeadline == "Cyclone not found" -> add(DoctorCheckKind.CONNECTOR, DoctorStatus.FAIL, DoctorReason.CYCLONE_MISSING)
                facts.connectorHeadline == "Cyclone not answering" -> add(DoctorCheckKind.CONNECTOR, DoctorStatus.FAIL, DoctorReason.CYCLONE_NOT_RESPONDING)
                facts.connectorHeadline == "Needs approval" || facts.gate?.approved == false -> add(DoctorCheckKind.CONNECTOR, DoctorStatus.FAIL, DoctorReason.APPROVAL_REQUIRED)
                facts.connectorHeadline != "Connected" || facts.gate == null -> add(DoctorCheckKind.CONNECTOR, DoctorStatus.FAIL, DoctorReason.CONNECTION_FAILED)
                else -> add(DoctorCheckKind.CONNECTOR, DoctorStatus.PASS, DoctorReason.CONNECTED)
            }
            when {
                facts.gate == null -> add(DoctorCheckKind.PERMISSIONS, DoctorStatus.WARN, DoctorReason.PERMISSIONS_UNKNOWN)
                facts.gate.minor < 1 -> add(DoctorCheckKind.PERMISSIONS, DoctorStatus.FAIL, DoctorReason.CYCLONE_TOO_OLD)
                !facts.gate.bindings -> add(DoctorCheckKind.PERMISSIONS, DoctorStatus.FAIL, DoctorReason.PERMISSIONS_MISSING)
                else -> add(DoctorCheckKind.PERMISSIONS, DoctorStatus.PASS, DoctorReason.PERMISSIONS_READY)
            }
            if (facts.providerState == "ready" && facts.gate?.startup == true) add(DoctorCheckKind.STARTUP, DoctorStatus.PASS, DoctorReason.STARTUP_READY)
            else add(DoctorCheckKind.STARTUP, DoctorStatus.WARN, DoctorReason.STARTUP_NOT_READY)
            val enabled = facts.bindings.count { it.enabled }
            add(DoctorCheckKind.BINDINGS, if (enabled > 0) DoctorStatus.PASS else DoctorStatus.WARN,
                if (enabled > 0) DoctorReason.BINDINGS_PRESENT else DoctorReason.NO_ENABLED_BINDINGS)
            facts.bindings.forEachIndexed { index, binding ->
                val problems = mutableListOf<Pair<DoctorStatus, DoctorReason>>()
                fun problem(status: DoctorStatus, reason: DoctorReason) { problems += status to reason }
                if (!binding.enabled) problem(DoctorStatus.WARN, DoctorReason.BINDING_DISABLED)
                run {
                    when (binding.profilePresent) {
                        null -> problem(DoctorStatus.WARN, DoctorReason.PROFILE_UNKNOWN)
                        false -> problem(DoctorStatus.FAIL, DoctorReason.PROFILE_MISSING)
                        true -> {
                            if (binding.profileReady != true) problem(DoctorStatus.WARN, DoctorReason.PROFILE_NOT_READY)
                            if (binding.userMatches == false) problem(DoctorStatus.WARN, DoctorReason.USER_CHANGED)
                            when (binding.appListed) {
                                null -> problem(DoctorStatus.WARN, DoctorReason.APP_LIST_UNKNOWN)
                                false -> problem(DoctorStatus.FAIL, DoctorReason.APP_NOT_LISTED)
                                true -> Unit
                            }
                        }
                    }
                    when {
                        binding.remoteIdentity -> problem(DoctorStatus.WARN, DoctorReason.IDENTITY_REMOTE)
                        binding.identityReadable == null -> problem(DoctorStatus.WARN, DoctorReason.IDENTITY_UNKNOWN)
                        !binding.identityReadable && binding.identityPresent -> problem(DoctorStatus.FAIL, DoctorReason.IDENTITY_UNREADABLE)
                        !binding.identityPresent -> problem(DoctorStatus.FAIL, DoctorReason.IDENTITY_MISSING)
                        !binding.identityCoherent -> problem(DoctorStatus.FAIL, DoctorReason.IDENTITY_INVALID)
                    }
                    if (!binding.identityIdMatches) problem(DoctorStatus.FAIL, DoctorReason.IDENTITY_ID_MISMATCH)
                    if (binding.duplicateRecord) problem(DoctorStatus.WARN, DoctorReason.DUPLICATE_BINDING)
                    if (binding.duplicateIdentifiers) problem(DoctorStatus.WARN, DoctorReason.LOCAL_CONFLICT)
                    if (binding.issuePresent) problem(DoctorStatus.WARN, DoctorReason.BINDING_ISSUE)
                    when (CloakHealth.cycloneState(binding.health)) {
                        CloakHealth.READY -> Unit
                        CloakHealth.UNKNOWN -> problem(DoctorStatus.WARN, DoctorReason.BINDING_PENDING)
                        CloakHealth.FAILED -> problem(DoctorStatus.FAIL, bindingReason(binding.health, DoctorReason.BINDING_FAILED))
                        else -> problem(DoctorStatus.WARN, bindingReason(binding.health, DoctorReason.BINDING_DEGRADED))
                    }
                }
                val status = when {
                    !binding.enabled -> DoctorStatus.WARN
                    problems.any { it.first == DoctorStatus.FAIL } -> DoctorStatus.FAIL
                    problems.isNotEmpty() -> DoctorStatus.WARN
                    else -> DoctorStatus.PASS
                }
                fun aspect(kind: DoctorCheckKind, reasons: Set<DoctorReason>, pass: DoctorReason) {
                    val selected = problems.filter { it.second in reasons }
                    val level = when {
                        selected.any { it.first == DoctorStatus.FAIL } -> DoctorStatus.FAIL
                        selected.isNotEmpty() -> DoctorStatus.WARN
                        else -> DoctorStatus.PASS
                    }
                    checks += DoctorCheck(kind, level, selected.map { it.second }.ifEmpty { listOf(pass) },
                        binding.displayContext, index + 1, binding.enabled)
                }
                checks += DoctorCheck(DoctorCheckKind.ENABLED, if (binding.enabled) DoctorStatus.PASS else DoctorStatus.WARN,
                    listOf(if (binding.enabled) DoctorReason.ENABLED_READY else DoctorReason.BINDING_DISABLED),
                    binding.displayContext, index + 1, binding.enabled)
                // Disabled rows are still inspectable; their checks never affect the verdict.
                run {
                    aspect(DoctorCheckKind.PROFILE, setOf(DoctorReason.PROFILE_UNKNOWN, DoctorReason.PROFILE_MISSING,
                        DoctorReason.PROFILE_NOT_READY, DoctorReason.USER_CHANGED, DoctorReason.APP_LIST_UNKNOWN,
                        DoctorReason.APP_NOT_LISTED), DoctorReason.PROFILE_READY)
                    aspect(DoctorCheckKind.IDENTITY, setOf(DoctorReason.IDENTITY_MISSING, DoctorReason.IDENTITY_UNREADABLE,
                        DoctorReason.IDENTITY_UNKNOWN, DoctorReason.IDENTITY_REMOTE, DoctorReason.IDENTITY_INVALID,
                        DoctorReason.IDENTITY_ID_MISMATCH), DoctorReason.IDENTITY_READY)
                    aspect(DoctorCheckKind.RECORDS, setOf(DoctorReason.USER_CHANGED, DoctorReason.DUPLICATE_BINDING,
                        DoctorReason.LOCAL_CONFLICT, DoctorReason.IDENTITY_ID_MISMATCH), DoctorReason.RECORDS_READY)
                }
                checks += DoctorCheck(DoctorCheckKind.BINDING, status,
                    if (!binding.enabled) listOf(DoctorReason.BINDING_DISABLED) else problems.map { it.second }.ifEmpty { listOf(DoctorReason.BINDING_READY) }, binding.displayContext, index + 1, binding.enabled)
            }
            val applicable = checks.filter { it.affectsReadiness }
            val verdict = when {
                applicable.any { it.status == DoctorStatus.FAIL } -> Readiness.FAILED
                applicable.any { it.status == DoctorStatus.WARN } -> Readiness.DEGRADED
                else -> Readiness.READY
            }
            return HealthDiagnostics(verdict, app, module, enabled, facts.bindings.size - enabled, checks)
        }

        private fun bindingReason(health: String, fallback: DoctorReason): DoctorReason = when (health) {
            "missing" -> DoctorReason.IDENTITY_MISSING
            "root approval needed" -> DoctorReason.ROOT_APPROVAL
            "Magisk approval timed out" -> DoctorReason.ROOT_TIMEOUT
            "Magisk unavailable" -> DoctorReason.ROOT_UNAVAILABLE
            "module missing" -> DoctorReason.MODULE_MISSING
            "module disabled" -> DoctorReason.MODULE_DISABLED
            "module removal pending" -> DoctorReason.MODULE_REMOVAL
            "module update needs reboot" -> DoctorReason.REBOOT_PENDING
            "module update needed" -> DoctorReason.MODULE_OUTDATED
            "module package invalid" -> DoctorReason.MODULE_INVALID
            "module install failed" -> DoctorReason.MODULE_INSTALL_FAILED
            "Zygisk disabled" -> DoctorReason.ZYGISK_DISABLED
            "Zygisk status unknown" -> DoctorReason.ZYGISK_UNKNOWN
            "unsupported architecture" -> DoctorReason.ABI_UNSUPPORTED
            "publish failed" -> DoctorReason.PUBLICATION_FAILED
            else -> fallback
        }
    }
}
