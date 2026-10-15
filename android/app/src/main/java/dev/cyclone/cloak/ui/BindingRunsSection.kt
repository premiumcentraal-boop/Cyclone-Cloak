package dev.cyclone.cloak.ui

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.cyclone.cloak.data.CloakBindingStore
import dev.cyclone.cloak.diagnostics.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant

@Composable
internal fun BindingRunsSection(state: CloakState, onRetry: (BindingRun) -> Unit) {
    val context = LocalContext.current
    val journal = remember(context) { BindingRuns.journal(context) }
    val runs by journal.runs.collectAsState()
    val failures by journal.failures.collectAsState()
    val scope = rememberCoroutineScope()
    var exporting by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    @Suppress("DEPRECATION")
    val appVersion = remember(context) { context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "unknown" }
    fun export(run: BindingRun?) {
        if (exporting) return
        exporting = true
        scope.launch {
            val result = withContext(Dispatchers.IO) { runCatching {
                val now = System.currentTimeMillis()
                LogBus.registry.flush()
                val files = BindingRunBundle.build(run?.let { listOf(it) } ?: runs, LogBus.registry.events(), CloakBindingStore.all(context),
                    state.rootDoctor, state.report, appVersion, run?.timestamp ?: (now - 86_400_000),
                    run?.takeUnless { it.active }?.end ?: now, now, failures)
                RunBundleShare.prepare(context, BindingRunBundle.zip(files))
            } }
            message = if (result.isSuccess) {
                if (runCatching { context.startActivity(Intent.createChooser(result.getOrThrow(), "Save or share binding diagnostics")) }.isSuccess)
                    "Redacted bundle ready. Choose an app to save or share it."
                else "No sharing app is available. Install or enable a local file manager and retry."
            } else "Could not create the debug bundle. Check available storage and retry."
            exporting = false
        }
    }
    CloakSectionHeader("Runs", runs.size)
    Text("Local binding history · newest first · up to 200 runs. Bundles contain redacted evidence, not device profile contents.",
        style = MaterialTheme.typography.bodySmall)
    if (failures > 0) Text("Some run evidence could not be stored. History may be incomplete ($failures storage errors).",
        color = MaterialTheme.colorScheme.error)
    TextButton(enabled = !exporting, onClick = { export(null) }) { Text("Download debug bundle · last 24h") }
    message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    if (runs.isEmpty()) Text("No binding runs recorded yet.")
    else LazyColumn(Modifier.fillMaxWidth().height(360.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(runs.asReversed(), key = { it.id }) { run -> BindingRunCard(run, !exporting && state.busy == null,
            { onRetry(run) }, { export(run) }) }
    }
}

@Composable
internal fun BindingRunCard(run: BindingRun, enabled: Boolean, onRetry: () -> Unit, onExport: () -> Unit) {
    var expanded by remember(run.id) { mutableStateOf(false) }
    val color = when (run.outcome) {
        RunOutcome.READY -> MaterialTheme.colorScheme.surfaceVariant
        RunOutcome.FAILED -> MaterialTheme.colorScheme.errorContainer
        RunOutcome.NEEDS_ATTENTION -> MaterialTheme.colorScheme.tertiaryContainer
    }
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = color)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("${run.operation.name.lowercase().replace('_', ' ')} · ${if (run.active) "in progress" else run.outcome.label}", style = MaterialTheme.typography.titleSmall)
            Text("Started ${Instant.ofEpochMilli(run.timestamp)} · " + if (run.active) "not finished" else
                "Ended ${Instant.ofEpochMilli(run.end)} · ${run.durationMs} ms", style = MaterialTheme.typography.bodySmall)
            Text(BindingRunFeedback.message(run), style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Hide run details" else "Review run details") }
            if (expanded) {
                Text("Run ${run.id}")
                Text("App: ${run.appVersion ?: "not recorded"} · module: ${run.moduleVersion ?: "not recorded"}")
                run.targets.forEach { target ->
                    Text("Profile: ${target.profileId}\nAndroid user: ${target.androidUser ?: "not assigned"}\n" +
                        "Apps: ${target.packages.joinToString()}\nDevice profile: ${target.deviceProfileId}")
                }
                Text("Reasons: ${run.reasons.joinToString().ifBlank { "none" }}")
                run.reconciliation?.let { Text("Reconciliation: ${it.written} written · ${it.cleared} cleared · ${it.issues} issues") }
                val phases = run.json().getJSONArray("publication_phases")
                Text("Publication phases: " + if (phases.length() == 0) "not observed" else (0 until phases.length()).joinToString("\n") {
                    phases.getJSONObject(it).let { phase -> "${phase.getString("phase")}: ${phase.getString("state")}" }
                })
                if (run.eventsDropped > 0) Text("${run.eventsDropped} events exceeded this run's retention limit.")
            }
            if (!run.active && run.outcome != RunOutcome.READY) TextButton(enabled = enabled, onClick = onRetry) { Text("Retry reconciliation") }
            TextButton(enabled = enabled, onClick = onExport) { Text("Download debug bundle") }
        }
    }
}
