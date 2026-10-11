package dev.cyclone.cloak.root

import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.After
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit

/** Exercises the production shell, including injected filesystem failures, with real kernel locks. */
class PublicationRecoveryTest {
    private lateinit var work: File
    private lateinit var root: File
    private val wsl = System.getenv("CLOAK_TEST_WSL") == "1"
    private fun path(file: File): String {
        val p = file.absolutePath.replace('\\', '/')
        return if (wsl) "/mnt/${p[0].lowercaseChar()}${p.substring(2)}" else p
    }
    private fun quote(s: String) = "'" + s.replace("'", "'\\''") + "'"
    private fun start(script: String): Process {
        // A script file avoids Windows/WSL command-line quote expansion of shell variables.
        val source = Files.createTempFile("cloak-shell-", ".sh").toFile()
        source.writeText(script + "\n")
        source.deleteOnExit()
        return ProcessBuilder(if (wsl)
            listOf("wsl.exe", "-d", "Ubuntu", "--exec", "/bin/sh", path(source))
            else listOf("/bin/sh", source.absolutePath)).redirectErrorStream(true).start()
    }
    private fun result(process: Process): Pair<Int, String> {
        assertTrue("shell timed out", process.waitFor(30, TimeUnit.SECONDS))
        return process.exitValue() to process.inputStream.bufferedReader().readText()
    }
    @Before fun setup() {
        assumeTrue(wsl || File("/bin/sh").canExecute())
        assumeTrue(result(start("command -v flock" )).first == 0)
        work = Files.createTempDirectory("cloak-publication").toFile()
        root = File(work, "root").apply { mkdirs() }
    }
    @After fun cleanup() { if (::work.isInitialized) work.deleteRecursively() }
    private fun stage(user: Int): File = File(work, "stage-$user").apply {
        mkdirs()
        File(this, "index.part").writeText("\"$user/com.test\":{\"key\":\"$user\"}")
        File(this, "$user").mkdirs()
        File(this, "$user/profile.json").writeText("{\"publisher\":$user}")
    }
    private fun script(user: Int, before: String = ""): String = "set -eu; $before " +
        CloakRootDoctor.mergePublishScript(path(root), path(stage(user)), PublishScope(user, false, null))
    private fun publish(user: Int, before: String = "") = result(start(script(user, before)))
    private fun seed(): String {
        val (exit, output) = publish(11)
        assertEquals(output, 0, exit)
        return File(root, "state-v1/index.json").readText()
    }
    private val failSwap = "mv() { case \"\$1\" in */state-v1.tmp) return 1;; esac; command mv \"\$@\"; };"

    @Test fun completedReportsSuccessAndKeepsLockInode() {
        val (exit, output) = publish(11)
        assertEquals(output, 0, exit)
        assertTrue(output.contains("CLOAK_PUBLICATION=COMPLETED"))
        assertTrue(File(root, "publication.lock").isFile)
        assertFalse(File(root, "publication.owner").exists())
    }
    @Test fun staleMarkerIsRecoveredOnlyAfterAcquiringKernelLock() {
        File(root, "publication.owner").writeText("stale owner, reused PID")
        val (exit, output) = publish(11)
        assertEquals(output, 0, exit)
        assertTrue(output.contains("CLOAK_PUBLICATION=RECOVERED"))
        assertTrue(output.contains("CLOAK_PUBLICATION_DETAIL=STALE_LOCK"))
        assertTrue(CloakRootDoctor.parseResult(output + "\nCLOAK_DOCTOR=READY", 0).staleLockDetected)
        assertFalse(File(root, "publication.owner").exists())
    }
    @Test fun killedOwnerReleasesKernelLockAndLeavesRecoverableMarker() {
        val lock = quote(path(File(root, "publication.lock")))
        val owner = quote(path(File(root, "publication.owner")))
        val crashed = result(start("exec 9>>$lock; flock -n 9; echo crashed > $owner; kill -KILL \$\$"))
        assertNotEquals(0, crashed.first)
        val (exit, output) = publish(11)
        assertEquals(output, 0, exit)
        assertTrue(output.contains("CLOAK_PUBLICATION=RECOVERED"))
    }
    @Test fun concurrentPublisherCannotChangeSharesOrTree() {
        val old = seed()
        val gate = File(root, "held")
        val release = File(root, "release")
        val pause = "cp() { touch ${quote(path(gate))}; " +
            "while [ ! -e ${quote(path(release))} ]; do sleep 0.05; done; command cp \"\$@\"; };"
        val holder = start(script(11, pause))
        try {
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
            while (!gate.exists() && System.nanoTime() < deadline) Thread.sleep(20)
            assertTrue("lock holder did not start", gate.exists())
            val owner = File(root, "publication.owner").readText()
            val (exit, output) = publish(12)
            assertNotEquals(output, 0, exit)
            assertTrue(output.contains("CLOAK_PUBLICATION=LOCKED"))
            assertFalse(output.contains("CLOAK_PUBLICATION_DETAIL=STALE_LOCK"))
            assertEquals(old, File(root, "state-v1/index.json").readText())
            assertFalse(File(root, "publishers/12").exists())
            assertEquals(owner, File(root, "publication.owner").readText())
        } finally { release.writeText(""); val (exit, output) = result(holder); assertEquals(output, 0, exit) }
        assertEquals(0, publish(12).first)
        val merged = File(root, "state-v1/index.json").readText()
        assertTrue(merged.contains("11/com.test") && merged.contains("12/com.test"))
    }
    @Test fun failedFinalRenameRestoresByteIdenticalTree() {
        val old = seed()
        val (exit, output) = publish(12, failSwap)
        assertNotEquals(output, 0, exit)
        assertTrue(output.contains("CLOAK_PUBLICATION=FAILED"))
        assertEquals(old, File(root, "state-v1/index.json").readText())
        assertTrue(File(root, "state-v1/11/profile.json").isFile)
        assertEquals(0, publish(12).first)
    }
    @Test fun failedPostSwapCheckRollsBackCompleteTree() {
        val old = seed()
        val state = quote(path(File(root, "state-v1/index.json")))
        val fault = "mv() { command mv \"\$@\" || return \$?; case \"\$1\" in */state-v1.tmp) : > $state;; esac; };"
        val (exit, output) = publish(12, fault)
        assertNotEquals(output, 0, exit)
        assertTrue(output.contains("CLOAK_PUBLICATION_DETAIL=LAST_TREE_RETAINED"))
        assertEquals(old, File(root, "state-v1/index.json").readText())
        assertEquals("{\"publisher\":11}", File(root, "state-v1/11/profile.json").readText())
    }
    @Test fun interruptedIncompleteCandidateRestoresPreviousCompleteTree() {
        seed()
        assertTrue(File(root, "state-v1").renameTo(File(root, "state-v1.previous")))
        File(root, "state-v1").mkdirs()
        File(root, "state-v1/index.json").writeText("")
        File(root, "publication.owner").writeText("interrupted")
        val (exit, output) = publish(12)
        assertEquals(output, 0, exit)
        assertTrue(output.contains("CLOAK_PUBLICATION=RECOVERED"))
        assertTrue(output.contains("CLOAK_PUBLICATION_DETAIL=STALE_LOCK"))
        assertTrue(File(root, "state-v1/11/profile.json").isFile)
        assertTrue(File(root, "state-v1/12/profile.json").isFile)
    }
    @Test fun killedDuringTreeRetirementRecoversOnNextPublication() {
        val old = seed()
        val fault = "mv() { command mv \"\$@\" || return \$?; case \"\$1\" in */state-v1) kill -KILL \$(sh -c 'echo \$PPID');; esac; };"
        assertNotEquals(0, publish(12, fault).first)
        assertEquals(old, File(root, "state-v1.previous/index.json").readText())
        val (exit, output) = publish(12)
        assertEquals(output, 0, exit)
        assertTrue(output.contains("CLOAK_PUBLICATION=RECOVERED"))
        assertTrue(File(root, "state-v1/11/profile.json").isFile)
    }
    @Test fun failedRollbackRetainsBackupAndNextAttemptRecoversIt() {
        val old = seed()
        val faults = "mv() { case \"\$1\" in */state-v1.tmp|*/state-v1.previous) return 1;; esac; command mv \"\$@\"; };"
        val (exit, output) = publish(12, faults)
        assertNotEquals(output, 0, exit)
        assertTrue(output.contains("CLOAK_PUBLICATION_DETAIL=RESTORE_PENDING"))
        assertEquals(old, File(root, "state-v1.previous/index.json").readText())
        val (retry, recovered) = publish(12)
        assertEquals(recovered, 0, retry)
        assertTrue(recovered.contains("CLOAK_PUBLICATION=RECOVERED"))
        assertTrue(File(root, "state-v1/11/profile.json").isFile)
    }
    @Test fun interruptedShareRenameRestoresOldShareBeforeMerge() {
        seed()
        assertTrue(File(root, "publishers/11").renameTo(File(root, "publishers/11.old")))
        val (exit, output) = publish(12)
        assertEquals(output, 0, exit)
        assertTrue(output.contains("CLOAK_PUBLICATION=RECOVERED"))
        assertTrue(File(root, "state-v1/11/profile.json").isFile)
    }
    @Test fun failedShareRenameRestoresPreviousShare() {
        seed()
        val faults = "mv() { case \"\$1\" in */11.tmp) return 1;; esac; command mv \"\$@\"; };"
        val (exit, output) = publish(11, faults)
        assertNotEquals(output, 0, exit)
        assertTrue(output.contains("CLOAK_PUBLICATION=FAILED"))
        assertTrue(File(root, "publishers/11/index.part").isFile)
        assertTrue(File(root, "state-v1/11/profile.json").isFile)
    }
    @Test fun failureBeforeTreeSwapLeavesOldTreeUntouched() {
        val old = seed()
        val faults = "cp() { return 1; };"
        assertNotEquals(0, publish(12, faults).first)
        assertEquals(old, File(root, "state-v1/index.json").readText())
    }
    @Test fun failedLegacyMigrationKeepsCompleteTreeForRetry() {
        val old = seed()
        File(root, "publishers").deleteRecursively()
        val faults = "mv() { case \"\$1\" in */publishers.migration) return 1;; esac; command mv \"\$@\"; };"
        assertNotEquals(0, publish(12, faults).first)
        assertEquals(old, File(root, "state-v1/index.json").readText())
        assertFalse(File(root, "publishers").exists())
        assertEquals(0, publish(12).first)
        assertTrue(File(root, "state-v1/11/profile.json").isFile)
        assertTrue(File(root, "state-v1/12/profile.json").isFile)
    }
    @Test fun resultParserDistinguishesAllPublicationReasons() {
        for (reason in PublicationReason.entries) {
            val success = reason == PublicationReason.COMPLETED || reason == PublicationReason.RECOVERED
            val output = "CLOAK_PUBLICATION=${reason.name}\nCLOAK_DOCTOR=${if (success) "READY" else "PUBLISH_FAILED"}"
            val result = CloakRootDoctor.parseResult(output, if (success) 0 else 48)
            assertEquals(reason, result.publicationReason)
            assertEquals(success, result.published)
            assertTrue(result.message.contains(reason.message))
        }
        val failed = CloakRootDoctor.parseResult("CLOAK_PUBLICATION=COMPLETED\nCLOAK_DOCTOR=READY", 48)
        assertFalse(failed.published)
        assertEquals(PublicationReason.FAILED, failed.publicationReason)
    }
}
