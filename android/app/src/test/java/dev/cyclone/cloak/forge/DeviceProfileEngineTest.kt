package dev.cyclone.cloak.forge

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class DeviceProfileEngineTest {
    private val engine: DeviceProfileEngine = DefaultDeviceProfileEngine()
    private val request = IdentityEngine.GenerationRequest("Unique", "ab".repeat(32), TestPhones["pixel_7"])

    @Test fun directAndUniqueKeepOriginalBodyForAnEmptyLibrary() {
        val direct = engine.generate(request)
        val unique = engine.generate_unique(request, emptyList())
        assertTrue(direct.getJSONObject("profile").similar(unique.getJSONObject("profile")))
        assertEquals(1, unique.getJSONObject("metadata").getInt("attempt_count"))
        assertEquals(request.seed, unique.getJSONObject("metadata").getString("chosen_seed"))
        assertFalse(unique.getJSONObject("profile").has("engine_version"))
    }

    @Test fun deterministicRetriesMatchSharedPythonVectorAndDoNotMutateLibrary() {
        val vector = JSONObject(javaClass.classLoader!!.getResource("engine-unique.json")!!.readText())
        val entries = vector.getJSONArray("library")
        val library = (0 until entries.length()).map(entries::getJSONObject)
        val before = entries.toString()
        val bundle = engine.generate_unique(request, library)
        assertTrue(bundle.similar(vector.getJSONObject("bundle")))
        assertTrue(bundle.similar(engine.generate_unique(request, library.reversed())))
        assertTrue(engine.check_uniqueness(bundle.getJSONObject("profile"), library).isEmpty())
        assertEquals(before, entries.toString())
    }

    @Test fun caseInsensitiveSingleFieldCollisionRetries() {
        val profile = engine.generate(request.copy(name = "other")).getJSONObject("profile")
        profile.put("identifiers", JSONObject().put("serial", profile.getJSONObject("identifiers").getString("serial").lowercase()))
        assertEquals(2, engine.generate_unique(request, listOf(profile)).getJSONObject("metadata").getInt("attempt_count"))
    }

    @Test fun exhaustedLibraryFailsWithoutMutation() {
        val library = (1..128).map { attempt -> engine.generate(request.copy(seed = if (attempt == 1) request.seed else DefaultDeviceProfileEngine.retrySeed(request.seed, attempt))).getJSONObject("profile") }
        val before = library.map(JSONObject::toString)
        assertTrue(runCatching { engine.generate_unique(request, library) }.isFailure)
        assertEquals(before, library.map(JSONObject::toString))
    }

    @Test fun bundleRoundTripPreservesEveryProfileValueIncludingLegacy() {
        for (version in listOf("0.2", "0.3")) {
            val raw = engine.generate_unique(request.copy(schema_version = version), emptyList())
            val bundle = EngineBundleCodec.decode(raw.toString())
            assertTrue(raw.getJSONObject("profile").similar(bundle.profile))
            assertTrue(raw.similar(JSONObject(EngineBundleCodec.encode(bundle))))
            assertTrue(bundle.profile.similar(EngineBundleCodec.decode(EngineBundleCodec.encode(bundle)).profile))
            assertTrue(Imports.parse(raw.toString(), emptySet()) is Imports.Parsed.Engine)
            assertTrue(Imports.parse(bundle.profile.toString(), emptySet()) is Imports.Parsed.Profile)
        }
    }

    @Test fun malformedBundlesRejectedAndClaimedFindingsNeverBypassValidation() {
        fun raw() = engine.generate_unique(request, emptyList())
        val malformed = listOf(
            raw().put("profile", JSONObject()), raw().put("extra", true), raw().put("metadata", "bad"),
            raw().also { it.getJSONObject("metadata").put("engine_version", "999") },
            raw().also { it.getJSONObject("metadata").put("schema_version", "0.2") },
            raw().also { it.getJSONObject("metadata").put("chosen_seed", "cd".repeat(32)) },
            raw().also { it.getJSONObject("metadata").put("attempt_count", 1.5) },
            raw().also { it.getJSONObject("metadata").put("source_confidence", "certified") },
            raw().also { it.getJSONObject("profile").getJSONObject("device").put("sdk_int", 1) },
            raw().put("findings", "trusted"), raw().put("posture", JSONObject().put("a", 1)),
        )
        malformed.forEach { assertTrue(runCatching { EngineBundleCodec.decode(it.toString()) }.isFailure) }
        listOf("[]", raw().toString() + "{}", "{'profile':{}}", "{\"profile\":{},\"profile\":{}}", "[".repeat(40), " ".repeat(2_000_001))
            .forEach { assertTrue(runCatching { EngineBundleCodec.decode(it) }.isFailure) }
    }
}
