package dev.cyclone.cloak.root

import dev.cyclone.cloak.cyclone.*
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit

/**
 * Runs the real merge-publish shell against a temporary root (POSIX sh). One device-wide state tree is shared by every
 * Cloak install on the phone; publishing from one Android user must never drop another install's bindings.
 */
class CloakPublishMergeTest {
    private lateinit var root: File
    private lateinit var work: File

    @Before
    fun setUp() {
        assumeTrue(File("/bin/sh").canExecute())
        work = Files.createTempDirectory("cloak-merge").toFile()
        root = File(work, "adb/cyclone_cloak")
    }

    private val b = "Cyclone_bbbbbbbbbbbbbbbb"
    private val c = "Cyclone_cccccccccccccccc"

    /** Builds a staging tree the way CloakResolver does, for [publisher]. */
    private fun stage(name: String, publisher: Int, vararg bindings: Triple<String, Int, String>): File {
        val staging = File(work, "staging-$name").apply { deleteRecursively(); mkdirs() }
        val entries = JSONObject()
        for ((profileId, user, pkg) in bindings) {
            val key = CloakStateLayout.key(profileId, user, pkg)
            File(staging, key).mkdirs()
            File(staging, "$key/profile.json").writeText("""{"name":"$name","device":{}}""")
            entries.put("$user/$pkg", JSONObject().put("profileId", profileId).put("cloakProfileId", name).put("key", key)
                .put("publisher", publisher))
        }
        File(staging, CloakStateLayout.INDEX_PART_FILE).writeText(CloakResolver.indexPart(entries))
        return staging
    }

    private fun publish(staging: File, scope: PublishScope): Int {
        val script = "set -eu; umask 077; " + CloakRootDoctor.mergePublishScript(root.absolutePath, staging.absolutePath, scope)
        val process = ProcessBuilder("/bin/sh", "-c", script).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        assertTrue("script hung", process.waitFor(20, TimeUnit.SECONDS))
        assertEquals("script failed: $output", 0, process.exitValue())
        return process.exitValue()
    }

    private fun indexText() = File(root, "state-v1/index.json").readText()
    private fun index(): JSONObject = JSONObject(indexText()).getJSONObject("entries")
    private fun profileName(user: Int, pkg: String): String {
        val key = index().getJSONObject("$user/$pkg").getString("key")
        return JSONObject(File(root, "state-v1/$key/profile.json").readText()).getString("name")
    }

    @Test
    fun aProfilesCloakPublishingKeepsMainsBindings() {
        publish(stage("main", 0, Triple(b, 11, "com.a"), Triple(c, 12, "com.a")), PublishScope(0, true, listOf(11, 12)))
        assertEquals(setOf("11/com.a", "12/com.a"), index().keys().asSequence().toSet())

        publish(stage("inC", 12, Triple(c, 12, "com.c")), PublishScope(12, false, null))
        assertEquals(setOf("11/com.a", "12/com.a", "12/com.c"), index().keys().asSequence().toSet())
        assertEquals("main", profileName(11, "com.a"))
        assertEquals("inC", profileName(12, "com.c"))

        // C unbinds its app: only C's share changes.
        publish(stage("inC", 12), PublishScope(12, false, null))
        assertEquals(setOf("11/com.a", "12/com.a"), index().keys().asSequence().toSet())
        assertFalse(File(root, "state-v1/${CloakStateLayout.key(c, 12, "com.c")}").exists())

        // Main republishes: C's share (now empty) and Main's are both kept as they are.
        publish(stage("main", 0, Triple(b, 11, "com.a")), PublishScope(0, true, listOf(11, 12)))
        assertEquals(setOf("11/com.a"), index().keys().asSequence().toSet())
    }

    @Test
    fun mainWinsAnAppBothBound() {
        publish(stage("inC", 12, Triple(c, 12, "com.a")), PublishScope(12, false, null))
        publish(stage("main", 0, Triple(c, 12, "com.a")), PublishScope(0, true, listOf(12)))
        // Same tuple, same key directory: Main's copy is assembled last.
        assertEquals("main", JSONObject(File(root, "state-v1/${CloakStateLayout.key(c, 12, "com.a")}/profile.json").readText()).getString("name"))
        // The index holds both members; Main's is last, and last wins in the module (nlohmann) and in Android's org.json.
        val text = indexText()
        assertTrue(text.lastIndexOf("\"cloakProfileId\":\"main\"") > text.lastIndexOf("\"cloakProfileId\":\"inC\""))
    }

    @Test
    fun mainDropsTheShareOfAProfileThatNoLongerExists() {
        publish(stage("inD", 13, Triple("Cyclone_dddddddddddddddd", 13, "com.a")), PublishScope(13, false, null))
        publish(stage("main", 0, Triple(b, 11, "com.a")), PublishScope(0, true, listOf(11)))
        assertEquals(setOf("11/com.a"), index().keys().asSequence().toSet())
        assertFalse(File(root, "publishers/13").exists())
    }

    @Test
    fun aTreePublishedBeforeSharesExistedBecomesMainsShare() {
        // What 0.8.0-alpha.6 and older published: one index for everything.
        val legacyKey = CloakStateLayout.key(b, 11, "com.a")
        File(root, "state-v1/$legacyKey").mkdirs()
        File(root, "state-v1/$legacyKey/profile.json").writeText("""{"name":"legacy","device":{}}""")
        // Byte for byte what Android's org.json wrote (insertion order: schemaVersion, then entries).
        File(root, "state-v1/index.json").writeText(
            """{"schemaVersion":2,"entries":{"11/com.a":{"profileId":"$b","cloakProfileId":"legacy","key":"$legacyKey"},""" +
                """"11/com.b":{"profileId":"$b","cloakProfileId":"legacy","key":"$legacyKey"}}}""",
        )
        // The first publish after the update comes from Cloak inside C.
        publish(stage("inC", 12, Triple(c, 12, "com.c")), PublishScope(12, false, null))
        assertEquals(setOf("11/com.a", "11/com.b", "12/com.c"), index().keys().asSequence().toSet())
        assertEquals("legacy", profileName(11, "com.a"))
    }

    @Test
    fun aLegacyIndexInTheOtherKeyOrderIsKeptToo() {
        val legacyKey = CloakStateLayout.key(b, 11, "com.a")
        File(root, "state-v1/$legacyKey").mkdirs()
        File(root, "state-v1/$legacyKey/profile.json").writeText("""{"name":"legacy","device":{}}""")
        File(root, "state-v1/index.json").writeText(
            """{"entries":{"11/com.a":{"profileId":"$b","cloakProfileId":"legacy","key":"$legacyKey"}},"schemaVersion":2}""",
        )
        publish(stage("inC", 12), PublishScope(12, false, null))
        assertEquals(setOf("11/com.a"), index().keys().asSequence().toSet())
    }

    @Test
    fun anUnreadableLegacyIndexIsDroppedNotCopied() {
        File(root, "state-v1").mkdirs()
        File(root, "state-v1/index.json").writeText("garbage")
        publish(stage("inC", 12, Triple(c, 12, "com.c")), PublishScope(12, false, null))
        assertEquals(setOf("12/com.c"), index().keys().asSequence().toSet())
    }

    @Test
    fun anEmptyPhoneStillGetsAValidIndex() {
        publish(stage("main", 0), PublishScope(0, true, emptyList()))
        assertEquals(0, index().length())
        assertEquals(2, JSONObject(indexText()).getInt("schemaVersion"))
    }

    @Test
    fun theFullPublishScriptStillChecksTheModuleFirst() {
        val script = CloakRootDoctor.publishScript(File("/data/user/12/dev.cyclone.cloak/cache/state-staging"), "arm64-v8a", "0.9.0-alpha.1",
            emptyList(), PublishScope(12, false, null))
        assertTrue(script.indexOf("CLOAK_DOCTOR=MODULE_MISSING") < script.indexOf("publishers"))
        assertTrue(script.contains("me='12'"))
        // Only Main marks itself as Main.
        assertFalse(script.contains("echo \"\$me\" > \"\$pubs.main\""))
        assertTrue(script.endsWith("echo CLOAK_DOCTOR=READY"))
    }

    @Test
    fun theMirrorReadParsesItsMarkersOutsideTheIndex() {
        val output = "CLOAK_ROOT=OK\nCLOAK_MODULE_VERSION=v1\nCLOAK_INDEX_BEGIN\n{\"schemaVersion\":2,\"entries\":{}}\nCLOAK_INDEX_END\nCLOAK_DOCTOR=READY"
        assertEquals("{\"schemaVersion\":2,\"entries\":{}}", CloakRootDoctor.publishedIndex(output))
        assertEquals(null, CloakRootDoctor.publishedIndex("CLOAK_DOCTOR=MODULE_MISSING"))
    }
}
