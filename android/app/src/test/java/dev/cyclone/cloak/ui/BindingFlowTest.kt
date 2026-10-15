package dev.cyclone.cloak.ui

import dev.cyclone.cloak.cyclone.*
import dev.cyclone.cloak.data.CloakBinding
import dev.cyclone.cloak.forge.CloakForge
import dev.cyclone.cloak.forge.TestPhones
import dev.cyclone.cloak.root.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class BindingFlowTest {
    private val profile = CycloneProfile("Cyclone_bbbbbbbbbbbbbbbb", "Work", "profile", "ready", 11, listOf("com.example.app"))
    private fun identity() = CloakForge.forgeProfile("Pixel profile", "ab".repeat(32), TestPhones["pixel_7"])
    private fun input(): BindingInputs {
        val identity = identity()
        return BindingInputs(listOf(profile), listOf(identity.getString("id") to identity), emptyList(), true, null)
    }
    private fun review(input: BindingInputs): BindingDraft {
        val draft = BindingDraft(BindingStep.REVIEW, profile.id, setOf("com.example.app"), input.identities.first().first, acknowledged = true)
        return draft.copy(reviewStamp = BindingFlow.stamp(draft, input))
    }
    private fun held(draft: BindingDraft, state: String = "ready") = CloakBinding(profile.id, 11, draft.packageNames.single(), draft.identityId!!, 2, true, 100, state)
    private val gate = CycloneGate(true, 3, setOf("profiles.read", "profiles.apps.read", "profiles.config", "profiles.startup"), emptySet())
    private fun report(draft: BindingDraft, issues: Map<String, String> = emptyMap(), stopped: String? = null) =
        SyncReport("Connected", "", gate = gate, issues = issues,
            reconcile = BindingReconciler.Result(setOf(tupleKey(profile.id, 11, draft.packageNames.single())), issues, 1, 1, 0, stopped))

    @Test fun stepsRequireSelectionsAndReviewDoesNotSaveOrSkipConfirmation() {
        val input = input()
        var flow = BindingDraft()
        assertEquals(flow, BindingFlow.next(flow, input))
        flow = flow.copy(profileId = profile.id)
        flow = BindingFlow.next(flow, input)
        assertEquals(BindingStep.APP, flow.step)
        assertEquals(flow, BindingFlow.next(flow, input))
        flow = BindingFlow.next(flow.copy(packageNames = setOf("com.example.app")), input)
        assertEquals(BindingStep.IDENTITY, flow.step)
        assertEquals(flow, BindingFlow.next(flow, input))
        flow = BindingFlow.next(flow.copy(identityId = input.identities.first().first), input)
        assertEquals(BindingStep.REVIEW, flow.step)
        assertNotNull(flow.reviewStamp)
        assertEquals(flow, BindingFlow.next(flow, input))
        assertTrue(input.bindings.isEmpty())
        assertTrue(BindingFlow.canConfirm(flow.copy(acknowledged = true), input))
    }
    @Test fun validReviewCreatesOnlySelectedAppAndPreservesStableKey() {
        val input = input(); val draft = review(input)
        val record = BindingFlow.records(draft, input, 500).single()
        assertEquals(profile.id, record.profileId)
        assertEquals("com.example.app", record.packageName)
        assertEquals(11, record.androidUserId)
        assertEquals(draft.identityId, record.cloakProfileId)
        assertEquals("pending", record.state)
        assertEquals(500L, record.updatedAt)
        assertTrue(input.bindings.isEmpty())
    }
    @Test fun blockedTargetsHaveActionableReasonsAndCannotProduceRecord() {
        val original = input()
        for ((changed, code) in listOf(
            original.copy(profiles = listOf(profile.copy(state = "in_trash"))) to "PROFILE_NOT_READY",
            original.copy(profiles = listOf(profile.copy(androidUserId = null))) to "ANDROID_USER_MISSING",
            original.copy(profiles = listOf(profile.copy(packages = emptyList()))) to "APP_MISSING",
            original.copy(profiles = listOf(profile.copy(packages = null))) to "APP_MISSING",
            original.copy(profiles = emptyList()) to "PROFILE_MISSING",
            original.copy(canBind = false) to "CONNECTOR_NOT_READY",
            original.copy(ownProfileId = "Cyclone_cccccccccccccccc") to "PROFILE_OUT_OF_SCOPE",
        )) {
            val draft = review(changed)
            assertTrue(code, BindingFlow.findings(draft, changed).any { it.code == code && it.isError && it.message.isNotBlank() })
            assertFalse(BindingFlow.canConfirm(draft, changed))
            assertThrows(IllegalArgumentException::class.java) { BindingFlow.records(draft, changed, 100) }
        }
    }
    @Test fun sharedIdentityWarnsAndRequiresAcknowledgement() {
        val original = input(); val draft = review(original)
        val input = original.copy(bindings = listOf(held(draft).copy(packageName = "com.example.other", enabled = false)))
        val pending = review(input).copy(acknowledged = false)
        assertTrue(BindingFlow.findings(pending, input).any { it.code == "IDENTITY_ALREADY_BOUND" && !it.isError })
        assertFalse(BindingFlow.canConfirm(pending, input))
        assertTrue(BindingFlow.canConfirm(pending.copy(acknowledged = true), input))
    }
    @Test fun duplicateDistinguishingValuesWarnWithoutChangingStoredProfiles() {
        val original = input()
        val duplicate = JSONObject(original.identities.first().second.toString()).put("id", "another-profile").put("name", "Other")
        val input = original.copy(identities = original.identities + ("another-profile" to duplicate))
        val before = input.identities.map { it.second.toString() }
        val draft = review(input).copy(acknowledged = false)
        assertTrue(BindingFlow.findings(draft, input).any { it.code == "DUPLICATE_IDENTIFIER" && it.field == "identity.identifiers.android_id" })
        assertFalse(BindingFlow.canConfirm(draft, input))
        assertTrue(BindingFlow.canConfirm(draft.copy(acknowledged = true), input))
        assertEquals(before, input.identities.map { it.second.toString() })
    }
    @Test fun duplicateTargetsAndMainMirrorsBlockRatherThanOverwrite() {
        val original = input(); val draft = review(original)
        for (records in listOf(listOf(held(draft), held(draft).copy(cloakProfileId = "conflicting")),
            listOf(held(draft).copy(origin = CloakBinding.ORIGIN_MAIN)))) {
            val input = original.copy(bindings = records)
            assertFalse(BindingFlow.canConfirm(review(input), input))
            assertEquals(records, input.bindings)
        }
    }
    @Test fun replacingOneTargetWarnsAndKeepsExistingMetadataOnExplicitConfirmation() {
        val original = input(); val draft = review(original)
        val existing = held(draft).copy(cloakProfileId = "old", revision = 9, enabled = false)
        val input = original.copy(bindings = listOf(existing))
        assertTrue(BindingFlow.findings(draft, input).any { it.code == "REPLACE_BINDING" })
        val record = BindingFlow.records(review(input), input, 700).single()
        assertEquals(9, record.revision)
        assertEquals(existing.profileId, record.profileId)
        assertEquals(existing.packageName, record.packageName)
        assertEquals(draft.identityId, record.cloakProfileId)
        assertEquals(existing, input.bindings.single())
    }
    @Test fun changedAppListUserOrIdentityInvalidatesReviewAcknowledgement() {
        val original = input(); val draft = review(original)
        val changedIdentity = JSONObject(original.identities.first().second.toString()).put("name", "Renamed")
        for (input in listOf(original.copy(profiles = listOf(profile.copy(androidUserId = 21))),
            original.copy(profiles = listOf(profile.copy(packages = listOf("com.example.app", "com.example.other")))),
            original.copy(identities = listOf(draft.identityId!! to changedIdentity)))) {
            assertFalse(BindingFlow.canConfirm(draft, input))
            assertTrue(BindingFlow.canConfirm(review(input), input))
        }
    }
    @Test fun invalidOrUnreadableIdentityBlocks() {
        val original = input(); val draft = review(original)
        assertFalse(BindingFlow.canConfirm(draft, original.copy(identities = emptyList())))
        val invalid = JSONObject(original.identities.first().second.toString())
        invalid.getJSONObject("display").put("width", -1)
        val input = original.copy(identities = listOf(draft.identityId!! to invalid))
        assertTrue(BindingFlow.findings(draft, input).any { it.isError && it.field.orEmpty().startsWith("identity.") })
        assertFalse(BindingFlow.canConfirm(review(input), input))
    }
    @Test fun successfulAndRetryCompletionsRequirePublishedAndReconciledReadiness() {
        val draft = review(input()); val records = listOf(held(draft))
        val failed = BindingFlow.completion(draft, RootDoctorResult(RootDoctorCode.READY),
            report(draft, mapOf(bindingKey(profile.id, draft.packageNames.single()) to "Cyclone couldn't save it (RATE_LIMITED); it will be retried")), records)
        assertTrue(failed.saved); assertFalse(failed.ready); assertTrue(failed.message.contains("RATE_LIMITED"))
        val retry = BindingFlow.completion(draft, RootDoctorResult(RootDoctorCode.READY), report(draft), records)
        assertTrue(retry.ready); assertTrue(retry.saved)
        assertEquals(records, listOf(held(draft)))
        assertFalse(BindingFlow.completion(draft, RootDoctorResult(RootDoctorCode.PUBLISH_FAILED), report(draft), records).ready)
        assertFalse(BindingFlow.completion(draft, RootDoctorResult(RootDoctorCode.READY), report(draft, stopped = "NOT_A_CONNECTOR"), records).ready)
        assertFalse(BindingFlow.completion(draft, RootDoctorResult(RootDoctorCode.READY), null, records).ready)
        assertFalse(BindingFlow.completion(draft, RootDoctorResult(RootDoctorCode.READY),
            report(draft).copy(failureCode = "CYCLONE_NOT_ANSWERING"), records).ready)
        assertFalse(BindingFlow.completion(draft, RootDoctorResult(RootDoctorCode.READY), report(draft), listOf(held(draft, "pending"))).ready)
    }
    @Test fun changedOrRemovedSavedTargetCannotClaimSuccess() {
        val draft = review(input())
        for (records in listOf(emptyList(), listOf(held(draft).copy(cloakProfileId = "changed")),
            listOf(held(draft).copy(enabled = false)), listOf(held(draft), held(draft)))) {
            val outcome = BindingFlow.completion(draft, RootDoctorResult(RootDoctorCode.READY), report(draft), records)
            assertFalse(outcome.saved); assertFalse(outcome.ready)
        }
    }
    @Test fun runningStateDisablesNavigationAndConfirmation() {
        val input = input(); val draft = review(input).copy(running = true)
        assertFalse(BindingFlow.canConfirm(draft, input))
        assertFalse(BindingFlow.canAdvance(draft, input))
    }
    private fun multiple(): BindingInputs = input().copy(profiles = listOf(profile.copy(
        packages = listOf("com.example.app", "com.example.other", "com.example.third"))))
    private fun reviewed(draft: BindingDraft, input: BindingInputs) = draft.copy(
        step = BindingStep.REVIEW, acknowledged = true, reviewStamp = BindingFlow.stamp(draft, input))

    @Test fun multipleSelectionAndAllAppsToggleRequireAtLeastOneApp() {
        val input = multiple()
        val empty = BindingDraft(step = BindingStep.APP, profileId = profile.id)
        assertFalse(BindingFlow.canAdvance(empty, input))
        val all = BindingFlow.selectAll(empty, input, true)
        assertEquals(input.profiles.single().packages!!.toSet(), all.packageNames)
        assertTrue(BindingFlow.allApps(all, input)); assertTrue(BindingFlow.canAdvance(all, input))
        val subset = BindingFlow.selectApps(all, all.packageNames - "com.example.other")
        assertFalse(BindingFlow.allApps(subset, input))
        assertEquals(BindingStep.IDENTITY, BindingFlow.next(subset, input).step)
        val cleared = BindingFlow.selectAll(subset, input, false)
        assertTrue(cleared.packageNames.isEmpty()); assertFalse(BindingFlow.canAdvance(cleared, input))
        val changed = BindingFlow.chooseProfile(all, "Cyclone_cccccccccccccccc")
        assertTrue(changed.packageNames.isEmpty())
    }
    @Test fun bothEntryPointsReachIdentityAndRequireReadableSelectionBeforeReview() {
        val input = multiple(); val id = input.identities.single().first
        for (preselected in listOf(null, id)) {
            var flow = BindingFlow.chooseProfile(BindingDraft(identityId = preselected), profile.id)
            flow = BindingFlow.next(flow, input)
            flow = BindingFlow.next(BindingFlow.selectAll(flow, input, true), input)
            assertEquals(BindingStep.IDENTITY, flow.step)
            assertEquals(preselected != null, BindingFlow.canAdvance(flow, input))
            flow = BindingFlow.next(flow.copy(identityId = id), input)
            assertEquals(BindingStep.REVIEW, flow.step)
            assertTrue(BindingFlow.canConfirm(flow.copy(acknowledged = true), input))
        }
    }
    @Test fun multipleAppStampIsOrderIndependentAndInvalidatedBySelectionOrAppListChanges() {
        val input = multiple()
        val draft = reviewed(review(input).copy(packageNames = linkedSetOf("com.example.app", "com.example.other")), input)
        assertEquals(BindingFlow.stamp(draft, input), BindingFlow.stamp(draft.copy(packageNames = linkedSetOf("com.example.other", "com.example.app")), input))
        assertFalse(BindingFlow.canConfirm(draft.copy(packageNames = setOf("com.example.app")), input))
        val changed = BindingFlow.selectApps(draft, setOf("com.example.third"))
        assertNull(changed.reviewStamp); assertFalse(changed.acknowledged)
        val refreshed = input.copy(profiles = listOf(input.profiles.single().copy(packages = listOf("com.example.app", "com.example.other"))))
        assertFalse(BindingFlow.canConfirm(draft, refreshed))
    }
    @Test fun submitWritesOneRecordPerSelectedAppAndKeepsUnselectedRecords() {
        val original = multiple(); val single = review(original)
        val previous = held(single).copy(revision = 9, enabled = false, cloakProfileId = "old")
        val unselected = previous.copy(packageName = "com.example.third", cloakProfileId = "keep")
        val input = original.copy(bindings = listOf(previous, unselected))
        val draft = reviewed(single.copy(packageNames = setOf("com.example.app", "com.example.other")), input)
        val records = BindingFlow.records(draft, input, 700)
        val saved = BindingFlow.applyRecords(input.bindings, records)
        assertEquals(2, records.size); assertEquals(draft.packageNames, records.map { it.packageName }.toSet())
        assertTrue(records.all { it.androidUserId == 11 && it.cloakProfileId == draft.identityId && it.enabled && it.state == "pending" && it.updatedAt == 700L })
        assertEquals(9, records.first { it.packageName == previous.packageName }.revision)
        assertEquals(0, records.first { it.packageName == "com.example.other" }.revision)
        assertEquals(unselected, saved.first { it.packageName == unselected.packageName })
        assertEquals(3, saved.size); assertTrue(BindingFlow.saved(draft, saved))
        assertEquals(listOf(previous, unselected), input.bindings)
    }
    @Test fun anyBlockedSelectedAppPreventsEntireSubmit() {
        val original = multiple(); val draft = review(original).copy(packageNames = setOf("com.example.app", "com.example.other"))
        val input = original.copy(bindings = listOf(held(review(original)).copy(packageName = "com.example.other", origin = CloakBinding.ORIGIN_MAIN)))
        val reviewed = reviewed(draft, input)
        assertFalse(BindingFlow.canConfirm(reviewed, input))
        assertTrue(BindingFlow.findings(reviewed, input).any { it.code == "MANAGED_BY_MAIN" && it.field == "app.com.example.other" })
        assertThrows(IllegalArgumentException::class.java) { BindingFlow.records(reviewed, input, 0) }
        val stale = reviewed.copy(packageNames = reviewed.packageNames + "com.example.missing")
        assertTrue(BindingFlow.findings(stale, input).any { it.code == "APP_MISSING" && it.field == "app.com.example.missing" })
    }
    @Test fun multiAppCompletionAndRetryRequireEverySavedTargetAndHealthState() {
        val input = multiple(); val draft = reviewed(review(input).copy(packageNames = setOf("com.example.app", "com.example.other")), input)
        val records = BindingFlow.records(draft, input, 100).map { it.copy(state = "ready") }
        assertTrue(BindingFlow.completion(draft, RootDoctorResult(RootDoctorCode.READY), report(review(input)), records).ready)
        val failed = BindingFlow.completion(draft, RootDoctorResult(RootDoctorCode.READY),
            report(review(input), mapOf(bindingKey(profile.id, "com.example.other") to "Write failed")), records)
        assertTrue(failed.saved); assertFalse(failed.ready); assertTrue(failed.message.contains("com.example.other"))
        assertFalse(BindingFlow.saved(draft, records.drop(1)))
        assertFalse(BindingFlow.saved(draft, records + records.last()))
        assertFalse(BindingFlow.completion(draft, RootDoctorResult(RootDoctorCode.READY), report(review(input)), records.map { it.copy(state = "pending") }).ready)
        val existing = input.copy(bindings = records)
        assertFalse(BindingFlow.findings(draft, existing).any { it.code == "IDENTITY_ALREADY_BOUND" })
    }

    @Test fun existingBulkPlanFeedsTheSamePerAppUpdateWithoutChangingAssignments() {
        val input = multiple(); val id = input.identities.single().first
        val plan = BulkBind.plan(listOf(id), input.profiles, input.bindings)
        val (target, selectedId) = plan.assignments.single()
        val draft = reviewed(BindingDraft(profileId = target.id, packageNames = target.packages!!.toSet(), identityId = selectedId), input)
        val guided = BindingFlow.applyRecords(input.bindings, BindingFlow.records(draft, input, 100))
        val bulkRecords = target.packages!!.map { CloakBinding(target.id, target.androidUserId!!, it, selectedId, 0, true, 100, "pending") }
        val bulk = BindingFlow.applyRecords(input.bindings, bulkRecords)
        assertEquals(bulk.sortedBy { it.packageName }, guided)
        assertEquals(target.packages!!.size, guided.size)
    }

}
