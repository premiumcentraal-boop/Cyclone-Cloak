package dev.cyclone.cloak.forge

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class BatchIdentityPlannerTest {
    private val planner = BatchIdentityPlanner()
    private val phones = TestPhones.all
    private fun request(count: Int = 3) = BatchIdentityPlanner.Request(listOf("pixel_7"), count, "ab".repeat(32), "Workshop")

    @Test fun deterministicPlansAndRegenerationPreserveEveryProfileValue() {
        val a = planner.plan(request(), phones, emptyList())
        val b = planner.plan(request(), phones.reversed(), emptyList())
        a.drafts.zip(b.drafts).forEach { (x, y) -> assertTrue(x.profile!!.similar(y.profile)); assertEquals(x.requestedSeed, y.requestedSeed) }
        val selected = setOf(a.drafts.first().key)
        val next = planner.regenerate(a, selected, phones, emptyList())
        val repeated = planner.regenerate(b, selected, phones, emptyList())
        assertTrue(next.drafts.first().profile!!.similar(repeated.drafts.first().profile))
        assertNotEquals(a.drafts.first().chosenSeed, next.drafts.first().chosenSeed)
        assertTrue(a.drafts.last().profile!!.similar(next.drafts.last().profile))
    }

    @Test fun libraryCollisionsRetryDeterministicallyAndBatchRemainsUnique() {
        val original = planner.plan(request(1), phones, emptyList()).drafts.single().profile!!
        val existing = JSONObject(original.toString()).put("name", "Saved")
        val before = existing.toString()
        val plan = planner.plan(request(), phones, listOf(existing))
        assertEquals(2, plan.drafts.first().attempts)
        assertEquals(DefaultDeviceProfileEngine.retrySeed(plan.drafts.first().requestedSeed, 2), plan.drafts.first().chosenSeed)
        assertTrue(plan.drafts.all { it.approved })
        val index = ProfileUniqueness((listOf(existing) + plan.drafts.map { it.profile!! }).mapIndexed { i, p -> "entry-$i" to p })
        assertTrue(index.duplicates.isEmpty())
        assertEquals(before, existing.toString())
    }

    @Test fun multipleTemplatesHaveDistinctSeedsAndCorrectDistribution() {
        val req = request(2).copy(templateIds = phones.take(3).map { it.id })
        val plan = planner.plan(req, phones, emptyList())
        assertEquals(6, plan.drafts.size)
        assertEquals(6, plan.drafts.map { it.chosenSeed }.distinct().size)
        assertEquals(3, plan.summary.getJSONObject("templates").length())
    }

    @Test fun invalidCountsMissingTemplatesAndMalformedLabelsAreRejected() {
        val bad = listOf(request(0), request(-1), request(21), request().copy(templateIds = emptyList()),
            request().copy(templateIds = listOf("missing")), request().copy(templateIds = listOf("pixel_7", "pixel_7")),
            request().copy(seed = "bad"), request().copy(labelPrefix = "secret\nvalue"),
            request().copy(labelPrefix = " bad"), request().copy(labelPrefix = "a".repeat(33)),
            request(20).copy(templateIds = phones.take(6).map { it.id }))
        bad.forEach { assertTrue(it.toString(), runCatching { planner.plan(it, phones, emptyList()) }.isFailure) }
    }

    @Test fun schemaFailureStaysVisibleAndCannotBeSaved() {
        val phone = TestPhones["pixel_7"]
        val broken = phone.copy(device = JSONObject(phone.device.toString()).put("sdk_int", 99))
        val plan = planner.plan(request(1), listOf(broken), emptyList())
        assertFalse(plan.drafts.single().approved)
        assertTrue(plan.drafts.single().findings.any { it.code == "RELEASE_SDK_MISMATCH" })
        assertEquals(1, plan.summary.getInt("validation_fail"))
        val results = BatchIdentitySave.save(plan, plan.drafts.map { it.key }.toSet(), { emptyList() }) { _, _ -> fail("Invalid profile saved") }
        assertEquals("SKIPPED", results.single().status)
    }

    @Test fun partialSavesPreserveEarlierSuccessAndReportEachItem() {
        val plan = planner.plan(request(), phones, emptyList())
        val saved = mutableListOf<JSONObject>(); var calls = 0
        val result = BatchIdentitySave.save(plan, plan.drafts.map { it.key }.toSet(), { saved }) { p, _ ->
            calls++; if (calls == 2) throw java.io.IOException("injected")
            saved += p
        }
        assertEquals(listOf("SAVED", "FAILED", "SAVED"), result.map { it.status })
        assertEquals(2, saved.size)
        assertTrue(result[1].reason.startsWith("SAVE_FAILED"))
    }

    @Test fun unapprovedAndChangedLibraryConflictsAreSkippedWithoutReplacement() {
        val plan = planner.plan(request(), phones, emptyList())
        val saved = plan.drafts.first().profile!!
        val bytes = saved.toString()
        val result = BatchIdentitySave.save(plan, setOf(plan.drafts.first().key), { listOf(saved) }) { _, _ -> fail("Must not replace") }
        assertEquals(listOf("SKIPPED", "SKIPPED", "SKIPPED"), result.map { it.status })
        assertTrue(result.first().reason.contains("PROFILE_EXISTS"))
        assertEquals(bytes, saved.toString())
    }

    @Test fun jsonRoundTripReproducesBodiesAndRetryMetadataWithoutIdentifiersInPlan() {
        val existing = planner.plan(request(1), phones, emptyList()).drafts.single().profile!!
        val a = planner.plan(request(), phones, listOf(JSONObject(existing.toString()).put("name", "Other")))
        val text = planner.encode(a)
        assertFalse(text.contains("android_id")); assertFalse(text.contains("identifiers"))
        val b = planner.decode(text, phones, emptyList())
        a.drafts.zip(b.drafts).forEach { (x, y) -> assertTrue(x.profile!!.similar(y.profile)); assertEquals(x.attempts, y.attempts) }
        assertTrue(JSONObject(text).getJSONObject("validation_summary").similar(b.summary))
    }

    @Test fun importedSummaryCannotApproveLibraryConflicts() {
        val plan = planner.plan(request(1), phones, emptyList())
        val root = JSONObject(planner.encode(plan)).put("validation_summary", JSONObject().put("validation_pass", 999))
        val imported = planner.decode(root.toString(), phones, listOf(plan.drafts.single().profile!!))
        assertFalse(imported.drafts.single().approved)
        assertEquals(1, imported.summary.getInt("validation_pass"))
        assertEquals(1, imported.summary.getInt("conflicting"))
    }

    @Test fun duplicateSeedsTamperedLabelsVersionsAndTemplateDriftRejected() {
        val plan = planner.plan(request(), phones, emptyList())
        val text = planner.encode(plan)
        val bad = listOf(
            JSONObject(text).put("planner_version", "999"),
            JSONObject(text).also { it.getJSONArray("drafts").put(1, it.getJSONArray("drafts").getJSONObject(0)) },
            JSONObject(text).also { it.getJSONArray("drafts").getJSONObject(0).put("seed", "ff".repeat(32)) },
            JSONObject(text).also { it.getJSONArray("drafts").getJSONObject(0).put("label", "bad\nlabel") },
            JSONObject(text).also { it.getJSONObject("request").put("per_template", 1.5) },
            JSONObject(text).also { it.getJSONArray("drafts").getJSONObject(0).put("template_sha256", "a".repeat(64)) })
        bad.forEach { assertTrue(runCatching { planner.decode(it.toString(), phones, emptyList()) }.isFailure) }
        assertTrue(runCatching { planner.decode("{\"planner_version\":1,\"planner_version\":1}", phones, emptyList()) }.isFailure)
    }

    @Test fun repeatedGenerationAlwaysRejectsDuplicateSeedsEvenWithBrokenEngine() {
        val good = DefaultDeviceProfileEngine()
        val fixed = good.generate_unique(IdentityEngine.GenerationRequest("Fixed", "ab".repeat(32), TestPhones["pixel_7"]), emptyList())
        val faulty = object : DeviceProfileEngine by good {
            override fun generate_unique(request: IdentityEngine.GenerationRequest, existingProfiles: List<JSONObject>) = JSONObject(fixed.toString())
        }
        assertTrue(runCatching { BatchIdentityPlanner(faulty).plan(request(), phones, emptyList()) }.isFailure)
    }
}
