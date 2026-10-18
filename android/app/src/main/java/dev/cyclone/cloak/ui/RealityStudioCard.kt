package dev.cyclone.cloak.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import dev.cyclone.cloak.data.EngineMetadataStore
import dev.cyclone.cloak.forge.DeviceReality
import dev.cyclone.cloak.forge.FleetDiversity
import dev.cyclone.cloak.forge.PhoneTemplate
import dev.cyclone.cloak.forge.RealityStudio
import org.json.JSONObject

@Composable
internal fun RealityStudioCard(profile: JSONObject, phones: List<PhoneTemplate>, busy: Boolean,
    onSave: (RealityStudio.Preview) -> Unit) {
    val hash = EngineMetadataStore.digest(profile)
    val session = remember(hash) { RealityStudio.Session(profile) }
    var revision by remember(hash) { mutableIntStateOf(0) }
    var message by remember(hash) { mutableStateOf<String?>(null) }
    var editing by remember(hash) { mutableStateOf<String?>(null) }
    var input by remember(hash) { mutableStateOf("") }
    var reveal by remember(hash) { mutableStateOf(false) }
    var removeValue by remember(hash) { mutableStateOf(false) }
    var review by remember(hash) { mutableStateOf(false) }
    val body = remember(hash, revision) { session.body }
    val preview = remember(hash, revision) { session.preview() }
    val compatible = phones.filter { RealityStudio.compatible(profile, it) }
    var templateId by remember(hash) { mutableStateOf(compatible.firstOrNull()?.id) }
    val phone = compatible.firstOrNull { it.id == templateId }
    fun act(block: () -> RealityStudio.Preview) {
        try {
            val result = block()
            message = if (!result.valid) result.findings.filter { it.isError }.joinToString("\n") { "${it.field}: ${it.code}: ${it.message}" }
                else if (result.changes.isEmpty()) "No supported changes. Unknown or unavailable facts were not invented."
                else "Draft updated. Review all changed paths before saving."
            revision++
        } catch (error: IllegalArgumentException) { message = error.message ?: "Invalid Reality edit." }
    }
    CloakDisclosure("Reality Studio", supporting = "Local draft editor · no runtime or binding changes") {
        if (profile.optString("schema_version") != "0.4") {
            Text("Schema ${profile.optString("schema_version")}: read-only. Existing identities are never migrated automatically.")
        } else {
            Text("Locks apply while this editor is open. Closing discards unsaved drafts and locks. User-set descriptions need verification; this is not a platform verdict.")
            if (compatible.isEmpty()) Text("No compatible template for this exact model/firmware. Manual schema-permitted edits remain available.")
            else CloakSelectField(label = "Compatible repair template", value = phone?.label ?: "Choose template",
                options = compatible, optionLabel = { it.label }, onSelect = { templateId = it.id }, enabled = !busy)
            TextButton(onClick = { phone?.let { act { session.repair(it) } } }, enabled = !busy && phone != null) { Text("Preview missing-field repair") }
            message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            DeviceReality.sections.sorted().forEach { section ->
                val envelope = body.optJSONObject("reality")?.optJSONObject(section)
                val availability = envelope?.optString("availability") ?: "unknown"
                val source = envelope?.optString("source") ?: "not recorded"
                CloakDisclosure(section, supporting = "$availability · $source") {
                    envelope?.optString("reason")?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    RealityStudio.fields(section).forEach { field ->
                        val path = RealityStudio.path(section, field)
                        val value = DeviceReality.at(body, path)
                        val userSet = field in (envelope?.let(RealityStudio::userFields).orEmpty())
                        val errors = preview.findings.filter { it.field?.startsWith("reality.$section") == true }
                        Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Text(field, style = MaterialTheme.typography.titleSmall)
                            Text(RealityStudio.display(value, path), style = MaterialTheme.typography.bodySmall)
                            Text("${if (userSet) "user-set (needs verification)" else source} · ${if (value == null) availability else "known"} · ${if (errors.any { it.isError }) "invalid" else if (value == null) "not checked (no value)" else "valid local description"}", style = MaterialTheme.typography.labelSmall)
                            Row {
                                Checkbox(path in session.locked, { session.lock(path, it); revision++ }, enabled = !busy)
                                Text("Preserve", Modifier.padding(top = 12.dp))
                                TextButton(onClick = {
                                    editing = path; reveal = !RealityStudio.sensitive(path); removeValue = false
                                    input = value?.toString() ?: if (RealityStudio.fieldRule(path).getString("type") == "boolean") "false" else ""
                                }, enabled = !busy && path !in session.locked && availability != "unavailable") { Text("Edit $field") }
                            }
                        }
                    }
                    TextButton(onClick = { phone?.let { act { session.regenerate(section, it) } } }, enabled = !busy && phone != null && availability != "unavailable") {
                        Text("Regenerate $section preview")
                    }
                }
            }
            TextButton(onClick = { review = true }, enabled = !busy && preview.valid && preview.changes.isNotEmpty()) { Text("Review Reality changes (${preview.changes.size})") }
        }
    }
    editing?.let { path ->
        val rule = RealityStudio.fieldRule(path)
        val type = rule.getString("type")
        val allowed = rule.optJSONArray("enum")?.let { array -> (0 until array.length()).map { array.getString(it) } }.orEmpty()
        AlertDialog(onDismissRequest = { editing = null }, title = { Text(path) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            properties = DialogProperties(usePlatformDefaultWidth = false), text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Contradictory values are rejected without changing the draft. Changes stay local until you confirm the review.")
                if (!reveal) TextButton(onClick = { reveal = true }) { Text("Reveal private value to edit") }
                else {
                    Row { Checkbox(removeValue, { removeValue = it }); Text("Remove optional value", Modifier.padding(top = 12.dp)) }
                    if (!removeValue) when {
                        allowed.isNotEmpty() -> CloakSelectField(label = "Value", value = input.ifBlank { "Choose value" },
                            options = allowed, optionLabel = { it }, onSelect = { input = it })
                        type == "boolean" -> Row { Checkbox(input == "true", { input = it.toString() }); Text("Enabled", Modifier.padding(top = 12.dp)) }
                        else -> {
                            OutlinedTextField(input, { input = it.take(32001) }, modifier = Modifier.fillMaxWidth(),
                                singleLine = type != "array", label = { Text(if (type == "array") "Structured value (JSON)" else "Value") })
                            if (rule.has("minimum")) Text("Allowed range: ${rule.get("minimum")}–${rule.get("maximum")}")
                            if (type == "array") Text("Edit the complete inventory or mode list; all entries are validated together.")
                        }
                    }
                }
                message?.let { Text(it) }
            }
        }, confirmButton = { TextButton(onClick = { act { session.edit(path, if (removeValue) "null" else if (type == "string") JSONObject.quote(input) else input) } }, enabled = !busy && reveal) { Text("Validate draft edit") } },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("Close") } })
    }
    if (review) AlertDialog(onDismissRequest = { review = false }, title = { Text("Confirm local Reality changes") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Text("Only this saved identity's Reality descriptions change. Existing publication is not refreshed automatically. Hash-bound evidence and engine metadata may become stale. No binding is changed.")
            preview.changes.forEach { Text("${it.path}\n${it.before} → ${it.after}", style = MaterialTheme.typography.bodySmall) }
            preview.findings.forEach { Text("${it.field}: ${it.code}: ${it.message}") }
        }
    }, confirmButton = { TextButton(onClick = { review = false; onSave(preview) }, enabled = !busy && preview.valid) { Text("Confirm and save identity") } },
        dismissButton = { TextButton(onClick = { review = false }) { Text("Cancel") } })
}

@Composable
internal fun FleetDiversityCard(profiles: List<JSONObject>) {
    val summary = remember(profiles.map(EngineMetadataStore::digest)) { FleetDiversity.analyze(profiles) }
    CloakDisclosure("Fleet diversity", supporting = "${summary.readable} readable identities · local descriptions only") {
        Text("Local diversity score: ${summary.score?.let { "$it/100" } ?: "not enough comparable facts"}")
        Text("Missing characteristics: ${summary.missingFacts}. Missing facts earn no diversity credit. No platform verdict.")
        if (summary.clusters.isEmpty()) Text("No repeated recorded clusters found.")
        summary.clusters.forEach { cluster ->
            Text("${cluster.paths.joinToString()} · ${cluster.labels.size} identities", style = MaterialTheme.typography.titleSmall)
            Text(cluster.labels.joinToString(), style = MaterialTheme.typography.bodySmall)
        }
        summary.recommendations.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
    }
}
