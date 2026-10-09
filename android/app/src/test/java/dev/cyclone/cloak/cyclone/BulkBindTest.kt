package dev.cyclone.cloak.cyclone

import dev.cyclone.cloak.data.CloakBinding
import org.junit.Assert.assertEquals
import org.junit.Test

class BulkBindTest {
    private fun cycloneProfile(id: String, userId: Int?, packages: List<String>): CycloneProfile =
        CycloneProfile(id, id, "profile", "ready", userId, packages)

    @Test
    fun bulkBindAssignsUnusedIdentitiesInOrder() {
        val profiles = listOf(
            cycloneProfile("Cyclone_00000000000000a1", 10, listOf("com.instagram.android")),
            cycloneProfile("Cyclone_00000000000000b2", 11, listOf("com.instagram.android")),
        )
        val plan = BulkBind.plan(listOf("cloak1", "cloak2"), profiles, emptyList())
        assertEquals(2, plan.assignments.size)
        assertEquals("cloak1", plan.assignments[0].second)
        assertEquals("cloak2", plan.assignments[1].second)
        assertEquals(0, plan.skippedBound)
    }

    @Test
    fun bulkBindSkipsAlreadyBoundProfilesAndReusesNoIdentity() {
        val profiles = listOf(
            cycloneProfile("Cyclone_00000000000000a1", 10, listOf("com.instagram.android")),
            cycloneProfile("Cyclone_00000000000000b2", 11, listOf("com.instagram.android")),
        )
        val existing = listOf(
            CloakBinding(
                profileId = "Cyclone_00000000000000a1",
                androidUserId = 10,
                packageName = "com.instagram.android",
                cloakProfileId = "cloak1",
                revision = 0,
                enabled = true,
                updatedAt = 0,
            ),
        )
        val plan = BulkBind.plan(listOf("cloak1", "cloak2"), profiles, existing)
        assertEquals(1, plan.assignments.size)
        assertEquals("Cyclone_00000000000000b2", plan.assignments[0].first.id)
        assertEquals("cloak2", plan.assignments[0].second)
        assertEquals(1, plan.skippedBound)
        assertEquals(0, plan.unusedCloakProfiles)
    }

    @Test
    fun bulkBindStopsWhenIdentitiesRunOut() {
        val profiles = listOf(
            cycloneProfile("Cyclone_00000000000000a1", 10, listOf("com.instagram.android")),
            cycloneProfile("Cyclone_00000000000000b2", 11, listOf("com.instagram.android")),
        )
        val plan = BulkBind.plan(emptyList(), profiles, emptyList())
        assertEquals(0, plan.assignments.size)
        assertEquals(0, plan.unusedCloakProfiles)
    }

    @Test
    fun bulkBindNeverBindsMainOrAProfileWithoutAUserNumber() {
        val profiles = listOf(
            CycloneProfile("owner", "This phone", "owner", "ready", null, listOf("com.instagram.android")),
            cycloneProfile("Cyclone_00000000000000a1", null, listOf("com.instagram.android")),
            CycloneProfile("Cyclone_00000000000000c3", "C", "profile", "setting_up", 12, listOf("com.instagram.android")),
            cycloneProfile("Cyclone_00000000000000b2", 11, listOf("com.instagram.android")),
        )
        val plan = BulkBind.plan(listOf("cloak1", "cloak2"), profiles, emptyList())
        assertEquals(listOf("Cyclone_00000000000000b2"), plan.assignments.map { it.first.id })
    }
}
