package dev.cyclone.cloak.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.cyclone.cloak.cyclone.CycloneProfile
import dev.cyclone.cloak.data.EngineMetadataStore
import dev.cyclone.cloak.data.PostureState
import dev.cyclone.cloak.cyclone.ConnectionPhase
import dev.cyclone.cloak.diagnostics.*
import dev.cyclone.cloak.forge.ProfileValidator
import java.time.Instant

internal data class ProfileReadiness(val rows: List<Pair<String, String>>, val next: String, val ready: Boolean)

internal fun profileReadiness(state: CloakState, profile: CycloneProfile, appVersion: String, runs: List<BindingRun>): ProfileReadiness {
    val bindings = state.bindings.filter { it.profileId == profile.id }
    val ids = bindings.map { it.cloakProfileId }.distinct()
    val identities = ids.mapNotNull { id -> state.identities.firstOrNull { it.first == id } }
    val findings = identities.flatMap { ProfileValidator.validateProfileResult(it.second).findings }
    val coherent = ids.isNotEmpty() && identities.size == ids.size && findings.none { it.isError }
    val postureReady = coherent && identities.all { (id, value) ->
        val record = state.postures[id]
        record != null && record.state == PostureState.PASS && record.checkedAt != null &&
            record.profileDigest == EngineMetadataStore.digest(value) && state.currentArtifactDigest != null &&
            record.artifactDigest == state.currentArtifactDigest &&
            PostureSummary.freshnessWarnings(record, id, EngineMetadataStore.digest(value), state.currentArtifactDigest, Instant.now()).isEmpty()
    }
    val versionMatch = state.rootDoctor.moduleVersion == appVersion
    val mapped = profile.androidUserId != null && bindings.all { it.androidUserId == profile.androidUserId }
    val latest = runs.filter { run -> run.targets.any { RunPrivacy.reference(it.profileId) == RunPrivacy.reference(profile.id) } }.maxByOrNull { it.timestamp }
    val lastSuccess = runs.filter { run -> !run.active && run.outcome == RunOutcome.READY && run.targets.any {
        RunPrivacy.reference(it.profileId) == RunPrivacy.reference(profile.id) && it.androidUser == profile.androidUserId
    } }.maxByOrNull { it.timestamp }
    val posture = identities.map { (id, value) ->
        val record = state.postures[id]
        val warnings = record?.let { PostureSummary.freshnessWarnings(it, id, EngineMetadataStore.digest(value), state.currentArtifactDigest, Instant.now()) }.orEmpty()
        "${value.optString("name")}: ${PostureSummary.label(record).substringAfter(": ")}" + if (warnings.isNotEmpty()) " · needs fresh report" else ""
    }.joinToString("\n").ifBlank { "Not recorded" }
    val next = when {
        !state.canBind -> state.connection.detail
        profile.isOwner -> "Choose a Cyclone profile; Main cannot be bound."
        !mapped -> "Finish Android user setup in Cyclone, then refresh. Resolve any old user mapping before replacing."
        !profile.bindable -> "Finish or restore this profile in Cyclone, then refresh."
        profile.packages.isNullOrEmpty() -> "Add apps in Cyclone and refresh its app list."
        bindings.isEmpty() -> "Choose Bind all apps and select a compatible device profile."
        !coherent -> "Review the device profile in Identities; fix validation errors or replace it."
        !versionMatch -> "Run Health Doctor to check the installed module against this app."
        !state.rootDoctor.published -> state.rootDoctor.message + " Retry the operation from Run history."
        latest?.outcome != RunOutcome.READY || latest.active -> "Review Run history and retry publication and reconciliation."
        !postureReady -> "Review compatibility findings and import a fresh local report from the device profile in Identities."
        else -> "Review compatibility evidence in Identities before use; local readiness does not prove runtime coverage."
    }
    return ProfileReadiness(listOf(
        "Cyclone connection" to state.connection.label,
        "Android user mapping" to (profile.androidUserId?.let { "$it" + if (!mapped) " · mismatch" else "" } ?: "Missing"),
        "Binding count" to "${bindings.size} · ${bindings.count { it.enabled }} enabled",
        "Module/app version match" to "${if (state.rootDoctor.moduleVersion == null) "Not checked" else if (versionMatch) "Match" else "Mismatch"} · module ${state.rootDoctor.moduleVersion ?: "unknown"} / app $appVersion",
        "Publication state" to state.rootDoctor.title + " · last app-wide check",
        "Compatibility posture" to posture,
        "Latest validation result" to if (ids.isEmpty()) "No device assigned" else if (!coherent) "Failed or profile unavailable" else "Pass · ${findings.count { !it.isError }} findings · checked against saved profile",
        "Last successful lifecycle check" to "Not recorded · lifecycle evidence is unavailable in this console",
        "Last successful binding check" to (lastSuccess?.let { Instant.ofEpochMilli(it.end).toString() } ?: "Not recorded"),
    ), next, coherent && mapped && versionMatch && postureReady && state.canBind && state.connection.phase == ConnectionPhase.CONNECTED &&
        state.rootDoctor.published && latest?.outcome == RunOutcome.READY && !latest.active && bindings.filter { it.enabled }.all { it.state == "ready" })
}

@Composable
internal fun ProfileReadinessCard(facts: ProfileReadiness, enabled: Boolean, onBindAll: () -> Unit, onReplace: () -> Unit, onHistory: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        CloakStatusPill(if (facts.ready) "Local checks ready" else "Needs attention", if (facts.ready) CloakStatusTone.READY else CloakStatusTone.PENDING)
        Text("Next: ${facts.next}", style = MaterialTheme.typography.bodySmall)
        Button(onClick = onBindAll, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text("Bind all apps") }
        CloakGroup {
            CloakDisclosure("Readiness details", supporting = "${facts.rows.size} local checks") {
                facts.rows.forEach { (label, value) ->
                    Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(value, style = MaterialTheme.typography.bodyMedium)
                }
            }
            CloakGroupDivider()
            CloakRow("Replace device profile", onClick = if (enabled) onReplace else null, showChevron = enabled)
            CloakGroupDivider()
            CloakRow("Run history", onClick = onHistory)
        }
    }
}
