package dev.cyclone.cloak.cyclone

import dev.cyclone.cloak.data.CloakBinding

/** Planning a bulk bind: one unused fleet identity per ready Cyclone profile that has no bindings yet. Pure. */
object BulkBind {
    data class Plan(
        val assignments: List<Pair<CycloneProfile, String>>,
        val skippedBound: Int,
        val unusedCloakProfiles: Int,
    )

    /**
     * Plans a bulk bind: every ready Cyclone profile that has no bindings yet
     * gets the next unused cloak profile. Already-bound Cyclone profiles are
     * left untouched, and cloak profiles in use are never handed out twice.
     */
    fun plan(
        cloakProfileIds: List<String>,
        cycloneProfiles: List<CycloneProfile>,
        existingBindings: List<CloakBinding>,
    ): Plan {
        val usedCloak = existingBindings.map { it.cloakProfileId }.toMutableSet()
        val boundProfiles = existingBindings.map { it.profileId }.toSet()
        val assignments = mutableListOf<Pair<CycloneProfile, String>>()
        var skippedBound = 0
        for (profile in cycloneProfiles) {
            // Never Main, never a profile without a known Android user number.
            if (!profile.bindable) continue
            val packages = profile.packages.orEmpty()
            if (packages.isEmpty()) continue
            if (profile.id in boundProfiles) {
                skippedBound++
                continue
            }
            val cloakId = cloakProfileIds.firstOrNull { it !in usedCloak } ?: break
            usedCloak.add(cloakId)
            assignments.add(profile to cloakId)
        }
        val available = cloakProfileIds.count { it !in usedCloak }
        return Plan(assignments, skippedBound, available)
    }
}
