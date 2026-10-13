package dev.cyclone.cloak.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun DiagnosticsPostureDialog(preview: DiagnosticsPosturePreview, busy: Boolean, onSave: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() }, title = { Text("Preview local diagnostics posture") },
        text = {
            Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Local evidence only; no platform verdict. Probe results describe the measured artifact, not the installed module.")
                Text("Profile: ${preview.report.profileId}")
                Text("Profile SHA-256: ${preview.report.profileDigest.take(12)}…")
                Text("Artifact: ${preview.report.artifactDigest?.take(12)?.plus("…") ?: "not retained"}")
                Text("Measured: ${preview.report.checkedAt}")
                Text(PostureSummary.label(preview.record))
                Text(preview.result.reason)
                preview.warnings.forEach { Text(it, color = MaterialTheme.colorScheme.error) }
                preview.report.checks.forEach { check ->
                    Text("${check.id}: ${check.status.name}", style = MaterialTheme.typography.bodySmall)
                    check.message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onSave, enabled = preview.canSave && !busy) { Text("Save local posture") } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancel") } },
    )
}
