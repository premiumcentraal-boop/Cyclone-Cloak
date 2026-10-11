package dev.cyclone.cloak.ui

import dev.cyclone.cloak.cyclone.*
import dev.cyclone.cloak.data.CloakBinding
import dev.cyclone.cloak.data.CloakBindingStore
import dev.cyclone.cloak.forge.ProfileUniqueness
import dev.cyclone.cloak.forge.ProfileValidator
import dev.cyclone.cloak.root.RootDoctorResult
import org.json.JSONObject
import java.security.MessageDigest

enum class BindingStep { PROFILE, APP, IDENTITY, REVIEW, RESULT }
data class BindingDraft(
    val step: BindingStep = BindingStep.PROFILE,
    val profileId: String? = null,
    val packageName: String? = null,
    val identityId: String? = null,
    val reviewStamp: String? = null,
    val acknowledged: Boolean = false,
    val running: Boolean = false,
    val outcome: BindingOutcome? = null,
    val note: String? = null,
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
        fun issue(field: String, code: String, message: String, warning: Boolean = false) {
            out += ProfileValidator.Finding(if (warning) ProfileValidator.WARNING else ProfileValidator.ERROR, code, message, field)
        }
        val profile = input.profiles.firstOrNull { it.id == draft.profileId }
        if (!input.canBind) issue("profile", "CONNECTOR_NOT_READY", "Refresh and approve profile settings in Cyclone → Settings → Connectors.")
        if (profile == null) issue("profile", "PROFILE_MISSING", "Choose a Cyclone profile from the latest synchronized list. Refresh if it is missing.")
        else {
            if (!profile.bindable) issue("profile", "PROFILE_NOT_READY", "This profile is not ready. Restore it or finish setup in Cyclone, then refresh.")
            if (profile.androidUserId == null) issue("profile", "ANDROID_USER_MISSING", "This profile has no Android user assignment. Finish setup in Cyclone, then refresh.")
            if (input.ownProfileId != null && input.ownProfileId != profile.id)
                issue("profile", "PROFILE_OUT_OF_SCOPE", "Bind this profile from Cloak in Main or from Cloak inside that profile.")
        }
        val packages = profile?.packages
        if (draft.packageName == null) issue("app", "APP_REQUIRED", "Choose an app from this profile's current list.")
        else if (packages == null || draft.packageName !in packages)
            issue("app", "APP_MISSING", "This app is missing from the current app list. Add it in Cyclone and refresh before binding.")
        val user = profile?.androidUserId
        if (profile != null && user != null && draft.packageName != null &&
            runCatching { CloakBindingStore.validate(profile.id, user, draft.packageName) }.isFailure)
            issue("app", "INVALID_TARGET", "The profile, Android user or app package is invalid. Refresh Cyclone's list.")
        val targets = input.bindings.filter { it.profileId == draft.profileId && it.packageName == draft.packageName }
        if (targets.size > 1) issue("app", "DUPLICATE_BINDING", "Multiple local records exist for this target. Review them in Profiles before binding.")
        if (targets.any { it.origin == CloakBinding.ORIGIN_MAIN })
            issue("app", "MANAGED_BY_MAIN", "This app binding is managed by Cloak in Main. Change it there.")
        if (targets.any { it.cloakProfileId != draft.identityId } && draft.identityId != null)
            issue("identity", "REPLACE_BINDING", "Confirmation replaces this app's existing compatibility profile. Go back to keep the current selection.", true)
        val identity = input.identities.firstOrNull { it.first == draft.identityId }?.second
        if (identity == null) issue("identity", "IDENTITY_MISSING", "Choose a readable saved compatibility profile or generate one.")
        else {
            if (identity.optString("id") != draft.identityId) issue("identity", "IDENTITY_REFERENCE_MISMATCH", "The saved profile reference is inconsistent. Review it in Identities before binding.")
            out += ProfileValidator.validateProfileResult(identity).findings.map { it.copy(field = "identity.${it.field.orEmpty()}") }
            out += ProfileUniqueness(input.identities).findings(identity, draft.identityId.orEmpty()).map {
                it.copy(field = "identity.${it.field}", message = "${it.message} Review or regenerate the value in Identities, or acknowledge keeping it.")
            }
            if (input.bindings.any { it.cloakProfileId == draft.identityId &&
                    (it.profileId != draft.profileId || it.packageName != draft.packageName) })
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
        val text = listOf(profile.toString(), draft.packageName, identity?.toString(),
            input.bindings.toString(), input.canBind, input.ownProfileId, findings(draft, input).toString()).joinToString("\n")
        return MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
    }
    fun canConfirm(draft: BindingDraft, input: BindingInputs): Boolean =
        draft.step == BindingStep.REVIEW && !draft.running && draft.reviewStamp == stamp(draft, input) &&
            findings(draft, input).none { it.isError } && (draft.acknowledged || findings(draft, input).none { it.severity == ProfileValidator.WARNING })

    fun record(draft: BindingDraft, input: BindingInputs, now: Long): CloakBinding {
        require(canConfirm(draft, input)) { "Review the current selection and resolve blocking findings first." }
        val profile = input.profiles.first { it.id == draft.profileId }
        val existing = input.bindings.singleOrNull { it.profileId == draft.profileId && it.packageName == draft.packageName }
        return existing?.copy(androidUserId = profile.androidUserId!!, cloakProfileId = draft.identityId!!,
            enabled = true, updatedAt = now, state = "pending") ?: CloakBinding(profile.id, profile.androidUserId!!,
            draft.packageName!!, draft.identityId!!, 0, true, now, "pending")
    }
    fun completion(draft: BindingDraft, root: RootDoctorResult, report: SyncReport?, records: List<CloakBinding>): BindingOutcome {
        val held = records.filter { it.profileId == draft.profileId && it.packageName == draft.packageName }
        val saved = held.singleOrNull()?.let { it.enabled && it.cloakProfileId == draft.identityId && it.origin == CloakBinding.ORIGIN_LOCAL } == true
        if (!saved) return BindingOutcome(false, false, "The saved target changed or is unavailable. Return to review; retry will not overwrite it.")
        val reconcile = report?.reconcile
        val issue = report?.issues?.get(bindingKey(draft.profileId!!, draft.packageName!!))
        val ready = root.published && report?.gate?.bindings == true && reconcile != null &&
            reconcile.stoppedBy == null && issue == null && CloakHealth.cycloneState(held.single().state) == CloakHealth.READY
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
