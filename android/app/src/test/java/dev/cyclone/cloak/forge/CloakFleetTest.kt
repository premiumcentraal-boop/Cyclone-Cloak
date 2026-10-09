package dev.cyclone.cloak.forge

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
