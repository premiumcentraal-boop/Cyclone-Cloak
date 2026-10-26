package dev.cyclone.cloak.ui

import dev.cyclone.cloak.cyclone.CloakPillTone
import dev.cyclone.cloak.data.CloakSpace
import dev.cyclone.cloak.root.RootDoctorCode

/** The app's top-level destinations. Home answers "what needs attention?"; the rest are the working screens. */
internal enum class CloakDestination(val label: String) { HOME("Home"), IDENTITIES("Identities"), PROFILES("Profiles"), HEALTH("Health") }

internal data class AttentionItem(val title: String, val detail: String?, val tone: CloakStatusTone, val destination: CloakDestination)

/** One step of the phone → identity → bind path, so the user always knows what comes next. */
internal data class SetupStep(val title: String, val done: Boolean, val destination: CloakDestination)

/** Pure presentation of [CloakState] for the Home dashboard. Reads state only; never changes it. */
internal object HomeAttention {
    /** Null while the first Cyclone check is still running. */
    fun loading(state: CloakState): Boolean = state.report == null && state.connection.cached == null

    fun items(state: CloakState): List<AttentionItem> = buildList {
        if (state.report == null && state.connection.cached != null) {
            add(AttentionItem("Showing last seen Cyclone profiles", "Verifying the connection in the background.", CloakStatusTone.PENDING, CloakDestination.PROFILES))
        }
        val report = state.report
        if (report != null && statusTone(report.headline) != CloakStatusTone.READY) {
            add(AttentionItem("Cyclone: ${report.headline}", report.message.ifBlank { null }, statusTone(report.headline), CloakDestination.PROFILES))
        } else if (report != null && !state.canBind) {
            add(AttentionItem("Binding permissions not granted", report.message.ifBlank { null }, CloakStatusTone.PENDING, CloakDestination.PROFILES))
        }
        val doctor = state.rootDoctor
        when (doctor.code) {
            RootDoctorCode.READY, RootDoctorCode.CHECKING -> Unit
            RootDoctorCode.NOT_CHECKED -> add(AttentionItem("Root setup not checked", "Run Check & repair in Health.", CloakStatusTone.PENDING, CloakDestination.HEALTH))
            else -> add(AttentionItem("Root Doctor: ${doctor.title}", null, CloakStatusTone.ERROR, CloakDestination.HEALTH))
        }
        state.registry.forEach { profile ->
            val pill = state.pills[profile.id] ?: return@forEach
            if (pill.tone == CloakPillTone.ATTENTION) add(AttentionItem("${profile.label}: ${pill.label}", pill.reason, CloakStatusTone.ERROR, CloakDestination.PROFILES))
        }
        val notReady = state.bindings.filter { it.enabled && it.state != "ready" }
        if (notReady.isNotEmpty()) {
            add(AttentionItem(plural(notReady.size, "bound app isn't ready", "bound apps aren't ready"),
                notReady.map { it.state }.distinct().joinToString(", "), CloakStatusTone.PENDING, CloakDestination.PROFILES))
        }
        val known = report?.snapshot?.registry?.map { it.id }?.toSet()
        val orphaned = state.bindings.count { !CloakSpace.isSpaceId(it.profileId) && bindingProfileMissing(it.profileId, known) }
        if (orphaned > 0) add(AttentionItem(plural(orphaned, "binding points to a missing Cyclone profile", "bindings point to a missing Cyclone profile"),
            null, CloakStatusTone.PENDING, CloakDestination.PROFILES))
        val duplicates = state.uniqueness.duplicates.size
        if (duplicates > 0) add(AttentionItem(plural(duplicates, "identifier is shared between identities", "identifiers are shared between identities"),
            "Review duplicates in Health.", CloakStatusTone.PENDING, CloakDestination.HEALTH))
        if (state.unreadableIdentityIds.isNotEmpty()) add(AttentionItem(plural(state.unreadableIdentityIds.size, "identity couldn't be read", "identities couldn't be read"),
            null, CloakStatusTone.ERROR, CloakDestination.HEALTH))
    }

    fun steps(state: CloakState): List<SetupStep> = listOf(
        SetupStep("Pick or build a phone", state.phones.isNotEmpty(), CloakDestination.IDENTITIES),
        SetupStep("Create an identity from it", state.identities.isNotEmpty(), CloakDestination.IDENTITIES),
        SetupStep("Bind it to apps in a Cyclone profile", state.bindings.isNotEmpty(), CloakDestination.PROFILES),
    )

    /** The first unfinished step, or null when setup is complete. */
    fun nextStep(state: CloakState): SetupStep? = steps(state).firstOrNull { !it.done }

    private fun plural(n: Int, one: String, many: String) = "$n ${if (n == 1) one else many}"
}
