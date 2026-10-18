package dev.cyclone.cloak.ui

import dev.cyclone.cloak.data.CloakBinding
import dev.cyclone.cloak.cyclone.CycloneProfile
import dev.cyclone.cloak.forge.ProfileValidator
import org.json.JSONObject

enum class ProfileAction { BIND, BIND_ALL, REPLACE }

/** UI plans use existing per-app records and validators. No runtime or connector contract. */
object ProfileOperations {
    fun oneTapBindAll(draft: BindingDraft, input: BindingInputs): Boolean =
        draft.action == ProfileAction.BIND_ALL && draft.identityId != null &&
            BindingFlow.findings(draft, input).none { it.isError || it.code in setOf("REPLACE_BINDING", "IDENTITY_ALREADY_BOUND", "DUPLICATE_IDENTIFIER") }

    fun availablePrefix(prefix: String, library: List<JSONObject>): String {
        val base = prefix.trim().ifBlank { "Device" }
        var candidate = base
        var suffix = 2
        while (library.any { it.optString("name").startsWith("$candidate ", ignoreCase = true) }) {
            val ending = " ${suffix++}"
            candidate = base.take(32 - ending.length).trimEnd() + ending
        }
        return candidate
    }
    fun draft(profile: CycloneProfile, input: BindingInputs, action: ProfileAction): BindingDraft {
        val held = input.bindings.filter { it.profileId == profile.id }
        val apps = if (action == ProfileAction.REPLACE) held.map { it.packageName }.toSet()
            else profile.packages.orEmpty().toSet()
        val identity = held.map { it.cloakProfileId }.distinct().singleOrNull()
        return BindingDraft(step = BindingStep.IDENTITY, profileId = profile.id, packageNames = apps,
            identityId = if (action == ProfileAction.REPLACE) null else identity, action = action,
            originalUser = profile.androidUserId,
            originalBindings = if (action == ProfileAction.REPLACE) held else emptyList(), note = when {
                profile.androidUserId == null -> "Finish Android user setup in Cyclone, then refresh before binding."
                profile.packages.isNullOrEmpty() -> "Add apps in Cyclone, then refresh the app list before binding."
                action == ProfileAction.REPLACE && held.isEmpty() -> "No apps are bound yet. Close this flow and choose Bind all apps first."
                else -> null
            })
    }

    fun replacementFindings(draft: BindingDraft, input: BindingInputs): List<ProfileValidator.Finding> {
        if (draft.action != ProfileAction.REPLACE) return emptyList()
        val current = input.bindings.filter { it.profileId == draft.profileId }
        val profile = input.profiles.firstOrNull { it.id == draft.profileId }
        return buildList {
            fun error(code: String, message: String) {
                add(ProfileValidator.Finding(ProfileValidator.ERROR, code, message, "profile"))
            }
            if (draft.originalBindings.isEmpty()) error("NO_BOUND_APPS", "Bind apps to this Cyclone profile before replacing its device profile.")
            if (profile?.androidUserId != draft.originalUser || current != draft.originalBindings)
                error("REPLACEMENT_CHANGED", "The Android user or bindings changed. Close this review and choose Replace device profile again.")
            if (draft.originalBindings.any { it.androidUserId != draft.originalUser })
                error("USER_MAPPING_CHANGED", "Binding user mapping differs from Cyclone. Refresh and resolve the mapping before replacing.")
        }
    }

    fun before(draft: BindingDraft): String = draft.originalBindings.map { it.cloakProfileId }.distinct().joinToString()
}
