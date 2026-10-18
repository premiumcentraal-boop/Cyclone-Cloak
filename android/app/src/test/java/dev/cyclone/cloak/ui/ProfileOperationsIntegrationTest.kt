package dev.cyclone.cloak.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.cyclone.cloak.cyclone.*
import dev.cyclone.cloak.data.*
import dev.cyclone.cloak.diagnostics.*
import dev.cyclone.cloak.forge.*
import dev.cyclone.cloak.root.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.zip.ZipInputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProfileOperationsIntegrationTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val profile = CycloneProfile("Cyclone_bbbbbbbbbbbbbbbb", "Work", "profile", "ready", 11,
        listOf("com.example.one", "com.example.two"))
    @Before fun reset() {
        File(context.filesDir, "profiles").deleteRecursively()
        CloakBindingStore.update(context) { emptyList() }
    }
    private fun create(): String {
        val plan = BatchIdentityPlanner().plan(BatchIdentityPlanner.Request(listOf("pixel_7"), 1, CloakForge.newSeed(), "Work ${CloakStore.all(context).size}"),
            listOf(TestPhones["pixel_7"]), CloakStore.all(context).map { it.second })
        val result = BatchIdentitySave.save(plan, plan.drafts.map { it.key }.toSet(),
            { CloakStore.all(context).map { it.second } }, { value, _ -> CloakStore.importBatch(context, listOf(value)); Unit })
        assertEquals("SAVED", result.single().status)
        return plan.drafts.single().profile!!.getString("id")
    }
    private fun input(p: CycloneProfile = profile) = BindingInputs(listOf(p), CloakStore.all(context), CloakBindingStore.all(context), true, null)
    private fun review(draft: BindingDraft, input: BindingInputs): BindingDraft {
        val reviewed = draft.copy(step = BindingStep.REVIEW, acknowledged = true)
        return reviewed.copy(reviewStamp = BindingFlow.stamp(reviewed, input))
    }
    private fun save(draft: BindingDraft, input: BindingInputs) {
        val records = BindingFlow.records(draft, input, 500)
        CloakBindingStore.update(context) { BindingFlow.applyRecords(it, records) }
    }
    @Test fun successfulCreationAndAssignmentUsesExistingStores() {
        val id = create(); val input = input()
        val draft = review(ProfileOperations.draft(profile, input, ProfileAction.BIND_ALL).copy(identityId = id), input)
        assertTrue(BindingFlow.canConfirm(draft, input))
        save(draft, input)
        assertEquals(profile.packages!!.toSet(), CloakBindingStore.all(context).map { it.packageName }.toSet())
        assertTrue(CloakBindingStore.all(context).all { it.androidUserId == 11 && it.cloakProfileId == id })
        assertEquals("0.4", CloakStore.find(context, id)?.optString("schema_version"))
    }
    @Test fun failedValidationCannotSaveOrAssign() {
        val id = create(); val input = input()
        val corrupt = JSONObject(input.identities.single().second.toString()).also { it.getJSONObject("display").put("width", -1) }
        val bad = input.copy(identities = listOf(id to corrupt))
        val draft = review(ProfileOperations.draft(profile, bad, ProfileAction.BIND_ALL).copy(identityId = id), bad)
        assertFalse(BindingFlow.canConfirm(draft, bad))
        assertThrows(IllegalArgumentException::class.java) { save(draft, bad) }
        assertTrue(CloakBindingStore.all(context).isEmpty())
    }
    @Test fun bindAllAddsMissingAppsAndKeepsOtherProfiles() {
        val id = create()
        val unrelated = CloakBinding("Cyclone_cccccccccccccccc", 12, "com.example.other", id, 3, false, 100)
        CloakBindingStore.update(context) { listOf(unrelated) }
        val input = input(); save(review(ProfileOperations.draft(profile, input, ProfileAction.BIND_ALL).copy(identityId = id), input), input)
        assertEquals(3, CloakBindingStore.all(context).size)
        assertTrue(unrelated in CloakBindingStore.all(context))
    }
    @Test fun replacementKeepsProfileUserAppsAndDisabledStateAfterConfirmation() {
        val old = create(); val new = create()
        val held = profile.packages!!.mapIndexed { index, pkg -> CloakBinding(profile.id, 11, pkg, old, 9, index == 0, 100) }
        CloakBindingStore.update(context) { held }
        val input = input(); val draft = review(ProfileOperations.draft(profile, input, ProfileAction.REPLACE).copy(identityId = new), input)
        assertEquals(held, CloakBindingStore.all(context))
        assertFalse(BindingFlow.canConfirm(draft.copy(reviewStamp = null), input))
        save(draft, input)
        val result = CloakBindingStore.all(context)
        assertEquals(held.map { Triple(it.profileId, it.androidUserId, it.packageName) }, result.map { Triple(it.profileId, it.androidUserId, it.packageName) })
        assertEquals(held.map { it.enabled }, result.map { it.enabled })
        assertTrue(result.all { it.cloakProfileId == new && it.revision == 9 })
    }
    @Test fun replacementRejectsChangedMappingAndRetainsExplicitAppSelection() {
        val id = create(); val record = CloakBinding(profile.id, 11, "com.example.one", id, 0, true, 100)
        CloakBindingStore.update(context) { listOf(record) }
        val input = input(); val draft = ProfileOperations.draft(profile, input, ProfileAction.REPLACE).copy(identityId = id)
        assertEquals(setOf("com.example.one"), draft.packageNames)
        val changed = input(profile.copy(androidUserId = 15))
        assertFalse(BindingFlow.canConfirm(review(draft, changed), changed))
        assertTrue(ProfileOperations.replacementFindings(draft, changed).any { it.code == "REPLACEMENT_CHANGED" })
    }
    @Test fun missingAndroidUserAndMissingAppListGiveConcreteNextSteps() {
        val id = create()
        for (p in listOf(profile.copy(androidUserId = null), profile.copy(packages = null))) {
            val input = input(p)
            val draft = review(ProfileOperations.draft(p, input, ProfileAction.BIND_ALL).copy(identityId = id), input)
            assertFalse(BindingFlow.canConfirm(draft, input))
            assertTrue(BindingFlow.findings(draft, input).any { it.isError && (it.message.contains("Cyclone") || it.message.contains("All apps")) })
            assertTrue(CloakBindingStore.all(context).isEmpty())
        }
    }
    @Test fun publicationRetryUsesSavedReplacementAndNeverRestoresOldIdentity() {
        val old = create(); val new = create()
        val current = CloakBinding(profile.id, 11, "com.example.one", new, 0, true, 100)
        CloakBindingStore.update(context) { listOf(current) }
        val run = BindingRun(operation = RunOperation.REPLACE,
            targets = listOf(RunTarget(profile.id, 11, listOf(current.packageName), new, previousDeviceProfiles = mapOf(current.packageName to old))),
            outcome = RunOutcome.FAILED, reasons = listOf("PUBLISH_FAILED"))
        assertEquals(RunRetry.RECONCILE, BindingRunRetry.plan(run, CloakBindingStore.all(context)))
        assertEquals(listOf(current), CloakBindingStore.all(context))
        assertEquals(RunRetry.BLOCKED, BindingRunRetry.plan(run, listOf(current.copy(cloakProfileId = old))))
    }
    @Test fun diagnosticExportRetainsRedactedBeforeAfterAndLegacyHistoryIsReadable() {
        val target = RunTarget(profile.id, 11, listOf("com.example.one"), "private new label",
            previousDeviceProfiles = mapOf("com.example.one" to "private old label"))
        val run = BindingRun(timestamp = 100, operation = RunOperation.REPLACE, targets = listOf(target), durationMs = 50)
        val journal = BindingRunJournal(File(context.cacheDir, "operations-test"))
        journal.append(run)
        val reread = BindingRunJournal(File(context.cacheDir, "operations-test")).runs.value.last()
        assertEquals(RunPrivacy.reference("private old label"), reread.targets.single().previousDeviceProfiles.values.single())
        val files = BindingRunBundle.build(listOf(reread), emptyList(), emptyList(), RootDoctorResult(RootDoctorCode.PUBLISH_FAILED), null, "test", 100, 150, 200)
        val zip = BindingRunBundle.zip(files)
        ZipInputStream(zip.inputStream()).use { stream ->
            var found = false
            while (true) { val entry = stream.nextEntry ?: break
                val text = stream.readBytes().toString(Charsets.UTF_8)
                assertFalse(text.contains("private old label")); assertFalse(text.contains("private new label"))
                if (entry.name == "runs.json") { found = true; assertTrue(text.contains("previous_device_profiles")) }
            }
            assertTrue(found)
        }
        val legacy = target.json().also { it.remove("previous_device_profiles") }
        assertTrue(RunTarget.parse(legacy).previousDeviceProfiles.isEmpty())
    }
}
