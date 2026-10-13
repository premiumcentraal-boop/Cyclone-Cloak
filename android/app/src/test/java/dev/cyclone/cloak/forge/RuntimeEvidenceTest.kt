package dev.cyclone.cloak.forge

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class RuntimeEvidenceTest {
    private val phases = listOf("unbound-before", "bound-a", "bound-a-repeat", "bound-b", "unbound-after")
    private fun evidence(): JSONObject {
        val runs = JSONArray()
        for (phase in phases) {
            val hash = when (phase) { "bound-a", "bound-a-repeat" -> "a"; "bound-b" -> "b"; else -> "c" }.repeat(64)
            val row = JSONObject().put("id", "build.MANUFACTURER").put("status", "PASS")
                .put("actual_sha256", hash).put("expected", if (phase == "bound-b") "Samsung" else "Google")
            runs.put(JSONObject().put("run", phase).put("package", "dev.cyclone.cloak.probe").put("rows", JSONArray().put(row)))
        }
        return JSONObject().put("format", 1).put("artifact", "d".repeat(64))
            .put("metadata", JSONObject().put("module_arm64_sha256", "d".repeat(64))).put("runs", runs)
    }
    private fun row(evidence: JSONObject, index: Int) = evidence.getJSONArray("runs").getJSONObject(index).getJSONArray("rows").getJSONObject(0)
    @Test fun completeDistinctStableFixtureProofMapsToVerified() {
        assertEquals("VERIFIED", RuntimeEvidence.toScoreInput(evidence())["device.manufacturer"])
        assertEquals("INCOMPLETE", RuntimeEvidence.toScoreInput(evidence())["identifiers.android_id"])
    }
    @Test fun partialPassNeverVerifiedAndPartialFailureIsVisible() {
        val partial = evidence().also { it.put("runs", JSONArray().put(it.getJSONArray("runs").getJSONObject(1))) }
        assertEquals("INCOMPLETE", RuntimeEvidence.toScoreInput(partial)["device.manufacturer"])
        row(partial, 0).put("status", "FAIL")
        assertEquals("FAIL", RuntimeEvidence.toScoreInput(partial)["device.manufacturer"])
    }
    @Test fun missingHashesMissingArtifactIdenticalExpectationsAndBlockedNeverVerify() {
        val missing = evidence(); row(missing, 0).remove("actual_sha256")
        assertEquals("INCOMPLETE", RuntimeEvidence.toScoreInput(missing)["device.manufacturer"])
        assertEquals("INCOMPLETE", RuntimeEvidence.toScoreInput(evidence().also { it.remove("artifact") })["device.manufacturer"])
        val same = evidence(); row(same, 3).put("expected", "Google")
        assertEquals("MATCH_ONLY", RuntimeEvidence.toScoreInput(same)["device.manufacturer"])
        val blocked = evidence(); row(blocked, 1).put("status", "BLOCKED")
        assertEquals("BLOCKED", RuntimeEvidence.toScoreInput(blocked)["device.manufacturer"])
    }
    @Test fun unstableRepeatOrChangedUnboundControlFails() {
        for (index in listOf(2, 4)) {
            val raw = evidence(); row(raw, index).put("actual_sha256", "e".repeat(64))
            assertEquals("FAIL", RuntimeEvidence.toScoreInput(raw)["device.manufacturer"])
        }
    }
    @Test fun duplicatePhasesRowsAndUnknownStatusesAreRejected() {
        val phase = evidence(); phase.getJSONArray("runs").getJSONObject(1).put("run", "unbound-before")
        val duplicate = evidence(); duplicate.getJSONArray("runs").getJSONObject(0).getJSONArray("rows").put(row(duplicate, 0))
        val status = evidence(); row(status, 1).put("status", "VERIFIED")
        for (raw in listOf(phase, duplicate, status)) assertTrue(runCatching { RuntimeEvidence.toScoreInput(raw) }.isFailure)
    }
    @Test fun coverageLabelsDistinguishImplementationFromEvidence() {
        val profile = IdentityEngine.generate(IdentityEngine.GenerationRequest("Example", "ab".repeat(32), TestPhones["pixel_7"])).getJSONObject("profile")
        val input = mapOf("device.model" to "VERIFIED", "identifiers.android_id" to "FAIL", "device.brand" to "BLOCKED")
        val rows = RuntimeEvidence.rows(profile, input).associateBy { it.field }
        assertEquals("verified", rows.getValue("device.model").state)
        assertEquals("failed", rows.getValue("identifiers.android_id").state)
        assertEquals("not_verified", rows.getValue("device.brand").state)
        assertEquals("applied", rows.getValue("device.manufacturer").state)
        assertEquals("pending", rows.getValue("display.width").state)
        assertEquals("not_verified", rows.getValue("seed").state)
    }
    @Test fun checkedInProbeArtifactsLoadWithoutPromotingPartialProof() {
        fun load(name: String): JSONObject {
            val path = "docs/runtime-evidence/$name"
            val file = listOf(File(path), File("../$path"), File("../../$path")).first { it.isFile }
            return JSONObject(file.readText())
        }
        val partial = RuntimeEvidence.toScoreInput(load("b3b-diagnostics-2026-10-13.json"))
        assertFalse(partial.values.contains("VERIFIED"))
        assertEquals("FAIL", partial["camera.cameras"])
        assertEquals("INCOMPLETE", partial["identifiers.mac"])
        val full = RuntimeEvidence.toScoreInput(load("b2a-drm.json"))
        assertEquals("VERIFIED", full["identifiers.widevine_id"])
    }
}
