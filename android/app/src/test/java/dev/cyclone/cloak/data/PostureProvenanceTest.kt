package dev.cyclone.cloak.data

import android.app.Application
import dev.cyclone.cloak.forge.CloakForge
import dev.cyclone.cloak.forge.TestPhones
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

class PostureProvenanceRuleTest {
    private val digest = "ab".repeat(32)

    private fun report(status: PostureCheckStatus = PostureCheckStatus.PASS, sequence: Long? = null) =
        CompatibilityPostureReport("provider", "2026-10-13T12:00:00Z", "profile-1", "cd".repeat(32),
            digest, listOf(PostureReportCheck("read", status, null)), sequence = sequence)

    private fun record(checkedAt: String, reportDigest: String?, sequence: Long? = null) = PostureResult(
        PostureState.PASS, checkedAt, source = "local", reason = "Local evidence.",
        providerId = "provider", artifactDigest = digest, profileDigest = "cd".repeat(32),
        reportDigest = reportDigest, sequence = sequence).recordFor("profile-1")

    @Test fun canonicalDigestIsStableAndMaterialChangesAlterIt() {
        val first = report().digest()
        val reordered = JSONObject(report().canonicalJson().toString())
        assertEquals(first, CompatibilityPostureReport.parse(reordered.put("report_digest", first)).digest())
        assertNotEquals(first, report(PostureCheckStatus.FAIL).digest())
        assertNotEquals(first, report(sequence = 2).digest())
    }

    @Test fun claimedDigestAndSequenceBoundsAreValidated() {
        val source = report().canonicalJson()
        assertTrue(runCatching { CompatibilityPostureReport.parse(source.put("report_digest", "bad")) }.isFailure)
        assertTrue(runCatching { CompatibilityPostureReport.parse(source.put("report_digest", "ef".repeat(32))) }.isFailure)
        assertTrue(runCatching { CompatibilityPostureReport.parse(report().canonicalJson().put("sequence", -1)) }.isFailure)
        val sequenced = report(sequence = 2)
        assertEquals(2L, CompatibilityPostureReport.parse(sequenced.canonicalJson().put("report_digest", sequenced.digest())).sequence)
    }

    @Test fun replacementRequiresMonotonicEvidence() {
        val candidate = record("2026-10-13T12:00:00Z", report().digest())
        assertTrue(PostureProvenance.canReplace(null, candidate))
        assertFalse(PostureProvenance.canReplace(candidate, candidate.copy()))
        assertFalse(PostureProvenance.canReplace(candidate, candidate.copy(checkedAt = "2026-10-13T11:00:00Z")))
        assertTrue(PostureProvenance.canReplace(candidate,
            candidate.copy(reportDigest = "ef".repeat(32), checkedAt = "2026-10-13T13:00:00Z")))
        val sequenced = candidate.copy(sequence = 3)
        assertFalse(PostureProvenance.canReplace(sequenced, sequenced.copy(reportDigest = "ef".repeat(32), sequence = 2)))
        assertTrue(PostureProvenance.canReplace(sequenced, sequenced.copy(reportDigest = "ef".repeat(32), sequence = 4)))
        assertFalse(PostureProvenance.canReplace(sequenced, sequenced.copy(reportDigest = "ef".repeat(32), sequence = null)))
        assertTrue(runCatching {
            PostureProvenance.canReplace(record("2026-10-13T12:00:00Z", null).copy(profileId = "other"), candidate)
        }.isFailure)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PostureProvenanceStoreTest {
    private lateinit var app: Application
    private lateinit var store: PostureProvenanceStore

    @Before fun setup() {
        app = RuntimeEnvironment.getApplication()
        app.getSharedPreferences("compatibility-posture-provenance", 0).edit().clear().commit()
        store = PostureProvenanceStore(app)
    }

    @Test fun consumedDigestsSurviveReopenAndCanBeCleared() {
        val first = "ab".repeat(32)
        val second = "cd".repeat(32)
        assertFalse(store.consumed("profile-1", first))
        store.remember("profile-1", first)
        store.remember("profile-1", first)
        store.remember("profile-1", second)
        assertTrue(PostureProvenanceStore(app).consumed("profile-1", first))
        assertTrue(store.consumed("profile-1", second))
        assertFalse(store.consumed("profile-2", first))
        store.clear("profile-1")
        assertFalse(store.consumed("profile-1", first))
    }

    @Test fun historyIsBoundedAndCorruptHistoryIsSafe() {
        fun hex(value: Int) = value.toString(16).padStart(64, '0')
        for (index in 0 until 70) store.remember("profile-1", hex(index))
        val reopened = PostureProvenanceStore(app)
        assertFalse(reopened.consumed("profile-1", hex(0)))
        assertTrue(reopened.consumed("profile-1", hex(69)))
        app.getSharedPreferences("compatibility-posture-provenance", 0).edit().putString("profile-1", "bad").commit()
        assertFalse(store.consumed("profile-1", "ab".repeat(32)))
        store.remember("profile-1", "ab".repeat(32))
        assertTrue(store.consumed("profile-1", "ab".repeat(32)))
    }
}
