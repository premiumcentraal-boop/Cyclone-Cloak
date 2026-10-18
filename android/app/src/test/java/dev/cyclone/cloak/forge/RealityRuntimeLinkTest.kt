package dev.cyclone.cloak.forge
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
class RealityRuntimeLinkTest {
    private fun profile() = JSONObject(javaClass.classLoader!!.getResource("reality.json")!!.readText()).getJSONObject("profile")
    @Test fun exactMappingAndNoMutation() {
        val p = profile(); val before = p.toString(); val links = RealityRuntimeLink.evaluate(p)
        assertEquals(10, links.size); assertTrue(links.all { it.parityPassed == true })
        assertEquals("display.density", links.first { it.fieldPath.endsWith("density_dpi") }.basePath)
        assertEquals(before, p.toString())
    }
    @Test fun everyMismatchIsReportedWithoutOverridingBase() {
        for (link in RealityRuntimeLink.evaluate(profile())) {
            val p = profile(); val parts = link.fieldPath.split('.'); val values = p.getJSONObject("reality").getJSONObject(parts[1]).getJSONObject("values")
            val old = values.get(parts.last()); values.put(parts.last(), if (old is Number) old.toInt() + 1 else "different")
            val base = DeviceReality.at(p, link.basePath)
            assertEquals(RealityRuntimeLink.State.MISMATCHED, RealityRuntimeLink.evaluate(p).first { it.fieldPath == link.fieldPath }.state)
            assertEquals(base, DeviceReality.at(p, link.basePath))
        }
        val p = profile(); p.getJSONObject("reality").getJSONObject("panel").getJSONObject("values").put("width", p.getJSONObject("display").getInt("width") + 1)
        val c = RealityRuntimeContract.generate(p); RealityRuntimeContract.validate(c)
        assertEquals(9, RealityRuntimeContract.readiness(p).counts[RealityRuntimeContract.Status.APPLIED])
    }
    @Test fun missingRealityAndBaseDoNotPromoteOldProfiles() {
        for (schema in listOf("0.2", "0.3", "0.4")) {
            assertTrue(RealityRuntimeLink.evaluate(JSONObject().put("schema_version", schema)).all { it.state == RealityRuntimeLink.State.NOT_PRESENT && it.parityPassed == null })
        }
        val p = profile(); p.getJSONObject("display").remove("width")
        assertEquals(RealityRuntimeLink.State.BASE_MISSING, RealityRuntimeLink.evaluate(p).first { it.basePath == "display.width" }.state)
        p.getJSONObject("reality").getJSONObject("panel").put("availability", "unavailable")
        assertTrue(RealityRuntimeLink.evaluate(p).filter { it.basePath.startsWith("display.") }.all { it.state == RealityRuntimeLink.State.NOT_PRESENT })
    }
    @Test fun originalVersionOneContractStillReadable() {
        val old = JSONObject(RealityRuntimeTable.legacyJson).getJSONArray("fields")
        for (i in 0 until old.length()) old.getJSONObject(i).put("present", true)
        RealityRuntimeContract.validate(JSONObject().put("format", 1).put("schema_version", "0.4").put("presence", "present").put("fields", old))
    }
}
