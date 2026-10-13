package dev.cyclone.cloak.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.cyclone.cloak.data.PostureRecord
import dev.cyclone.cloak.data.PostureState
import java.time.Instant

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CompatibilityReportCard(record: PostureRecord?, profileId: String, profileDigest: String,
    artifactDigest: String?, busy: Boolean, error: String?, now: Instant = Instant.now(),
    onLoad: () -> Unit, onMarkRotation: (PostureRecord?) -> Unit, onClearReset: (PostureRecord, Boolean) -> Unit) {
    var action by rememberSaveable(profileId) { mutableStateOf<String?>(null) }
    var confirmedRecord by remember(profileId) { mutableStateOf<PostureRecord?>(null) }
    var resetCompleted by rememberSaveable(profileId) { mutableStateOf(false) }
    val details = PostureSummary.details(record, profileId, profileDigest, artifactDigest, now)
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Local compatibility report", style = MaterialTheme.typography.titleSmall)
            details.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
            Text("Local compatibility evidence only; no platform verdict.", style = MaterialTheme.typography.bodySmall)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            FlowRow {
                TextButton(onClick = onLoad, enabled = !busy) { Text("Load compatibility report") }
                TextButton(onClick = { action = "details" }, enabled = !busy) { Text("Review report details") }
                TextButton(onClick = { confirmedRecord = record; action = "mark" }, enabled = !busy) { Text("Mark needs rotation") }
                TextButton(onClick = { confirmedRecord = record; resetCompleted = false; action = "clear" },
                    enabled = !busy && record?.state == PostureState.NEEDS_ROTATION) { Text("Clear after explicit local reset") }
            }
        }
    }
    if (action == "details") AlertDialog(onDismissRequest = { action = null }, title = { Text("Compatibility report details") },
        text = { Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(PostureSummary.label(record))
            details.forEach { Text(it) }
            Text(record?.reason ?: "No readable local report is recorded. Load a compatibility report to review evidence.")
            record?.providerId?.let { Text("Provider: $it") }
            Text("This is the saved report summary. Individual check rows are available in the import preview; they are not stored here.")
            Text("Artifact matching concerns the bundled payload, not the installed module. Local evidence only; no platform verdict.")
        } }, confirmButton = { TextButton(onClick = { action = null }) { Text("Close") } })
    if (action == "mark" || action == "clear") {
        val clear = action == "clear"
        val changed = record != confirmedRecord
        AlertDialog(onDismissRequest = { if (!busy) action = null },
            title = { Text(if (clear) "Clear rotation state?" else "Mark this identity as needs rotation?") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (clear) "This clears the local evidence state to Unknown. It does not reset or reseed the identity. Previous evidence is not a new check."
                    else "This marks the local record as Needs rotation. It does not rotate identifiers, change bindings, or reset the identity.")
                if (clear) Row {
                    Checkbox(checked = resetCompleted, onCheckedChange = { resetCompleted = it }, enabled = !busy)
                    Text("I have completed an explicit local reset or reseed.")
                }
                if (changed) Text("The saved report changed. Cancel and review it again before continuing.", color = MaterialTheme.colorScheme.error)
            } },
            confirmButton = { TextButton(enabled = !busy && !changed && (!clear || resetCompleted), onClick = {
                if (clear) confirmedRecord?.let { onClearReset(it, resetCompleted) } else onMarkRotation(confirmedRecord)
                action = null
            }) { Text(if (clear) "Confirm completed reset" else "Confirm needs rotation") } },
            dismissButton = { TextButton(onClick = { action = null }, enabled = !busy) { Text("Cancel") } })
    }
}
