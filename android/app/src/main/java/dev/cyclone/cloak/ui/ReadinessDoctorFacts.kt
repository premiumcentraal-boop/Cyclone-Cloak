package dev.cyclone.cloak.ui

import dev.cyclone.cloak.data.CloakBinding
import dev.cyclone.cloak.forge.ProfileValidator

/** Read-only snapshot adapter. No publication, reconciliation or storage writes. */
internal object ReadinessDoctorFacts {
    fun fromState(state: CloakState, appVersion: String?): DoctorFacts {
        val profiles = state.report?.snapshot?.registry?.associateBy { it.id }
        val identities = state.identities.toMap()
        val duplicateTargets = state.bindings.groupBy { it.profileId to it.packageName }
            .filterValues { it.size > 1 }.keys
        val duplicateIdentities = state.uniqueness.duplicates.flatMap { it.profiles }.toSet()
        return DoctorFacts(state.rootDoctor, appVersion, state.report?.headline, state.syncing,
            state.report?.gate, state.providerState, state.bindings.map { binding ->
                val profile = profiles?.get(binding.profileId)
                val identity = identities[binding.cloakProfileId]
                val remote = binding.origin == CloakBinding.ORIGIN_MAIN
                BindingDoctorFacts(
                    displayContext = "${profile?.label ?: binding.profileId} · ${binding.packageName}",
                    enabled = binding.enabled, health = binding.state,
                    profilePresent = profiles?.containsKey(binding.profileId), profileReady = profile?.bindable,
                    userMatches = profile?.androidUserId?.let { it == binding.androidUserId },
                    appListed = profile?.packages?.contains(binding.packageName),
                    identityPresent = identity != null || binding.cloakProfileId in state.unreadableIdentityIds,
                    issuePresent = state.report?.issues?.containsKey("${binding.profileId}\n${binding.packageName}") == true,
                    identityReadable = if (remote || !state.identityInventoryKnown) null else identity != null,
                    identityCoherent = identity?.let { !ProfileValidator.validateProfileResult(it).hasErrors } ?: true,
                    identityIdMatches = identity?.optString("id")?.let { it == binding.cloakProfileId } ?: true,
                    remoteIdentity = remote,
                    duplicateRecord = (binding.profileId to binding.packageName) in duplicateTargets,
                    duplicateIdentifiers = binding.cloakProfileId in duplicateIdentities,
                )
            }, localInventoryKnown = state.identityInventoryKnown,
            unreadableIdentities = state.unreadableIdentityIds.isNotEmpty(),
            conflictingIdentities = duplicateIdentities.isNotEmpty(),
            mismatchedIdentities = state.identities.any { (id, p) -> p.optString("id") != id },
            syncIssues = state.report?.issues?.isNotEmpty() == true,
            reconciling = state.report?.reconciliationPending == true)
    }
}
