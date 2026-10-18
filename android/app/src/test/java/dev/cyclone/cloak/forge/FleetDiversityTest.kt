package dev.cyclone.cloak.forge

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class FleetDiversityTest {
    private fun p(id: String, seed: String) = CloakForge.forgeProfile(id, seed.repeat(64), TestPhones[id], "0.4")
    @Test fun repeatedCorrelatedFactsClusterWithoutLeakingValues() {
        val a = p("pixel_7", "a"); val b = p("pixel_7", "b").put("name", "Second")
        val result = FleetDiversity.analyze(listOf(a, b))
        assertEquals(0, result.score)
        assertTrue(result.clusters.any { it.paths.size > 1 && it.labels.size == 2 })
        assertTrue(result.recommendations.any { it.contains("fixed hardware") })
        val output = result.toString()
        assertFalse(output.contains(a.getString("seed")))
        assertFalse(output.contains(a.getJSONObject("device").getString("fingerprint")))
    }
    @Test fun distinctDevicesImproveScoreAndMissingFactsEarnNoCredit() {
        val a = p("pixel_7", "a"); val b = p("galaxy_s23", "b")
        val distinct = FleetDiversity.analyze(listOf(a, b))
        assertTrue(distinct.score!! > 0)
        assertNull(FleetDiversity.analyze(listOf(a)).score)
        assertNull(FleetDiversity.analyze(listOf(JSONObject(), JSONObject())).score)
        assertEquals(12, FleetDiversity.analyze(listOf(JSONObject(), JSONObject())).missingFacts)
    }
    @Test fun balancedPlanExhaustsTemplatesBeforeRepeatingAndIsDeterministic() {
        val planner = BatchIdentityPlanner()
        val r = BatchIdentityPlanner.Request(listOf("pixel_7", "galaxy_s23", "pixel_7_pro"), 2, "ab".repeat(32))
        val a = planner.plan(r, TestPhones.all, emptyList())
        val b = planner.plan(r, TestPhones.all.reversed(), emptyList())
        assertEquals(3, a.drafts.take(3).map { it.templateId }.distinct().size)
        assertEquals(a.drafts.map { it.chosenSeed }, b.drafts.map { it.chosenSeed })
        assertEquals(3, a.summary.getInt("repeated_reality_clusters"))
        assertTrue(a.drafts.all { it.approved })
        val restored = planner.decode(planner.encode(a), TestPhones.all, emptyList())
        assertEquals("balanced", restored.request.diversityPolicy)
        a.drafts.zip(restored.drafts).forEach { (x, y) -> assertTrue(x.profile!!.similar(y.profile)) }
    }
    @Test fun originalV1PlansWithoutPolicyKeepLegacyOrderAndSeeds() {
        val planner = BatchIdentityPlanner()
        val r = BatchIdentityPlanner.Request(listOf("pixel_7", "galaxy_s23"), 2, "ab".repeat(32), diversityPolicy = "legacy")
        val a = planner.plan(r, TestPhones.all, emptyList())
        val json = planner.encode(a)
        assertFalse(JSONObject(json).getJSONObject("request").has("diversity_policy"))
        val b = planner.decode(json, TestPhones.all, emptyList())
        assertEquals("legacy", b.request.diversityPolicy)
        assertEquals(a.drafts.map { it.chosenSeed }, b.drafts.map { it.chosenSeed })
        assertEquals(a.drafts[0].templateId, a.drafts[1].templateId)
        val invalid = JSONObject(json).also { it.getJSONObject("request").put("diversity_policy", "fake") }
        assertThrows(IllegalArgumentException::class.java) { planner.decode(invalid.toString(), TestPhones.all, emptyList()) }
    }
    @Test fun repeatedTemplateAliasesDoNotCrowdOutDistinctStorageClusters() {
        val pixel = TestPhones["pixel_7"]
        val alias = pixel.copy(id = "aaa_alias", label = "Alias")
        val reality = JSONObject(pixel.reality.toString())
        reality.getJSONObject("memory").getJSONObject("values").put("storage_gib", 256)
        val larger = pixel.copy(id = "larger_storage", label = "Larger storage", reality = reality)
        val planner = BatchIdentityPlanner()
        val phones = listOf(alias, pixel, larger, TestPhones["galaxy_s23"])
        val plan = planner.plan(BatchIdentityPlanner.Request(phones.map { it.id }, 1, "ab".repeat(32)), phones, emptyList())
        assertEquals(3, plan.drafts.take(3).map { FleetDiversity.clusterKey(it.profile!!) }.distinct().size)
        assertEquals(1, plan.summary.getInt("repeated_reality_clusters"))
    }
}
