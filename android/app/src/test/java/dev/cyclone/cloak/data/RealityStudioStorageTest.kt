package dev.cyclone.cloak.data

import android.app.Application
import dev.cyclone.cloak.forge.CloakForge
import dev.cyclone.cloak.forge.RealityStudio
import dev.cyclone.cloak.forge.TestPhones
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RealityStudioStorageTest {
    @Test fun confirmedAtomicLocalSavePreservesBindingsAndPublicationEvidence() {
        val app: Application = RuntimeEnvironment.getApplication()
        val binding = File(app.noBackupFilesDir, "cloak-bindings-v1.json")
        binding.parentFile!!.mkdirs(); binding.writeText("{\"schemaVersion\":2,\"bindings\":[]}")
        val publication = File(app.filesDir, "publication-evidence.json").also { it.writeText("{\"result\":\"COMPLETED\"}") }
        val bindingBytes = binding.readBytes(); val published = publication.readBytes()
        val p = CloakForge.forgeProfile("Studio", "ab".repeat(32), TestPhones["pixel_7"], "0.4")
        CloakStore.save(app, p)
        val preview = RealityStudio.Session(p).edit("reality.memory.values.storage_gib", "256")
        assertEquals(EngineMetadataStore.digest(p), preview.originalHash)
        RealityStudio.save(preview, p, CloakStore.all(app), true) { CloakStore.save(app, it) }
        assertEquals(256, requireNotNull(CloakStore.find(app, p.getString("id"))).getJSONObject("reality").getJSONObject("memory").getJSONObject("values").getInt("storage_gib"))
        assertArrayEquals(bindingBytes, binding.readBytes()); assertArrayEquals(published, publication.readBytes())
    }
}
