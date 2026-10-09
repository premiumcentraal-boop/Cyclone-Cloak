package dev.cyclone.cloak.forge

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneCatalogTest {
    @Test
    fun theBuiltInPhonesLoadAndAreCoherent() {
        assertEquals(listOf("pixel_4", "pixel_7", "galaxy_s23"), TestPhones.all.map { it.id })
        for (phone in TestPhones.all) {
            assertTrue(phone.builtIn)
            assertEquals(phone.id, emptyList<ProfileValidator.Finding>(), ProfileValidator.validatePhone(phone))
            val profile = CloakForge.forgeProfile("Vault 01", "ab".repeat(32), phone)
            assertEquals(phone.id, emptyList<ProfileValidator.Finding>(), ProfileValidator.validateProfile(profile))
            assertEquals(phone.id, profile.getJSONObject("meta").getString("template"))
        }
        assertEquals("Google · Android 13 · panther", TestPhones["pixel_7"].summary)
    }

    @Test
    fun aPhoneRoundTripsThroughJsonAndItsExportFile() {
        val phone = TestPhones["galaxy_s23"].copy(builtIn = false)
        val again = PhoneTemplate.fromJson(JSONObject(phone.toJson().toString()))
        assertEquals(phone.toJson().toString(), again.toJson().toString())
        val parsed = Imports.parse(Imports.exportPhone(phone).toString(), takenPhoneIds = emptySet()) as Imports.Parsed.Phone
        assertEquals("galaxy_s23", parsed.phone.id)
        assertTrue(parsed.findings.isEmpty())
    }

    @Test
    fun idsAreSafeAndUnique() {
        assertEquals("pixel_8_pro", PhoneTemplate.idFor("Pixel 8 Pro!", emptySet()))
        assertEquals("pixel_8_2", PhoneTemplate.idFor("Pixel 8", setOf("pixel_8")))
        assertEquals("phone", PhoneTemplate.idFor("   ", emptySet()))
        assertTrue(runCatching { PhoneTemplate.fromJson(JSONObject().put("id", "../x")) }.isFailure)
    }

    @Test
    fun aPhoneImportedUnderATakenIdGetsANewOne() {
        val phone = TestPhones["pixel_7"]
        val parsed = Imports.parse(Imports.exportPhone(phone).toString(), setOf("pixel_7")) as Imports.Parsed.Phone
        assertEquals("pixel_7_2", parsed.phone.id)
    }
}
