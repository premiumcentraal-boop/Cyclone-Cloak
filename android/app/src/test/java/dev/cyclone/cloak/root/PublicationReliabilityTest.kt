package dev.cyclone.cloak.root

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class PublicationReliabilityTest {
    @get:Rule val temp = TemporaryFolder()
    private fun validStage(): File = temp.newFolder().apply {
        val key = "ab".repeat(32)
        File(this, "index.part").writeText("\"11/com.test\":{\"key\":\"$key\"}")
        File(this, key).mkdirs()
        File(this, "$key/profile.json").writeText("{\"name\":\"unchanged\"}")
        File(this, "$key/pif.json").writeText("{}")
    }
    @Test fun validAndEmptyStagingRemainAcceptedWithoutRewritingBodies() {
        val stage = validStage()
        val profile = File(stage, "${"ab".repeat(32)}/profile.json")
        val before = profile.readBytes()
        assertTrue(CloakRootDoctor.validateStaging(stage))
        assertArrayEquals(before, profile.readBytes())
        val empty = temp.newFolder(); File(empty, "index.part").writeText("")
        assertTrue(CloakRootDoctor.validateStaging(empty))
    }
    @Test fun malformedPartMissingProfileAndTraversalAreRejected() {
        val malformed = validStage(); File(malformed, "index.part").writeText("not JSON")
        assertFalse(CloakRootDoctor.validateStaging(malformed))
        val missing = validStage(); File(missing, "${"ab".repeat(32)}/profile.json").delete()
        assertFalse(CloakRootDoctor.validateStaging(missing))
        val unsafe = validStage(); File(unsafe, "index.part").writeText("\"11/com.test\":{\"key\":\"../other\"}")
        assertFalse(CloakRootDoctor.validateStaging(unsafe))
    }
    @Test fun busyOperationalFailureAndOpenFailureMapToDifferentReasons() {
        assertEquals(PublicationReason.LOCKED, CloakRootDoctor.parseResult("CLOAK_ROOT=OK\nCLOAK_PUBLICATION=LOCKED\nCLOAK_DOCTOR=PUBLISH_FAILED", 48).publicationReason)
        assertEquals(PublicationReason.LOCK_FAILED, CloakRootDoctor.parseResult("CLOAK_ROOT=OK\nCLOAK_PUBLICATION=LOCK_FAILED\nCLOAK_DOCTOR=PUBLISH_FAILED", 48).publicationReason)
        val open = CloakRootDoctor.parseResult("CLOAK_ROOT=OK\nCLOAK_PHASE=LOCK_OPEN:STARTED", 1)
        assertEquals(PublicationReason.LOCK_FAILED, open.publicationReason)
        assertFalse(open.message.contains("Another publisher"))
    }
    @Test fun generatedScriptUsesInheritedStandardDescriptorAndNeverUnlinksLock() {
        val script = CloakRootDoctor.mergePublishScript("/root", "/stage", PublishScope(11, false, null))
        assertTrue(script.contains("exec 0>>")); assertTrue(script.contains("flock -n 0"))
        assertFalse(script.contains("flock -n 9"))
        assertFalse(Regex("rm[^;]*publication\\.lock").containsMatchIn(script))
    }
}
