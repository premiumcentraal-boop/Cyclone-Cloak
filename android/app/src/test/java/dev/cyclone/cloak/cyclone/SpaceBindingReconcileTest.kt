package dev.cyclone.cloak.cyclone

import dev.cyclone.cloak.data.CloakBinding
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SpaceBindingReconcileTest {
    private val space = "Cloak_0123456789abcdef"
    private val profile = "Cyclone_bbbbbbbbbbbbbbbb"
    private val snapshot = ProfilesSnapshot(
        current = null,
        profiles = listOf(CycloneProfile(profile, "Profile B", "profile", "ready", 11, listOf("com.example.app"))),
    )

    private fun binding(profileId: String, user: Int) =
        CloakBinding(profileId, user, "com.example.app", "identity-1", 1, true, 1L, "ready")

    @Test
    fun aSpaceBindingIsNeverReportedMissingOrPushedToCyclone() {
        val plan = ReconcilePlanner.plan(
            Placement.Main,
            snapshot,
            listOf(binding(space, 10), binding(profile, 11)),
        ) { JSONObject() }
        assertFalse(plan.issues.keys.any { it.startsWith(space) })
        assertFalse(plan.preserve.any { it.startsWith(space) })
        assertEquals(listOf(profile), plan.desired.map { it.profileId })
    }
}
