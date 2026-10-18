package dev.cyclone.cloak.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.cyclone.cloak.forge.BatchIdentityPlanner
import dev.cyclone.cloak.forge.BatchIdentitySave
import dev.cyclone.cloak.forge.PhoneTemplate

data class BatchWorkshopState(val plan: BatchIdentityPlanner.Plan? = null,
    val results: List<BatchIdentitySave.Item> = emptyList(), val message: String? = null)

@Composable
internal fun BatchIdentityWorkshop(state: BatchWorkshopState, phones: List<PhoneTemplate>, busy: Boolean,
    onPreview: (List<String>, Int, String) -> Unit, onRegenerate: (Set<String>) -> Unit,
    onRemove: (Set<String>) -> Unit, onSave: (Set<String>) -> Unit,
    onImport: (android.net.Uri) -> Unit, onExport: (android.net.Uri) -> Unit, onClose: () -> Unit) {
    var templates by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var count by rememberSaveable { mutableStateOf("2") }
    var prefix by rememberSaveable { mutableStateOf("") }
    var selected by remember(state.plan) { mutableStateOf(emptySet<String>()) }
    var details by remember { mutableStateOf<BatchIdentityPlanner.Draft?>(null) }
    var confirmation by remember { mutableStateOf<String?>(null) }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(onImport) }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { it?.let(onExport) }
    val close = { if (!busy) { if (state.plan != null) confirmation = "close" else onClose() } }
    BackHandler { close() }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            TextButton(onClick = close, enabled = !busy) { Text("Back") }
            Text("Batch Identity Workshop", style = MaterialTheme.typography.headlineSmall)
            Text("Preview local drafts before saving. No bindings are created.")
            if (busy) Text("Working locally…")
            state.message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        }
        if (state.plan == null) {
            item { Text("Base templates", style = MaterialTheme.typography.titleMedium) }
            items(phones, key = { it.id }) { phone ->
                Row {
                    Checkbox(phone.id in templates, { checked -> templates = if (checked) templates + phone.id else templates - phone.id }, enabled = !busy)
                    Text(phone.label, Modifier.padding(top = 12.dp))
                }
            }
            item {
                OutlinedTextField(count, { count = it }, label = { Text("Drafts per template (1–20)") },
                    isError = count.toIntOrNull() !in 1..20, singleLine = true, enabled = !busy)
                OutlinedTextField(prefix, { prefix = it }, label = { Text("Optional label prefix") }, singleLine = true, enabled = !busy)
                Text("Up to 100 drafts total. Validation and local uniqueness checks run before review.")
                Button(onClick = { onPreview(templates, count.toInt(), prefix) },
                    enabled = !busy && templates.isNotEmpty() && count.toIntOrNull() in 1..20 && templates.size * (count.toIntOrNull() ?: 0) <= 100) { Text("Preview drafts") }
                TextButton(onClick = { importer.launch(arrayOf("application/json", "text/plain")) }, enabled = !busy) { Text("Import batch plan") }
            }
        } else {
            val plan = state.plan
            val summary = plan.summary
            val savedKeys = state.results.filter { it.status == "SAVED" }.map { it.key }.toSet()
            item {
                CloakGroup {
                    Column(Modifier.padding(12.dp)) {
                        Text("Batch review", style = MaterialTheme.typography.titleMedium)
                        Text("Total drafts: ${summary.getInt("total")}")
                        Text("Schema/coherence: ${summary.getInt("validation_pass")} pass, ${summary.getInt("validation_fail")} fail")
                        Text("Uniqueness (library + batch): ${summary.getInt("unique")} clear, ${summary.getInt("conflicting")} conflicting")
                        Text("Uniqueness not checked: ${summary.getInt("uniqueness_not_checked")}")
                        Text("Engine 1 · schema ${summary.getString("schema_version")} · planner ${BatchIdentityPlanner.VERSION}")
                        Text("Warnings: ${summary.getInt("warnings")}. Review draft details before approving.")
                        Text("Local diversity: ${if (summary.isNull("diversity_score")) "not enough comparable facts" else "${summary.getInt("diversity_score")}/100"} · policy ${plan.request.diversityPolicy}")
                        val repeats = summary.getInt("repeated_reality_clusters")
                        if (repeats > 0) Text("$repeats repeated reality clusters. More drafts than distinct selected hardware/firmware combinations; reseeding cannot change fixed hardware.", color = MaterialTheme.colorScheme.error)
                        plan.drafts.groupingBy { it.templateName }.eachCount().forEach { (name, n) -> Text("$name: $n") }
                    }
                }
                Row {
                    Checkbox(selected.size == plan.drafts.size && selected.isNotEmpty(), { checked ->
                        selected = if (checked) plan.drafts.map { it.key }.toSet() else emptySet()
                    }, enabled = !busy)
                    Text("Select all drafts", Modifier.padding(top = 12.dp))
                }
                TextButton(onClick = { onRegenerate(selected - savedKeys) }, enabled = !busy && (selected - savedKeys).isNotEmpty()) { Text("Regenerate selected") }
                TextButton(onClick = { confirmation = "remove" }, enabled = !busy && selected.isNotEmpty()) { Text("Remove selected") }
            }
            items(plan.drafts, key = { it.key }) { draft ->
                CloakGroup {
                    Column(Modifier.padding(12.dp)) {
                        Row {
                            Checkbox(draft.key in selected, { checked -> selected = if (checked) selected + draft.key else selected - draft.key }, enabled = !busy)
                            Column {
                                Text(draft.label, style = MaterialTheme.typography.titleSmall)
                                Text(draft.templateName)
                            }
                        }
                        Text("Validation: ${if (draft.valid) "pass" else "failed"} · Uniqueness: ${if (draft.profile == null) "not checked" else if (draft.unique) "clear" else "conflict"}")
                        state.results.firstOrNull { it.key == draft.key }?.let { Text("${it.status}: ${it.reason}") }
                        TextButton(onClick = { details = draft }) { Text("Review details") }
                    }
                }
            }
            item {
                Text("Selecting a draft is approval after reviewing its details. Conflicting or invalid drafts cannot be saved.")
                Button(onClick = { confirmation = "save" }, enabled = !busy && plan.drafts.any { it.key in selected && it.key !in savedKeys && it.approved }) { Text("Save approved drafts") }
                TextButton(onClick = { confirmation = "export" }, enabled = !busy && plan.drafts.isNotEmpty()) { Text("Export batch plan") }
                if (state.results.isNotEmpty()) Text("Saved ${state.results.count { it.status == "SAVED" }} · Failed ${state.results.count { it.status == "FAILED" }} · Skipped ${state.results.count { it.status == "SKIPPED" }}")
            }
        }
    }
    details?.let { draft ->
        AlertDialog(onDismissRequest = { details = null }, title = { Text("Private draft details") },
            text = { Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("${draft.label} · attempt ${draft.attempts} · revision ${draft.revision}")
                FindingsList(draft.findings)
                Text(draft.profile?.toString(2) ?: "No valid profile was generated.")
            } }, confirmButton = { TextButton(onClick = { details = null }) { Text("Close") } })
    }
    confirmation?.let { action ->
        AlertDialog(onDismissRequest = { confirmation = null }, title = { Text(when (action) {
            "save" -> "Save approved drafts?"; "export" -> "Export private batch plan?"; "remove" -> "Remove selected drafts?"; else -> "Leave workshop?"
        }) }, text = { Text(when (action) {
            "save" -> "Only selected valid, unique drafts will be added locally. Existing identities and bindings will not be replaced. Partial results are shown per item."
            "export" -> "The plan includes private seeds that can reproduce identifiers. Keep this local file private. Profile bodies and raw identifier values are excluded."
            "remove" -> "This removes drafts from the preview only. Saved identities are preserved."
            else -> "Unsaved drafts will be discarded. Saved identities are preserved."
        }) }, confirmButton = { TextButton(onClick = {
            confirmation = null
            when (action) { "save" -> onSave(selected); "export" -> exporter.launch("cloak-batch-plan.json"); "remove" -> onRemove(selected); else -> onClose() }
        }, enabled = !busy) { Text("Confirm") } }, dismissButton = { TextButton(onClick = { confirmation = null }) { Text("Cancel") } })
    }
}
