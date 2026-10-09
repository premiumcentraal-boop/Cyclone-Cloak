package dev.cyclone.cloak.forge

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportsTest {
    private val profile = CloakForge.forgeProfile("Work phone", "cd".repeat(32), TestPhones["pixel_7"])

    @Test
    fun aGoodProfileImportsWithoutFindings() {
        val parsed = Imports.parse(profile.toString(), emptySet()) as Imports.Parsed.Profile
        assertTrue(parsed.findings.isEmpty())
    }

    @Test
    fun anIncoherentProfileSaysExactlyWhatIsWrongAndWhere() {
        val bad = JSONObject(profile.toString())
        bad.getJSONObject("device").put("sdk_int", 34)
        val parsed = Imports.parse(bad.toString(), emptySet()) as Imports.Parsed.Profile
        val finding = parsed.findings.single()
        assertEquals("RELEASE_SDK_MISMATCH", finding.code)
        assertEquals("device.sdk_int", finding.field)
        assertEquals("release '13' implies SDK 33, profile says 34", finding.message)
    }

    @Test
    fun fleetsAreRecognised() {
        val fleet = CloakFleet.exportFleet(CloakFleet.forgeFleet(TestPhones["pixel_4"], 2).map { it.getString("name") to it })
        assertEquals(2, (Imports.parse(fleet.toString(), emptySet()) as Imports.Parsed.Fleet).profiles.size)
    }

    @Test
    fun foreignFilesAreRefusedWithAReason() {
        for (text in listOf("{\"hello\":1}", "{broken", "just some words")) {
            val error = runCatching { Imports.parse(text, emptySet()) }.exceptionOrNull()
            assertTrue(text, error is IllegalArgumentException && !error.message.isNullOrBlank())
        }
    }

    @Test
    fun aPhoneCanBeTakenFromAnyProfile() {
        val phone = PhoneTemplate.fromProfile("from_profile", "From profile", profile)
        assertTrue(ProfileValidator.validatePhone(phone).isEmpty())
        assertEquals("Pixel 7", phone.device.getString("model"))
    }
}
