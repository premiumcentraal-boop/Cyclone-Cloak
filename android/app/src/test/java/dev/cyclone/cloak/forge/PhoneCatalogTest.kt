package dev.cyclone.cloak.forge

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneCatalogTest {
    @Test
    fun theBuiltInPhonesLoadAndAreCoherent() {
        val ids = TestPhones.all.map { it.id }
        assertEquals(listOf("pixel_4", "pixel_7", "galaxy_s23"), ids.take(3))
        assertTrue(ids.containsAll(listOf("pixel_6", "pixel_6a", "pixel_7a", "pixel_8")))
        assertEquals("catalog ids must be unique", ids.size, ids.toSet().size)
        assertTrue(ids.containsAll(listOf("pixel_6_pro", "pixel_7_pro", "pixel_8_pro", "pixel_8a",
            "pixel_9", "pixel_9_pro", "pixel_9_pro_xl", "pixel_9a")))
        assertTrue(ids.size >= 15)
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
    @Test fun optionalCatalogMetadataRoundTripsWithoutChangingProfiles() {
        val classes = mutableSetOf<String>()
        for (phone in TestPhones.all) {
            assertTrue(phone.confidence in setOf("public reference", "approximated", "needs verification"))
            val encoded = phone.toJson()
            val decoded = PhoneTemplate.fromJson(encoded)
            assertTrue(encoded.similar(decoded.toJson()))
            val exported = Imports.parse(Imports.exportPhone(phone).toString(), emptySet()) as Imports.Parsed.Phone
            assertTrue(encoded.similar(exported.phone.toJson()))
            val profile = CloakForge.forgeProfile("Catalog", "ab".repeat(32), phone)
            assertTrue(!profile.has("confidence") && !profile.has("classification"))
            phone.classification?.let { metadata ->
                val tags = metadata.getJSONArray("device_classes")
                for (i in 0 until tags.length()) classes.add(tags.getString(i))
                assertTrue(metadata.getString("hardware_feature_group").isNotBlank())
                assertTrue(metadata.getString("camera_class") in setOf("dual-rear-front", "triple-rear-front"))
            }
        }
        assertTrue(classes.containsAll(listOf("compact", "mainstream", "large-display", "midrange",
            "older-supported-device", "newer-supported-device")))
        val legacy = TestPhones["pixel_7"].toJson().also { it.remove("confidence"); it.remove("classification") }
        assertTrue(legacy.similar(PhoneTemplate.fromJson(legacy).toJson()))
    }

}
