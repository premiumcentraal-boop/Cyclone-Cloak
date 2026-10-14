package dev.cyclone.cloak.ui

import dev.cyclone.cloak.diagnostics.*

import dev.cyclone.cloak.cyclone.CloakHealth
import dev.cyclone.cloak.cyclone.SyncReport
import dev.cyclone.cloak.cyclone.bindingKey
import dev.cyclone.cloak.data.CloakBinding
import dev.cyclone.cloak.root.RootDoctorResult

fun bindingRunTarget(b: CloakBinding) = RunTarget(b.profileId, b.androidUserId, listOf(b.packageName), b.cloakProfileId)

/** Observation only: never updates a binding or changes reconciliation decisions. */
class BindingRunResult {
    var root: RootDoctorResult? = null
    var report: SyncReport? = null
    var exception = false
    var resolvedTargets: List<RunTarget>? = null
    fun complete(run: BindingRun, current: List<CloakBinding>, duration: Long, captured: RunEventCapture): BindingRun {
        val targets = resolvedTargets ?: run.targets
        val reasons = linkedSetOf<String>()
        if (exception) reasons += "ACTION_FAILED"
        if (root == null) reasons += "PUBLICATION_NOT_RUN"
        else if (root?.published != true) reasons += root!!.publicationReason?.name ?: root!!.code.name
        report?.failureCode?.let { reasons += it }
        report?.reconcile?.stoppedBy?.let { reasons += it }
        if (report?.headline != "Connected" || report?.gate?.bindings != true) reasons += "CONNECTOR_NOT_READY"
        if (report?.reconcile == null) reasons += "RECONCILIATION_NOT_VERIFIED"
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
        captured.events.filter { it.optString("severity") == "ERROR" }.forEach {
            if (!it.isNull("reason")) reasons += DiagnosticPrivacy.code(it.getString("reason"))
        }
        val failed = exception || (root != null && root?.published != true && root?.publicationReason?.name != "LOCKED") ||
            "REMOVE_NOT_CONFIRMED" in reasons || "BINDING_MISSING_OR_AMBIGUOUS" in reasons
        return run.copy(targets = targets, outcome = when { reasons.isEmpty() -> RunOutcome.READY; failed -> RunOutcome.FAILED; else -> RunOutcome.NEEDS_ATTENTION },
            reasons = reasons.toList(), durationMs = duration.coerceAtLeast(0), events = captured.events.toList(), eventsDropped = captured.dropped,
            moduleVersion = root?.moduleVersion, moduleCode = root?.code?.name,
            connectorMinor = report?.gate?.minor, connectorApproved = report?.gate?.approved)
    }
}
