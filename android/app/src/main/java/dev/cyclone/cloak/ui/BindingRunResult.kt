package dev.cyclone.cloak.ui

import dev.cyclone.cloak.diagnostics.*

import dev.cyclone.cloak.cyclone.CloakHealth
import dev.cyclone.cloak.cyclone.SyncReport
import dev.cyclone.cloak.cyclone.bindingKey
import dev.cyclone.cloak.data.CloakBinding
import dev.cyclone.cloak.data.CloakSpace
import dev.cyclone.cloak.root.RootDoctorResult

fun bindingRunTarget(b: CloakBinding) = RunTarget(b.profileId, b.androidUserId, listOf(b.packageName), b.cloakProfileId)

/** Observation only: never updates a binding or changes reconciliation decisions. */
class BindingRunResult {
    var root: RootDoctorResult? = null
    var report: SyncReport? = null
    var exception = false
    var resolvedTargets: List<RunTarget>? = null
    private fun observedReasons(captured: RunEventCapture): List<String> = buildList {
        captured.events.filter { it.optString("severity") == "ERROR" && !it.isNull("reason") }.forEach {
            add(DiagnosticPrivacy.code(it.getString("reason")))
        }
        root?.takeUnless { it.published }?.let { add(it.publicationReason?.name ?: it.code.name) }
        report?.failureCode?.let(::add)
        report?.reconcile?.stoppedBy?.let(::add)
    }
    fun snapshot(run: BindingRun, duration: Long, captured: RunEventCapture): BindingRun = run.copy(
        targets = resolvedTargets ?: run.targets, durationMs = duration.coerceAtLeast(0),
        reasons = (run.reasons + observedReasons(captured)).distinct(),
        events = captured.events.toList(), eventsDropped = captured.dropped,
        moduleVersion = root?.moduleVersion, moduleCode = root?.code?.name,
        connectorMinor = report?.gate?.minor, connectorApproved = report?.gate?.approved,
        reconciliation = report?.reconcile?.let { RunReconciliation(it.written, it.statuses, it.cleared, it.issues.size, it.stoppedBy) })
    fun complete(run: BindingRun, current: List<CloakBinding>, duration: Long, captured: RunEventCapture): BindingRun {
        val targets = resolvedTargets ?: run.targets
        val reasons = (run.reasons + observedReasons(captured)).toCollection(linkedSetOf())
        if (root != null && root?.published != true) reasons += root!!.publicationReason?.name ?: root!!.code.name
        report?.failureCode?.let { reasons += it }
        report?.reconcile?.stoppedBy?.let { reasons += it }
        if (root == null) reasons += "PUBLICATION_NOT_RUN"
        if (exception) reasons += "ACTION_FAILED"
        // A run that only touches spaces never reaches Cyclone, so connector reasons do not apply to it.
        val spacesOnly = targets.isNotEmpty() && targets.all { CloakSpace.isSpaceId(it.profileId) }
        if (!spacesOnly && (report?.headline != "Connected" || report?.gate?.bindings != true)) reasons += "CONNECTOR_NOT_READY"
        if (!spacesOnly && report?.reconcile == null) reasons += "RECONCILIATION_NOT_VERIFIED"
        if (targets.isEmpty() || targets.all { it.packages.isEmpty() }) reasons += "NO_TARGETS"
        for (target in targets) for (pkg in target.packages) {
            val records = current.filter { RunPrivacy.reference(it.profileId) == RunPrivacy.reference(target.profileId) && it.packageName == pkg }
            if (!target.expectedPresent) { if (records.isNotEmpty()) reasons += "REMOVE_NOT_CONFIRMED" }
            else {
                    val record = records.singleOrNull()
                    if (record == null) reasons += "BINDING_MISSING_OR_AMBIGUOUS"
                    else if (RunPrivacy.reference(record.cloakProfileId) != RunPrivacy.reference(target.deviceProfileId)) reasons += "BINDING_CHANGED"
                    else if (target.expectedEnabled == false && record.enabled) reasons += "DISABLE_NOT_CONFIRMED"
                    else if (target.expectedEnabled == true && !record.enabled) reasons += "ENABLE_NOT_CONFIRMED"
                    else if (record.enabled && CloakHealth.cycloneState(record.state) != CloakHealth.READY) reasons += "BINDING_NOT_READY"
            }
            if (report?.issues?.containsKey(bindingKey(target.profileId, pkg)) == true) reasons += "RECONCILIATION_ISSUE"
        }
        val failed = exception || (root != null && root?.published != true && root?.publicationReason?.name != "LOCKED") ||
            "REMOVE_NOT_CONFIRMED" in reasons || "BINDING_MISSING_OR_AMBIGUOUS" in reasons
        return snapshot(run, duration, captured).copy(active = false,
            outcome = when { reasons.isEmpty() -> RunOutcome.READY; failed -> RunOutcome.FAILED; else -> RunOutcome.NEEDS_ATTENTION }, reasons = reasons.toList())
    }
}

/** Fixed operator guidance; raw connector errors and private identity data never enter it. */
object BindingRunFeedback {
    fun message(run: BindingRun): String = when {
        run.active && run.events.lastOrNull { it.optString("action_id") == "batch-session" }?.optString("reason") == "WAITING_FOR_APPROVAL" ->
            "Waiting for approval. Allow Cyclone Cloak in Magisk for this Android profile; do not start another run."
        run.active -> "This run is active. Wait for it to finish; no additional tap is needed."
        run.outcome == RunOutcome.READY -> "Publication and reconciliation completed."
        run.reasons.firstOrNull() in setOf("ROOT_TIMEOUT", "APPROVAL_TIMEOUT", "APPROVAL_EXPIRED", "CONFIRMATION_EXPIRED") ->
            "The approval or confirmation window expired. Retry and approve Cyclone Cloak in the app that asks. Saved local records are retained."
        run.reasons.firstOrNull() == "ROOT_REQUIRED" -> "Root approval was denied. Retry and allow Cyclone Cloak in Magisk for this Android profile."
        run.reasons.firstOrNull() == "INTERRUPTED" -> "The run stopped before completion. Retry using the retained selection and local records."
        run.connectorApproved == false || run.reasons.any { it in setOf("NOT_APPROVED", "SCOPE_NOT_GRANTED") } ->
            "Finish the pending connector approval in Cyclone, then retry this run."
        run.reasons.any { it.startsWith("CYCLONE_") || it == "CONNECTOR_NOT_READY" } ->
            "The connector did not finish. Reconnect to Cyclone, then retry this run; saved local records are retained."
        else -> "The run did not complete. Retry using the retained local records; changed targets require review."
    }
}

enum class RunRetry { RECONCILE, REVIEW, BLOCKED }

object BindingRunRetry {
    fun plan(run: BindingRun, current: List<CloakBinding>): RunRetry {
        if (run.active || run.outcome == RunOutcome.READY || run.targets.isEmpty() || run.targets.any { it.packages.isEmpty() }) return RunRetry.BLOCKED
        var missing = false
        for (target in run.targets) for (pkg in target.packages) {
            val records = current.filter { RunPrivacy.reference(it.profileId) == RunPrivacy.reference(target.profileId) && it.packageName == pkg }
            if (!target.expectedPresent) { if (records.isNotEmpty()) return RunRetry.BLOCKED }
            else if (records.isEmpty() && run.operation == RunOperation.BIND) missing = true
            else {
                val record = records.singleOrNull() ?: return RunRetry.BLOCKED
                if (RunPrivacy.reference(record.cloakProfileId) != RunPrivacy.reference(target.deviceProfileId) ||
                    (target.androidUser != null && record.androidUserId != target.androidUser) ||
                    (target.expectedEnabled != null && record.enabled != target.expectedEnabled)) return RunRetry.BLOCKED
            }
        }
        return if (!missing) RunRetry.RECONCILE else if (run.targets.size == 1) RunRetry.REVIEW else RunRetry.BLOCKED
    }
}
