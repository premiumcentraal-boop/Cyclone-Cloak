package dev.cyclone.cloak.data

import android.app.Application
import dev.cyclone.cloak.forge.CloakForge
import dev.cyclone.cloak.forge.EngineBundleCodec
import dev.cyclone.cloak.forge.Imports
import dev.cyclone.cloak.forge.TestPhones
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
class PostureStoreTest {
    private lateinit var app: Application
    private lateinit var store: PostureStore
    private fun record(id: String = "profile-1") = PostureResult().recordFor(id)
    @Before fun setup() {
        app = RuntimeEnvironment.getApplication()
        app.getSharedPreferences("compatibility-posture", 0).edit().clear().commit()
        store = PostureStore(app)
    }
    @Test fun saveReadUpdateClearAndReopenPreserveOtherRecords() {
        assertNull(store.read("profile-1"))
        store.update(record()); store.update(record("profile-2"))
        assertEquals(record(), PostureStore(app).read("profile-1"))
        val changed = record().copy(state = PostureState.DEGRADED, reason = "Local check needs review.")
        store.update(changed)
        assertEquals(changed, store.read("profile-1"))
        store.clear("profile-1"); store.clear("missing")
        assertNull(store.read("profile-1"))
        assertEquals(record("profile-2"), store.read("profile-2"))
    }
    @Test fun corruptRecordIsHiddenWithoutDeletionAndExplicitUpdateRecovers() {
        val preferences = app.getSharedPreferences("compatibility-posture", 0)
        for (text in listOf("not json", "{}", record().toJson().put("state", "invalid").toString(), "x".repeat(4097))) {
            preferences.edit().putString("profile-1", text).commit()
            assertNull(store.read("profile-1"))
            assertEquals(text, preferences.getString("profile-1", null))
            store.update(record())
            assertEquals(record(), store.read("profile-1"))
        }
    }
    @Test fun wrongProfileKeyOrPreferenceTypeIsSafelyHidden() {
        val preferences = app.getSharedPreferences("compatibility-posture", 0)
        preferences.edit().putString("profile-1", record("profile-2").toJson().toString()).commit()
        assertNull(store.read("profile-1"))
        preferences.edit().putInt("profile-1", 12).commit()
        assertNull(store.read("profile-1"))
        store.clear("profile-1")
        assertFalse(preferences.contains("profile-1"))
    }
    @Test fun rotationTransitionsRequireExplicitPersistenceAndNeverTouchProfileBody() {
        val profile = CloakForge.forgeProfile("Rotation", "ab".repeat(32), TestPhones["pixel_7"])
        val id = CloakStore.save(app, profile)
        val file = File(app.filesDir, "profiles/$id.json"); val bytes = file.readBytes()
        val marked = PostureRotation.markNeedsRotation(id, "Owner requested local rotation.")
        assertNull(store.read(id))
        store.update(marked)
        assertEquals(PostureState.NEEDS_ROTATION, store.read(id)!!.state)
        store.update(PostureRotation.afterExplicitReset(marked, true))
        assertEquals(PostureState.UNKNOWN, store.read(id)!!.state)
        assertArrayEquals(bytes, file.readBytes())
    }
    @Test fun legacyAndCurrentImportsAndEnginePreferencesRemainUntouched() {
        val enginePreferences = app.getSharedPreferences("device-profile-engine", 0)
        enginePreferences.edit().putString("untouched", "existing engine sidecar").commit()
        for (version in listOf("0.2", "0.3")) {
            val profile = CloakForge.forgeProfile("Legacy $version", "cd".repeat(32), TestPhones["pixel_7"]).put("schema_version", version)
            val parsed = Imports.parse(profile.toString(), emptySet()) as Imports.Parsed.Profile
            val beforeImport = EngineMetadataStore.digest(parsed.profile)
            val id = CloakStore.save(app, parsed.profile)
            val file = File(app.filesDir, "profiles/$id.json"); val bytes = file.readBytes()
            store.update(record(id)); store.read(id); store.clear(id)
            assertArrayEquals(bytes, file.readBytes())
            assertEquals(beforeImport, EngineMetadataStore.digest(CloakStore.find(app, id)!!))
            assertNull(EngineMetadataStore(app, EngineBundleCodec::validateMetadata).read(profile))
        }
        assertEquals("existing engine sidecar", enginePreferences.getString("untouched", null))
    }
    @Test fun evaluatingDefaultProviderDoesNotCreateARecord() {
        LocalCompatibilityPostureProvider().evaluate("profile-1")
        assertNull(store.read("profile-1"))
        assertFalse(File(app.filesDir, "profiles/profile-1.json").exists())
    }
    @Test fun diagnosticsPreviewAndSavePreserveProfileFiles() {
        val profile = CloakForge.forgeProfile("Diagnostics", "ab".repeat(32), TestPhones["pixel_7"])
        val id = CloakStore.save(app, profile)
        val file = File(app.filesDir, "profiles/$id.json"); val before = file.readBytes()
        val raw = org.json.JSONObject().put("format", 1)
            .put("metadata", org.json.JSONObject().put("measured_at_utc", "2026-10-13T12:00:00Z"))
            .put("runs", org.json.JSONArray().put(org.json.JSONObject().put("run", "bound-a")
                .put("package", "dev.cyclone.cloak.probe.diag").put("rows", org.json.JSONArray().put(
                    org.json.JSONObject().put("id", "wifi.ssid").put("status", "OBSERVED")))))
        val diagnostics = dev.cyclone.cloak.ui.DiagnosticsPostureAdapter.convert(raw.toString(), profile)
        val now = java.time.Instant.parse("2026-10-13T12:00:00Z")
        val review = dev.cyclone.cloak.ui.DiagnosticsReview(diagnostics, diagnostics.preview(profile, null, now))
        assertNull(store.read(id))
        assertEquals(dev.cyclone.cloak.ui.DiagnosticsReview.SaveResult.Saved, review.save(profile, null, now, store::update))
        assertEquals(PostureState.UNKNOWN, store.read(id)!!.state)
        assertEquals(EngineMetadataStore.digest(profile), store.read(id)!!.profileDigest)
        assertNull(store.read(id)!!.artifactDigest)
        assertArrayEquals(before, file.readBytes())
    }
}
