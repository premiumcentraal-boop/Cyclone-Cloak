package dev.cyclone.cloak.play

import android.content.Context
import dev.cyclone.cloak.data.EngineMetadataStore
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ServiceSurfaceStoreTest {
    private val app get() = RuntimeEnvironment.getApplication()
    private val preferences get() = app.getSharedPreferences("google-surface-isolation", Context.MODE_PRIVATE)
    private val record = SurfaceFixtures.record()
    private val binding = ServiceSurfaceBinding(record, "com.android.vending", "b".repeat(64))
    private val evidence = ServiceSurfaceEvidence(binding, SurfaceFixtures.observed(record))
    private val context = PlayReadinessContext(record.profileId, record.profileHash, 0, binding.artifactHash, Instant.parse("2026-10-19T12:01:00Z"))
    private fun store() = ServiceSurfaceStore(app)
    private fun key() = "binding:" + EngineMetadataStore.digest(JSONObject().put("profile", record.profileId).put("package", binding.packageName))
    @Before fun setup() { preferences.edit().clear().commit(); GoogleSurfaceStore(app).save(record) }

    @Test fun saveReadAndReopenLeaveExistingLibraryUntouched() {
        val original = preferences.getString("library", null)
        assertEquals(ServiceIsolationState.UNKNOWN, store().evaluate(binding, context).state)
        store().save(evidence)
        assertTrue(store().read(record.profileId, binding.packageName).readable)
        assertEquals(evidence.observation, store().read(record.profileId, binding.packageName).evidence!!.observation)
        assertEquals(ServiceIsolationState.ISOLATED, store().evaluate(binding, context).state)
        assertEquals(original, preferences.getString("library", null))
    }
    @Test fun bindingPackagesAndProfilesDoNotShareEvidence() {
        store().save(evidence)
        assertNull(store().read(record.profileId, "com.google.android.gms").evidence)
        assertNull(store().read("other-profile", binding.packageName).evidence)
    }
    @Test fun requestedReviewSurvivesSaveUnlessExplicitlyConfirmed() {
        store().requestReview(record.profileId, binding.packageName)
        store().save(evidence)
        assertEquals(ServiceIsolationState.NEEDS_REVIEW, store().evaluate(binding, context).state)
        store().save(evidence, reviewConfirmed = true)
        assertEquals(ServiceIsolationState.ISOLATED, store().evaluate(binding, context).state)
        store().requestReview(record.profileId, binding.packageName)
        store().save(evidence)
        assertEquals(ServiceIsolationState.NEEDS_REVIEW, store().evaluate(binding, context).state)
    }
    @Test fun corruptStoreFailsClosedWithoutOverwritingIt() {
        for (bad in listOf("broken", "{}", "{\"format\":1,\"format\":1}", " ".repeat(262145))) {
            preferences.edit().putString(key(), bad).commit()
            assertFalse(store().read(record.profileId, binding.packageName).readable)
            assertEquals(ServiceIsolationState.UNKNOWN, store().evaluate(binding, context).state)
            assertThrows(ServiceSurfaceStore.StoreUnreadable::class.java) { store().save(evidence) }
            assertEquals(bad, preferences.getString(key(), null))
        }
    }
    @Test fun mismatchedOrMalformedStoredFieldsAreHidden() {
        val original = JSONObject(store().encode(evidence, false))
        val variants = listOf(JSONObject(original.toString()).put("package", "com.google.android.gms"),
            JSONObject(original.toString()).put("token", "DO_NOT_ECHO"), JSONObject(original.toString()).put("review_requested", 1),
            JSONObject(original.toString()).also { it.getJSONObject("observation").put("private_boundary", "true") },
            JSONObject(original.toString()).put("artifact_hash", "broken"))
        for (bad in variants) {
            preferences.edit().putString(key(), bad.toString()).commit()
            assertFalse(store().read(record.profileId, binding.packageName).readable)
        }
    }
    @Test fun corruptAssignmentLibraryCannotClaimIsolation() {
        store().save(evidence)
        preferences.edit().putString("library", "broken").commit()
        assertEquals(ServiceIsolationState.UNKNOWN, store().evaluate(binding, context).state)
    }
    @Test fun sharingConsentExpiresWhenActiveInventoryChanges() {
        val surfaces = GoogleSurfaceStore(app)
        val peer = SurfaceFixtures.record("peer", 17)
        surfaces.save(peer, surfaces.review(peer).digest, true)
        store().save(evidence)
        assertEquals(ServiceIsolationState.SHARED_CONFIRMED, store().evaluate(binding, context).state)
        val changed = peer.copy(androidUserId = 18)
        surfaces.save(changed, surfaces.review(changed).digest, true)
        // The old consent was not copied; this explicit confirmation covered the changed group.
        assertEquals(ServiceIsolationState.SHARED_CONFIRMED, store().evaluate(binding, context).state)
        surfaces.clear(peer.profileId)
        assertEquals(ServiceIsolationState.ISOLATED, store().evaluate(binding, context).state)
        assertThrows(GoogleSurfaceStore.ReviewRequired::class.java) { surfaces.save(peer) }
    }
    @Test fun malformedEvidenceNeverLeaksValues() {
        val bad = evidence.copy(observation = evidence.observation.copy(source = "DO NOT ECHO"))
        val error = assertThrows(ServiceSurfaceStore.InvalidRecord::class.java) { store().save(bad) }
        assertEquals("SSI_MALFORMED_RECORD", error.message)
        assertNull(store().read(record.profileId, binding.packageName).evidence)
    }
    @Test fun bindingMismatchRemainsUnderReviewAfterContextIsRestored() {
        store().save(evidence)
        assertEquals(ServiceIsolationState.NEEDS_REVIEW, store().evaluate(binding, context.copy(androidUserId = 17)).state)
        assertEquals(ServiceIsolationState.NEEDS_REVIEW, store().evaluate(binding, context).state)
        store().save(evidence, reviewConfirmed = true)
        assertEquals(ServiceIsolationState.ISOLATED, store().evaluate(binding, context).state)
    }
    @Test fun oneCorruptRecordDoesNotHideHealthyAppsAndRestoredEvidenceIsReadable() {
        store().save(evidence)
        val saved = preferences.getString(key(), null)!!
        val other = binding.copy(packageName = "com.google.android.gms")
        store().save(evidence.copy(binding = other))
        preferences.edit().putString(key(), "broken").commit()
        assertEquals(ServiceIsolationState.ISOLATED, store().evaluate(other, context).state)
        assertEquals(ServiceIsolationState.UNKNOWN, store().evaluate(binding, context).state)
        preferences.edit().putString(key(), saved).commit()
        assertEquals(ServiceIsolationState.ISOLATED, store().evaluate(binding, context).state)
        assertEquals(1, GoogleSurfaceStore(app).snapshot().records.size)
    }
}
