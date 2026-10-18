package dev.cyclone.cloak.ui

import dev.cyclone.cloak.data.JsonDirectory
import dev.cyclone.cloak.forge.CloakForge
import dev.cyclone.cloak.forge.ProfileValidator
import dev.cyclone.cloak.forge.TestPhones
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

class ProfileGenerationTest {
    @get:Rule val temp = TemporaryFolder()
    private val phone get() = TestPhones["pixel_7"]
    private val seed = "ab".repeat(32)
    private fun store(): Pair<File, JsonDirectory> = temp.newFolder().let { it to JsonDirectory(it) }
    @Test fun normalGenerationDeliversMetadataAndUnchangedBodyToTheSidecarSavePath() {
        val result = ProfileGeneration.generate(phone, emptyList(), save = { fail("Sidecar callback must be used"); "" }, seedFactory = { seed },
            saveEngine = { profile, metadata ->
                assertTrue(profile.similar(CloakForge.forgeProfile(profile.getString("name"), seed, phone, "0.4")))
                assertEquals(seed, metadata.getString("chosen_seed"))
                assertEquals(1, metadata.getInt("attempt_count"))
                profile.getString("id")
            })
        assertTrue(result is ProfileGeneration.Result.Saved)
    }

    @Test
    fun validGenerationUsesOneSeedAndAtomicallySavesTheCompleteForgePayload() {
        val (folder, store) = store()
        val before = phone.toJson()
        var seedCalls = 0
        var saveCalls = 0
        val result = ProfileGeneration.generate(phone, store.all(), save = {
            saveCalls++
            assertFalse(ProfileValidator.validateProfileResult(it).hasErrors)
            store.write(it.getString("id"), it)
            it.getString("id")
        }, seedFactory = { seedCalls++; seed }) as ProfileGeneration.Result.Saved
        val saved = store.read(result.id)!!
        assertEquals(1, seedCalls)
        assertEquals(1, saveCalls)
        assertEquals(seed, saved.getString("seed"))
        assertTrue(CloakForge.forgeProfile(result.name, seed, phone, "0.4").similar(saved))
        assertEquals(setOf("schema_version", "id", "name", "seed", "device", "identifiers", "telephony", "network", "display", "locale", "camera", "ua", "integrity", "health", "meta", "reality"), saved.keySet())
        assertTrue(before.similar(phone.toJson()))
        assertEquals(listOf("${result.id}.json"), folder.listFiles()!!.map { it.name })
    }

    @Test
    fun validationFailureDoesNotCallSaveAndPreservesExistingIdentityBytes() {
        val (folder, store) = store()
        val existing = CloakForge.forgeProfile("Existing", seed, phone)
        store.write(existing.getString("id"), existing)
        val bytes = folder.listFiles()!!.single().readBytes()
        val broken = phone.copy(device = JSONObject(phone.device.toString()).put("sdk_int", 34))
        var saved = false
        val result = ProfileGeneration.generate(broken, store.all(), save = { saved = true; "unused" },
            seedFactory = { "cd".repeat(32) }) as ProfileGeneration.Result.Rejected
        assertFalse(saved)
        val finding = result.findings.single { it.code == "RELEASE_SDK_MISMATCH" }
        assertEquals("device.sdk_int", finding.field)
        assertTrue(finding.isError)
        assertTrue(finding.message.startsWith("device.sdk_int"))
        assertEquals(1, store.all().size)
        assertArrayEquals(bytes, folder.listFiles()!!.single().readBytes())
    }

    @Test
    fun sameSeedNameAndTemplateProduceIdenticalValuesInIndependentStores() {
        val (_, first) = store()
        val (_, second) = store()
        fun generate(store: JsonDirectory) = ProfileGeneration.generate(phone, emptyList(), save = {
            store.write(it.getString("id"), it); it.getString("id")
        }, seedFactory = { seed }, name = "Stable profile") as ProfileGeneration.Result.Saved
        val a = generate(first)
        val b = generate(second)
        assertEquals(a.id, b.id)
        assertTrue(first.read(a.id)!!.similar(second.read(b.id)))
    }

    @Test
    fun differentSeedsProduceDistinctIdsAndEveryDerivedIdentifier() {
        val (_, store) = store()
        fun generate(seed: String) = ProfileGeneration.generate(phone, emptyList(), save = {
            store.write(it.getString("id"), it); it.getString("id")
        }, seedFactory = { seed }, name = "Same name") as ProfileGeneration.Result.Saved
        val first = generate(seed)
        val second = generate("cd".repeat(32))
        assertNotEquals(first.id, second.id)
        val a = store.read(first.id)!!
        val b = store.read(second.id)!!
        for (key in a.getJSONObject("identifiers").keySet()) {
            assertNotEquals(key, a.getJSONObject("identifiers").getString(key), b.getJSONObject("identifiers").getString(key))
        }
        assertTrue(a.getJSONObject("device").similar(b.getJSONObject("device")))
        assertEquals(2, store.all().size)
    }

    @Test
    fun allCatalogTemplatesGenerateValidSavedProfiles() {
        val (_, store) = store()
        for (phone in TestPhones.all) {
            val result = ProfileGeneration.generate(phone, store.all(), save = {
                store.write(it.getString("id"), it); it.getString("id")
            }, seedFactory = { java.security.MessageDigest.getInstance("SHA-256").digest(phone.id.toByteArray()).joinToString("") { "%02x".format(it) } }) as ProfileGeneration.Result.Saved
            assertTrue(result.findings.isEmpty())
            assertFalse(ProfileValidator.validateProfileResult(store.read(result.id)!!).hasErrors)
        }
        assertEquals(TestPhones.all.size, store.all().size)
    }

    @Test
    fun warningsAreSavedAndReturnedForDisplay() {
        val (_, store) = store()
        val reality = JSONObject(phone.reality.toString())
        reality.getJSONObject("panel").getJSONObject("values").put("refresh_hz", 300)
        val uncertain = phone.copy(display = JSONObject(phone.display.toString()).put("refresh_rate_hz", 300), reality = reality)
        val result = ProfileGeneration.generate(uncertain, emptyList(), save = {
            store.write(it.getString("id"), it); it.getString("id")
        }, seedFactory = { seed }) as ProfileGeneration.Result.Saved
        assertTrue(result.findings.any { it.code == "DISPLAY_SUSPICIOUS" && it.severity == "warning" })
        assertNotNull(store.read(result.id))
    }

    @Test
    fun duplicateProfileIdCannotOverwriteExistingRecord() {
        val (folder, store) = store()
        val existing = CloakForge.forgeProfile("Repeat", seed, phone)
        store.write(existing.getString("id"), existing)
        val bytes = folder.listFiles()!!.single().readBytes()
        val result = ProfileGeneration.generate(phone, store.all(), save = { fail("Must not save"); "" },
            seedFactory = { seed }, name = "Repeat") as ProfileGeneration.Result.Failed
        assertEquals("NAME_EXISTS", result.code)
        assertArrayEquals(bytes, folder.listFiles()!!.single().readBytes())
    }

    @Test
    fun automaticNameSuffixAvoidsExistingNamesWithoutChangingThem() {
        val (_, store) = store()
        val existing = CloakForge.forgeProfile("PIXEL 7 PROFILE", seed, phone)
        store.write(existing.getString("id"), existing)
        val result = ProfileGeneration.generate(phone, store.all(), save = {
            store.write(it.getString("id"), it); it.getString("id")
        }, seedFactory = { "cd".repeat(32) }) as ProfileGeneration.Result.Saved
        assertEquals("Pixel 7 profile 2", result.name)
        assertTrue(existing.similar(store.read(existing.getString("id"))))
    }

    @Test
    fun seedAndStorageFailuresHaveExplicitSafeMessages() {
        val seedFailure = ProfileGeneration.generate(phone, emptyList(), save = { fail("Must not save"); "" },
            seedFactory = { throw IOException("private details") }) as ProfileGeneration.Result.Failed
        assertEquals("GENERATION_FAILED", seedFailure.code)
        assertFalse(seedFailure.message.contains("private details"))
        val (folder, _) = store()
        val obstructed = File(folder, "not-a-directory").also { it.writeText("keep") }
        val failingStore = JsonDirectory(obstructed)
        val saveFailure = ProfileGeneration.generate(phone, emptyList(), save = {
            failingStore.write(it.getString("id"), it); it.getString("id")
        }, seedFactory = { seed }) as ProfileGeneration.Result.Failed
        assertEquals("SAVE_FAILED", saveFailure.code)
        assertEquals("keep", obstructed.readText())
    }

    @Test
    fun randomSeedsAreFreshAndPersistedRatherThanRegeneratedDuringValidation() {
        val (_, store) = store()
        fun generate() = ProfileGeneration.generate(phone, store.all(), save = {
            store.write(it.getString("id"), it); it.getString("id")
        }) as ProfileGeneration.Result.Saved
        val first = generate()
        val second = generate()
        val seedA = store.read(first.id)!!.getString("seed")
        val seedB = store.read(second.id)!!.getString("seed")
        assertTrue(Regex("[0-9a-f]{64}").matches(seedA))
        assertTrue(Regex("[0-9a-f]{64}").matches(seedB))
        assertNotEquals(seedA, seedB)
        assertTrue(CloakForge.forgeProfile(first.name, seedA, phone, "0.4").similar(store.read(first.id)))
    }
    @Test fun duplicateIdentifiersTriggerUniqueGenerationBeforeSaving() {
        val original = CloakForge.forgeProfile("Original", seed, phone)
        var saves = 0
        val result = ProfileGeneration.generate(phone, listOf(original.getString("id") to original),
            save = { saves++; "unused" }, seedFactory = { seed }, name = "Copy")
        assertTrue(result is ProfileGeneration.Result.Saved)
        assertEquals(1, saves)
        assertEquals(0, (result as ProfileGeneration.Result.Saved).findings.count { it.code == "DUPLICATE_IDENTIFIER" })
    }

    @Test fun libraryRebuildToleratesUnreadableFilesAndPreservesOldBytes() {
        val (folder, store) = store()
        val original = CloakForge.forgeProfile("Original", seed, phone)
        val copy = JSONObject(original.toString()).put("id", "copy")
        store.write(original.getString("id"), original)
        store.write("copy", copy)
        val file = File(folder, "copy.json")
        val before = file.readBytes()
        File(folder, "broken.json").writeText("not json")
        File(folder, ".index.json").writeText("corrupt obsolete cache")
        assertEquals(11, dev.cyclone.cloak.forge.ProfileUniqueness(store.all()).duplicates.size)
        File(folder, ".index.json").delete()
        assertEquals(11, dev.cyclone.cloak.forge.ProfileUniqueness(store.all()).duplicates.size)
        assertArrayEquals(before, file.readBytes())
        assertNotNull(store.read("copy"))
        store.delete("copy")
        assertTrue(dev.cyclone.cloak.forge.ProfileUniqueness(store.all()).duplicates.isEmpty())
    }

}
