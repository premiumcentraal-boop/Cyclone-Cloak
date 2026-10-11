package dev.cyclone.cloak.data

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class AtomicLibraryImportTest {
    @get:Rule val temp = TemporaryFolder()
    private fun move(a: File, b: File) { Files.move(a.toPath(), b.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING) }
    @Test fun successfulBatchPreservesExistingFilesAndAddsAllEntries() {
        val folder = File(temp.root, "profiles"); val store = JsonDirectory(folder)
        store.write("old", JSONObject().put("keep", 1)); val old = File(folder, "old.json").readBytes()
        File(folder, "corrupt.json").writeText("{broken")
        store.writeBatch(listOf("one" to JSONObject().put("v", 1), "two" to JSONObject().put("v", 2)))
        assertArrayEquals(old, File(folder, "old.json").readBytes())
        assertEquals("{broken", File(folder, "corrupt.json").readText())
        assertEquals(3, store.all().size)
    }
    @Test fun partialStagingFailurePublishesNoneOfTheImportedBatch() {
        val folder = File(temp.root, "profiles"); val store = JsonDirectory(folder)
        store.write("old", JSONObject().put("keep", 1)); val old = File(folder, "old.json").readBytes()
        val broken = object : JSONObject() { override fun toString(indentFactor: Int): String = throw IOException("injected staging failure") }
        assertThrows(IOException::class.java) { store.writeBatch(listOf("one" to JSONObject().put("v", 1), "two" to broken)) }
        assertArrayEquals(old, File(folder, "old.json").readBytes())
        assertEquals(listOf("old"), store.all().map { it.first })
        assertFalse(temp.root.listFiles()!!.any { it.name.startsWith(".profiles.stage-") })
    }
    @Test fun failedFinalSwapRollsBackCompleteLibrary() {
        val folder = File(temp.root, "profiles"); JsonDirectory(folder).write("old", JSONObject().put("keep", 1))
        val bytes = File(folder, "old.json").readBytes(); var moves = 0
        val store = JsonDirectory(folder) { a, b -> if (++moves == 2) throw IOException("injected commit failure") else move(a, b) }
        assertThrows(IOException::class.java) { store.writeBatch(listOf("new" to JSONObject().put("v", 2))) }
        assertArrayEquals(bytes, File(folder, "old.json").readBytes())
        assertFalse(File(folder, "new.json").exists())
        assertEquals(1, JsonDirectory(folder).all().size)
    }
    @Test fun failedRollbackLeavesBackupForNextReadRecovery() {
        val folder = File(temp.root, "profiles"); JsonDirectory(folder).write("old", JSONObject().put("keep", 1))
        var moves = 0
        val store = JsonDirectory(folder) { a, b -> if (++moves >= 2) throw IOException("injected rename failure") else move(a, b) }
        assertThrows(IOException::class.java) { store.writeBatch(listOf("new" to JSONObject().put("v", 2))) }
        assertTrue(File(temp.root, ".profiles.previous/old.json").isFile)
        assertEquals(listOf("old"), JsonDirectory(folder).all().map { it.first })
        assertFalse(File(folder, "new.json").exists())
    }
    @Test fun collisionAndSanitizedNameCollisionCannotOverwrite() {
        val folder = File(temp.root, "profiles"); val store = JsonDirectory(folder)
        store.write("old", JSONObject().put("v", 1))
        assertThrows(IllegalArgumentException::class.java) { store.writeBatch(listOf("old" to JSONObject().put("v", 2))) }
        assertThrows(IllegalArgumentException::class.java) { store.writeBatch(listOf("a/b" to JSONObject(), "a_b" to JSONObject())) }
        assertEquals(1, store.read("old")!!.getInt("v"))
    }
    @Test fun failedAtomicExportLeavesPreviousFileByteIdentical() {
        val folder = File(temp.root, "backups"); JsonDirectory(folder).write("backup", JSONObject().put("complete", true))
        val old = File(folder, "backup.json").readBytes()
        val store = JsonDirectory(folder) { _, _ -> throw IOException("injected export failure") }
        assertThrows(IOException::class.java) { store.write("backup", JSONObject().put("complete", false)) }
        assertArrayEquals(old, File(folder, "backup.json").readBytes())
        assertEquals(listOf("backup.json"), folder.list()!!.sorted())
    }
}
