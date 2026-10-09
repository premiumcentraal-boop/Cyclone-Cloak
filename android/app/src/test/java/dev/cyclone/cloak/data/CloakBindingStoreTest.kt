package dev.cyclone.cloak.data

import dev.cyclone.cloak.root.PublishScope

import dev.cyclone.cloak.forge.*
import dev.cyclone.cloak.cyclone.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CloakBindingStoreTest {
    private val b = "Cyclone_bbbbbbbbbbbbbbbb"
    private val c = "Cyclone_cccccccccccccccc"

    @Test
    fun bindingsAreKeyedByProfileAndAppNotUserNumber() {
        val list = CloakBindingStore.upsertIn(listOf(binding(b, 11, "com.a")), binding(b, 21, "com.a", cloak = "new"))
        assertEquals(1, list.size)
        assertEquals(21, list.single().androidUserId)
        assertEquals("new", list.single().cloakProfileId)
    }

    @Test
    fun moveAndForgetFollowTheProfile() {
        val list = listOf(binding(b, 11, "com.a"), binding(b, 11, "com.b"), binding(c, 12, "com.a"))
        val moved = CloakBindingStore.moveUser(list, b, 21)
        assertEquals(listOf(21, 21, 12), moved.map { it.androidUserId })
        assertEquals(listOf(c), CloakBindingStore.forgetProfile(moved, b).map { it.profileId })
    }

    @Test
    fun mainsMirrorReplacesOlderMirrorsAndWinsOverALocalBindingForTheSameApp() {
        val local = binding(c, 12, "com.a", cloak = "local")
        val keep = binding(c, 12, "com.keep", cloak = "local")
        val oldMirror = binding(c, 12, "com.gone", origin = CloakBinding.ORIGIN_MAIN)
        val mirror = binding(c, 12, "com.a", cloak = "main", origin = CloakBinding.ORIGIN_MAIN)
        val next = CloakBindingStore.replaceMirrored(listOf(local, keep, oldMirror), listOf(mirror))
        assertEquals(setOf("com.keep" to "local", "com.a" to "main"), next.map { it.packageName to it.cloakProfileId }.toSet())
    }

    @Test
    fun oldFilesKeyedByUserNumberCollapseToTheNewestRecord() {
        val text = JSONObject().put("schemaVersion", 1).put("bindings", JSONArray()
            .put(JSONObject().put("profileId", b).put("androidUserId", 11).put("packageName", "com.a")
                .put("cloakProfileId", "old").put("updatedAt", 1))
            .put(JSONObject().put("profileId", b).put("androidUserId", 21).put("packageName", "com.a")
                .put("cloakProfileId", "new").put("updatedAt", 2))).toString()
        val parsed = CloakBindingStore.parse(text)
        assertEquals(listOf("new"), parsed.map { it.cloakProfileId })
        assertEquals(CloakBinding.ORIGIN_LOCAL, parsed.single().origin)
    }

    @Test
    fun aRoundTripKeepsOriginAndMirroredSummaryAndEqualsItself() {
        val mirrored = binding(c, 12, "com.a", origin = CloakBinding.ORIGIN_MAIN)
            .copy(mirroredSummary = CloakIdentity.summary("x", null).toString())
        val again = CloakBindingStore.parse(CloakBindingStore.serialize(listOf(mirrored)))
        assertEquals(listOf(mirrored), again)
        assertTrue(again.single().mirroredSummary!!.contains("\"cloakProfileId\":\"x\""))
    }

    @Test
    fun publishScopeLimitsAProfilesCloakToItsOwnApps() {
        val inC = PublishScope(12, isMain = false, liveUsers = null)
        assertTrue(inC.publishes(binding(c, 12, "com.a")))
        assertTrue(!inC.publishes(binding(b, 11, "com.a")))
        assertTrue(!inC.publishes(binding(c, 12, "com.a", origin = CloakBinding.ORIGIN_MAIN)))
        val main = PublishScope(0, isMain = true, liveUsers = listOf(11, 12))
        assertTrue(main.publishes(binding(b, 11, "com.a")) && main.publishes(binding(c, 12, "com.a")))
    }
}
