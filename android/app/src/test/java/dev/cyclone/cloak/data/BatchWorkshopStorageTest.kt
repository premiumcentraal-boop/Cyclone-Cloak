package dev.cyclone.cloak.data

import android.app.Application
import dev.cyclone.cloak.forge.BatchIdentityPlanner
import dev.cyclone.cloak.forge.BatchIdentitySave
import dev.cyclone.cloak.forge.EngineBundleCodec
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
class BatchWorkshopStorageTest {
    @Test fun additiveSavesPreserveBindingsAndBodiesAndKeepMetadataSeparate() {
        val app: Application = RuntimeEnvironment.getApplication()
        val bindingFile = File(app.noBackupFilesDir, "cloak-bindings-v1.json")
        bindingFile.parentFile!!.mkdirs()
        bindingFile.writeText("{\"schemaVersion\":2,\"bindings\":[]}")
        val bytes = bindingFile.readBytes()
        val plan = BatchIdentityPlanner().plan(BatchIdentityPlanner.Request(listOf("pixel_7"), 2, "ab".repeat(32)), TestPhones.all, emptyList())
        val sidecar = EngineMetadataStore(app, EngineBundleCodec::validateMetadata)
        val result = BatchIdentitySave.save(plan, plan.drafts.map { it.key }.toSet(), { CloakStore.all(app).map { it.second } }) { p, m ->
            sidecar.save(p, m) { CloakStore.importBatch(app, listOf(it)); it.getString("id") }
        }
        assertTrue(result.all { it.status == "SAVED" })
        assertArrayEquals(bytes, bindingFile.readBytes())
        plan.drafts.forEach { draft ->
            val profile = draft.profile!!
            assertEquals(EngineMetadataStore.digest(profile), EngineMetadataStore.digest(requireNotNull(CloakStore.find(app, profile.getString("id")))))
            assertNotNull(sidecar.read(profile))
            assertFalse(profile.has("planner_version"))
        }
        val again = BatchIdentitySave.save(plan, plan.drafts.map { it.key }.toSet(), { CloakStore.all(app).map { it.second } }) { _, _ -> fail("Replacement attempted") }
        assertTrue(again.all { it.status == "SKIPPED" })
        assertArrayEquals(bytes, bindingFile.readBytes())
    }
}
