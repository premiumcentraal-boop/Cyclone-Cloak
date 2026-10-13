package dev.cyclone.cloak.data

import android.app.Application
import dev.cyclone.cloak.forge.DefaultDeviceProfileEngine
import dev.cyclone.cloak.forge.IdentityEngine
import dev.cyclone.cloak.forge.EngineBundleCodec
import dev.cyclone.cloak.forge.TestPhones
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EngineMetadataStoreTest {
    private lateinit var app: Application
    private lateinit var sidecars: EngineMetadataStore
    private fun bundle() = DefaultDeviceProfileEngine().generate_unique(IdentityEngine.GenerationRequest("Sidecar", "ab".repeat(32), TestPhones["pixel_7"]), emptyList())
    @Before fun setup() {
        app = RuntimeEnvironment.getApplication()
        app.getSharedPreferences("device-profile-engine", 0).edit().clear().commit()
        sidecars = EngineMetadataStore(app, EngineBundleCodec::validateMetadata)
    }

    @Test fun sidecarIsSeparateAndSavedBodyUsesExistingLayout() {
        val raw = bundle(); val profile = raw.getJSONObject("profile")
        val before = profile.toString()
        val id = sidecars.save(profile, raw.getJSONObject("metadata")) { CloakStore.save(app, it) }
        val file = File(app.filesDir, "profiles/$id.json")
        assertEquals(EngineMetadataStore.digest(profile), EngineMetadataStore.digest(JSONObject(file.readText())))
        assertEquals(before, profile.toString())
        assertEquals(raw.getJSONObject("metadata").toString(), sidecars.read(profile)!!.toString())
        assertFalse(JSONObject(file.readText()).has("metadata"))
    }

    @Test fun legacyProfileIsReadableWithoutMetadataAndNeverRewrittenByReading() {
        val profile = bundle().getJSONObject("profile").put("schema_version", "0.2")
        val id = CloakStore.save(app, profile)
        val file = File(app.filesDir, "profiles/$id.json"); val before = file.readBytes()
        assertNull(sidecars.read(profile))
        assertEquals(EngineMetadataStore.digest(profile), EngineMetadataStore.digest(CloakStore.find(app, id)!!))
        assertArrayEquals(before, file.readBytes())
    }

    @Test fun failedWriteRollsBackMetadataAndMissingOrCorruptSidecarDoesNotBreakProfile() {
        val raw = bundle(); val profile = raw.getJSONObject("profile")
        assertTrue(runCatching { sidecars.save(profile, raw.getJSONObject("metadata")) { throw IOException("disk") } }.isFailure)
        assertNull(sidecars.read(profile))
        CloakStore.save(app, profile)
        app.getSharedPreferences("device-profile-engine", 0).edit().putString(profile.getString("id"), "corrupt").commit()
        assertNull(sidecars.read(profile))
        assertNotNull(CloakStore.find(app, profile.getString("id")))
    }

    @Test fun editedBodyInvalidatesSidecarWithoutDeletingAnythingAndKeyOrderIsIrrelevant() {
        val raw = bundle(); val profile = raw.getJSONObject("profile")
        sidecars.save(profile, raw.getJSONObject("metadata")) { CloakStore.save(app, it) }
        val reordered = JSONObject()
        profile.keys().asSequence().toList().reversed().forEach { reordered.put(it, profile.get(it)) }
        assertNotNull(sidecars.read(reordered))
        assertNull(sidecars.read(JSONObject(profile.toString()).put("name", "edited")))
        assertNotNull(sidecars.read(profile))
    }
    @Test fun failedBodyWriteRestoresPreviousCompleteSidecar() {
        val raw = bundle(); val profile = raw.getJSONObject("profile")
        val minimal = JSONObject(raw.getJSONObject("metadata").toString()).also { it.remove("source_confidence") }
        sidecars.save(profile, minimal) { CloakStore.save(app, it) }
        val before = File(app.filesDir, "profiles/${profile.getString("id")}.json").readBytes()
        assertTrue(runCatching { sidecars.save(profile, raw.getJSONObject("metadata")) { throw IOException("disk") } }.isFailure)
        assertEquals(minimal.toString(), sidecars.read(profile)!!.toString())
        assertArrayEquals(before, File(app.filesDir, "profiles/${profile.getString("id")}.json").readBytes())
    }
    @Test fun integralFloatingPointValuesKeepMetadataAfterJsonStorageRoundTrip() {
        val raw = bundle(); val profile = raw.getJSONObject("profile")
        profile.getJSONObject("display").put("refresh_rate_hz", 90.0)
        val id = sidecars.save(profile, raw.getJSONObject("metadata")) { CloakStore.save(app, it) }
        assertNotNull(sidecars.read(CloakStore.find(app, id)!!))
    }
}
