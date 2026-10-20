package dev.cyclone.cloak.forge

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.concurrent.TimeUnit

class CohortSeparationTest {
    private val policy = requireNotNull(javaClass.classLoader!!.getResource("cohort-separation-v1.json")).readText()
    private val analyzer = CohortSeparation(policy)
    private fun p(seed: String = "a", phone: String = "pixel_7", version: String = "0.4") =
        CloakForge.forgeProfile("Local draft", seed.repeat(64), TestPhones[phone], version)
    private fun twin(p: JSONObject) = JSONObject(p.toString()).put("id", "second").put("name", "Second draft")

    @Test fun identicalProfilesAndCorrelatedDescriptionsAreFlagged() {
        val a = p(); val b = twin(a); val before = a.toString()
        val result = analyzer.analyze(listOf(a, b))
        assertEquals(0, result.getInt("independence_score"))
        assertEquals(14, result.getJSONArray("identifier_findings").length())
        assertTrue(result.getJSONArray("shared_groups").toString().contains("device.fingerprint"))
        assertTrue(result.getJSONArray("warnings").toString().contains("HOMOGENEOUS_COHORT"))
        assertEquals(before, a.toString())
    }
    @Test fun nearDuplicateIsReportedWithoutItsValue() {
        val a = p(); val b = twin(a); val ids = b.getJSONObject("identifiers")
        val old = ids.getString("android_id")
        ids.put("android_id", (if (old.first() == '0') "1" else "0") + old.drop(1))
        val result = analyzer.analyze(listOf(a, b)).toString()
        assertTrue(result.contains("near_duplicate")); assertFalse(result.contains(old))
    }
    @Test fun distinctIdentifiersDoNotGuaranteeDistinctHardware() {
        val result = analyzer.analyze(listOf(p(), p("b")))
        assertEquals(0, result.getJSONArray("identifier_findings").length())
        val scores = result.getJSONArray("pairs").getJSONObject(0).getJSONObject("categories")
        assertEquals(100, scores.getJSONObject("stable_identifiers").getInt("distance"))
        assertEquals(0, scores.getJSONObject("hardware_description").getInt("distance"))
        assertTrue(result.getInt("independence_score") < 60)
    }
    @Test fun emptyAndSingleLibraryHaveNoScore() {
        for (library in listOf(emptyList(), listOf(p()))) {
            val result = analyzer.analyze(library)
            assertTrue(result.isNull("independence_score")); assertEquals(0, result.getInt("confidence"))
        }
    }
    @Test fun olderProfilesRemainReadableWithMissingFacts() {
        for (version in listOf("0.2", "0.3")) {
            val a = p(version = version)
            assertEquals(24, analyzer.analyze(listOf(a, twin(a))).getJSONArray("missing_reality").length())
        }
    }
    @Test fun malformedAndDuplicateRecordsFailWithSafeCode() {
        for (library in listOf(listOf(JSONObject()), listOf(p(), p()), List(201) { p() })) {
            val error = assertThrows(IllegalArgumentException::class.java) { analyzer.analyze(library) }
            assertEquals("COHORT_INPUT_INVALID", error.message)
        }
    }
    @Test fun orderingIsStableAndNoRawIdentifiersOrSeedAreReturned() {
        val a = p(); val b = p("b", "pixel_8")
        val result = analyzer.analyze(listOf(a, b))
        assertTrue(result.similar(analyzer.analyze(listOf(b, a))))
        val text = result.toString()
        for (profile in listOf(a, b)) {
            val ids = profile.getJSONObject("identifiers")
            ids.keys().forEach { assertFalse(text.contains(ids.getString(it))) }
            assertFalse(text.contains(profile.getString("seed")))
            assertFalse(text.contains(profile.getJSONObject("device").getString("fingerprint")))
        }
    }
    @Test fun completeReportsMatchPythonIncludingUnknownUnavailableAndMalformedCases() {
        val a = p(); val near = twin(a)
        val old = near.getJSONObject("identifiers").getString("android_id")
        near.getJSONObject("identifiers").put("android_id", (if (old.first() == '0') "1" else "0") + old.drop(1))
        val unavailable = p("c")
        unavailable.getJSONObject("reality").put("compute", JSONObject()
            .put("availability", "unavailable").put("source", "needs verification").put("reason", "Unavailable.").put("values", JSONObject()))
        val play = p("e").put("play_surface", JSONObject(requireNotNull(javaClass.classLoader!!.getResource("play-surface.json")).readText()).getJSONObject("surface"))
        val otherPlay = twin(play)
        otherPlay.getJSONObject("play_surface").getJSONObject("account").put("slot_id", "different-local-slot")
        val libraries = listOf(emptyList(), listOf(a), listOf(a, twin(a)), listOf(a, near),
            listOf(a, p("b", "pixel_8")), listOf(p(version = "0.2"), p("d", version = "0.3")),
            listOf(a, unavailable), listOf(play, otherPlay), listOf(JSONObject()), listOf(a, a),
            listOf(p().put("name", "Local \uD83C\uDF10 draft")),
            listOf(p().put("name", "secret\nlabel")), listOf(p().put("schema_version", "secret-version")),
            listOf(p().put("device", JSONObject.NULL)))
        val root = generateSequence(File(requireNotNull(System.getProperty("user.dir")))) { it.parentFile }
            .first { File(it, "forge/cloak_forge/cohort_separation.py").isFile }
        val script = """
            import json,sys
            sys.path.insert(0,'forge')
            from cloak_forge.cohort_separation import analyze
            result=[]
            for library in json.load(sys.stdin):
                try: result.append(analyze(library))
                except ValueError: result.append({'error':'COHORT_INPUT_INVALID'})
            print(json.dumps(result))
        """.trimIndent()
        val process = ProcessBuilder("uv", "run", "--offline", "--", "python", "-X", "utf8", "-c", script).directory(root).start()
        val output = java.util.concurrent.CompletableFuture.supplyAsync { process.inputStream.bufferedReader().readText() }
        process.outputStream.bufferedWriter().use { it.write(JSONArray(libraries.map { JSONArray(it) }).toString()) }
        if (!process.waitFor(30, TimeUnit.SECONDS)) { process.destroyForcibly(); error("Cohort parity timed out") }
        assertEquals("Cohort parity failed", 0, process.exitValue())
        val expected = JSONArray(output.get(5, TimeUnit.SECONDS))
        libraries.forEachIndexed { index, library ->
            val actual = try { analyzer.analyze(library) } catch (_: IllegalArgumentException) {
                JSONObject().put("error", "COHORT_INPUT_INVALID")
            }
            assertTrue("Cohort report parity case $index", actual.similar(expected.getJSONObject(index)))
        }
    }
}
