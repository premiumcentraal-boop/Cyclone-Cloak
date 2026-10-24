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
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
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
import dev.cyclone.cloak.data.EngineMetadataStore
import dev.cyclone.cloak.forge.RuntimeEvidence

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun IdentitiesScreen(state: CloakState, vm: CloakViewModel) {
    var diagnosticsTarget by rememberSaveable { mutableStateOf<String?>(null) }
    val diagnosticsImporter = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val target = diagnosticsTarget
        if (uri != null && target != null) vm.loadDiagnosticsForPosture(uri, target)
        diagnosticsTarget = null
    }
    var query by rememberSaveable { mutableStateOf("") }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var renaming by remember { mutableStateOf<Identity?>(null) }
    var deleting by remember { mutableStateOf<Identity?>(null) }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(vm::import) }
    val evidenceImporter = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(vm::importEngineEvidence) }
    var postureTarget by remember { mutableStateOf<String?>(null) }
    val postureImporter = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val target = postureTarget
        postureTarget = null
        if (uri != null && target != null) vm.importPostureReport(uri, target)
    }
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
        item { CloakHeroCard("Identities", "Your saved device profiles. Choose one to review or bind.") }
        item {
            Button(onClick = vm::newCompatibleDevice, enabled = state.busy == null,
                modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.small) { Text("New compatible device") }
            TextButton(onClick = vm::openBatchWorkshop, enabled = state.busy == null) { Text("Advanced batch workshop") }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CloakActionButton("Import", Icons.Rounded.FileOpen, { importer.launch(arrayOf("*/*")) }, Modifier.weight(1f), outlined = true)
                CloakActionButton("Back up all", Icons.Rounded.Backup, { exportTarget = null; includeIdentity = false; backupLabel = ""; exporting = true },
                    Modifier.weight(1f), outlined = true, enabled = state.identities.isNotEmpty())
            }
        }

        state.backupMessage?.let { message -> item { Text(message, style = MaterialTheme.typography.bodySmall) } }
        if (state.backups.isNotEmpty()) {
            item { CloakSectionHeader("Local backups", state.backups.size) }
            item {
                CloakGroup {
                    state.backups.forEachIndexed { index, (id, manifest) ->
                        if (index > 0) CloakGroupDivider()
                        CloakRow("Review import: ${manifest.optString("label").ifBlank { manifest.optString("created_at") }}",
                            supporting = "${manifest.optInt("profile_count")} profiles", icon = Icons.Rounded.Backup,
                            onClick = { if (state.busy == null) vm.importLocalBackup(id) })
                    }
                }
            }
        }
        item { CloakSectionHeader("Saved identities", state.identities.size) }
        item { FleetDiversityCard(state.identities.map { it.second }) }
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
                PostureSummaryLine(state.postures[id], Modifier.padding(horizontal = 8.dp))
                if (selected == id) {
                    val profiles = bound.map { it.profileId }.distinct()
                        .map { pid -> state.registry.firstOrNull { it.id == pid }?.label ?: pid }
                    CloakGroup {
                        CloakRow(
                            if (bound.isEmpty()) "Not bound to any app" else "Bound to ${bound.size} apps in ${profiles.joinToString(", ")}",
                            icon = Icons.Rounded.Layers, tone = if (bound.isEmpty()) CloakStatusTone.PENDING else CloakStatusTone.READY,
                        ) {
                            FilledTonalButton(onClick = { vm.startBinding(id) }, contentPadding = PaddingValues(horizontal = 12.dp)) { Text("Bind to profile apps") }
                            CloakOverflowMenu(listOf(
                                CloakMenuAction("Export profile", enabled = state.busy == null) { exportTarget = id; includeIdentity = false; backupLabel = ""; exporting = true },
                                CloakMenuAction("Duplicate", enabled = state.busy == null) { vm.duplicateIdentity(id) },
                                CloakMenuAction("Review identifiers", enabled = state.busy == null) { vm.reviewIdentity(id) },
                                CloakMenuAction("Rename") { renaming = identity },
                                CloakMenuAction("Delete", destructive = true) { deleting = identity },
                            ), contentDescription = "More for ${profile.optString("name")}")
                        }
                        CloakGroupDivider()
                        RealityStudioCard(profile, state.phones, state.busy != null) { vm.saveReality(id, it) }
                        CloakDisclosure("Diagnostics & evidence", supporting = "Local checks, kept apart from the saved identity values") {
                            RealityCoverageCard(profile, state.engineCoverage)
                            CompatibilityReportCard(state.postures[id], id, EngineMetadataStore.digest(profile),
                                state.currentArtifactDigest, state.busy != null, state.postureErrors[id],
                                onLoad = { postureTarget = id; postureImporter.launch(arrayOf("application/json", "text/plain")) },
                                onMarkRotation = { vm.markPostureNeedsRotation(id, it) },
                                onClearReset = { record, confirmed -> vm.clearPostureAfterReset(id, record, confirmed) })
                            val metadata = state.engineMetadata[id]
                            EngineIdentitySummary.labels(profile, metadata).forEach { label ->
                                Text(label, style = MaterialTheme.typography.bodySmall)
                            }
                            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(selected = coverageId == id, onClick = { coverageId = if (coverageId == id) null else id }, label = { Text("Field coverage") })
                                AssistChip(onClick = { evidenceImporter.launch(arrayOf("application/json", "text/plain")) }, enabled = state.busy == null,
                                    label = { Text("Load probe evidence") })
                                AssistChip(onClick = { diagnosticsTarget = id; diagnosticsImporter.launch(arrayOf("application/json", "text/plain")) },
                                    enabled = state.busy == null, label = { Text("Load diagnostics for posture") })
                            }
                            if (coverageId == id) {
                                Text(if (state.engineCoverage.isEmpty()) "Implementation coverage; device evidence not loaded."
                                    else "Probe fixture coverage for the artifact in the loaded report. Saved identity and installed module are not verified by this report.",
                                    style = MaterialTheme.typography.bodySmall)
                                RuntimeEvidence.rows(profile, state.engineCoverage).forEach { row ->
                                    Text("${row.field}: ${row.state} (${row.testStatus})", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    state.diagnosticsReview?.let { review ->
        DiagnosticsPostureDialog(review.preview, state.busy != null, vm::saveDiagnosticsPosture, vm::dismissDiagnosticsPosture,
            state.postureErrors[review.diagnostics.report.profileId])
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
