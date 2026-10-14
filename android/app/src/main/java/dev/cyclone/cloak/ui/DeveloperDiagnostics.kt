package dev.cyclone.cloak.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.cyclone.cloak.diagnostics.DiagnosticEvent
import dev.cyclone.cloak.diagnostics.LogBus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class DiagnosticFilter(val subsystem: String = "", val severity: String = "", val action: String = "", val text: String = "") {
    fun accepts(event: DiagnosticEvent): Boolean = event.subsystem.contains(subsystem, true) &&
        event.severity.name.contains(severity, true) && event.actionId.contains(action, true) &&
        event.json().toString().contains(text, true)
}

@Composable
internal fun DeveloperDiagnosticsEntry(moduleVersion: String?) {
    var open by remember { mutableStateOf(false) }
    TextButton(onClick = { open = true }) { Text("Developer diagnostics") }
    if (open) DeveloperDiagnosticsPanel(moduleVersion) { open = false }
}

@Composable
internal fun DeveloperDiagnosticsPanel(moduleVersion: String?, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val events by LogBus.registry.recentEvents.collectAsState()
    var filter by remember { mutableStateOf(DiagnosticFilter()) }
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    @Suppress("DEPRECATION")
    val appVersion = remember(context) { context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "unknown" }
    fun report() = LogBus.registry.bundle(appVersion, moduleVersion, Build.VERSION.SDK_INT,
        Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown")
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri == null) { status = "Save cancelled"; return@rememberLauncherForActivityResult }
        scope.launch {
            busy = true
            val result = withContext(Dispatchers.IO) { runCatching {
                val bytes = LogBus.registry.zip(report())
                checkNotNull(context.contentResolver.openOutputStream(uri)).use { it.write(bytes) }
            } }
            status = if (result.isSuccess) "Redacted diagnostics bundle saved" else "Could not save diagnostics. Choose a writable location and retry."
            busy = false
        }
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Developer diagnostics")
            Text("Local, bounded evidence. No profile contents or platform verdicts.")
            OutlinedTextField(filter.subsystem, { filter = filter.copy(subsystem = it) }, label = { Text("Subsystem") }, singleLine = true)
            OutlinedTextField(filter.severity, { filter = filter.copy(severity = it) }, label = { Text("Severity (INFO, WARNING, ERROR)") }, singleLine = true)
            OutlinedTextField(filter.action, { filter = filter.copy(action = it) }, label = { Text("Action id") }, singleLine = true)
            OutlinedTextField(filter.text, { filter = filter.copy(text = it) }, label = { Text("Text") }, singleLine = true)
            val visible = events.filter(filter::accepts).asReversed()
            Text("${visible.size} events · ${LogBus.registry.dropped.get()} dropped")
            LazyColumn(Modifier.fillMaxWidth().height(180.dp)) {
                if (visible.isEmpty()) item { Text("No matching events") }
                items(visible) { event ->
                    Text("${java.time.Instant.ofEpochMilli(event.timestamp)} ${event.severity} ${event.subsystem}\n" +
                        "${event.actionId} ${event.transition} ${event.reason.orEmpty()} ${event.errorSummary.orEmpty()}" +
                        (event.durationMs?.let { " ${it}ms" } ?: ""))
                }
            }
            TextButton(enabled = !busy, onClick = {
                scope.launch {
                    busy = true
                    val json = withContext(Dispatchers.IO) { runCatching { report().toString(2) }.getOrNull() }
                    val copied = json != null && runCatching {
                        context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Cloak diagnostics", json))
                    }.isSuccess
                    status = if (copied) "Redacted diagnostics copied" else "Could not copy diagnostics. Retry or save a bundle."
                    busy = false
                }
            }) { Text("Copy diagnostics") }
            TextButton(enabled = !busy, onClick = { save.launch("cloak-diagnostics-${System.currentTimeMillis()}.zip") }) { Text("Save diagnostics bundle") }
            status?.let { Text(it) }
            TextButton(onClick = onClose) { Text("Close") }
    }
}
