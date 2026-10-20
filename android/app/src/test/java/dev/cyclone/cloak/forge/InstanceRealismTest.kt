package dev.cyclone.cloak.forge

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.time.Instant
import java.util.concurrent.TimeUnit

class InstanceRealismTest {
    private fun request(seed: String = "a".repeat(64), tuple: String? = null) =
        IdentityEngine.GenerationRequest("Instance", seed, TestPhones["pixel_7"], "0.4", true, tuple)
    private fun generate() = IdentityEngine.generate(request()).getJSONObject("profile")

    @Test fun sameSeedAndTupleAreDeterministicAndOnlyLegalSkuPreferencesVary() {
        val engine = DefaultDeviceProfileEngine()
        val rows = (0 until 20).map { i -> engine.generate_unique(request("%064x".format(i)), emptyList()).getJSONObject("profile") }
        assertTrue(IdentityEngine.generate(request()).similar(IdentityEngine.generate(request())))
        assertEquals(setOf(128, 256), rows.map { DeviceReality.at(it, "reality.memory.values.storage_gib") }.toSet())
        assertEquals(setOf(8), rows.map { DeviceReality.at(it, "reality.memory.values.ram_gib") }.toSet())
        assertTrue(rows.map { it.getJSONObject("meta").getJSONObject("instance_realism").getString("tuple_id") }.distinct().size >= 4)
        for (p in rows) assertTrue(ProfileValidator.errors(p).isEmpty())
        val a = IdentityEngine.generate(request(tuple = "sku-8-128-new_york")).getJSONObject("profile")
        val b = IdentityEngine.generate(request(tuple = "sku-8-256-new_york")).getJSONObject("profile")
        val ids = a.getJSONObject("identifiers")
        ids.keys().forEach { assertNotEquals(ids.getString(it), b.getJSONObject("identifiers").getString(it)) }
    }
    @Test fun allSharedPhoneRecipeOptionsValidate() {
        val data = JSONObject(InstanceRealismRules.json).getJSONObject("sources")
        for (id in data.keys().asSequence().filter { it.startsWith("phones.") }) {
            val source = data.getJSONObject(id)
            val options = source.getJSONArray("tuples")
            for (i in 0 until options.length()) {
                val r = request().copy(template = TestPhones[source.getString("template_id")], realism_tuple = options.getJSONObject(i).getString("id"))
                assertTrue(ProfileValidator.errors(IdentityEngine.generate(r).getJSONObject("profile")).isEmpty())
            }
        }
    }
    @Test fun impossibleHardwareOrUnapprovedFirmwareCannotKeepValidProvenance() {
        val a = generate()
        val changed = JSONObject(a.toString()).also { it.getJSONObject("display").put("width", 1440) }
        assertTrue(ProfileValidator.errors(changed).isNotEmpty())
        val invalidMemory = JSONObject(a.toString()).also { it.getJSONObject("reality").getJSONObject("memory").getJSONObject("values").put("ram_gib", 16) }
        assertTrue(ProfileValidator.errors(invalidMemory).isNotEmpty())
        val invalidFirmware = JSONObject(a.toString()).also { it.getJSONObject("device").put("bootloader", "invented-000") }
        assertTrue(ProfileValidator.errors(invalidFirmware).isNotEmpty())
        val missing = JSONObject(a.toString()).also { it.getJSONObject("meta").getJSONObject("instance_realism").remove("provenance") }
        assertTrue(ProfileValidator.errors(missing).isNotEmpty())
        val wrongIdentifier = JSONObject(a.toString()).also { it.getJSONObject("identifiers").put("android_id", "0123456789abcdef") }
        assertTrue(ProfileValidator.errors(wrongIdentifier).isNotEmpty())
        val wrongRadio = JSONObject(a.toString()).also { it.getJSONObject("reality").getJSONObject("radio").getJSONObject("values").put("wifi_name", "Cloak-not-derived") }
        assertTrue(ProfileValidator.errors(wrongRadio).isNotEmpty())
    }
    @Test fun uniqueGenerationRetriesCrossSlotCollisionsAndReplaysMetadata() {
        val engine = DefaultDeviceProfileEngine()
        val a = engine.generate_unique(request(), emptyList()).getJSONObject("profile")
        val saved = JSONObject(a.toString()).put("id", "other").put("identifiers",
            JSONObject().put("serial", a.getJSONObject("identifiers").getString("android_id")))
        val before = saved.toString()
        val b = engine.generate_unique(request(), listOf(saved))
        val metadata = b.getJSONObject("metadata")
        assertEquals(2, metadata.getInt("attempt_count"))
        assertEquals(before, saved.toString())
        assertEquals(b.getJSONObject("profile").getString("seed"), metadata.getString("chosen_seed"))
        val replay = IdentityEngine.generate(request(metadata.getString("realism_request_seed"), metadata.getJSONObject("instance_realism").getString("tuple_id")))
        assertTrue(replay.getJSONObject("profile").similar(b.getJSONObject("profile")))
        assertTrue(EngineBundleCodec.decode(b.toString()).profile.similar(b.getJSONObject("profile")))
    }
    @Test fun oldProfilesAndLegacyGenerationStayUnchanged() {
        for (version in listOf("0.2", "0.3", "0.4")) {
            val r = request().copy(schema_version = version, instance_realism = false)
            val expected = CloakForge.forgeProfile(r.name, r.seed, r.template, version)
            assertTrue(expected.similar(IdentityEngine.generate(r).getJSONObject("profile")))
            assertTrue(ProfileValidator.errors(expected).isEmpty())
            if (version != "0.4") assertThrows(IllegalArgumentException::class.java) { IdentityEngine.generate(r.copy(instance_realism = true)) }
        }
        assertThrows(IllegalArgumentException::class.java) { IdentityEngine.generate(request(tuple = "unsupported")) }
        assertThrows(IllegalArgumentException::class.java) { IdentityEngine.generate(request().copy(instance_realism = false, realism_tuple = "unsupported")) }
    }
    @Test fun unknownFactsStayUnknownAndProvenanceContainsNoRawIdentifiers() {
        val p = generate()
        val baseline = CloakForge.forgeProfile(p.getString("name"), p.getString("seed"), TestPhones["pixel_7"], "0.4")
        for (section in listOf("compute", "panel", "cameras", "sensors", "power", "drm", "packages"))
            assertTrue(p.getJSONObject("reality").getJSONObject(section).similar(baseline.getJSONObject("reality").getJSONObject(section)))
        val before = JSONObject(baseline.getJSONObject("reality").getJSONObject("webview").toString())
        val after = JSONObject(p.getJSONObject("reality").getJSONObject("webview").toString())
        before.getJSONObject("values").remove("user_agent"); after.getJSONObject("values").remove("user_agent")
        assertTrue(before.similar(after))
        val text = p.getJSONObject("meta").getJSONObject("instance_realism").toString()
        assertFalse(text.contains(p.getString("seed")))
        val ids = p.getJSONObject("identifiers"); ids.keys().forEach { assertFalse(text.contains(ids.getString(it))) }
    }
    @Test fun fullIdentityBackupPreservesProvenanceAndRedactedBackupHasNoFalseDerivationClaim() {
        val p = generate()
        val full = ProfileBackup.export(listOf(p), "test", includeIdentity = true, now = Instant.parse("2026-10-20T12:00:00Z"))
        val review = ProfileBackup.parse(full.toString(), emptyList())
        assertTrue(review.findings.joinToString { it.code }, review.canSave)
        assertTrue(p.getJSONObject("meta").similar(review.profiles.single().getJSONObject("meta")))
        val redacted = ProfileBackup.export(listOf(p), "test", now = Instant.parse("2026-10-20T12:00:00Z"))
        assertFalse(redacted.getJSONArray("profiles").getJSONObject(0).has("meta"))
    }
    @Test fun completeProfilesProvenanceAndPlansMatchPython() {
        val requests = listOf(request(), request("b".repeat(64), "sku-8-256-los_angeles"), request().copy(template = TestPhones["pixel_8"]))
        val root = generateSequence(File(requireNotNull(System.getProperty("user.dir")))) { it.parentFile }
            .first { File(it, "tools/generate_instance_realism.py").isFile }
        val script = """
            import json,sys
            sys.path.insert(0,'forge')
            from cloak_forge.engine import generate_unique
            print(json.dumps([generate_unique(r,[]) for r in json.load(sys.stdin)]))
        """.trimIndent()
        val process = ProcessBuilder("uv", "run", "--offline", "--", "python", "-X", "utf8", "-c", script).directory(root).start()
        val output = java.util.concurrent.CompletableFuture.supplyAsync { process.inputStream.bufferedReader().readText() }
        process.outputStream.bufferedWriter().use { writer -> writer.write(JSONArray(requests.map { r ->
            JSONObject().put("name", r.name).put("seed", r.seed).put("template", r.template.id).put("schema_version", "0.4")
                .put("instance_realism", true).also { if (r.realism_tuple != null) it.put("realism_tuple", r.realism_tuple) }
        }).toString()) }
        if (!process.waitFor(30, TimeUnit.SECONDS)) { process.destroyForcibly(); error("Instance parity timed out") }
        assertEquals("Instance parity failed", 0, process.exitValue())
        val expected = JSONArray(output.get(5, TimeUnit.SECONDS))
        requests.forEachIndexed { i, r ->
            val actual = DefaultDeviceProfileEngine().generate_unique(r, emptyList())
            assertTrue("Instance profile parity $i", actual.getJSONObject("profile").similar(expected.getJSONObject(i).getJSONObject("profile")))
            assertTrue(actual.getJSONObject("metadata").getJSONObject("instance_realism").similar(expected.getJSONObject(i).getJSONObject("metadata").getJSONObject("instance_realism")))
            assertEquals(expected.getJSONObject(i).getJSONObject("metadata").getString("chosen_seed"), actual.getJSONObject("metadata").getString("chosen_seed"))
        }
    }
}
