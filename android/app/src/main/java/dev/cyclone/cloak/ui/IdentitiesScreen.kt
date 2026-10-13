package dev.cyclone.cloak.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.cyclone.cloak.forge.CloakFleet
import dev.cyclone.cloak.forge.RuntimeEvidence

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun IdentitiesScreen(state: CloakState, vm: CloakViewModel) {
    var phoneId by rememberSaveable { mutableStateOf<String?>(null) }
    val phone = state.phone(phoneId) ?: state.phones.firstOrNull()
    var name by rememberSaveable { mutableStateOf("") }
    var count by rememberSaveable { mutableIntStateOf(5) }
    var query by rememberSaveable { mutableStateOf("") }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var renaming by remember { mutableStateOf<Identity?>(null) }
    var deleting by remember { mutableStateOf<Identity?>(null) }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(vm::import) }
    val evidenceImporter = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(vm::importEngineEvidence) }
    var coverageId by rememberSaveable { mutableStateOf<String?>(null) }
    var exportTarget by remember { mutableStateOf<String?>(null) }
    var exporting by remember { mutableStateOf(false) }
    var includeIdentity by rememberSaveable { mutableStateOf(false) }
    var backupLabel by rememberSaveable { mutableStateOf("") }


    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            ) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("New identity", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "A copy of a phone with its own fresh identifiers. They are generated, never read from your own phone.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text("Phone", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.phones.forEach { option ->
                            FilterChip(selected = option.id == phone?.id, onClick = { phoneId = option.id }, label = { Text(option.label) })
                        }
                    }
                    phone?.let { Text(it.summary, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    OutlinedTextField(
                        value = name,
                        onValueChange = { if (it.length <= 64) name = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Name") },
                        placeholder = { Text("For example, Work Pixel") },
                        singleLine = true,
                    )
                    Button(
                        onClick = { phone?.let { vm.createIdentity(name, it.id); name = "" } },
                        enabled = name.isNotBlank() && phone != null && state.busy == null,
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.small,
                    ) { Text("Create identity") }
                    HorizontalDivider()
                    Text("A fleet", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Several identities from the same phone, named ${CloakFleet.FLEET_PREFIX} 01, 02, … Making the same fleet again gives the same identities.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(onClick = { count = (count - 1).coerceIn(1, CloakFleet.MAX_FLEET_SIZE) }, shape = MaterialTheme.shapes.small) {
                            Icon(Icons.Rounded.Remove, contentDescription = "Fewer")
                        }
                        Text("$count", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                        OutlinedButton(onClick = { count = (count + 1).coerceIn(1, CloakFleet.MAX_FLEET_SIZE) }, shape = MaterialTheme.shapes.small) {
                            Icon(Icons.Rounded.Add, contentDescription = "More")
                        }
                    }
                    OutlinedButton(
                        onClick = { phone?.let { vm.forgeFleet(it.id, count) } },
                        enabled = phone != null && state.busy == null,
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.small,
                    ) { Text("Create $count from ${phone?.label ?: "a phone"}") }
                }
            }
        }

        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { importer.launch(arrayOf("*/*")) }, modifier = Modifier.weight(1f), shape = MaterialTheme.shapes.small) {
                    Text("Import")
                }
                OutlinedButton(
                    onClick = { run { exportTarget = null; includeIdentity = false; backupLabel = ""; exporting = true } },
                    enabled = state.identities.isNotEmpty(),
                    modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.small,
                ) { Text("Back up all") }
            }
        }

        state.backupMessage?.let { message -> item { Text(message, style = MaterialTheme.typography.bodySmall) } }
        if (state.backups.isNotEmpty()) {
            item { CloakSectionTitle("Local backups", state.backups.size) }
            items(state.backups, key = { "backup-" + it.first }) { (id, manifest) ->
                OutlinedButton(onClick = { vm.importLocalBackup(id) }, enabled = state.busy == null) {
                    Text("Review import: ${manifest.optString("label").ifBlank { manifest.optString("created_at") }} · ${manifest.optInt("profile_count")} profiles")
                }
            }
        }
        item { CloakSectionTitle("Identities", state.identities.size) }
        if (state.identities.size > 6) {
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Search") },
                    singleLine = true,
                )
            }
        }
        val shown = state.identities.filter { (_, p) ->
            query.isBlank() || listOf(p.optString("name"), p.optJSONObject("device")?.optString("model").orEmpty())
                .any { it.contains(query.trim(), ignoreCase = true) }
        }
        if (state.identities.isEmpty()) {
            item {
                CloakEmptyState(
                    title = "No identities yet",
                    body = "Create one above, or import an identity or a fleet file.",
                    icon = Icons.Rounded.Add,
                )
            }
        }
        items(shown, key = { it.first }) { identity ->
            val (id, profile) = identity
            val bound = state.bindingsOf(id)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                CloakIdentityCard(id = id, profile = profile, selected = selected == id, onSelect = { selected = if (selected == id) null else id })
                if (selected == id) {
                    val metadata = state.engineMetadata[id]
                    EngineIdentitySummary.labels(profile, metadata).forEach { label ->
                        Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 8.dp))
                    }
                    val profiles = bound.map { it.profileId }.distinct()
                        .map { pid -> state.registry.firstOrNull { it.id == pid }?.label ?: pid }
                    Text(
                        if (bound.isEmpty()) "Not bound to any app" else "Bound to ${bound.size} apps in ${profiles.joinToString(", ")}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                    FlowRow(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
                        TextButton(onClick = { coverageId = if (coverageId == id) null else id }) { Text("Field coverage") }
                        TextButton(onClick = { evidenceImporter.launch(arrayOf("application/json", "text/plain")) }, enabled = state.busy == null) { Text("Load probe evidence") }
                        TextButton(onClick = { vm.startBinding(id) }) { Text("Bind to profile apps") }
                        TextButton(onClick = { exportTarget = id; includeIdentity = false; backupLabel = ""; exporting = true }, enabled = state.busy == null) { Text("Export profile") }
                        TextButton(onClick = { vm.duplicateIdentity(id) }, enabled = state.busy == null) { Text("Duplicate") }
                        TextButton(onClick = { vm.reviewIdentity(id) }, enabled = state.busy == null) { Text("Review identifiers") }
                        TextButton(onClick = { renaming = identity }) { Text("Rename") }
                        TextButton(onClick = { deleting = identity }) { Text("Delete") }
                    }
                    if (coverageId == id) {
                        Text(if (state.engineCoverage.isEmpty()) "Implementation coverage; device evidence not loaded."
                            else "Probe fixture coverage for the artifact in the loaded report. Saved identity and installed module are not verified by this report.",
                            style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 8.dp))
                        RuntimeEvidence.rows(profile, state.engineCoverage).forEach { row ->
                            Text("${row.field}: ${row.state} (${row.testStatus})", style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(horizontal = 8.dp))
                        }
                    }
                }
            }
        }
    }

    renaming?.let { (id, profile) ->
        NameDialog(
            title = "Rename identity",
            hint = "New name",
            confirm = "Rename",
            initial = profile.optString("name"),
            onConfirm = { vm.renameIdentity(id, it); renaming = null },
            onDismiss = { renaming = null },
        )
    }
    deleting?.let { (id, profile) ->
        val bound = state.bindingsOf(id).size
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete ${profile.optString("name")}?") },
            text = {
                Text(
                    if (bound > 0) "It's bound to $bound apps. Remove those bindings in Profiles first."
                    else "Its identifiers are gone for good. Export it first if you may want it back.",
                )
            },
            confirmButton = {
                TextButton(onClick = { vm.deleteIdentity(id); deleting = null }, enabled = bound == 0) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
    if (exporting) AlertDialog(
        onDismissRequest = { exporting = false }, title = { Text(if (exportTarget == null) "Back up profile library" else "Export profile") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Save a complete local file in Downloads/CycloneCloak and Cloak's backup library. Binding state, Cyclone IDs and publication data are excluded.")
            OutlinedTextField(value = backupLabel, onValueChange = { backupLabel = it.take(128) }, label = { Text("Optional backup label") })
            Row { androidx.compose.material3.Checkbox(checked = includeIdentity, onCheckedChange = { includeIdentity = it })
                Text("Include sensitive identity values and seed", modifier = Modifier.padding(top = 12.dp)) }
            Text(if (includeIdentity) "This backup can reproduce the saved identity values. Keep it private."
                else "Identity values are excluded. Import creates fresh identifiers and a seed.")
        } },
        confirmButton = { TextButton(onClick = { vm.exportProfiles(exportTarget, includeIdentity, backupLabel); exporting = false }, enabled = state.busy == null) { Text("Save local backup") } },
        dismissButton = { TextButton(onClick = { exporting = false }) { Text("Cancel") } },
    )

}
