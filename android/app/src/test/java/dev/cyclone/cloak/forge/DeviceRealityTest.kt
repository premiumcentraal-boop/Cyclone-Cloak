package dev.cyclone.cloak.forge

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class DeviceRealityTest {
    private fun vector() = JSONObject(javaClass.classLoader!!.getResource("reality.json")!!.readText())
    private fun profile() = vector().getJSONObject("profile")
    @Test fun generationMatchesPythonWithoutMutation() {
        val phone = TestPhones["pixel_7"]; val before = phone.toJson().toString()
        val generated = CloakForge.forgeProfile("Reality", "ab".repeat(32), phone, "0.4")
        assertTrue(generated.similar(profile()))
        assertEquals(before, phone.toJson().toString())
        assertTrue(ProfileValidator.errors(generated).isEmpty())
        val importedTemplate = PhoneTemplate.fromProfile("imported", "Imported", generated)
        assertTrue(importedTemplate.reality.getJSONObject("compute").similar(phone.reality.getJSONObject("compute")))
        assertFalse(importedTemplate.reality.has("inventory_id"))
        assertTrue(ProfileValidator.validatePhone(importedTemplate).none { it.isError })
        assertTrue(ProfileValidator.errors(CloakForge.forgeProfile("Imported", "cd".repeat(32), importedTemplate, "0.4")).isEmpty())
        assertThrows(IllegalArgumentException::class.java) { PhoneTemplate.fromJson(phone.toJson().put("reality", "bad")) }
    }
    @Test fun suppliedWebViewVersionAndRadioStateArePreservedAndCoherent() {
        val reality = JSONObject(TestPhones["pixel_7"].reality.toString())
        reality.put("webview", JSONObject().put("availability", "known").put("source", "approximated").put("reason", "Test provider")
            .put("values", JSONObject().put("provider", "com.google.android.webview").put("version", "122.0.6261.115")))
        reality.put("radio", JSONObject().put("availability", "known").put("source", "approximated").put("reason", "Test state")
            .put("values", JSONObject().put("wifi_state", "disabled")))
        val phone = TestPhones["pixel_7"].copy(reality = reality)
        assertTrue(ProfileValidator.validatePhone(phone).none { it.isError })
        val p = CloakForge.forgeProfile("Reality", "ab".repeat(32), phone, "0.4")
        assertTrue(ProfileValidator.errors(p).isEmpty())
        assertTrue(p.getJSONObject("ua").getString("value").contains("Chrome/122.0.6261.115 "))
        assertEquals("disabled", DeviceReality.at(p, "reality.radio.values.wifi_state"))
        assertEquals("com.google.android.webview", DeviceReality.at(p, "reality.webview.values.provider"))
        assertFalse(CloakForge.forgeProfile("Old", "ab".repeat(32), phone).has("reality"))
    }
    @Test fun explicitlyUnavailableSectionsStayUnavailableOnGeneration() {
        val reality = JSONObject(TestPhones["pixel_7"].reality.toString())
        for (section in listOf("radio", "webview")) reality.put(section, JSONObject().put("availability", "unavailable")
            .put("source", "needs verification").put("reason", "No data").put("values", JSONObject()))
        val p = CloakForge.forgeProfile("Reality", "ab".repeat(32), TestPhones["pixel_7"].copy(reality = reality), "0.4")
        assertTrue(ProfileValidator.errors(p).isEmpty())
        for (section in listOf("radio", "webview")) assertTrue(p.getJSONObject("reality").getJSONObject(section).similar(reality.getJSONObject(section)))
    }
    @Test fun everySharedRuleCaseAgreesWithPython() {
        val vector = vector(); val cases = vector.getJSONArray("cases")
        for (i in 0 until cases.length()) {
            val case = cases.getJSONObject(i); val p = profile(); val patch = case.getJSONObject("patch")
            patch.keys().forEach { path ->
                val parts = path.split('.'); var parent = p
                parts.dropLast(1).forEach { key -> parent = parent.getJSONObject(key) }
                parent.put(parts.last(), patch.get(path))
            }
            val findings = DeviceReality.validate(p).sortedWith(compareBy({ it.field }, { it.code }))
            val actual = JSONArray(findings.map { JSONObject().put("severity", it.severity).put("code", it.code).put("field", it.field).put("message", it.message) })
            assertTrue(case.getString("name"), actual.similar(case.getJSONArray("findings")))
        }
    }
    @Test fun allTemplatesGenerateValidRealityAndDifferentSeedsDiffer() {
        for (phone in TestPhones.all) {
            val a = CloakForge.forgeProfile("Reality", "ab".repeat(32), phone, "0.4")
            val b = CloakForge.forgeProfile("Reality", "cd".repeat(32), phone, "0.4")
            assertTrue(phone.id, ProfileValidator.errors(a).isEmpty())
            assertNotEquals(a.getJSONObject("reality").getString("inventory_id"), b.getJSONObject("reality").getString("inventory_id"))
        }
    }
    @Test fun legacyImportAndBackupDoNotMaterializeOrMigrateReality() {
        for (version in listOf("0.2", "0.3")) {
            val old = CloakForge.forgeProfile("Old", "ab".repeat(32), TestPhones["pixel_7"], version)
            val before = old.toString()
            val imported = ProfileBackup.parse(ProfileBackup.export(listOf(old), "test", true).toString(), emptyList()).profiles.single()
            assertEquals(before, old.toString())
            assertEquals(version, imported.getString("schema_version"))
            assertFalse(imported.has("reality"))
            assertTrue(ProfileValidator.errors(imported).isEmpty())
        }
    }
    @Test fun fullBackupPreservesRealityAndDefaultBackupRedactsDistinguishingValues() {
        val p = profile(); val before = p.toString()
        val full = ProfileBackup.parse(ProfileBackup.export(listOf(p), "test", true).toString(), emptyList()).profiles.single()
        assertTrue(full.getJSONObject("reality").similar(p.getJSONObject("reality")))
        val redacted = ProfileBackup.export(listOf(p), "test").toString()
        for (path in DeviceReality.uniquePaths) assertFalse(redacted.contains(DeviceReality.at(p, path).toString()))
        val imported = ProfileBackup.parse(redacted, emptyList()).profiles.single()
        assertTrue(ProfileValidator.errors(imported).isEmpty()); assertEquals(before, p.toString())
    }
    @Test fun newUniquenessFieldsCanBeExplicitlyRegenerated() {
        val p = profile(); val other = JSONObject(p.toString()).put("id", "other")
        val index = ProfileUniqueness(listOf("other" to other))
        assertTrue(index.findings(p).map { it.field }.containsAll(DeviceReality.uniquePaths))
        for (path in DeviceReality.uniquePaths) {
            val regenerated = index.regenerate(p, path) { "cd".repeat(32) }
            assertNotEquals(DeviceReality.at(p, path), DeviceReality.at(regenerated, path))
            assertTrue(index.findings(regenerated).none { it.field == path })
            assertTrue(ProfileValidator.errors(regenerated).isEmpty())
        }
    }
    @Test fun localCoherenceDoesNotBecomeVerifiedAndMalformedEvidenceFailsClosed() {
        val p = profile(); val before = p.toString(); val summary = RealityCoverage.summarize(p)
        assertEquals(100, summary.score); assertTrue("drm" in summary.missing)
        assertTrue(summary.rows.all { it.state == "unmapped" })
        val row = JSONObject().put("id", "reality.drm.security_level").put("status", "PASS")
            .put("expected_sha256", "a".repeat(64)).put("actual_sha256", "a".repeat(64))
        fun report() = JSONObject().put("device_reality", JSONObject().put("format", 1).put("rows", JSONArray().put(row)))
        assertEquals("match-only", RealityCoverage.summarize(p, report()).rows.single { it.field == "reality.drm.security_level" }.state)
        row.put("actual_sha256", JSONObject.NULL)
        assertThrows(IllegalArgumentException::class.java) { RealityCoverage.summarize(p, report()) }
        assertEquals(before, p.toString())
        assertNull(RealityCoverage.summarize(CloakForge.forgeProfile("Old", "ab".repeat(32), TestPhones["pixel_7"])).score)
    }
}
