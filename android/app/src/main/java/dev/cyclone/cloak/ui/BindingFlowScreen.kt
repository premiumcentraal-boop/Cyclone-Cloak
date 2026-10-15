package dev.cyclone.cloak.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.cyclone.cloak.forge.PhoneTemplate
import dev.cyclone.cloak.forge.ProfileValidator

@Composable
internal fun BindingFlowScreen(
    draft: BindingDraft, input: BindingInputs, phones: List<PhoneTemplate>, busy: Boolean,
    onProfile: (String) -> Unit, onApps: (Set<String>) -> Unit, onIdentity: (String) -> Unit,
    onGenerate: (String) -> Unit, onNext: () -> Unit, onBack: () -> Unit,
    onAcknowledge: (Boolean) -> Unit, onConfirm: () -> Unit, onRetry: () -> Unit,
    onRefresh: () -> Unit, onClose: () -> Unit,
) {
    val locked = busy || draft.running
    BackHandler(enabled = !locked) { onBack() }
    val profile = input.profiles.firstOrNull { it.id == draft.profileId }
    val identity = input.identities.firstOrNull { it.first == draft.identityId }?.second
    val findings = BindingFlow.findings(draft, input)
    val field = when (draft.step) { BindingStep.PROFILE -> "profile"; BindingStep.APP -> "app"; BindingStep.IDENTITY -> "identity"; else -> "" }
    val visible = findings.filter { field.isEmpty() || it.field.orEmpty().startsWith(field) }
    Scaffold { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Bind a device profile", style = MaterialTheme.typography.headlineSmall)
            Text("Choose a Cyclone profile and apps, then select the device profile to bind.", style = MaterialTheme.typography.bodySmall)
            Text("Step ${draft.step.ordinal + 1} of 5 · ${when (draft.step) {
                BindingStep.PROFILE -> "Choose Cyclone profile"; BindingStep.APP -> "App selection"
                BindingStep.IDENTITY -> "Choose device profile"; BindingStep.REVIEW -> "Review summary"
                BindingStep.RESULT -> "Binding result"
            }}", style = MaterialTheme.typography.titleMedium)
            if (locked) LinearProgressIndicator(Modifier.fillMaxWidth())
            when (draft.step) {
                BindingStep.PROFILE -> {
                    Text("Using the latest synchronized Cyclone list. Refresh if setup or apps have changed.")
                    TextButton(onClick = onRefresh, enabled = !locked) { Text("Refresh Cyclone") }
                    if (input.profiles.isEmpty()) Text("No profiles available. Refresh and check connector approval.")
                    input.profiles.forEach { p ->
                        OutlinedButton(onClick = { onProfile(p.id) }, enabled = !locked, modifier = Modifier.fillMaxWidth()) {
                            Text("${if (p.id == draft.profileId) "✓ " else ""}${p.label} · ${p.androidUserId?.let { "user $it" } ?: "no Android user"} · ${p.state}")
                        }
                    }
                }
                BindingStep.APP -> {
                    Text("Cyclone profile: ${profile?.label ?: "Unavailable"}")
                    if (profile?.packages.isNullOrEmpty()) Text("No app list available. Add an app in Cyclone, then refresh.")
                    val packages = profile?.packages.orEmpty().toSet()
                    Row(Modifier.fillMaxWidth().toggleable(
                        value = BindingFlow.allApps(draft, input), enabled = !locked && packages.isNotEmpty(), role = Role.Checkbox,
                        onValueChange = { onApps(BindingFlow.selectAll(draft, input, it).packageNames) }),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Checkbox(checked = BindingFlow.allApps(draft, input), onCheckedChange = null, enabled = !locked && packages.isNotEmpty())
                        Text("All apps (${packages.size})", Modifier.padding(top = 12.dp))
                    }
                    Text("${draft.packageNames.size} selected. Choose one or more apps for this device profile.")
                    packages.sorted().forEach { pkg ->
                        Row(Modifier.fillMaxWidth().toggleable(
                            value = pkg in draft.packageNames, enabled = !locked, role = Role.Checkbox,
                            onValueChange = { checked -> onApps(if (checked) draft.packageNames + pkg else draft.packageNames - pkg) }),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Checkbox(checked = pkg in draft.packageNames, onCheckedChange = null, enabled = !locked)
                            Text(pkg, Modifier.padding(top = 12.dp))
                        }
                    }
                    TextButton(onClick = onRefresh, enabled = !locked) { Text("Refresh Cyclone") }
                }
                BindingStep.IDENTITY -> {
                    Text("Choose a saved device profile before review.")
                    if (identity != null) Text("Selected device profile: ${identity.optString("name").ifBlank { draft.identityId.orEmpty() }}")
                    if (input.identities.isEmpty()) Text("No saved device profiles. Generate one below, or create/import one in Identities.")
                    input.identities.forEach { (id, value) ->
                        OutlinedButton(onClick = { onIdentity(id) }, enabled = !locked, modifier = Modifier.fillMaxWidth()) {
                            Text("${if (id == draft.identityId) "✓ " else ""}${value.optString("name").ifBlank { id }}")
                        }
                    }
                    Text("Or generate from a phone template", style = MaterialTheme.typography.titleSmall)
                    Text("Generation saves a validated profile locally. Cancelling this binding keeps that profile.")
                    phones.forEach { phone ->
                        TextButton(onClick = { onGenerate(phone.id) }, enabled = !locked) { Text("Generate ${phone.label} profile") }
                    }
                }
                BindingStep.REVIEW, BindingStep.RESULT -> {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Final binding review", style = MaterialTheme.typography.titleMedium)
                            Text("Cyclone profile: ${profile?.label ?: draft.profileId.orEmpty()}")
                            Text(if (BindingFlow.allApps(draft, input)) "All apps (${draft.packageNames.size})" else "Selected apps (${draft.packageNames.size})")
                            draft.packageNames.sorted().forEach { Text("App package: $it") }
                            Text("Android user: ${profile?.androidUserId ?: "Not assigned"}")
                            Text("Device profile: ${identity?.optString("name") ?: draft.identityId.orEmpty()}")
                            Text("One binding per selected app will be saved.", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            draft.note?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            if (draft.step != BindingStep.RESULT) {
                visible.forEach { f ->
                    Text("${f.field}: ${f.message}", color = if (f.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (draft.step == BindingStep.REVIEW && findings.any { it.severity == ProfileValidator.WARNING }) {
                    Row {
                        Checkbox(checked = draft.acknowledged, onCheckedChange = onAcknowledge, enabled = !locked)
                        Text("I reviewed the warnings and want to keep this selection.", modifier = Modifier.padding(top = 12.dp))
                    }
                }
                if (draft.step == BindingStep.REVIEW) {
                    if (draft.reviewStamp != BindingFlow.stamp(draft, input)) {
                        Text("The target or library changed. Review the latest summary before confirming.")
                        TextButton(onClick = onNext, enabled = !locked) { Text("Review latest summary") }
                    }
                    Button(onClick = onConfirm, enabled = !locked && BindingFlow.canConfirm(draft, input)) { Text("Confirm binding") }
                } else Button(onClick = onNext, enabled = !locked && BindingFlow.canAdvance(draft, input)) { Text("Continue") }
            } else {
                val outcome = draft.outcome
                Text(if (outcome?.ready == true) "Binding ready" else if (outcome?.saved == true) "Saved · needs attention" else "Binding not completed",
                    style = MaterialTheme.typography.titleMedium)
                Text(outcome?.message ?: "Saving and checking the binding…")
                if (outcome != null && !outcome.ready) Button(onClick = onRetry, enabled = !locked) {
                    Text(if (outcome.saved) "Retry reconciliation" else "Retry binding")
                }
                if (outcome?.ready != true) TextButton(onClick = onBack, enabled = !locked) { Text("Return to review") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (draft.step != BindingStep.RESULT) TextButton(onClick = onBack, enabled = !locked) { Text("Back") }
                TextButton(onClick = onClose, enabled = !locked) { Text(if (draft.outcome?.ready == true) "Done" else "Close") }
            }
        }
    }
}
