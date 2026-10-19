package dev.cyclone.cloak.play

import dev.cyclone.cloak.forge.PlaySurfaceContract
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.Duration
import java.time.Instant

internal object ReadinessFixtures {
    val now: Instant = Instant.parse("2026-10-19T12:00:00Z")
    fun context() = PlayReadinessContext("profile-a", "a".repeat(64), 17, "b".repeat(64), now)
    fun report(): JSONObject = JSONObject().put("format", 1).put("profile_id", "profile-a").put("profile_hash", "a".repeat(64))
        .put("android_user_id", 17).put("artifact_hash", "b".repeat(64)).put("timestamp", "2026-10-19T11:00:00Z")
        .put("play_services", JSONObject().put("package_name", "com.google.android.gms").put("state", "enabled"))
        .put("play_store", JSONObject().put("package_name", "com.android.vending").put("state", "enabled"))
        .put("web_provider", JSONObject().put("package_name", "com.google.android.webview").put("state", "enabled"))
        .put("play_protect", "enabled").put("google_account", "associated").put("google_settings_access", "accessible")
        .put("integrity_api", "unknown").put("warnings", JSONArray()).put("failures", JSONArray())
    fun response() = JSONObject().put("response_digest", "c".repeat(64)).put("provider_id", "platform-reader")
        .put("received_at", "2026-10-19T10:59:00Z")
}

class PlayReadinessEvaluationTest {
    private val provider = LocalPlayReadinessProvider()
    private fun eval(j: JSONObject? = ReadinessFixtures.report(), c: PlayReadinessContext = ReadinessFixtures.context()) = provider.evaluate(c, j?.toString())
    @Test fun completeLocalReportIsReadyAndContractCompatible() {
        val r = eval()
        assertEquals(PlayReadinessState.READY, r.state)
        assertTrue(r.accepted)
        assertEquals(PlayReviewState.CURRENT, r.review)
        assertTrue(PlaySurfaceContract.validateReadiness(JSONObject(r.contractRecordJson)).isEmpty())
        assertTrue(r.findings.isEmpty())
    }
    @Test fun parserPreservesExactEvidenceAndUnknownStates() {
        val text = "  " + ReadinessFixtures.report().put("play_protect", "unknown").toString() + "\n"
        val parsed = PlayReadinessReport.parse(text)
        assertEquals(text, parsed.text)
        parsed.json.put("profile_id", "mutated")
        assertEquals("profile-a", parsed.json.getString("profile_id"))
        assertEquals(PlayReadinessState.UNKNOWN, provider.evaluate(ReadinessFixtures.context(), text).state)
    }
    @Test fun malformedMissingExtraDuplicateAndSecretFieldsAreRejected() {
        val good = ReadinessFixtures.report()
        val values = listOf("{}", "[]", good.toString()+" trailing", good.toString().replace("\"format\":1", "\"format\":1,\"format\":1"),
            good.toString().replace("\"format\":1", "\"format\":NaN"), JSONObject(good.toString()).put("token", "DO_NOT_ECHO").toString(),
            JSONObject(good.toString()).put("format", 2).toString(), JSONObject(good.toString()).put("android_user_id", "17").toString(),
            JSONObject(good.toString()).put("play_protect", "certified").toString(), "x".repeat(PlayReadinessReport.MAX_BYTES+1))
        values.forEach {
            assertFalse(assertThrows(PlayReadinessReport.InvalidReport::class.java) { PlayReadinessReport.parse(it) }.message!!.contains("DO_NOT_ECHO"))
            val r = provider.evaluate(ReadinessFixtures.context(), it)
            assertEquals(PlayReadinessState.UNKNOWN, r.state)
            assertEquals(PlayReviewState.NEEDS_REVIEW, r.review)
            assertFalse(r.accepted)
        }
        for (key in good.keys().asSequence().toList()) {
            val incomplete = JSONObject(good.toString()).also { it.remove(key) }
            assertThrows(PlayReadinessReport.InvalidReport::class.java) { PlayReadinessReport.parse(incomplete.toString()) }
        }
    }
    @Test fun datesHashesPackagesAndWarningCodesAreStrict() {
        val changes: List<(JSONObject) -> Unit> = listOf(
            { it.put("timestamp", "2026-02-30T00:00:00Z") }, { it.put("profile_hash", "A".repeat(64)) },
            { it.put("android_user_id", -1) }, { it.getJSONObject("play_store").put("package_name", "com.example.imposter") },
            { it.getJSONObject("web_provider").put("package_name", JSONObject.NULL) },
            { it.put("warnings", JSONArray(listOf("USER_REPORTED_WARNING", "USER_REPORTED_WARNING"))) },
            { it.put("failures", JSONArray(listOf("ARBITRARY_SECRET"))) },
            { it.put("integrity_response", ReadinessFixtures.response()) })
        changes.forEach { change -> assertThrows(PlayReadinessReport.InvalidReport::class.java) { PlayReadinessReport.parse(ReadinessFixtures.report().also(change).toString()) } }
    }
    @Test fun profileUserAndArtifactMismatchRejectWithoutRelabelling() {
        val base = ReadinessFixtures.context()
        for (c in listOf(base.copy(profileId="other"), base.copy(profileHash="d".repeat(64)), base.copy(androidUserId=0), base.copy(artifactHash="d".repeat(64)))) {
            val r = eval(c=c)
            assertFalse(r.accepted)
            assertEquals(PlayReadinessState.UNKNOWN, r.state)
            assertEquals(PlayReviewState.NEEDS_REVIEW, r.review)
            val stored = JSONObject(r.contractRecordJson)
            assertEquals(base.profileHash, stored.getString("profile_hash"))
            assertEquals(base.artifactHash, stored.getString("artifact_hash"))
        }
    }
    @Test fun freshnessBoundaryStaleAndFutureReports() {
        val c = ReadinessFixtures.context().copy(freshness=Duration.ofHours(1))
        assertEquals(PlayReadinessState.READY, eval(c=c).state)
        val stale = eval(c=c.copy(now=c.now.plusSeconds(1)))
        assertFalse(stale.accepted); assertEquals("PR_STALE", stale.findings.single().code)
        assertEquals(PlayReviewState.NEEDS_REVIEW, stale.review)
        val future = eval(ReadinessFixtures.report().put("timestamp", "2026-10-19T12:00:01Z"))
        assertEquals("PR_FUTURE_REPORT", future.findings.single().code)
        assertFalse(future.accepted)
    }
    @Test fun invalidFreshnessContextAndFractionalClockHandling() {
        for (window in listOf(Duration.ZERO, Duration.ofSeconds(-1), Duration.ofDays(366)))
            assertThrows(IllegalArgumentException::class.java) { ReadinessFixtures.context().copy(freshness=window) }
        assertEquals(PlayReadinessState.UNKNOWN, provider.evaluate(ReadinessFixtures.context().copy(now=ReadinessFixtures.now.plusNanos(5)), null).state)
    }
    @Test fun eachMissingRequiredObservationAlwaysUnknown() {
        for (field in listOf("play_services", "play_store", "web_provider", "play_protect", "google_account", "google_settings_access")) {
            val j = ReadinessFixtures.report()
            if (j.get(field) is JSONObject) j.getJSONObject(field).put("state", "unknown") else j.put(field, "unknown")
            assertEquals(field, PlayReadinessState.UNKNOWN, eval(j).state)
            j.put("failures", JSONArray(listOf("LOCAL_CHECK_FAILED")))
            assertEquals(PlayReadinessState.UNKNOWN, eval(j).state)
        }
        assertEquals(PlayReadinessState.UNKNOWN, eval(null).state)
    }
    @Test fun packageAndServiceBlocksRemainExplicit() {
        for (field in listOf("play_services", "play_store", "web_provider")) for (state in listOf("disabled", "missing", "blocked")) {
            val r = eval(ReadinessFixtures.report().also { it.getJSONObject(field).put("state", state) })
            assertEquals(PlayReadinessState.BLOCKED, r.state)
            assertEquals("PR_PACKAGE_BLOCK", r.findings.single().code)
        }
        for (field in listOf("google_account", "google_settings_access"))
            assertEquals(PlayReadinessState.BLOCKED, eval(ReadinessFixtures.report().put(field,"blocked")).state)
    }
    @Test fun permissionLimitationsAreNotPasses() {
        for (field in listOf("play_services", "play_store", "web_provider", "play_protect", "google_account", "google_settings_access", "integrity_api")) {
            val j = ReadinessFixtures.report()
            if (j.get(field) is JSONObject) j.getJSONObject(field).put("state", "permission_denied") else j.put(field, "permission_denied")
            val r = eval(j)
            assertEquals(PlayReadinessState.DEGRADED, r.state)
            assertEquals("PR_PERMISSION_LIMITATION", r.findings.single().code)
        }
    }
    @Test fun limitationsWarningsFailuresAndRequestedReview() {
        assertEquals(PlayReadinessState.DEGRADED, eval(ReadinessFixtures.report().put("play_protect", "disabled")).state)
        assertEquals(PlayReadinessState.DEGRADED, eval(ReadinessFixtures.report().put("google_account", "none")).state)
        assertEquals(PlayReadinessState.DEGRADED, eval(ReadinessFixtures.report().put("warnings", JSONArray(listOf("USER_REPORTED_WARNING")))).state)
        assertEquals(PlayReadinessState.BLOCKED, eval(ReadinessFixtures.report().put("failures", JSONArray(listOf("LOCAL_CHECK_FAILED")))).state)
        val review = provider.evaluate(ReadinessFixtures.context(), ReadinessFixtures.report().toString(), true)
        assertEquals(PlayReadinessState.UNKNOWN, review.state)
        assertEquals(PlayReviewState.NEEDS_REVIEW, review.review)
    }
    @Test fun optionalPlatformMetadataNeverPromotesMissingEvidence() {
        val j = ReadinessFixtures.report().put("integrity_api", "available").put("integrity_response", ReadinessFixtures.response())
        val ready = eval(j)
        assertEquals(listOf("integrity_api", "integrity_response"), ready.optionalObservationFields)
        assertTrue(ready.findings.isEmpty()) // No integrity PASS finding or verdict is manufactured.
        j.put("play_protect", "unknown")
        assertEquals(PlayReadinessState.UNKNOWN, eval(j).state)
        assertEquals(PlayReadinessState.UNKNOWN, DefaultPlayReadinessProvider().evaluate(ReadinessFixtures.context(), j.toString()).state)
        val response = j.getJSONObject("integrity_response").put("token", "SECRET")
        assertThrows(PlayReadinessReport.InvalidReport::class.java) { PlayReadinessReport.parse(j.put("integrity_response", response).toString()) }
    }
    @Test fun stateTransitionsAreRecomputedFromEvidenceWithoutMemory() {
        val cases = listOf(ReadinessFixtures.report(), ReadinessFixtures.report().put("play_protect","unknown"),
            ReadinessFixtures.report().put("warnings", JSONArray(listOf("PACKAGE_LIMITATION"))),
            ReadinessFixtures.report().put("failures", JSONArray(listOf("LOCAL_CHECK_FAILED"))))
        val states = listOf(PlayReadinessState.READY, PlayReadinessState.UNKNOWN, PlayReadinessState.DEGRADED, PlayReadinessState.BLOCKED)
        for (first in cases) for ((next, expected) in cases.zip(states)) { eval(first); assertEquals(expected, eval(next).state) }
    }
    @Test fun evidenceDigestIsOfExactBytes() {
        val a = provider.evaluate(ReadinessFixtures.context(), ReadinessFixtures.report().toString())
        val b = provider.evaluate(ReadinessFixtures.context(), " " + ReadinessFixtures.report().toString())
        assertNotEquals(JSONObject(a.contractRecordJson).getString("evidence_digest"), JSONObject(b.contractRecordJson).getString("evidence_digest"))
    }
}
