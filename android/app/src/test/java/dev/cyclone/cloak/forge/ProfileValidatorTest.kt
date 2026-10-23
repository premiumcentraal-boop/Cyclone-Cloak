package dev.cyclone.cloak.forge

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ProfileValidatorTest {
    private fun profile() = CloakForge.forgeProfile("Validation test", "ab".repeat(32), TestPhones["pixel_7"])

    @Test fun androidWebViewUserAgentPreservesExactValueAndCoherence() {
        for (suffix in listOf("", "; wv", " Build/TQ3A.230805.001", " Build/TQ3A.230805.001; wv")) {
            val profile = profile()
            val value = "Mozilla/5.0 (Linux; Android 13; Pixel 7$suffix) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Version/4.0 Chrome/155.0.8059.43 Mobile Safari/537.36"
            profile.put("ua", JSONObject().put("value", value))
            assertFalse(ProfileValidator.validateProfile(profile).any { it.isError })
            assertEquals(value, profile.getJSONObject("ua").getString("value"))
        }
    }

    @Test fun webViewStillChecksModelReleaseAndSyntax() {
        val value = "Mozilla/5.0 (Linux; Android 13; Pixel 7 Build/TQ3A.230805.001; wv) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Version/4.0 Chrome/155.0.8059.43 Mobile Safari/537.36"
        for ((replacement, code) in listOf(
            ("Pixel 7" to "Pixel 8") to "UA_MODEL_MISMATCH",
            ("Android 13" to "Android 16") to "UA_RELEASE_MISMATCH",
            ("Version/4.0" to "Version/broken") to "UA_FORMAT",
        )) {
            val profile = profile().put("ua", JSONObject().put("value", value.replace(replacement.first, replacement.second)))
            assertTrue(ProfileValidator.validateProfile(profile).any { it.code == code })
        }
    }

    @Test fun webViewRealityAndVendorSensorsUseTheSharedSchema() {
        val profile = CloakForge.forgeProfile("Synthetic host syntax", "ab".repeat(32), TestPhones["pixel_7"], "0.4")
        val value = "Mozilla/5.0 (Linux; Android 13; Pixel 7 Build/TQ3A.230805.001; wv) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Version/4.0 Chrome/155.0.8059.43 Mobile Safari/537.36"
        profile.put("ua", JSONObject().put("value", value))
        val reality = profile.getJSONObject("reality")
        reality.getJSONObject("webview").getJSONObject("values")
            .put("user_agent", value).put("version", "155.0.8059.43")
        reality.put("sensors", JSONObject().put("availability", "known").put("source", "derived")
            .put("reason", "Synthetic descriptor checks the shared real-API bounds, not hardware evidence.")
            .put("values", JSONObject().put("inventory", org.json.JSONArray().put(JSONObject()
                .put("type", 131073).put("name", "Synthetic vendor sensor").put("min_delay_us", -1)
                .put("max_delay_us", 0).put("range", Float.MAX_VALUE.toDouble()).put("resolution", 0)))))
        assertFalse(ProfileValidator.validateProfile(profile).any { it.isError })
    }

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

    @Test
    fun eachCoherenceRuleGroupReportsItsFieldAndStableReason() {
        val cases = listOf(
            listOf("device.manufacturer", "Samsung", "warning", "MODEL_MANUFACTURER_MISMATCH"),
            listOf("device.sdk_int", 34, "error", "RELEASE_SDK_MISMATCH"),
            listOf("device.brand", "other", "error", "FP_BRAND_MISMATCH"),
            listOf("display.height", 6000, "warning", "DISPLAY_ASPECT"),
            listOf("display.density", 1600, "warning", "DISPLAY_DENSITY_MISMATCH"),
            listOf("locale.timezone", "Europe//Berlin", "error", "LOCALE"),
            listOf("identifiers.serial", "bad serial", "error", "IDENTIFIER_FORMAT"),
            listOf("telephony.carrier_name", "Carrier\nInjected", "error", "TELEPHONY_CARRIER_FORMAT"),
            listOf("telephony.carrier_name", 123, "error", "TELEPHONY_CARRIER"),
            listOf("telephony.mcc", "310\n", "error", "TELEPHONY_MCC"),
            listOf("telephony.mcc", "٣١٠", "error", "TELEPHONY_MCC"),
            listOf("telephony.mcc", "000", "info", "TELEPHONY_PLACEHOLDER_OPERATOR"),
            listOf("network.egress_hint", "x".repeat(129), "error", "NETWORK_HINT_FORMAT"),
            listOf("network.egress_hint", "region\u0000", "error", "NETWORK_HINT_FORMAT"),
            listOf("device.bootloader", 123, "error", "HARDWARE_FIELD_FORMAT"),
            listOf("device.baseband", "none", "warning", "HARDWARE_MODEM_UNSPECIFIED"),
            listOf("device.first_api_level", 28, "warning", "HARDWARE_NETWORK_GENERATION"),
            listOf("device.hardware", "generic", "info", "HARDWARE_UNSPECIFIED"),
        )
        for ((path, value, severity, code) in cases) {
            val profile = profile()
            val parts = (path as String).split('.')
            profile.getJSONObject(parts[0]).put(parts[1], value)
            val result = ProfileValidator.validateProfileResult(profile)
            val field = when (code) { "DISPLAY_ASPECT" -> "display.width"; "HARDWARE_NETWORK_GENERATION" -> "telephony.network_type"; else -> path }
            val finding = result.findings.single { it.code == code && it.field == field }
            assertEquals(path, severity, finding.severity)
            assertTrue(path, finding.message.startsWith(field as String))
            assertEquals(path, severity == "error", result.hasErrors)
        }
    }

    @Test
    fun summaryIsSortedBySeverityFieldCodeAndMessageAndCannotBeMutated() {
        val source = mutableListOf(
            ProfileValidator.Finding("info", "NOTE", "note", "a"),
            ProfileValidator.Finding("warning", "WARN", "warning", "a"),
            ProfileValidator.Finding("error", "Z_CODE", "z reason", "b"),
            ProfileValidator.Finding("error", "A_CODE", "b reason", "b"),
            ProfileValidator.Finding("error", "A_CODE", "a reason", "b"),
            ProfileValidator.Finding("error", "ROOT", "root reason"),
            ProfileValidator.Finding("error", "LAST", "field first", "a"),
        )
        val result = ProfileValidator.summarize(source)
        assertEquals(result.findings, ProfileValidator.summarize(source.reversed()).findings)
        assertEquals(listOf("ROOT", "LAST", "A_CODE", "A_CODE", "Z_CODE", "WARN", "NOTE"), result.findings.map { it.code })
        assertEquals(listOf("a reason", "b reason"), result.findings.slice(2..3).map { it.message })
        assertEquals(5, result.errorCount)
        assertEquals(1, result.warningCount)
        assertEquals(1, result.infoCount)
        assertTrue(result.hasErrors)
        source.clear()
        assertEquals(7, result.findings.size)
        assertThrows(UnsupportedOperationException::class.java) { (result.findings as MutableList).clear() }
    }

    @Test
    fun summaryAndLegacyApiMatchWithoutMutatingProfile() {
        val profile = profile()
        profile.getJSONObject("device").put("hardware", "generic").put("baseband", "none")
        profile.getJSONObject("display").put("width", 0)
        val before = JSONObject(profile.toString())
        val result = ProfileValidator.validateProfileResult(profile)
        assertEquals(ProfileValidator.validateProfile(profile), result.findings)
        assertEquals(1, result.errorCount)
        assertEquals(1, result.warningCount)
        assertEquals(1, result.infoCount)
        assertTrue(before.similar(profile))
    }

    @Test
    fun validOpaqueNetworkHintsAndMissingOptionalHardwareAreAccepted() {
        val profile = profile()
        profile.getJSONObject("device").remove("baseband")
        profile.getJSONObject("device").remove("bootloader")
        profile.remove("network")
        assertTrue(ProfileValidator.validateProfileResult(profile).findings.isEmpty())
        profile.put("network", JSONObject().put("egress_hint", "😀".repeat(128)))
        assertTrue(ProfileValidator.validateProfile(profile).isEmpty())
        val phone = PhoneTemplate.fromProfile("test", "test", profile)
        assertEquals(ProfileValidator.validatePhone(phone), ProfileValidator.validatePhoneResult(phone).findings)
        phone.network.put("egress_hint", "bad\n")
        assertTrue(ProfileValidator.validatePhone(phone).any { it.code == "NETWORK_HINT_FORMAT" && it.isError })
    }

    @Test
    fun findingsRejectInvalidSeverityCodeOrEmptyExplanation() {
        assertThrows(IllegalArgumentException::class.java) { ProfileValidator.Finding("fatal", "CODE", "reason") }
        assertThrows(IllegalArgumentException::class.java) { ProfileValidator.Finding("info", "bad-code", "reason") }
        assertThrows(IllegalArgumentException::class.java) { ProfileValidator.Finding("warning", "CODE", " ") }
    }

    @Test
    fun malformedTelephonyReportsOnlyOneBlockFailure() {
        val profile = profile().put("telephony", org.json.JSONArray())
        assertEquals(1, ProfileValidator.validateProfile(profile).count { it.code == "BLOCK_TYPE" && it.field == "telephony" })
    }
}
