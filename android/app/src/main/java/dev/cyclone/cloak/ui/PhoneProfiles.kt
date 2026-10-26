package dev.cyclone.cloak.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
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

/**
 * The phone profiles, shown inside Identities: the built-in templates and the owner's own. A phone is where an identity
 * starts, so both live on one page. Dialogs and file pickers belong to the caller, which owns the screen.
 */
internal fun LazyListScope.phoneProfileItems(
    state: CloakState,
    vm: CloakViewModel,
    onMake: (PhoneTemplate) -> Unit,
    onExport: (PhoneTemplate) -> Unit,
    onDelete: (PhoneTemplate) -> Unit,
    onImport: () -> Unit,
) {
    val builtIn = state.phones.filter { it.builtIn }
    val own = state.phones.filter { !it.builtIn }
    fun identitiesFrom(phone: PhoneTemplate) = state.identities.count { it.second.optJSONObject("meta")?.optString("template") == phone.id }

    item { CloakSectionHeader("Phone profiles", state.phones.size) }
    item {
        ProfileGenerationCard(state.phones, state.busy != null, state.generation,
            onGenerate = vm::generateProfile, onDismiss = vm::dismissGenerationResult)
    }
    item {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CloakActionButton("Build a phone", Icons.Rounded.Build, vm::newPhone, Modifier.weight(1f), outlined = true)
            CloakActionButton("Import", Icons.Rounded.FileOpen, onImport, Modifier.weight(1f), outlined = true)
        }
    }
    item {
        Text(
            "Import takes a phone file, a build.prop or getprop dump, an identity, or a whole fleet.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
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
                    onMake = { onMake(phone) }, onClone = { vm.clonePhone(phone) }, onEdit = { vm.editPhone(phone) },
                    onExport = { onExport(phone) }, onDelete = { onDelete(phone) })
            }
        }
    }
    item { CloakSectionHeader("Built in", builtIn.size) }
    item {
        CloakGroup {
            builtIn.forEachIndexed { index, phone ->
                if (index > 0) CloakGroupDivider()
                PhoneRow(phone, identitiesFrom(phone),
                    onMake = { onMake(phone) }, onClone = { vm.clonePhone(phone) }, onEdit = null,
                    onExport = { onExport(phone) }, onDelete = null)
            }
        }
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
