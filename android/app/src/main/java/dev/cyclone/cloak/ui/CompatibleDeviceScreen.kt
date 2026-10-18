package dev.cyclone.cloak.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.cyclone.cloak.forge.PhoneTemplate
import dev.cyclone.cloak.cyclone.CycloneProfile

@Composable
internal fun CompatibleDeviceScreen(workshop: BatchWorkshopState, phones: List<PhoneTemplate>, profiles: List<CycloneProfile>,
    busy: Boolean, onPreview: (List<String>, Int, String) -> Unit, onSave: (Set<String>) -> Unit,
    onAssign: (String, String) -> Unit, onClose: () -> Unit) {
    var template by rememberSaveable { mutableStateOf<String?>(null) }
    var count by rememberSaveable { mutableStateOf("1") }
    var prefix by rememberSaveable { mutableStateOf("") }
    var selected by remember(workshop.plan) { mutableStateOf(emptySet<String>()) }
    var device by rememberSaveable { mutableStateOf<String?>(null) }
    var target by rememberSaveable { mutableStateOf<String?>(null) }
    BackHandler(enabled = !busy, onBack = onClose)
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background, contentColor = MaterialTheme.colorScheme.onBackground) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("New compatible device", style = MaterialTheme.typography.titleLarge)
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        workshop.message?.let { Text(it) }
        val plan = workshop.plan
        if (plan == null) {
            CloakSelectField("Real device template", phones.firstOrNull { it.id == template }?.label ?: "Choose a template",
                phones, { it.label }, { template = it.id }, optionSupporting = { it.summary }, enabled = !busy)
            if (phones.isEmpty()) Text("No templates available. Import a real device template in Phones, then return here.")
            OutlinedTextField(count, { count = it }, label = { Text("Compatible profiles (1–20)") }, singleLine = true,
                enabled = !busy, isError = count.toIntOrNull() !in 1..20)
            OutlinedTextField(prefix, { prefix = it.take(32) }, label = { Text("Label prefix (optional)") }, singleLine = true, enabled = !busy,
                supportingText = { Text("Up to 32 letters, digits, spaces, dots, underscores or hyphens.") })
            Button(onClick = { onPreview(listOf(template!!), count.toInt(), prefix.trim()) },
                enabled = !busy && template != null && count.toIntOrNull() in 1..20) { Text("Generate for review") }
        } else {
            val savedKeys = workshop.results.filter { it.status == "SAVED" }.map { it.key }.toSet()
            CloakGroup {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Quality and validation", style = MaterialTheme.typography.titleMedium)
                    Text("${plan.summary.getInt("validation_pass")} pass · ${plan.summary.getInt("validation_fail")} fail · ${plan.summary.getInt("conflicting")} conflicts")
                    Text("${plan.summary.getInt("warnings")} warnings · local coherence only; runtime coverage is separate.")
                }
            }
            plan.drafts.forEach { draft ->
                CloakGroup {
                    Column(Modifier.padding(12.dp)) {
                        Row {
                            Checkbox(draft.key in selected || draft.key in savedKeys,
                                { selected = if (it) selected + draft.key else selected - draft.key },
                                enabled = !busy && draft.approved && draft.key !in savedKeys)
                            Column { Text(draft.label); Text(if (draft.approved) "Validation passed · unique" else "Needs correction") }
                        }
                        FindingsList(draft.findings)
                        if (!draft.approved) Text("Choose another template or retry generation before saving this profile.")
                        workshop.results.firstOrNull { it.key == draft.key }?.let { Text("${it.status}: ${it.reason}") }
                    }
                }
            }
            Button(onClick = { onSave(selected) }, enabled = !busy && (selected - savedKeys).isNotEmpty()) { Text("Save approved profiles") }
            if (plan.drafts.none { it.approved }) TextButton(onClick = { onPreview(plan.request.templateIds, plan.request.perTemplate, prefix) }, enabled = !busy) { Text("Retry generation") }
            val saved = plan.drafts.filter { it.key in savedKeys && it.profile != null }
            if (saved.isNotEmpty()) {
                Text("Assign a saved device", style = MaterialTheme.typography.titleMedium)
                CloakSelectField("Device profile", saved.firstOrNull { it.profile?.optString("id") == device }?.label ?: "Choose a saved device",
                    saved, { it.label }, { device = it.profile!!.getString("id") }, enabled = !busy)
                CloakSelectField("Cyclone profile", profiles.firstOrNull { it.id == target }?.label ?: "Choose a Cyclone profile",
                    profiles.filter { !it.isOwner }, { it.label }, { target = it.id },
                    optionSupporting = { it.androidUserId?.let { user -> "Android user $user" } ?: "Finish setup in Cyclone, then refresh" }, enabled = !busy)
                if (profiles.none { !it.isOwner }) Text("Create a profile in Cyclone, approve Cloak in Connectors, then refresh Profiles to assign this saved device.")
                Button(onClick = { onAssign(device!!, target!!) }, enabled = !busy && device != null && target != null) { Text("Review assignment") }
            }
        }
        TextButton(onClick = onClose, enabled = !busy) { Text("Close") }
    }
    }
}
