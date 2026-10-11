package dev.cyclone.cloak.forge

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ProfileValidatorTest {
    private fun profile() = CloakForge.forgeProfile("Validation test", "ab".repeat(32), TestPhones["pixel_7"])

    @Test
    fun invalidApiTypesHaveMachineReadableFieldReasons() {
        for (value in listOf(true, "33", 33.5)) {
            val profile = profile()
            profile.getJSONObject("device").put("sdk_int", value)
            val finding = ProfileValidator.validateProfile(profile).single { it.code == "API_TYPE" }
            assertTrue(finding.isError)
            assertEquals("device.sdk_int", finding.field)
            assertEquals("device.sdk_int must be an integer", finding.message)
        }
    }

    @Test
    fun brokenFingerprintDoesNotHideApiErrors() {
        val profile = profile()
        profile.getJSONObject("device").put("fingerprint", "broken").put("sdk_int", "invalid").put("first_api_level", 1)
        assertTrue(ProfileValidator.validateProfile(profile).map { it.code }.containsAll(listOf("FINGERPRINT_FORMAT", "API_TYPE", "FIRST_API_RANGE")))
    }

    @Test
    fun suspiciousManufacturerAndDensityAreWarningsOnly() {
        val profile = profile()
        profile.getJSONObject("device").put("manufacturer", "Samsung")
        profile.getJSONObject("display").put("density", 1600)
        val findings = ProfileValidator.validateProfile(profile)
        assertFalse(findings.any { it.isError })
        assertTrue(findings.any { it.code == "MODEL_MANUFACTURER_MISMATCH" && it.field == "device.manufacturer" })
        assertTrue(findings.any { it.code == "DISPLAY_DENSITY_MISMATCH" && it.field == "display.density" })
    }

    @Test
    fun invalidIdentifierAndLocaleFormatsReportTheirOwnFields() {
        val profile = profile()
        profile.getJSONObject("identifiers").put("android_id", "0123456789abcdef\n").put("mac", "FF:FF:FF:FF:FF:FF")
        profile.getJSONObject("locale").put("timezone", "Europe/../Berlin")
        val findings = ProfileValidator.validateProfile(profile)
        for ((code, field) in listOf("IDENTIFIER_FORMAT" to "identifiers.android_id", "IDENTIFIER_ADDRESS" to "identifiers.mac", "LOCALE" to "locale.timezone")) {
            val finding = findings.single { it.code == code && it.field == field }
            assertTrue(finding.isError)
            assertTrue(finding.message.startsWith(field))
        }
    }

    @Test
    fun invalidOptionalBlocksDoNotDisappear() {
        val profile = profile().put("locale", JSONObject.NULL).put("display", "1080x2400")
        assertEquals(setOf("locale", "display"), ProfileValidator.validateProfile(profile).filter { it.code == "BLOCK_TYPE" }.map { it.field }.toSet())
    }

    @Test
    fun validTemplatesAndPhonesStayValidWithoutMutation() {
        for (phone in TestPhones.all) {
            val profile = CloakForge.forgeProfile("Validation test", "ab".repeat(32), phone)
            val before = JSONObject(profile.toString())
            assertTrue(ProfileValidator.validateProfile(profile).isEmpty())
            assertTrue(ProfileValidator.validatePhone(phone).isEmpty())
            assertTrue(before.similar(profile))
        }
    }
}
