package dev.cyclone.cloak.play

import android.app.Application
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34])
class PlayReadinessStoreTest {
    private lateinit var app: Application
    private lateinit var store: PlayReadinessStore
    private val c get() = ReadinessFixtures.context()
    private val text get() = ReadinessFixtures.report().toString()
    private val prefs get() = app.getSharedPreferences("play-readiness-evaluation", 0)
    @Before fun setup() {
        app = RuntimeEnvironment.getApplication()
        prefs.edit().clear().commit()
        store = PlayReadinessStore(app)
    }
    @Test fun missingSaveReadUpdateClearAndReopen() {
        assertEquals(PlayReadinessState.UNKNOWN, store.read(c).state)
        assertTrue(prefs.all.isEmpty())
        store.save(c, text)
        assertEquals(PlayReadinessState.READY, PlayReadinessStore(app).read(c).state)
        store.save(c, ReadinessFixtures.report().put("play_protect", "disabled").toString())
        assertEquals(PlayReadinessState.DEGRADED, store.read(c).state)
        store.clear(c.profileId)
        assertEquals(PlayReadinessState.UNKNOWN, store.read(c).state)
    }
    @Test fun rereadRechecksTimeProfileArtifactAndUser() {
        store.save(c, text)
        for (changed in listOf(c.copy(now=c.now.plus(Duration.ofDays(2))), c.copy(profileHash="e".repeat(64)),
            c.copy(artifactHash="e".repeat(64)), c.copy(androidUserId=0))) {
            val r = store.read(changed)
            assertEquals(PlayReadinessState.UNKNOWN, r.state)
            assertEquals(PlayReviewState.NEEDS_REVIEW, r.review)
        }
        assertEquals(PlayReadinessState.UNKNOWN, store.read(c).state)
        assertEquals(PlayReadinessState.READY, store.save(c, text, reviewConfirmed=true).state)
    }
    @Test fun explicitReviewIsStickyUntilFreshReportAndConfirmation() {
        store.save(c, text)
        store.requestReview(c.profileId)
        assertEquals(PlayReviewState.NEEDS_REVIEW, store.read(c).review)
        assertEquals(PlayReadinessState.UNKNOWN, store.read(c).state)
        assertEquals(PlayReadinessState.UNKNOWN, store.save(c, text).state)
        assertEquals(PlayReadinessState.READY, store.save(c, text, reviewConfirmed=true).state)
        store.clear(c.profileId)
        store.requestReview(c.profileId)
        assertEquals(PlayReviewState.NEEDS_REVIEW, store.read(c).review)
    }
    @Test fun rejectedReportsCannotReplaceStoredEvidence() {
        store.save(c, text)
        val before = prefs.all.toMap()
        for (bad in listOf("{}", ReadinessFixtures.report().put("profile_hash", "e".repeat(64)).toString(),
            ReadinessFixtures.report().put("artifact_hash", "e".repeat(64)).toString(),
            ReadinessFixtures.report().put("timestamp", "2026-10-10T00:00:00Z").toString())) {
            assertThrows(PlayReadinessStore.RejectedReport::class.java) { store.save(c, bad) }
            assertEquals(before, prefs.all)
        }
    }
    @Test fun malformedAndForeignStorageIsSafelyHiddenAndPreserved() {
        store.save(c, text)
        val valid = prefs.getString(c.profileId, null)!!
        for (bad in listOf("broken", "{}", "x".repeat(131073), JSONObject(valid).put("profile_id", "other").toString(),
            JSONObject(valid).put("report", text.replace("profile-a", "profile-b")).toString(),
            JSONObject(valid).also { it.getJSONObject("readiness").put("evidence_digest", "f".repeat(64)) }.toString(),
            JSONObject(valid).put("token", "DO_NOT_ECHO").toString())) {
            prefs.edit().putString(c.profileId, bad).commit()
            val r = store.read(c)
            assertEquals(PlayReadinessState.UNKNOWN, r.state)
            assertEquals(PlayReviewState.NEEDS_REVIEW, r.review)
            assertEquals("PR_STORE_UNREADABLE", r.findings.single().code)
            assertEquals(bad, prefs.getString(c.profileId, null))
            assertThrows(PlayReadinessStore.UnreadableStore::class.java) { store.save(c, text) }
        }
        prefs.edit().putInt(c.profileId, 1).commit()
        assertEquals(PlayReadinessState.UNKNOWN, store.read(c).state)
    }
    @Test fun perProfileStoragePreservesOtherRecordsAndMetadata() {
        val other = c.copy(profileId="profile-b", androidUserId=0)
        store.save(c, text)
        store.save(other, ReadinessFixtures.report().put("profile_id", "profile-b").put("android_user_id", 0).toString())
        store.clear(c.profileId)
        assertEquals(PlayReadinessState.READY, store.read(other).state)
        assertEquals(PlayReadinessState.UNKNOWN, store.read(c).state)
    }
    @Test fun storesActualSuppliedIntegrityMetadataWithoutVerdictsOrOtherStoreChanges() {
        val isolation = app.getSharedPreferences("google-surface-isolation", 0)
        isolation.edit().putString("sentinel", "unchanged").commit()
        val profileFile = java.io.File(app.filesDir, "profiles/readiness-sentinel.json")
        profileFile.parentFile!!.mkdirs(); profileFile.writeText("unchanged profile")
        val j = ReadinessFixtures.report().put("integrity_api", "available").put("integrity_response", ReadinessFixtures.response())
        store.save(c, j.toString())
        val raw = JSONObject(prefs.getString(c.profileId,null)!!).getString("report")
        assertEquals("c".repeat(64), JSONObject(raw).getJSONObject("integrity_response").getString("response_digest"))
        assertFalse(raw.contains("verdict"))
        assertEquals("unchanged", isolation.getString("sentinel",null))
        assertEquals("unchanged profile", profileFile.readText())
    }
    @Test fun persistedContractSnapshotIsValidatedButNeverUsedToPromoteUnknownEvidence() {
        store.save(c, ReadinessFixtures.report().put("play_protect", "unknown").toString())
        val envelope = JSONObject(prefs.getString(c.profileId, null)!!)
        val snapshot = envelope.getJSONObject("readiness")
        assertEquals("UNKNOWN", snapshot.getString("state"))
        snapshot.put("state", "READY").put("reason_codes", org.json.JSONArray(listOf("PS_LOCAL_CHECKS_PASSED")))
        prefs.edit().putString(c.profileId, envelope.toString()).commit()
        assertEquals(PlayReadinessState.UNKNOWN, store.read(c).state)
        store.requestReview(c.profileId)
        assertEquals("UNKNOWN", JSONObject(prefs.getString(c.profileId, null)!!).getJSONObject("readiness").getString("state"))
    }
}
