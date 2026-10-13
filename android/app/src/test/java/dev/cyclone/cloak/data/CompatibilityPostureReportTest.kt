package dev.cyclone.cloak.data

import dev.cyclone.cloak.forge.CloakForge
import dev.cyclone.cloak.forge.TestPhones
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.Instant

class CompatibilityPostureReportTest {
    private val now = Instant.parse("2026-10-13T15:00:00Z")
    private val artifact = "cd".repeat(32)
    private val profile = CloakForge.forgeProfile("Posture Pixel", "ab".repeat(32), TestPhones["pixel_7"])
    private val profileDigest = EngineMetadataStore.digest(profile)

    private fun check(id: String, status: PostureCheckStatus) =
        PostureReportCheck(id, status, if (status == PostureCheckStatus.FAIL) "Failed" else null)

    private fun report(checks: List<PostureReportCheck> = listOf(check("build.model", PostureCheckStatus.PASS))) =
        CompatibilityPostureReport("test-provider", "2026-10-13T14:00:00Z", profile.optString("id"),
            profileDigest, artifact, checks)

    private fun source() = JSONObject()
        .put("format", 1)
        .put("provider_id", "test-provider")
        .put("checked_at", "2026-10-13T14:00:00Z")
        .put("profile_id", profile.optString("id"))
        .put("profile_sha256", profileDigest)
        .put("artifact_digest", artifact)
        .put("raw_verdict", "pass")
        .put("checks", JSONArray().put(JSONObject().put("id", "build.model").put("status", "PASS")))

    @Test fun parserAcceptsTheLocalShapeAndCapturesFailuresWarningsAndRawVerdict() {
        val parsed = CompatibilityPostureReport.parse(source().put("raw_verdict", "local-ok"))
        assertEquals("test-provider", parsed.providerId)
        assertEquals(profileDigest, parsed.profileDigest)
        assertEquals(artifact, parsed.artifactDigest)
        assertEquals("local-ok", parsed.rawVerdict)
        assertEquals(listOf("build.model"), parsed.passIds)
        assertTrue(parsed.warningIds.isEmpty() && parsed.failureIds.isEmpty())
    }

    @Test fun parserRejectsMalformedVersionsFieldsChecksAndValues() {
        val bad = listOf(
            source().put("format", 2), source().also { it.remove("checked_at") }, source().put("profile_sha256", "short"),
            source().put("artifact_digest", "SHORT".repeat(8)), source().put("extra", true),
            source().put("checks", JSONArray()), source().put("raw_verdict", ""),
            source().put("checks", JSONArray().put(JSONObject().put("id", "same").put("status", "PASS"))
                .put(JSONObject().put("id", "same").put("status", "PASS"))),
            source().put("checks", JSONArray().put(JSONObject().put("id", "row").put("status", "OTHER").put("extra", 1))),
        )
        bad.forEach { assertTrue(runCatching { CompatibilityPostureReport.parse(it) }.isFailure) }
    }

    @Test fun definiteCheckRowsMapToPassDegradedFailedOrUnknown() {
        assertEquals(PostureState.PASS, LocalPostureDecision.evaluate(report(), profile, now, artifact).state)
        assertEquals(PostureState.DEGRADED, LocalPostureDecision.evaluate(report(listOf(
            check("one", PostureCheckStatus.PASS), check("two", PostureCheckStatus.WARNING))), profile, now, artifact).state)
        assertEquals(PostureState.FAILED, LocalPostureDecision.evaluate(report(listOf(
            check("one", PostureCheckStatus.PASS), check("two", PostureCheckStatus.FAIL))), profile, now, artifact).state)
        assertEquals(PostureState.UNKNOWN, LocalPostureDecision.evaluate(report(listOf(
            check("one", PostureCheckStatus.UNKNOWN))), profile, now, artifact).state)
    }

    @Test fun rawVerdictIsPreservedButCannotOverrideAFailedRow() {
        val result = LocalPostureDecision.evaluate(
            report(listOf(check("row", PostureCheckStatus.FAIL))).copy(rawVerdict = "pass"), profile, now, artifact)
        assertEquals(PostureState.FAILED, result.state)
        assertTrue(result.reason.contains("1 failed"))
    }

    @Test fun optionalCauseIsPreservedChangesDigestAndUnknownBlocksSilentPass() {
        val plain = report()
        val caused = report(listOf(PostureReportCheck("build.model", PostureCheckStatus.UNKNOWN,
            "Not tested.", PostureCheckCause.PROBE_NOT_RUN)))
        val parsed = CompatibilityPostureReport.parse(caused.canonicalJson().put("report_digest", caused.digest()))
        assertEquals(PostureCheckCause.PROBE_NOT_RUN, parsed.checks.single().cause)
        assertNotEquals(plain.digest(), caused.digest())
        assertTrue(runCatching {
            CompatibilityPostureReport.parse(caused.canonicalJson().put("checks", JSONArray().put(
                JSONObject().put("id", "build.model").put("status", "UNKNOWN").put("cause", "OTHER"))))
        }.isFailure)
        val withPass = report(listOf(PostureReportCheck("one", PostureCheckStatus.PASS, null),
            PostureReportCheck("two", PostureCheckStatus.UNKNOWN, null, PostureCheckCause.PROBE_NOT_RUN)))
        assertEquals(PostureState.UNKNOWN, LocalPostureDecision.evaluate(withPass, profile, now, artifact).state)
    }

    @Test fun profileArtifactAndFreshnessChangesForceRotationInsteadOfReusingEvidence() {
        val stale = report().copy(checkedAt = "2026-10-05T14:00:00Z")
        val future = report().copy(checkedAt = "2026-10-13T15:01:01Z")
        listOf(
            report().copy(profileDigest = "ef".repeat(32)),
            report().copy(artifactDigest = "ef".repeat(32)),
            stale, future,
        ).forEach { report ->
            val result = LocalPostureDecision.evaluate(report, profile, now, artifact)
            assertEquals(PostureState.NEEDS_ROTATION, result.state)
            assertEquals(report.profileDigest, result.profileDigest)
        }
    }

    @Test fun unknownArtifactDigestNeverBecomesPassAndMismatchIsVisible() {
        val withoutArtifact = LocalPostureDecision.evaluate(report(), profile, now, null)
        assertEquals(PostureState.UNKNOWN, withoutArtifact.state)
        assertTrue(withoutArtifact.reason.contains("artifact freshness is unknown"))
        val changed = LocalPostureDecision.evaluate(report(), profile, now, "ef".repeat(32))
        assertEquals(PostureState.NEEDS_ROTATION, changed.state)
    }

    @Test fun providerBridgesOnlyExplicitReportsAndNeverInventsEvidence() {
        val provider = LocalReportPostureProvider({ id -> if (id == "known") profile else null },
            { id -> if (id == "known") report() else null }, { artifact }, { now })
        assertEquals(PostureState.PASS, provider.evaluate("known").state)
        val missing = provider.evaluate("missing")
        assertEquals(PostureState.UNKNOWN, missing.state)
        assertEquals("local-report", missing.providerId)
        val withoutProfile = LocalReportPostureProvider({ null }, { report() }, { artifact }, { now }).evaluate("known")
        assertEquals(PostureState.NEEDS_ROTATION, withoutProfile.state)
        val invalid = LocalReportPostureProvider({ id -> if (id == "known") profile else null },
            { id -> if (id == "known") report().copy(profileId = "other") else null }, { artifact }, { now }).evaluate("known")
        assertEquals(PostureState.UNKNOWN, invalid.state)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PostureArtifactsTest {
    @Test fun bundledModuleArtifactDigestIsLowercaseSha256() {
        val digest = PostureArtifacts.bundledModuleDigest(RuntimeEnvironment.getApplication(), "arm64-v8a")
        assertNotNull(digest)
        assertTrue(Regex("[0-9a-f]{64}").matches(digest!!))
        assertNull(PostureArtifacts.bundledModuleDigest(RuntimeEnvironment.getApplication(), "missing-abi"))
    }
}
