package dev.cyclone.cloak.forge

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class RealityRuntimeContractTest {
    private fun profile() = JSONObject(javaClass.classLoader!!.getResource("reality.json")!!.readText()).getJSONObject("profile")
    @Test fun deterministicAndAllFieldsCovered() {
        val p = profile(); val before = p.toString(); val contract = RealityRuntimeContract.generate(p)
        RealityRuntimeContract.validate(contract)
        assertTrue(contract.similar(RealityRuntimeContract.generate(p))); assertEquals(before, p.toString())
        assertTrue(contract.similar(JSONObject(javaClass.classLoader!!.getResource("reality-runtime-contract.json")!!.readText())))
        assertEquals(97, contract.getJSONArray("fields").length())
        assertFalse(contract.toString().contains(p.getString("seed")))
        val source = JSONObject(javaClass.classLoader!!.getResource("reality-runtime-fields.json")!!.readText())
        val rows = contract.getJSONArray("fields")
        for (i in 0 until rows.length()) rows.getJSONObject(i).remove("present")
        assertTrue(rows.similar(source.getJSONArray("fields")))
    }
    @Test fun oldProfilesAndFutureSectionsRemainReadable() {
        for (version in listOf("0.2", "0.3", "0.4")) {
            val c = RealityRuntimeContract.generate(JSONObject().put("schema_version", version))
            RealityRuntimeContract.validate(c); assertEquals("not present", c.getString("presence"))
        }
        val p = profile(); val expected = RealityRuntimeContract.generate(p)
        p.getJSONObject("reality").put("future", JSONObject().put("private", "hidden"))
        assertTrue(expected.similar(RealityRuntimeContract.generate(p)))
    }
    @Test fun allUnreviewedStatusChangesRejected() {
        for (status in RealityRuntimeContract.Status.entries) {
            val c = RealityRuntimeContract.generate(profile()); val rows = c.getJSONArray("fields")
            val row = (0 until rows.length()).map { rows.getJSONObject(it) }.first { it.getString("status") != status.name }
            row.put("status", status.name)
            assertThrows(IllegalArgumentException::class.java) { RealityRuntimeContract.validate(c) }
        }
    }
    @Test fun malformedUnknownAndDuplicateRejected() {
        for (change in listOf("field_path", "surface", "reason", "present")) {
            val c = RealityRuntimeContract.generate(profile()); c.getJSONArray("fields").getJSONObject(0).put(change, "invalid")
            assertThrows(IllegalArgumentException::class.java) { RealityRuntimeContract.validate(c) }
        }
        val c = RealityRuntimeContract.generate(profile()); c.getJSONArray("fields").put(1, c.getJSONArray("fields").get(0))
        assertThrows(IllegalArgumentException::class.java) { RealityRuntimeContract.validate(c) }
        val p = profile(); p.getJSONObject("reality").getJSONObject("panel").getJSONObject("values").put("width", -1)
        assertThrows(IllegalArgumentException::class.java) { RealityRuntimeContract.generate(p) }
    }
    @Test fun readinessIsNotVerification() {
        val summary = RealityCoverage.summarize(profile()).runtime!!
        assertEquals(0, summary.counts[RealityRuntimeContract.Status.APPLIED]); assertEquals(97, summary.counts.values.sum())
        assertTrue(summary.missingSurfaces.isNotEmpty())
    }
    @Test fun reportCompatibilityAndUnprovenMatchRejected() {
        RealityRuntimeContract.validateObservation(JSONObject().put("format", 1).put("presence", "not present").put("rows", org.json.JSONArray()))
        val fields = RealityRuntimeContract.table(); val rows = org.json.JSONArray()
        for (i in 0 until fields.length()) {
            val field = fields.getJSONObject(i)
            rows.put(JSONObject().put("field_path", field.getString("field_path")).put("surface", field.get("surface"))
                .put("observation_status", "UNMAPPED").put("digest", JSONObject.NULL).put("duration_ms", 0).put("reason", "NOT_MEASURED"))
        }
        val report = JSONObject().put("format", 1).put("presence", "present").put("rows", rows)
        RealityRuntimeContract.validateObservation(report)
        rows.getJSONObject(0).put("observation_status", "PASS")
        assertThrows(IllegalArgumentException::class.java) { RealityRuntimeContract.validateObservation(report) }
        rows.getJSONObject(0).put("observation_status", "UNMAPPED").put("field_path", "reality.future")
        assertThrows(IllegalArgumentException::class.java) { RealityRuntimeContract.validateObservation(report) }
    }
}
