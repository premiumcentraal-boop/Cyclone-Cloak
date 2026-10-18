package dev.cyclone.cloak.forge

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class RealityStudioTest {
    private val phone = TestPhones["pixel_7"]
    private fun profile() = CloakForge.forgeProfile("Studio", "ab".repeat(32), phone, "0.4")
    @Test fun editIsValidatedAndInputIsNotMutated() {
        val p = profile(); val before = p.toString(); val s = RealityStudio.Session(p)
        val result = s.edit("reality.memory.values.storage_gib", "256")
        assertTrue(result.valid); assertEquals(256, DeviceReality.at(s.body, "reality.memory.values.storage_gib"))
        assertTrue(result.changes.any { it.path == "reality.memory.values.storage_gib" })
        assertTrue("storage_gib" in RealityStudio.userFields(s.body.getJSONObject("reality").getJSONObject("memory")))
        assertEquals(before, p.toString())
    }
    @Test fun contradictionHasPreciseReasonAndLeavesDraftUnchanged() {
        val s = RealityStudio.Session(profile()); val before = s.body.toString()
        val invalid = s.edit("reality.memory.values.ram_gib", "2")
        assertFalse(invalid.valid); assertTrue(invalid.findings.any { it.code == "REALITY_STORAGE" && it.field == "reality.memory" })
        assertEquals(before, s.body.toString())
        assertFalse(s.edit("reality.panel.values.width", "999").valid)
        assertFalse(s.edit("reality.power.values.battery_percent", "101").valid)
    }
    @Test fun locksPreserveUserValuesAcrossDeterministicRegeneration() {
        val path = "reality.radio.values.wifi_name"
        val s = RealityStudio.Session(profile())
        assertTrue(s.edit(path, "\"Preserved local label\"").valid)
        s.lock(path, true)
        s.regenerate("radio", phone)
        assertEquals("Preserved local label", DeviceReality.at(s.body, path))
        assertThrows(IllegalArgumentException::class.java) { s.edit(path, "\"Other\"") }
        s.lock(path, false)
        s.regenerate("radio", phone)
        assertEquals(DeviceReality.at(profile(), path), DeviceReality.at(s.body, path))
        assertTrue(s.regenerate("radio", phone).valid)
    }
    @Test fun repairFillsOnlyMissingFactsWithoutInventingUnavailableHardware() {
        val p = profile()
        p.getJSONObject("reality").getJSONObject("compute").getJSONObject("values").remove("soc")
        p.getJSONObject("reality").getJSONObject("memory").getJSONObject("values").put("storage_gib", 256)
        p.getJSONObject("reality").getJSONObject("power").put("availability", "unavailable").put("values", JSONObject())
        val s = RealityStudio.Session(p)
        val result = s.repair(phone)
        assertTrue(result.valid)
        assertEquals("Google Tensor G2", DeviceReality.at(s.body, "reality.compute.values.soc"))
        assertEquals(256, DeviceReality.at(s.body, "reality.memory.values.storage_gib"))
        assertEquals("unavailable", DeviceReality.at(s.body, "reality.power.availability"))
        assertEquals("unknown", DeviceReality.at(s.body, "reality.sensors.availability"))
        assertFalse(result.changes.any { it.path.startsWith("reality.power") })
    }
    @Test fun missingLockedFieldAndUnavailableFactsStayUntouched() {
        val p = profile(); p.getJSONObject("reality").getJSONObject("memory").getJSONObject("values").remove("ram_gib")
        val s = RealityStudio.Session(p); s.lock("reality.memory.values.ram_gib", true)
        s.repair(phone)
        assertNull(DeviceReality.at(s.body, "reality.memory.values.ram_gib"))
        assertThrows(IllegalArgumentException::class.java) { s.repair(TestPhones["pixel_7_pro"]) }
    }
    @Test fun malformedUnknownPrivateAndLegacyEditsCannotChangeDraft() {
        val s = RealityStudio.Session(profile())
        assertThrows(IllegalArgumentException::class.java) { s.edit("reality.compute.values.keybox", "\"secret\"") }
        assertThrows(IllegalArgumentException::class.java) { s.edit("reality.compute.values.cores", "{\"x\":1,\"x\":2}") }
        val old = CloakForge.forgeProfile("Old", "ab".repeat(32), phone)
        val legacy = RealityStudio.Session(old)
        assertThrows(IllegalArgumentException::class.java) { legacy.repair(phone) }
        assertEquals(old.toString(), legacy.body.toString())
    }
    @Test fun explicitSaveOnlyWritesProfileAndRejectsStaleOrInvalidProposals() {
        val p = profile(); val s = RealityStudio.Session(p)
        val preview = s.edit("reality.memory.values.storage_gib", "256")
        var writes = 0
        assertThrows(IllegalArgumentException::class.java) { RealityStudio.save(preview, p, listOf(p.getString("id") to p), false) { writes++ } }
        assertThrows(IllegalArgumentException::class.java) { RealityStudio.save(preview, JSONObject(p.toString()).put("name", "Changed"), emptyList(), true) { writes++ } }
        val bad = JSONObject(preview.body).put("seed", "cd".repeat(32))
        assertThrows(IllegalArgumentException::class.java) { RealityStudio.save(preview.copy(body = bad.toString()), p, emptyList(), true) { writes++ } }
        RealityStudio.save(preview, p, listOf(p.getString("id") to p), true) { writes++; assertEquals(p.getString("id"), it.getString("id")) }
        assertEquals(1, writes)
        val invalid = s.edit("reality.panel.values.width", "123")
        assertThrows(IllegalArgumentException::class.java) { RealityStudio.save(invalid, p, emptyList(), true) { writes++ } }
        assertEquals(1, writes)
    }
    @Test fun duplicatesAreCheckedAtSaveAndPreviewRedactsPrivateNames() {
        val p = profile(); val other = CloakForge.forgeProfile("Other", "cd".repeat(32), phone, "0.4")
        val s = RealityStudio.Session(p); val path = "reality.radio.values.wifi_name"
        val value = DeviceReality.at(other, path) as String
        val result = s.edit(path, JSONObject.quote(value))
        assertTrue(result.valid)
        assertFalse(result.changes.toString().contains(value))
        assertThrows(IllegalArgumentException::class.java) { RealityStudio.save(result, p, listOf(other.getString("id") to other), true) { fail("duplicate saved") } }
    }
}
