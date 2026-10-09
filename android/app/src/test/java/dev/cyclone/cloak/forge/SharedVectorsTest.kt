package dev.cyclone.cloak.forge

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * `forge/tests/vectors` (made by forge/tools/make_vectors.py from the Python forge): the Kotlin validator and dump
 * importer must answer exactly what the Python forge answers. forge/tests/test_vectors.py runs the same files.
 */
class SharedVectorsTest {
    private fun resource(name: String) = JSONObject(javaClass.classLoader!!.getResource(name)!!.readText())

    private fun apply(profile: JSONObject, mutations: JSONArray): JSONObject {
        for (i in 0 until mutations.length()) {
            val mutation = mutations.getJSONArray(i)
            val path = mutation.getString(0).split(".")
            var target = profile
            for (part in path.dropLast(1)) target = target.getJSONObject(part)
            if (mutation.length() == 1) target.remove(path.last()) else target.put(path.last(), mutation.get(1))
        }
        return profile
    }

    @Test
    fun validationMatchesThePythonForge() {
        val vectors = resource("validation.json")
        val today = LocalDate.parse(vectors.getString("today"))
        val cases = vectors.getJSONArray("cases")
        for (i in 0 until cases.length()) {
            val case = cases.getJSONObject(i)
            val profile = apply(
                CloakForge.forgeProfile(vectors.getString("name"), vectors.getString("seed"), TestPhones[case.getString("phone")]),
                case.getJSONArray("mutations"),
            )
            val got = ProfileValidator.validateProfile(profile, today).map { listOf(it.severity, it.code) }
            val expect = case.getJSONArray("expect").let { a -> (0 until a.length()).map { a.getJSONArray(it).let { p -> listOf(p.getString(0), p.getString(1)) } } }
            assertEquals(case.getString("name"), expect, got)
        }
    }

    @Test
    fun dumpsDraftWhatThePythonForgeDrafts() {
        val cases = resource("dumps.json").getJSONArray("cases")
        for (i in 0 until cases.length()) {
            val case = cases.getJSONObject(i)
            val got = PropImport.draftBlocks(PropImport.parse(case.getString("text")))
            val expect = case.getJSONObject("expect")
            // By JSON value: 1709683200 parses as an Int, the importer stores a Long.
            assertTrue("${case.getString("name")}: expected $expect, got $got", expect.similar(got))
        }
    }

    @Test
    fun aDumpBecomesADraftPhoneNamedAfterItsModel() {
        val text = resource("dumps.json").getJSONArray("cases").getJSONObject(1).getString("text")
        val parsed = Imports.parse(text, emptySet()) as Imports.Parsed.Phone
        assertEquals(true, parsed.fromDump)
        assertEquals("SM-S911B · Android 14", parsed.phone.label)
        assertEquals("sm_s911b_android_14", parsed.phone.id)
        // A dump can't tell the screen size: the builder must ask for it.
        assertEquals(setOf("DISPLAY_FIELD", "DISPLAY_CLASS"), parsed.findings.filter { it.isError }.map { it.code }.toSet())
    }
}
