package dev.cyclone.cloak.forge

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class ProfileBackupTest {
    private fun profile(seed: String = "ab".repeat(32), name: String = "Pixel profile") = CloakForge.forgeProfile(name, seed, TestPhones["pixel_7"])
    @Test fun defaultExportExcludesSecretsAndBindingPublicationMetadata() {
        val source = profile().put("bindings", "Cyclone_private").put("meta", JSONObject().put("token", "private-token"))
            .put("network", JSONObject().put("egress_hint", "private-host")).put("integrity", JSONObject().put("keybox_tag", "private-key"))
        val backup = ProfileBackup.export(listOf(source), "0.10.0-rc.1", label = "Weekend", now = Instant.parse("2026-10-11T12:00:00Z"))
        val body = backup.getJSONArray("profiles").getJSONObject(0)
        assertFalse(body.has("seed")); assertFalse(body.has("identifiers")); assertFalse(body.has("id"))
        for (secret in listOf("private-token", "Cyclone_private", "private-key", "private-host", source.getString("seed"))) assertFalse(backup.toString().contains(secret))
        val manifest = backup.getJSONObject("manifest")
        assertEquals("0.4", manifest.getString("schema_version")); assertEquals("0.10.0-rc.1", manifest.getString("app_version"))
        assertEquals(1, manifest.getInt("profile_count")); assertEquals("Weekend", manifest.getString("label"))
    }
    @Test fun safeBackupImportsWithFreshIdentityAndExplicitConfirmation() {
        val source = profile(); val backup = ProfileBackup.export(listOf(source), "v", false)
        val review = ProfileBackup.parse(backup.toString(), emptyList(), seed = { "cd".repeat(32) })
        assertTrue(review.canSave)
        assertNotEquals(source.getString("seed"), review.profiles.single().getString("seed"))
        assertNotEquals(source.getJSONObject("identifiers").getString("android_id"), review.profiles.single().getJSONObject("identifiers").getString("android_id"))
        var calls = 0
        assertThrows(IllegalArgumentException::class.java) { ProfileBackup.commit(review, emptyList(), false, true) { calls++ } }
        assertEquals(0, calls)
        ProfileBackup.commit(review, emptyList(), true, true) { assertEquals(1, it.size); calls++ }
        assertEquals(1, calls)
    }
    @Test fun explicitIdentitySelectionRetainsSeedAndIdentifiersButExcludesOtherData() {
        val source = profile(); val backup = ProfileBackup.export(listOf(source), "v", true)
        val body = backup.getJSONArray("profiles").getJSONObject(0)
        assertEquals(source.getString("seed"), body.getString("seed"))
        assertTrue(source.getJSONObject("identifiers").similar(body.getJSONObject("identifiers")))
        assertFalse(body.has("health")); assertFalse(body.has("meta"))
        assertTrue(ProfileBackup.parse(backup.toString(), emptyList()).canSave)
    }
    @Test fun invalidProfilePreventsWholeBatchAndNeverCallsSave() {
        val one = profile(); val two = profile("cd".repeat(32), "Other")
        val backup = ProfileBackup.export(listOf(one, two), "v", true)
        backup.getJSONArray("profiles").getJSONObject(1).getJSONObject("display").put("width", -1)
        val review = ProfileBackup.parse(backup.toString(), emptyList())
        assertFalse(review.canSave)
        assertTrue(review.findings.any { it.isError && it.field == "profiles[1].display.width" })
        var saved = false
        assertThrows(IllegalArgumentException::class.java) { ProfileBackup.commit(review, emptyList(), true, true) { saved = true } }
        assertFalse(saved)
    }
    @Test fun duplicatesWithinImportAndAgainstLibraryRequireAcknowledgement() {
        val source = profile(); val existing = listOf(source.getString("id") to source)
        val backup = ProfileBackup.export(listOf(source, source), "v", true)
        val review = ProfileBackup.parse(backup.toString(), existing)
        assertTrue(review.canSave)
        assertEquals(2, review.profiles.map { it.getString("id") }.distinct().size)
        assertTrue(review.profiles.none { it.getString("id") == source.getString("id") })
        assertTrue(review.findings.any { it.code == "DUPLICATE_IDENTIFIER" })
        assertThrows(IllegalArgumentException::class.java) { ProfileBackup.commit(review, existing, true, false) {} }
        ProfileBackup.commit(review, existing, true, true) {}
        assertEquals("Pixel profile", source.getString("name"))
    }
    @Test fun mismatchedSchemaFormatCountAndMalformedEntriesFailSafely() {
        val backup = ProfileBackup.export(listOf(profile()), "v", true)
        val mutations: List<(JSONObject) -> Unit> = listOf(
            { it.put("schemaVersion", 999) }, { it.getJSONObject("manifest").put("schema_version", "999") },
            { it.getJSONObject("manifest").put("profile_count", 2) }, { it.getJSONArray("profiles").put(0, "not a profile") },
            { it.getJSONArray("profiles").getJSONObject(0).put("schema_version", "999") },
        )
        mutations.forEach { mutate ->
            val invalid = JSONObject(backup.toString()); mutate(invalid)
            assertThrows(Exception::class.java) { ProfileBackup.parse(invalid.toString(), emptyList()) }
        }
        assertThrows(Exception::class.java) { ProfileBackup.parse("{broken", emptyList()) }
        assertThrows(Exception::class.java) { ProfileBackup.parse(backup.toString() + " trailing", emptyList()) }
    }
    @Test fun legacySingleProfileAndFleetRequireReviewedBatchCommit() {
        val source = profile()
        assertTrue(ProfileBackup.parse(source.toString(), emptyList()).canSave)
        val fleet = CloakFleet.exportFleet(listOf(source.getString("id") to source))
        assertEquals(1, ProfileBackup.parse(fleet.toString(), emptyList()).profiles.size)
        fleet.getJSONArray("profiles").put("invalid")
        assertThrows(Exception::class.java) { ProfileBackup.parse(fleet.toString(), emptyList()) }
    }
    @Test fun changedLibraryRejectsStaleReview() {
        val source = profile(); val review = ProfileBackup.parse(source.toString(), emptyList())
        assertThrows(IllegalArgumentException::class.java) { ProfileBackup.commit(review, listOf("other" to profile("cd".repeat(32))), true, true) {} }
    }
}
