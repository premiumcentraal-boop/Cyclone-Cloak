package dev.cyclone.cloak.diagnostics

import dev.cyclone.cloak.cyclone.*
import dev.cyclone.cloak.data.CloakBinding
import dev.cyclone.cloak.root.*
import dev.cyclone.cloak.ui.BindingRunResult
import dev.cyclone.cloak.ui.bindingRunTarget
import dev.cyclone.cloak.ui.BindingRunFeedback
import dev.cyclone.cloak.ui.BindingRunRetry
import dev.cyclone.cloak.ui.RunRetry
import org.junit.Assert.*
import org.junit.Test

class BindingRunResultTest {
    private val binding = CloakBinding("Cyclone_0123456789abcdef", 16, "org.example.app", "0123456789abcdef", 0, true, 1, "ready")
    private val run = BindingRun(operation = RunOperation.BIND, targets = listOf(bindingRunTarget(binding)))
    private fun healthy() = BindingRunResult().apply {
        root = RootDoctorResult(RootDoctorCode.READY)
        report = SyncReport("Connected", "", gate = CycloneGate(true, 3,
            setOf("profiles.read", "profiles.apps.read", "profiles.config"), emptySet()),
            reconcile = BindingReconciler.Result(emptySet(), emptyMap(), 0, 0, 0))
    }
    @Test fun verifiedReadyRunDoesNotChangeExistingBinding() {
        val records = listOf(binding)
        val result = healthy().complete(run, records, 20, RunEventCapture())
        assertEquals(RunOutcome.READY, result.outcome); assertTrue(result.reasons.isEmpty())
        assertEquals(listOf(binding), records)
    }
    @Test fun preflightFailureIsFailedAndNeverClaimedReadyFromOldState() {
        val observation = BindingRunResult().apply { root = RootDoctorResult(RootDoctorCode.ROOT_TIMEOUT) }
        val result = observation.complete(run, listOf(binding), 30, RunEventCapture())
        assertEquals(RunOutcome.FAILED, result.outcome); assertTrue("ROOT_TIMEOUT" in result.reasons)
    }
    @Test fun lockedPublicationNeedsAttentionAndHasReason() {
        val observation = healthy().apply { root = RootDoctorResult(RootDoctorCode.PUBLISH_FAILED, publicationReason = PublicationReason.LOCKED) }
        val result = observation.complete(run, listOf(binding), 1, RunEventCapture())
        assertEquals(RunOutcome.NEEDS_ATTENTION, result.outcome); assertTrue("LOCKED" in result.reasons)
    }
    @Test fun removeAndDisableUseTheirExpectedStateRatherThanOldHealth() {
        val removed = run.copy(operation = RunOperation.REMOVE, targets = run.targets.map { it.copy(expectedPresent = false) })
        assertEquals(RunOutcome.READY, healthy().complete(removed, emptyList(), 1, RunEventCapture()).outcome)
        assertEquals(RunOutcome.FAILED, healthy().complete(removed, listOf(binding), 1, RunEventCapture()).outcome)
        val disabled = run.copy(operation = RunOperation.DISABLE, targets = run.targets.map { it.copy(expectedEnabled = false) })
        assertEquals(RunOutcome.READY, healthy().complete(disabled, listOf(binding.copy(enabled = false, state = "pending")), 1, RunEventCapture()).outcome)
    }
    @Test fun connectorIssuesAndUnverifiedReconciliationNeverPromoteToReady() {
        val observation = healthy().apply { report = SyncReport("Connected", "", issues = mapOf(bindingKey(binding.profileId, binding.packageName) to "private details")) }
        val result = observation.complete(run, listOf(binding), 1, RunEventCapture())
        assertEquals(RunOutcome.NEEDS_ATTENTION, result.outcome)
        assertTrue("RECONCILIATION_ISSUE" in result.reasons); assertTrue("RECONCILIATION_NOT_VERIFIED" in result.reasons)
        assertFalse(result.json().toString().contains("private details"))
    }
    @Test fun expiredApprovalKeepsSelectionAndOffersDirectRetry() {
        val result = healthy().apply { root = RootDoctorResult(RootDoctorCode.ROOT_TIMEOUT) }
            .complete(run, emptyList(), 30000, RunEventCapture())
        assertEquals("ROOT_TIMEOUT", result.reasons.first())
        assertEquals(run.targets, result.targets)
        assertTrue(BindingRunFeedback.message(result).contains("expired"))
        assertEquals(RunRetry.REVIEW, BindingRunRetry.plan(result, emptyList()))
    }
    @Test fun partialSavedRunRetriesExistingRecordsWithoutRecreatingThem() {
        val pending = binding.copy(state = "pending")
        val records = listOf(pending)
        val failed = healthy().apply { root = RootDoctorResult(RootDoctorCode.PUBLISH_FAILED) }
            .complete(run, records, 10, RunEventCapture())
        assertEquals(RunRetry.RECONCILE, BindingRunRetry.plan(failed, records))
        assertEquals(listOf(pending), records)
        assertEquals(RunOutcome.READY, healthy().complete(run.copy(operation = RunOperation.RETRY_RECONCILIATION,
            retryOf = failed.id), listOf(binding), 10, RunEventCapture()).outcome)
        assertEquals(RunRetry.BLOCKED, BindingRunRetry.plan(failed, listOf(binding.copy(cloakProfileId = "aaaaaaaaaaaaaaaa"))))
    }
    @Test fun connectorFailureAfterPublicationNeverClaimsReady() {
        val result = healthy().apply { report = SyncReport.failed("CYCLONE_NOT_ANSWERING", "private details") }
            .complete(run, listOf(binding), 10, RunEventCapture())
        assertEquals(RunOutcome.NEEDS_ATTENTION, result.outcome)
        assertEquals("CYCLONE_NOT_ANSWERING", result.reasons.first())
        assertTrue(BindingRunFeedback.message(result).contains("Reconnect"))
        assertFalse(result.json().toString().contains("private details"))
    }
    @Test fun finalPublicationFailureOverridesSuccessfulReconciliation() {
        val result = healthy().apply { root = RootDoctorResult(RootDoctorCode.PUBLISH_FAILED,
            publicationReason = PublicationReason.FAILED) }.complete(run, listOf(binding), 10, RunEventCapture())
        assertEquals(RunOutcome.FAILED, result.outcome)
        assertEquals("FAILED", result.reasons.first())
    }
    @Test fun activeAndEmptySelectionsCannotBeRetried() {
        assertEquals(RunRetry.BLOCKED, BindingRunRetry.plan(run.copy(active = true), listOf(binding)))
        assertEquals(RunRetry.BLOCKED, BindingRunRetry.plan(run.copy(targets = run.targets.map { it.copy(packages = emptyList()) }), emptyList()))
    }
    @Test fun checkpointsRetainFirstObservedFailureWithoutPrivateErrorText() {
        val capture = RunEventCapture().apply { accept(DiagnosticEvent(1, Severity.ERROR, "app", "connector", "request",
            "1", "2", null, Transition.FAILED, "CONFIRMATION_EXPIRED", null, null)) }
        val observation = healthy().apply { root = RootDoctorResult(RootDoctorCode.PUBLISH_FAILED) }
        val checkpoint = observation.snapshot(run, 10, capture)
        assertEquals("CONFIRMATION_EXPIRED", checkpoint.reasons.first())
        val completed = observation.complete(run, listOf(binding), 10, capture)
        assertEquals(checkpoint.reasons.first(), completed.reasons.first())
        assertTrue(BindingRunFeedback.message(completed).contains("expired"))
    }
}
