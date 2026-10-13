package dev.cyclone.cloak.forge

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class IdentityEngineTest {
    private val seed = "ab".repeat(32)
    private fun generate(phone: PhoneTemplate = TestPhones["pixel_7"], version: String = "0.3") =
        IdentityEngine.generate(IdentityEngine.GenerationRequest("Vault 01", seed, phone, version))

    @Test fun allCatalogEntriesRoundTripWithoutChangingGeneration() {
        for (phone in TestPhones.all) {
            val bundle = generate(phone)
            val profile = bundle.getJSONObject("profile")
            assertTrue(phone.id, profile.similar(CloakForge.forgeProfile("Vault 01", seed, phone)))
            assertEquals(phone.id, CloakForge.forgeProfile("Vault 01", seed, phone).toString(), profile.toString())
            assertEquals(phone.id, emptyList<ProfileValidator.Finding>(), IdentityEngine.validate(profile))
            assertEquals(0, bundle.getJSONArray("findings").length())
            assertEquals(0, bundle.getJSONArray("uniqueness").length())
            assertTrue(bundle.getJSONObject("posture").similar(CloakForge.buildPif(profile)))
            assertFalse(profile.has("source_confidence"))
            assertFalse(profile.has("sensor_class_list"))
        }
    }

    @Test fun engineBundleAndCoverageMatchPythonVector() {
        val vector = JSONObject(javaClass.classLoader!!.getResource("engine.json")!!.readText())
        val bundle = generate()
        assertTrue(bundle.similar(vector.getJSONObject("bundle")))
        val report = vector.getJSONObject("device_report")
        val mapping = report.keys().asSequence().associateWith(report::getString)
        assertTrue(IdentityEngine.score_coverage(bundle.getJSONObject("profile"), mapping)
            .similar(vector.getJSONObject("coverage")))
        val other = JSONObject(bundle.getJSONObject("profile").toString()).put("id", vector.getString("other_id"))
        val findings = IdentityEngine.check_uniqueness(bundle.getJSONObject("profile"), listOf(other))
        val expected = vector.getJSONArray("uniqueness")
        assertEquals(expected.length(), findings.size)
        findings.forEachIndexed { i, f ->
            val e = expected.getJSONObject(i)
            assertEquals(e.getString("code"), f.code)
            assertEquals(e.getString("severity"), f.severity)
            assertEquals(e.getString("field"), f.field)
            assertEquals(e.getString("message"), f.message)
        }
    }

    @Test fun legacyVersionChangesOnlySchemaVersion() {
        val normal = generate().getJSONObject("profile").put("schema_version", "0.2")
        val legacy = generate(version = "0.2").getJSONObject("profile")
        assertTrue(normal.similar(legacy))
        assertTrue(IdentityEngine.validate(legacy).isEmpty())
        assertThrows(IllegalArgumentException::class.java) { generate(version = "9") }
    }

    @Test fun uniquenessExcludesSelfAndKeepsInputUntouched() {
        val profile = generate().getJSONObject("profile")
        val other = JSONObject().put("id", "other").put("identifiers",
            JSONObject().put("mac", profile.getJSONObject("identifiers").getString("mac").lowercase()))
        val before = profile.toString()
        val findings = IdentityEngine.check_uniqueness(profile, listOf(profile, other, other))
        assertEquals(listOf("identifiers.mac"), findings.map { it.field })
        assertTrue(findings.single().message.contains("1 other"))
        assertEquals(before, profile.toString())
    }

    @Test fun coverageDoesNotPromoteRawPassIntoVerification() {
        val rows = IdentityEngine.score_coverage(generate().getJSONObject("profile"),
            mapOf("identifiers.android_id" to "PASS")).getJSONArray("rows")
        val byField = (0 until rows.length()).map(rows::getJSONObject).associateBy { it.getString("field") }
        assertEquals("PASS", byField.getValue("identifiers.android_id").getString("test_status"))
        assertEquals("not verified", byField.getValue("camera.cameras").getString("test_status"))
        assertEquals("PENDING", byField.getValue("display.width").getString("implementation"))
    }

    @Test fun corpusIsOptionalAndInvalidConfidenceIsRejected() {
        val original = TestPhones["pixel_7"].toJson()
        assertTrue(original.has("sensor_class_list"))
        val bad = PhoneTemplate.fromJson(JSONObject(original.toString()).put("source_confidence", "guaranteed"))
        assertTrue(ProfileValidator.validatePhone(bad).any { it.code == "SOURCE_CONFIDENCE" && it.isError })
        val legacy = JSONObject(original.toString())
        listOf("confidence", "source_confidence", "camera_class", "expected_capability_set", "sensor_class_list", "sources").forEach(legacy::remove)
        assertTrue(ProfileValidator.validatePhone(PhoneTemplate.fromJson(legacy)).isEmpty())
    }

    @Test fun catalogRejectsImpossibleAspectAndInvalidCorpusFields() {
        val phone = TestPhones["pixel_7"]
        val bad = phone.copy(display = JSONObject(phone.display.toString()).put("width", 120).put("height", 8000),
            corpus = JSONObject().put("sensor_class_list", "invented").put("drm_security_level", "L9"))
        val codes = ProfileValidator.validatePhone(bad).filter { it.isError }.map { it.code }
        assertTrue(codes.containsAll(listOf("CATALOG_DISPLAY_ASPECT", "CORPUS_LIST", "DRM_SECURITY_LEVEL")))
    }

    @Test fun cameraFacingSizeLevelAndAspectMirrorPython() {
        val profile = generate().getJSONObject("profile")
        profile.put("camera", JSONObject().put("cameras", JSONArray().put(JSONObject()
            .put("facing", true).put("hardware_level", 9).put("pixel_width", 100).put("pixel_height", 300))))
        val codes = IdentityEngine.validate(profile).map { it.code }
        assertTrue(codes.containsAll(listOf("CAMERA_FACING", "CAMERA_LEVEL", "CAMERA_SIZE", "CAMERA_ASPECT")))
        profile.put("camera", "bad")
        assertTrue(IdentityEngine.validate(profile).any { it.code == "CAMERA" })
    }
}
