package dev.cyclone.cloak.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class JsonDirectoryTest {
    private val root = Files.createTempDirectory("cloak-json").toFile()
    private val dir = JsonDirectory(root)

    @Test
    fun writesReadsAndDeletesById() {
        dir.write("abc", JSONObject().put("name", "A"))
        assertEquals("A", dir.read("abc")!!.getString("name"))
        assertTrue(dir.delete("abc"))
        assertNull(dir.read("abc"))
    }

    @Test
    fun aCorruptFileIsSkippedNotFatal() {
        dir.write("good", JSONObject().put("name", "G"))
        File(root, "bad.json").writeText("{not json")
        assertEquals(listOf("good"), dir.all().map { it.first })
        assertNull(dir.read("bad"))
    }

    @Test
    fun idsCantEscapeTheFolder() {
        dir.write("../../etc/x", JSONObject())
        assertEquals(listOf(".._.._etc_x"), root.list()!!.filter { it.endsWith(".json") }.map { it.removeSuffix(".json") })
        assertTrue(root.listFiles()!!.none { it.name.endsWith(".tmp") })
    }
}
