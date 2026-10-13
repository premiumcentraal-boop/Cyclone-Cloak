package dev.cyclone.cloak.ui

import dev.cyclone.cloak.data.EngineMetadataStore
import dev.cyclone.cloak.data.PostureCheckStatus
import dev.cyclone.cloak.data.PostureState
import dev.cyclone.cloak.forge.CloakForge
import dev.cyclone.cloak.forge.TestPhones
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.time.Instant

class DiagnosticsPostureAdapterTest {
    private val now = Instant.parse("2026-10-13T12:00:00Z")
    private val artifact = "d".repeat(64)
    private fun profile() = CloakForge.forgeProfile("Example", "ab".repeat(32), TestPhones["pixel_7"])
    private fun evidence(profile: JSONObject, scoped: Boolean = true): JSONObject {
        val runs = JSONArray()
        for (phase in listOf("unbound-before", "bound-a", "bound-a-repeat", "bound-b", "unbound-after")) {
            val hash = when (phase) { "bound-a", "bound-a-repeat" -> "a"; "bound-b" -> "b"; else -> "c" }.repeat(64)
            runs.put(JSONObject().put("run", phase).put("package", "dev.cyclone.cloak.probe").put("rows", JSONArray().put(
                JSONObject().put("id", "build.MANUFACTURER").put("status", "PASS").put("actual_sha256", hash)
                    .put("expected", if (phase == "bound-b") "Samsung" else "Google"))))
        }
        val json = JSONObject().put("format", 1).put("artifact", artifact).put("runs", runs)
            .put("metadata", JSONObject().put("module_arm64_sha256", artifact).put("measured_at_utc", now.toString()))
        if (scoped) json.put("profile_id", profile.getString("id")).put("profile_sha256", EngineMetadataStore.digest(profile))
        return json
    }
    private fun adapt(json: JSONObject, profile: JSONObject) = DiagnosticsPostureAdapter.convert(json.toString(), profile)
    @Test fun onlyVerifiedMapsToPass() {
        assertEquals(PostureCheckStatus.PASS, DiagnosticsPostureAdapter.mapStatus("VERIFIED"))
        assertEquals(PostureCheckStatus.FAIL, DiagnosticsPostureAdapter.mapStatus("FAIL"))
        for (status in listOf("PASS", "PARTIAL", "BLOCKED", "INCOMPLETE", "UNMAPPED", "OBSERVED", "MATCH_ONLY", "not verified", "UNSTABLE", "CONTROL_CHANGED"))
            assertEquals(status, PostureCheckStatus.WARNING, DiagnosticsPostureAdapter.mapStatus(status))
        assertTrue(runCatching { DiagnosticsPostureAdapter.mapStatus("invented") }.isFailure)
    }
    @Test fun completeScopedProofCanPassWithoutChangingProfile() {
        val profile = profile(); val before = profile.toString()
        val adapted = adapt(evidence(profile), profile)
        val preview = adapted.preview(profile, artifact, now)
        assertEquals(PostureState.PASS, preview.result.state)
        assertEquals(PostureCheckStatus.PASS, adapted.report.checks.single().status)
        assertEquals(EngineMetadataStore.digest(profile), adapted.report.profileDigest)
        assertEquals(before, profile.toString())
        assertNull(adapted.report.rawVerdict)
        assertFalse(adapted.report.checks.toString().contains("Google"))
    }
    @Test fun unscopedFixtureAndPartialPassNeverPromote() {
        val profile = profile()
        assertEquals(PostureState.DEGRADED, adapt(evidence(profile, false), profile).preview(profile, artifact, now).result.state)
        val partial = evidence(profile).also { it.put("runs", JSONArray().put(it.getJSONArray("runs").getJSONObject(1))) }
        assertEquals(PostureState.UNKNOWN, adapt(partial, profile).preview(profile, artifact, now).result.state)
        partial.getJSONArray("runs").getJSONObject(0).getJSONArray("rows").getJSONObject(0).put("status", "FAIL")
        assertEquals(PostureState.FAILED, adapt(partial, profile).preview(profile, artifact, now).result.state)
    }
    @Test fun inconsistentArtifactIsDiscardedAndUnknown() {
        val profile = profile(); val raw = evidence(profile)
        raw.getJSONObject("metadata").put("module_arm64_sha256", "e".repeat(64))
        val preview = adapt(raw, profile).preview(profile, artifact, now)
        assertNull(preview.report.artifactDigest)
        assertNull(preview.record.artifactDigest)
        assertEquals(PostureState.UNKNOWN, preview.result.state)
        assertTrue(preview.warnings.any { it.contains("disagree") })
    }
    @Test fun differentCurrentArtifactIsVisibleAndCannotPass() {
        val profile = profile(); val preview = adapt(evidence(profile), profile).preview(profile, "e".repeat(64), now)
        assertEquals(artifact, preview.report.artifactDigest)
        assertEquals(PostureState.NEEDS_ROTATION, preview.result.state)
        assertTrue(preview.warnings.any { it.contains("different artifact") })
    }
    @Test fun anotherProfileCannotBeSaved() {
        val profile = profile(); val raw = evidence(profile).put("profile_id", "another-profile")
        val adapted = adapt(raw, profile); val preview = adapted.preview(profile, artifact, now)
        assertFalse(preview.canSave)
        assertEquals(PostureState.UNKNOWN, preview.result.state)
        var writes = 0
        assertEquals(DiagnosticsReview.SaveResult.Blocked, DiagnosticsReview(adapted, preview).save(profile, artifact, now) { writes++ })
        assertEquals(0, writes)
    }
    @Test fun profileDigestMismatchAndBodyEditBlockSaving() {
        val profile = profile()
        val mismatch = adapt(evidence(profile).put("profile_sha256", "e".repeat(64)), profile).preview(profile, artifact, now)
        assertFalse(mismatch.canSave)
        assertEquals(PostureState.NEEDS_ROTATION, mismatch.result.state)
        val adapted = adapt(evidence(profile), profile)
        val review = DiagnosticsReview(adapted, adapted.preview(profile, artifact, now))
        profile.put("name", "Edited")
        var writes = 0
        val result = review.save(profile, artifact, now) { writes++ }
        assertTrue(result is DiagnosticsReview.SaveResult.ReviewChanged)
        assertFalse((result as DiagnosticsReview.SaveResult.ReviewChanged).review.preview.canSave)
        assertEquals(0, writes)
    }
    @Test fun staleAndFutureEvidenceNeverPass() {
        val profile = profile(); val adapted = adapt(evidence(profile), profile)
        for (time in listOf(now.plusSeconds(8 * 86400), now.minusSeconds(120))) {
            val preview = adapted.preview(profile, artifact, time)
            assertEquals(PostureState.NEEDS_ROTATION, preview.result.state)
            assertTrue(preview.warnings.isNotEmpty())
        }
    }
    @Test fun explicitSaveRechecksTimeAndWritesOnlyReviewedRecord() {
        val profile = profile(); val adapted = adapt(evidence(profile), profile)
        val review = DiagnosticsReview(adapted, adapted.preview(profile, artifact, now))
        var writes = 0
        assertTrue(review.save(profile, artifact, now.plusSeconds(8 * 86400)) { writes++ } is DiagnosticsReview.SaveResult.ReviewChanged)
        assertEquals(0, writes)
        assertEquals(DiagnosticsReview.SaveResult.Saved, review.save(profile, artifact, now) { record ->
            assertEquals(profile.getString("id"), record.profileId)
            assertEquals(EngineMetadataStore.digest(profile), record.profileDigest)
            writes++
        })
        assertEquals(1, writes)
    }
    @Test fun malformedReportsAreRejected() {
        val profile = profile()
        val cases = listOf("[]", "{}", "{", "{'format':1}", "{\"format\":1,\"format\":1}", evidence(profile).toString() + "{}",
            evidence(profile).put("format", 2).toString(), evidence(profile).put("artifact", "bad").toString(),
            evidence(profile).put("runs", JSONArray()).toString(),
            evidence(profile).also { it.getJSONObject("metadata").put("measured_at_utc", "yesterday") }.toString(),
            evidence(profile).also { it.getJSONArray("runs").getJSONObject(1).put("run", "unbound-before") }.toString(),
            evidence(profile).also { it.getJSONArray("runs").getJSONObject(1).getJSONArray("rows").getJSONObject(0).put("status", "VERIFIED") }.toString(),
            evidence(profile).also { it.getJSONObject("metadata").put("profile_id", "different") }.toString(),
            "[".repeat(33) + "]".repeat(33))
        cases.forEach { assertTrue(it.take(100), runCatching { DiagnosticsPostureAdapter.convert(it, profile) }.isFailure) }
    }
    @Test fun checkedInPartialEvidenceRetainsAllReadsAndNeverPasses() {
        val path = "docs/runtime-evidence/b3b-diagnostics-2026-10-13.json"
        val file = listOf(File(path), File("../$path"), File("../../$path")).first { it.isFile }
        val profile = profile(); val adapted = DiagnosticsPostureAdapter.convert(file.readText(), profile)
        assertEquals(70, adapted.report.checks.count { it.id.startsWith("read:") })
        assertFalse(adapted.report.checks.any { it.status == PostureCheckStatus.PASS })
        assertTrue(adapted.report.checks.any { it.status == PostureCheckStatus.FAIL })
    }

    @Test fun blockedReadsCarryMachineReadableCauseAndDoNotBecomePass() {
        val raw = evidence(profile())
        for (index in 0 until raw.getJSONArray("runs").length()) {
            raw.getJSONArray("runs").getJSONObject(index).getJSONArray("rows").getJSONObject(0)
                .put("status", "BLOCKED").put("reason", "SecurityException:android.permission.ACCESS_WIFI_STATE")
        }
        val adapted = DiagnosticsPostureAdapter.convert(raw.toString(), profile())
        val blocked = adapted.report.checks.first { it.cause == dev.cyclone.cloak.data.PostureCheckCause.PERMISSION }
        assertEquals(PostureCheckStatus.UNKNOWN, blocked.status)
        assertTrue(blocked.message!!.contains("cause: PERMISSION"))
        assertFalse(adapted.report.checks.any { it.status == PostureCheckStatus.PASS })
    }

    @Test fun mismatchAndMissingPhasesUseDistinctCauses() {
        val path = "docs/runtime-evidence/b3b-diagnostics-2026-10-13.json"
        val file = listOf(File(path), File("../$path"), File("../../$path")).first { it.isFile }
        val adapted = DiagnosticsPostureAdapter.convert(file.readText(), profile())
        assertTrue(adapted.report.checks.any {
            it.cause == dev.cyclone.cloak.data.PostureCheckCause.MISMATCH && it.status == PostureCheckStatus.FAIL
        })
        assertTrue(adapted.report.checks.any {
            it.cause == dev.cyclone.cloak.data.PostureCheckCause.PROBE_NOT_RUN && it.status == PostureCheckStatus.UNKNOWN
        })
    }
}
