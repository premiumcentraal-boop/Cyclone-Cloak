package dev.cyclone.cloak.play

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import dev.cyclone.cloak.data.CloakStore
import dev.cyclone.cloak.forge.CloakForge
import dev.cyclone.cloak.forge.Imports
import dev.cyclone.cloak.forge.TestPhones
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GoogleSurfaceStoreTest {
    private lateinit var app: Application
    private lateinit var store: GoogleSurfaceStore
    private val prefs get() = app.getSharedPreferences("google-surface-isolation", Context.MODE_PRIVATE)
    @Before fun setup() {
        app = RuntimeEnvironment.getApplication()
        prefs.edit().clear().commit()
        store = GoogleSurfaceStore(app)
    }
    @Test fun saveReadUpdateClearAndReopen() {
        val a = SurfaceFixtures.record(); val b = SurfaceFixtures.record("profile-b", 17, "slot-b")
        assertTrue(store.snapshot().readable)
        assertNull(store.read(a.profileId))
        store.save(a); store.save(b)
        assertEquals(2, GoogleSurfaceStore(app).snapshot().records.size)
        assertEquals(17, store.read(b.profileId)!!.androidUserId)
        store.save(a.copy(active = false))
        assertFalse(store.read(a.profileId)!!.active)
        store.clear(a.profileId); store.clear("missing")
        assertNull(store.read(a.profileId))
        assertNotNull(store.read(b.profileId))
    }
    @Test fun separateAppPrivateSandboxesDoNotShareAssignments() {
        // Separate preference namespaces model Android's per-user sandbox; no cross-user API is used.
        val otherContext = object : ContextWrapper(app) {
            override fun getSharedPreferences(name: String, mode: Int) = super.getSharedPreferences("test-secondary-$name", mode)
        }
        otherContext.getSharedPreferences("google-surface-isolation", 0).edit().clear().commit()
        val other = GoogleSurfaceStore(otherContext)
        store.save(SurfaceFixtures.record())
        assertTrue(other.snapshot().records.isEmpty())
        other.save(SurfaceFixtures.record(user = 17))
        assertEquals(0, store.read("profile-a")!!.androidUserId)
        assertEquals(17, other.read("profile-a")!!.androidUserId)
    }
    @Test fun sharedAssignmentMustConfirmCurrentReviewAndPersistsForEveryPeer() {
        val a = SurfaceFixtures.record(); val b = SurfaceFixtures.record("profile-b", 17)
        store.save(a)
        assertThrows(GoogleSurfaceStore.ReviewRequired::class.java) { store.save(b) }
        assertNull(store.read(b.profileId))
        val review = store.review(b)
        assertEquals(listOf("profile-a", "profile-b"), review.sharedProfileIds)
        assertThrows(GoogleSurfaceStore.ReviewRequired::class.java) { store.save(b, review.digest, false) }
        assertThrows(GoogleSurfaceStore.ReviewRequired::class.java) { store.save(b, "a".repeat(64), true) }
        store.save(b, review.digest, true)
        for (r in listOf(a, b)) assertEquals(IsolationState.SHARED_ACCOUNT_CONFIRMED,
            GoogleSurfaceStore(app).evaluate(r.profileId, r.androidUserId, r.profileHash, SurfaceFixtures.observed(r)).state)
    }
    @Test fun staleReviewAndChangedAssignmentsRequireNewConfirmation() {
        val a = SurfaceFixtures.record(); val b = SurfaceFixtures.record("profile-b", 17)
        store.save(a)
        val stale = store.review(b)
        store.save(a.copy(profileHash = "b".repeat(64)))
        assertThrows(GoogleSurfaceStore.ReviewRequired::class.java) { store.save(b, stale.digest, true) }
        store.save(b, store.review(b).digest, true)
        val changed = b.copy(profileHash = "c".repeat(64))
        assertThrows(GoogleSurfaceStore.ReviewRequired::class.java) { store.save(changed) }
        assertEquals(b.profileHash, store.read(b.profileId)!!.profileHash)
        val third = SurfaceFixtures.record("profile-c", 18)
        assertThrows(GoogleSurfaceStore.ReviewRequired::class.java) { store.save(third) }
    }
    @Test fun inactiveAssignmentDoesNotNeedSharedConfirmation() {
        store.save(SurfaceFixtures.record())
        val inactive = SurfaceFixtures.record("profile-b", 17, active = false)
        store.save(inactive)
        assertThrows(GoogleSurfaceStore.ReviewRequired::class.java) { store.save(inactive.copy(active = true)) }
    }
    @Test fun corruptStorageIsHiddenPreservedAndCannotBypassReuseProtection() {
        val bad = listOf("{", "{}", "x".repeat(2097153),
            "{\"format\":1,\"records\":[],\"confirmations\":[],\"extra\":\"SECRET\"}",
            "{\"format\":1,\"format\":1,\"records\":[],\"confirmations\":[]}")
        for (text in bad) {
            prefs.edit().putString("library", text).commit()
            assertFalse(store.snapshot().readable)
            assertNull(store.read("profile-a"))
            assertEquals(IsolationState.UNKNOWN, store.evaluate("profile-a", 0, SurfaceFixtures.hash).state)
            assertThrows(GoogleSurfaceStore.InventoryUnreadable::class.java) { store.save(SurfaceFixtures.record()) }
            assertThrows(GoogleSurfaceStore.InventoryUnreadable::class.java) { store.clear("profile-a") }
            assertEquals(text, prefs.getString("library", null))
        }
        prefs.edit().putInt("library", 1).commit()
        assertFalse(store.snapshot().readable)
    }
    @Test fun duplicateProfileOrMalformedRecordMakesWholeInventoryUnknown() {
        val j = JSONObject(GoogleSurfaceCodec.encode(SurfaceFixtures.record()))
        for (records in listOf(JSONArray().put(j).put(j), JSONArray().put(JSONObject(j.toString()).put("profile_hash", "bad")))) {
            val text = JSONObject().put("format", 1).put("records", records).put("confirmations", JSONArray()).toString()
            prefs.edit().putString("library", text).commit()
            assertFalse(store.snapshot().readable)
            assertEquals("GS_INVENTORY_UNREADABLE", store.evaluate("profile-a", 0, SurfaceFixtures.hash).checks.single().reason)
        }
    }
    @Test fun clearingPeerInvalidatesPreviousSharingConsent() {
        val a = SurfaceFixtures.record(); val b = SurfaceFixtures.record("profile-b", 17)
        store.save(a); store.save(b, store.review(b).digest, true)
        store.clear(b.profileId)
        assertThrows(GoogleSurfaceStore.ReviewRequired::class.java) { store.save(b) }
        assertEquals(IsolationState.READY, store.evaluate(a.profileId, 0, a.profileHash, SurfaceFixtures.observed(a)).state)
    }
    @Test fun profileFilesImportsBindingsAndOtherSidecarsRemainByteIdentical() {
        val sentinels = listOf(File(app.noBackupFilesDir, "cloak-bindings-v1.json"), File(app.filesDir, "publication-sentinel"))
        sentinels.forEach { it.parentFile!!.mkdirs(); it.writeText("existing state") }
        val engine = app.getSharedPreferences("device-profile-engine", 0)
        engine.edit().putString("sentinel", "existing engine metadata").commit()
        for (version in listOf("0.2", "0.3", "0.4")) {
            val p = CloakForge.forgeProfile("Legacy", "ab".repeat(32), TestPhones["pixel_7"]).put("schema_version", version)
            val imported = Imports.parse(p.toString(), emptySet()) as Imports.Parsed.Profile
            val id = CloakStore.save(app, imported.profile)
            val file = File(app.filesDir, "profiles/$id.json"); val before = file.readBytes()
            val assignment = SurfaceFixtures.record()
            store.save(assignment); store.evaluate(assignment.profileId, 0, assignment.profileHash); store.clear(assignment.profileId)
            assertArrayEquals(before, file.readBytes())
            assertEquals(dev.cyclone.cloak.data.EngineMetadataStore.digest(imported.profile),
                dev.cyclone.cloak.data.EngineMetadataStore.digest(CloakStore.find(app, id)!!))
        }
        sentinels.forEach { assertEquals("existing state", it.readText()) }
        assertEquals("existing engine metadata", engine.getString("sentinel", null))
    }
    @Test fun evaluationNeverPersistsObservationsOrCreatesAssignments() {
        val before = prefs.all.toMap()
        store.evaluate("profile-a", 0, SurfaceFixtures.hash, SurfaceFixtures.observed())
        assertEquals(before, prefs.all)
        store.save(SurfaceFixtures.record())
        val saved = prefs.getString("library", null)
        assertEquals(IsolationState.READY, store.evaluate("profile-a", 0, SurfaceFixtures.hash, SurfaceFixtures.observed()).state)
        assertEquals(saved, prefs.getString("library", null))
        assertFalse(saved!!.contains("privateDataBoundary"))
    }
}
