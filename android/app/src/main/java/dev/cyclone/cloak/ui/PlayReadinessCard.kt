package dev.cyclone.cloak.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.cyclone.cloak.play.PlayImportPreview
import dev.cyclone.cloak.play.PlayReadinessCardState
import dev.cyclone.cloak.play.ServiceSurfaceIsolation

@Composable
internal fun PlayReadinessCard(state: PlayReadinessCardState, choices: List<Pair<String, String>>, enabled: Boolean,
    error: String?, profileLabel: (String) -> String, onSelect: (String) -> Unit, onImport: () -> Unit,
    onImportIntegrity: (() -> Unit)? = null) {
    var choosing by remember { mutableStateOf(false) }
    var details by remember { mutableStateOf(false) }
    OutlinedCard(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Play/Google local readiness · ${ServiceSurfaceIsolation.gate(state.verdict, state.serviceIsolation.values.toList())}", style = MaterialTheme.typography.titleSmall)
            Text("Evidence-bound local readiness. Authenticated verdicts are separate evidence, not platform certification.", style = MaterialTheme.typography.bodySmall)
            Text("Device profile: ${state.identityLabel ?: "not selected"}")
            TextButton(enabled = enabled && choices.isNotEmpty(), onClick = { choosing = true }) { Text("Choose evidence device profile") }
            DropdownMenu(expanded = choosing, onDismissRequest = { choosing = false }) {
                choices.forEach { (id, label) -> DropdownMenuItem(text = { Text(label) }, onClick = { choosing = false; onSelect(id) }) }
            }
            Text("Checked: ${state.timestamp ?: "not recorded"}", style = MaterialTheme.typography.bodySmall)
            Text("Source: ${state.source}", style = MaterialTheme.typography.bodySmall)
            state.details.forEach { (label, value) -> Text("$label: $value", style = MaterialTheme.typography.bodySmall) }
            Text("Service isolation: local evidence only", style = MaterialTheme.typography.bodySmall)
            if (state.serviceIsolation.isEmpty()) Text("Service isolation: UNKNOWN · no app-scoped evidence", style = MaterialTheme.typography.bodySmall)
            state.serviceIsolation.forEach { (packageName, isolation) ->
                Text("$packageName: ${isolation.state.name}", style = MaterialTheme.typography.bodySmall)
                isolation.messages.forEach { Text(it, style = MaterialTheme.typography.bodySmall,
                    color = if (isolation.permitsReady) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error) }
            }
            Text("Play Integrity: ${state.integrity.state.name.replace('_', ' ')}", style = MaterialTheme.typography.bodySmall)
            Text("${state.integrity.description} (${state.integrity.reason})", style = MaterialTheme.typography.bodySmall)
            state.integrity.verdict?.let { verdict ->
                Text("Provider: ${verdict.provider} · decoded ${verdict.decodedAt}", style = MaterialTheme.typography.bodySmall)
                Text("App: ${verdict.appRecognition} · licensing: ${verdict.appLicensing}", style = MaterialTheme.typography.bodySmall)
                Text("Device labels: ${verdict.deviceRecognition.sorted().joinToString().ifEmpty { "none returned" }}", style = MaterialTheme.typography.bodySmall)
            }
            Text("Account visibility does not verify slot ownership or cross-user isolation.", style = MaterialTheme.typography.bodySmall)
            state.reasons.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            if (state.sharedProfiles.isNotEmpty()) {
                Text(if (state.sharingConfirmed) "Shared account slot: explicitly confirmed" else "Account-slot conflict: shared use needs confirmation",
                    color = if (state.sharingConfirmed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                Text(state.sharedProfiles.joinToString(", ", transform = profileLabel), style = MaterialTheme.typography.bodySmall)
                if (!state.sharingConfirmed) Text("Import and review a matching report to confirm this exact sharing group.", style = MaterialTheme.typography.bodySmall)
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            TextButton(onClick = { details = !details }) { Text(if (details) "Hide report binding details" else "Report binding details") }
            if (details) {
                Text("Saved file SHA-256: ${state.profileHash ?: "unavailable"}", style = MaterialTheme.typography.bodySmall)
                Text("App + bundled module artifact SHA-256: ${state.artifactHash ?: "unavailable"}", style = MaterialTheme.typography.bodySmall)
                Text("This digest does not independently verify the installed runtime module.", style = MaterialTheme.typography.bodySmall)
            }
            TextButton(enabled = enabled && state.contextAvailable, onClick = onImport) { Text("Import diagnostics report") }
            onImportIntegrity?.let { action -> TextButton(enabled = enabled && state.contextAvailable, onClick = action) { Text("Import authenticated verdict") } }
        }
    }
}

@Composable
internal fun PlayEvidenceReviewDialog(review: PlayImportPreview, enabled: Boolean, error: String?,
    profileLabel: (String) -> String, onSave: (Boolean) -> Unit, onDismiss: () -> Unit) {
    var sharing by remember(review.stamp) { mutableStateOf(false) }
    if (sharing) {
        AlertDialog(onDismissRequest = { if (enabled) sharing = false }, title = { Text("Confirm shared account-slot metadata") },
            text = { Text("Allow this exact local account slot to be shared by: ${review.sharedProfiles.joinToString(", ", transform = profileLabel)}. This records consent only; it does not change Google accounts or Android access.") },
            confirmButton = { TextButton(enabled = enabled, onClick = { onSave(true) }) { Text("Confirm shared use and save") } },
            dismissButton = { TextButton(enabled = enabled, onClick = { sharing = false }) { Text("Back to report") } })
    } else AlertDialog(onDismissRequest = { if (enabled) onDismiss() }, title = { Text("Review local Play/Google evidence") },
        text = {
            Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Profile: ${profileLabel(review.target.profileId)} · user ${review.target.androidUserId}")
                Text("Preview: ${review.result.state.name}")
                Text("Source: local diagnostics · ${org.json.JSONObject(review.projection).getString("timestamp")}")
                Text("Missing required evidence stays UNKNOWN. Play Protect and actual Google settings access are not measured by this harness.")
                review.result.findings.forEach { Text("${it.field}: ${it.message} (${it.code})") }
                review.rowNotes.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                if (review.sharedProfiles.isNotEmpty() && !review.sharingConfirmed) Text("Shared account-slot confirmation is required before saving.", color = MaterialTheme.colorScheme.error)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Text("Saving writes only local readiness evidence and approved assignment metadata. No binding or account changes.")
            }
        },
        confirmButton = { TextButton(enabled = enabled && review.result.accepted, onClick = {
            if (review.sharedProfiles.isNotEmpty() && !review.sharingConfirmed) sharing = true else onSave(false)
        }) { Text("Save local evidence") } },
        dismissButton = { TextButton(enabled = enabled, onClick = onDismiss) { Text("Cancel") } })
}
