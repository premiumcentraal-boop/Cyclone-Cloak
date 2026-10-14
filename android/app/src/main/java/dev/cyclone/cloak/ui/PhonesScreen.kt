package dev.cyclone.cloak.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.cyclone.cloak.forge.PhoneTemplate

@Composable
internal fun PhonesScreen(state: CloakState, vm: CloakViewModel) {
    var exporting by remember { mutableStateOf<PhoneTemplate?>(null) }
    var making by remember { mutableStateOf<PhoneTemplate?>(null) }
    var deleting by remember { mutableStateOf<PhoneTemplate?>(null) }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(vm::import) }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val phone = exporting
        if (uri != null && phone != null) vm.exportPhone(phone, uri)
        exporting = null
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("phones-list"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            CloakHeroCard(
                title = "Phones",
                body = "Every identity copies a phone: its model, build, carrier, screen and locale. Use a built-in phone, " +
                    "build your own, or import one from a real phone's build.prop or `adb shell getprop` output.",
            )
        }
        item {
            ProfileGenerationCard(state.phones, state.busy != null, state.generation,
                onGenerate = vm::generateProfile, onDismiss = vm::dismissGenerationResult)
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CloakActionButton("Build a phone", Icons.Rounded.Build, vm::newPhone, Modifier.weight(1f))
                CloakActionButton("Import", Icons.Rounded.FileOpen, { importer.launch(arrayOf("*/*")) }, Modifier.weight(1f), outlined = true)
            }
        }
        item {
            Text(
                "Import takes a phone file, a build.prop or getprop dump, an identity, or a whole fleet.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val builtIn = state.phones.filter { it.builtIn }
        val own = state.phones.filter { !it.builtIn }
        fun identitiesFrom(phone: PhoneTemplate) = state.identities.count { it.second.optJSONObject("meta")?.optString("template") == phone.id }
        item { CloakSectionHeader("Your phones", own.size) }
        if (own.isEmpty()) {
            item {
                CloakEmptyState(
                    title = "No phones of your own yet",
                    body = "Build one, import a dump, or clone a built-in phone below and change what you need.",
                    icon = Icons.Rounded.Add,
                )
            }
        } else item {
            CloakGroup {
                own.forEachIndexed { index, phone ->
                    if (index > 0) CloakGroupDivider()
                    PhoneRow(phone, identitiesFrom(phone),
                        onMake = { making = phone }, onClone = { vm.clonePhone(phone) }, onEdit = { vm.editPhone(phone) },
                        onExport = { exporting = phone; exporter.launch("${phone.id}.cloak-phone.json") }, onDelete = { deleting = phone })
                }
            }
        }
        item { CloakSectionHeader("Built in", builtIn.size) }
        item {
            CloakGroup {
                builtIn.forEachIndexed { index, phone ->
                    if (index > 0) CloakGroupDivider()
                    PhoneRow(phone, identitiesFrom(phone),
                        onMake = { making = phone }, onClone = { vm.clonePhone(phone) }, onEdit = null,
                        onExport = { exporting = phone; exporter.launch("${phone.id}.cloak-phone.json") }, onDelete = null)
                }
            }
        }
    }

    making?.let { phone ->
        NameDialog(
            title = "New identity from ${phone.label}",
            hint = "For example, Work Pixel",
            confirm = "Create",
            onConfirm = { name -> vm.createIdentity(name, phone.id); making = null },
            onDismiss = { making = null },
        )
    }
    deleting?.let { phone ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete ${phone.label}?") },
            text = { Text("Identities already made from it keep their own copy and keep working.") },
            confirmButton = { TextButton(onClick = { vm.deletePhone(phone); deleting = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun PhoneRow(
    phone: PhoneTemplate,
    identities: Int,
    onMake: () -> Unit,
    onClone: () -> Unit,
    onEdit: (() -> Unit)?,
    onExport: () -> Unit,
    onDelete: (() -> Unit)?,
) {
    val extra = listOfNotNull(
        phone.telephony.optString("carrier_name").takeIf { it.isNotBlank() },
        phone.locale.optString("country").takeIf { it.isNotBlank() },
        if (identities > 0) "$identities ${if (identities == 1) "identity" else "identities"}" else null,
    ).joinToString(" · ")
    CloakRow(
        phone.label,
        modifier = Modifier.testTag("phone-card/${phone.id}"),
        supporting = listOf(phone.summary.ifBlank { "No details yet" }, extra).filter { it.isNotBlank() }.joinToString("\n"),
        icon = Icons.Rounded.PhoneAndroid,
        tone = if (phone.builtIn) CloakStatusTone.PENDING else null,
    ) {
        FilledTonalButton(onClick = onMake, contentPadding = PaddingValues(horizontal = 12.dp)) { Text("Make identity") }
        CloakOverflowMenu(listOfNotNull(
            CloakMenuAction("Clone", onClick = onClone),
            onEdit?.let { CloakMenuAction("Edit", onClick = it) },
            CloakMenuAction("Share", onClick = onExport),
            onDelete?.let { CloakMenuAction("Delete", destructive = true, onClick = it) },
        ), contentDescription = "More for ${phone.label}")
    }
}

@Composable
internal fun NameDialog(
    title: String,
    hint: String,
    confirm: String,
    initial: String = "",
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { if (it.length <= 64) name = it },
                singleLine = true,
                label = { Text("Name") },
                placeholder = { Text(hint) },
                supportingText = { Text("${name.length}/64") },
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(name.trim()) }, enabled = name.isNotBlank()) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
