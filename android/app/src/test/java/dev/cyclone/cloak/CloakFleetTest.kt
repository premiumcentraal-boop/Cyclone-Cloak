package dev.cyclone.cloak

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CloakFleetTest {
    @Test
    fun fleetNamesAreDeterministicAndPadded() {
        assertEquals(listOf("Vault 01", "Vault 02", "Vault 03"), CloakFleet.fleetNames(3))
        assertEquals(99, CloakFleet.fleetNames(99).last().let { it.takeLast(2).toInt() })
    }

    @Test
    fun fleetNamesRejectImpossibleCounts() {
        assertThrows(IllegalArgumentException::class.java) { CloakFleet.fleetNames(0) }
        assertThrows(IllegalArgumentException::class.java) { CloakFleet.fleetNames(100) }
    }

    @Test
    fun forgedFleetIsDeterministic() {
        val first = CloakFleet.forgeFleet("pixel_7", 3).map { it.toString() }
        val again = CloakFleet.forgeFleet("pixel_7", 3).map { it.toString() }
        assertEquals(first, again)
        assertEquals(listOf("Vault 01", "Vault 02", "Vault 03"), CloakFleet.forgeFleet("pixel_7", 3).map { it.optString("name") })
    }

    @Test
    fun eachFleetProfileHasItsOwnIdentity() {
        val fleet = CloakFleet.forgeFleet("pixel_7", 5)
        val ids = fleet.map { it.getString("id") }.toSet()
        assertEquals(5, ids.size)
        val androidIds = fleet.map { it.getJSONObject("identifiers").getString("android_id") }.toSet()
        assertEquals(5, androidIds.size)
    }

    private fun cycloneProfile(id: String, userId: Int?, packages: List<String>): CycloneProfile =
        CycloneProfile(id, id, "profile", "ready", userId, packages)

    @Test
    fun bulkBindAssignsUnusedIdentitiesInOrder() {
        val profiles = listOf(
            cycloneProfile("Cyclone_00000000000000a1", 10, listOf("com.instagram.android")),
            cycloneProfile("Cyclone_00000000000000b2", 11, listOf("com.instagram.android")),
        )
        val plan = CloakFleet.planBulkBind(listOf("cloak1", "cloak2"), profiles, emptyList())
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
        val plan = CloakFleet.planBulkBind(listOf("cloak1", "cloak2"), profiles, existing)
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
        val plan = CloakFleet.planBulkBind(emptyList(), profiles, emptyList())
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
        val plan = CloakFleet.planBulkBind(listOf("cloak1", "cloak2"), profiles, emptyList())
        assertEquals(listOf("Cyclone_00000000000000b2"), plan.assignments.map { it.first.id })
    }

    @Test
    fun fleetExportImportRoundTrips() {
        val fleet = CloakFleet.forgeFleet("pixel_7", 3).map { it.optString("name") to it }
        val body = CloakFleet.exportFleet(fleet)
        val imported = CloakFleet.parseFleet(body.toString())
        assertEquals(fleet.map { it.second.toString() }, imported.map { it.toString() })
    }

    @Test
    fun fleetImportRejectsForeignFiles() {
        assertThrows(IllegalArgumentException::class.java) {
            CloakFleet.parseFleet(JSONObject().put("kind", "something-else").toString())
        }
    }
}
