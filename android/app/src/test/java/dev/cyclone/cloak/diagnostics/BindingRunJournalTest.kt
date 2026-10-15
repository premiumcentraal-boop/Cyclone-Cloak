package dev.cyclone.cloak.diagnostics

import android.app.Application
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class BindingRunJournalTest {
    @get:Rule val temp = TemporaryFolder()
    private val target = RunTarget("Cyclone_0123456789abcdef", 16, listOf("org.example.one", "org.example.two"), "0123456789abcdef")
    private fun run(n: Int = 1) = BindingRun(timestamp = n.toLong(), operation = RunOperation.BIND, targets = listOf(target))

    @Test fun schemaRoundTripsAllOperationsAndOutcomes() {
        for (operation in RunOperation.entries) for (outcome in RunOutcome.entries) {
            val run = run().copy(operation = operation, outcome = outcome, reasons = listOf("ROOT_TIMEOUT"), durationMs = 12)
            val decoded = BindingRun.parse(JSONObject(run.json().toString()))
            assertEquals(run.id, decoded.id); assertEquals(operation, decoded.operation); assertEquals(outcome, decoded.outcome)
            assertEquals(listOf(target), decoded.targets); assertEquals(listOf("ROOT_TIMEOUT"), decoded.reasons)
            assertEquals(12L, decoded.durationMs)
        }
    }
    @Test fun rotationBoundsFilesAndRetainsNewestRunsAcrossReopen() {
        val dir = temp.newFolder()
        val journal = BindingRunJournal(dir)
        repeat(210) { journal.append(run(it)) }
        val restored = BindingRunJournal(dir).runs.value
        assertEquals(160, restored.size)
        assertEquals(50L, restored.first().timestamp); assertEquals(209L, restored.last().timestamp)
        assertEquals(4, dir.listFiles()!!.count { it.extension == "jsonl" })
    }
    @Test fun byteCapRotatesBeforeRecordCountLimit() {
        val dir = temp.newFolder()
        val journal = BindingRunJournal(dir, fileCap = 2048)
        repeat(30) { journal.append(run(it)) }
        assertTrue(dir.listFiles()!!.all { it.length() <= 2048 })
        assertTrue(journal.runs.value.size < 30)
        assertEquals(29L, journal.runs.value.last().timestamp)
    }
    @Test fun corruptAndTruncatedRowsDoNotHideLaterAppends() {
        val dir = temp.newFolder()
        File(dir, "runs.jsonl").writeText("not-json\n{\"unfinished\":")
        val journal = BindingRunJournal(dir)
        val run = run()
        journal.append(run)
        assertEquals(listOf(run.id), BindingRunJournal(dir).runs.value.map { it.id })
    }
    @Test fun interruptedOperationRecoveredOnceWithoutBindingMutation() {
        val dir = temp.newFolder()
        val journal = BindingRunJournal(dir)
        val run = run()
        journal.begin(run)
        val recovered = BindingRunJournal(dir).runs.value.single()
        assertEquals(run.id, recovered.id); assertEquals(RunOutcome.NEEDS_ATTENTION, recovered.outcome)
        assertEquals(listOf("INTERRUPTED"), recovered.reasons)
        assertEquals(1, BindingRunJournal(dir).runs.value.size)
    }
    @Test fun finishedRunDoesNotRecoverAsInterrupted() {
        val dir = temp.newFolder()
        val journal = BindingRunJournal(dir)
        val run = run()
        journal.begin(run); journal.append(run.copy(outcome = RunOutcome.READY))
        assertEquals(RunOutcome.READY, BindingRunJournal(dir).runs.value.single().outcome)
    }
    @Test fun storageFailureIsReportedWithoutThrowingIntoBindingWork() {
        val blocked = temp.newFile()
        val journal = BindingRunJournal(blocked)
        journal.begin(run()); journal.append(run())
        assertTrue(journal.failures.value >= 2)
        assertTrue(journal.runs.value.isEmpty())
    }
    @Test fun producerCaptureSurvivesRingOverflowAndHasItsOwnBound() {
        DiagnosticsRegistry(capacity = 2).use { registry ->
            val captured = RunEventCapture()
            registry.captureRun(captured) { repeat(300) { registry.event("publication", "merge", Transition.COMPLETED) } }
            assertEquals(256, captured.events.size); assertEquals(44, captured.dropped)
            assertTrue(registry.events().size <= 2)
            registry.event("publication", "unrelated", Transition.STARTED)
            assertEquals(256, captured.events.size)
        }
    }
    @Test fun runVersionsAndHandshakeRemainHistoricalAcrossRoundTrip() {
        val run = run().copy(appVersion = "0.11.0-alpha.11", moduleVersion = "0.11.0-alpha.10", moduleCode = "MODULE_OUTDATED",
            connectorMinor = 3, connectorApproved = true)
        val restored = BindingRun.parse(run.json())
        assertEquals(run.appVersion, restored.appVersion); assertEquals(run.moduleVersion, restored.moduleVersion)
        assertEquals(3, restored.connectorMinor); assertEquals(true, restored.connectorApproved)
    }
    @Test fun activeCheckpointIsVisibleAndRetainsPartialEvidenceAfterInterruption() {
        val dir = temp.newFolder()
        val journal = BindingRunJournal(dir, now = { 100 })
        val started = run().copy(timestamp = 10)
        journal.begin(started)
        assertTrue(journal.runs.value.single().active)
        assertTrue(journal.runs.value.single().json().isNull("end_timestamp"))
        journal.begin(started.copy(durationMs = 20, reconciliation = RunReconciliation(1, 0, 0, 1, "CYCLONE_NOT_ANSWERING")))
        assertEquals(0, journal.failures.value)
        assertNotNull(journal.runs.value.single().reconciliation)
        assertNotNull(JSONObject(File(dir, "pending.json").readText()).optJSONObject("reconciliation"))
        assertEquals(1, journal.runs.value.size)
        val restored = BindingRunJournal(dir, now = { 100 }).runs.value.single()
        assertFalse(restored.active)
        assertEquals(started.targets, restored.targets)
        assertEquals(100L, restored.json().getLong("end_timestamp"))
        assertEquals("CYCLONE_NOT_ANSWERING", restored.reconciliation!!.stoppedBy)
        assertEquals("INTERRUPTED", restored.json().getString("first_failure_reason"))
    }
    @Test fun olderRecordsRemainReadableWithoutOptionalCheckpointFields() {
        val old = run().json()
        listOf("active", "end_timestamp", "first_failure_reason", "retry_of", "reconciliation").forEach(old::remove)
        val decoded = BindingRun.parse(old)
        assertFalse(decoded.active); assertNull(decoded.retryOf); assertNull(decoded.reconciliation)
        assertEquals(run().targets, decoded.targets)
    }
    @Test fun successfulRetryKeepsItsOwnRecordAndReferenceToFailedAttempt() {
        val journal = BindingRunJournal(temp.newFolder())
        val failed = run().copy(reasons = listOf("ROOT_TIMEOUT"))
        journal.append(failed)
        val retry = run(2).copy(retryOf = failed.id, outcome = RunOutcome.READY)
        journal.begin(retry); journal.append(retry)
        assertEquals(2, journal.runs.value.size)
        assertEquals(failed.id, journal.runs.value.last().retryOf)
        assertFalse(journal.runs.value.last().active)
    }
    @Test fun unfinishedReplacementAndLegacyBackupRetainCompleteCheckpoint() {
        val dir = temp.newFolder()
        val started = run()
        BindingRunJournal(dir).begin(started)
        File(dir, "pending.json.new").writeText("{unfinished")
        assertEquals(started.id, BindingRunJournal(dir).runs.value.single().id)
        val legacy = temp.newFolder()
        File(legacy, "pending.json").writeText("{unfinished")
        File(legacy, "pending.json.bak").writeText(started.json().toString())
        assertEquals(started.id, BindingRunJournal(legacy).runs.value.single().id)
    }
    @Test fun failedRecoveryAppendDoesNotDeleteThePendingEvidence() {
        val dir = temp.newFolder()
        val started = run()
        BindingRunJournal(dir).begin(started)
        val blocked = File(dir, "runs.jsonl").apply { mkdir() }
        val failed = BindingRunJournal(dir)
        assertTrue(failed.failures.value > 0)
        assertTrue(File(dir, "pending.json").isFile)
        assertTrue(blocked.delete())
        assertEquals(started.id, BindingRunJournal(dir).runs.value.single().id)
    }
}
