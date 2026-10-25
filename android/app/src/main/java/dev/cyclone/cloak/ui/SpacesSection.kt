package dev.cyclone.cloak.ui

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.cyclone.cloak.data.CloakSpace
import dev.cyclone.cloak.root.SpaceApp
import dev.cyclone.cloak.root.SpaceRuntime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Cloak's own spaces, listed above the Cyclone profiles. Creating and removing them is limited to Main. */
@Composable
internal fun SpacesSection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val status by SpaceRuntime.status.collectAsState()
    val spaces = remember(status) { runCatching { SpaceRuntime.spaces(context) }.getOrDefault(emptyList()) }
    val current = remember(status) { SpaceRuntime.current(context) }
    val available = SpaceRuntime.available()
    var creating by rememberSaveable { mutableStateOf(false) }
    var deleting by rememberSaveable { mutableStateOf<String?>(null) }

    fun openSpace(id: String, packageName: String) {
        scope.launch(Dispatchers.IO) { SpaceRuntime.open(context, id, packageName) }
    }

    fun resumeSpace(id: String) {
        scope.launch(Dispatchers.IO) { SpaceRuntime.resume(context, id) }
    }

    CloakSectionHeader("Spaces", spaces.size)
    if (!available) CloakNotice("Spaces need Android 13 or newer, and are managed from Main.")
    if (status.message.isNotBlank()) {
        CloakNotice(status.message, tone = if (status.busy) CloakStatusTone.PENDING else null)
    }
    CloakActionButton("New space", Icons.Rounded.Add, { creating = true }, Modifier.fillMaxWidth(),
        enabled = available && !status.busy)
    if (current != null) {
        CloakActionButton("Back to Main", Icons.Rounded.Home, { runCatching { SpaceRuntime.backToMain(context) } },
            Modifier.fillMaxWidth(), outlined = true, enabled = !status.busy)
    }
    spaces.forEach { space ->
        CloakGroup {
            CloakDisclosure(title = space.label, supporting = spaceSupporting(space)) {
                if (space.ready) {
                    space.packages.forEach { packageName ->
                        CloakRow(
                            title = appLabel(context, packageName),
                            supporting = "Open in ${space.label}",
                            onClick = if (status.busy) null else ({ openSpace(space.id, packageName) }),
                            showChevron = false,
                        )
                    }
                    CloakRow(
                        title = "Delete space",
                        tone = CloakStatusTone.ERROR,
                        onClick = if (status.busy || !available) null else ({ deleting = space.id }),
                        showChevron = false,
                    )
                } else if (space.state == CloakSpace.STATE_CREATING) {
                    CloakRow(
                        title = "Finish setup",
                        supporting = "Picks up where setup stopped. Nothing is created twice.",
                        onClick = if (status.busy || !available) null else ({ resumeSpace(space.id) }),
                        showChevron = false,
                    )
                } else {
                    CloakRow(title = "Removal did not finish", supporting = "Try deleting it again.",
                        onClick = if (status.busy || !available) null else ({ deleting = space.id }), showChevron = false)
                }
            }
        }
    }

    if (creating) {
        SpaceCreateDialog(
            suggestedLabel = "Space ${spaces.size + 1}",
            onDismiss = { creating = false },
            onCreate = { label, packages ->
                creating = false
                scope.launch(Dispatchers.IO) { SpaceRuntime.create(context, label, packages) }
            },
        )
    }
    deleting?.let { id ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete ${spaces.firstOrNull { it.id == id }?.label ?: "this space"}?") },
            text = { Text("The space's apps and their data are removed, and its bindings are removed too. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    deleting = null
                    scope.launch(Dispatchers.IO) { SpaceRuntime.delete(context, id) }
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SpaceCreateDialog(suggestedLabel: String, onDismiss: () -> Unit, onCreate: (String, List<String>) -> Unit) {
    val context = LocalContext.current
    var label by rememberSaveable { mutableStateOf(suggestedLabel) }
    var query by rememberSaveable { mutableStateOf("") }
    var selected by remember { mutableStateOf(setOf<String>()) }
    val apps by produceState(initialValue = emptyList<SpaceApp>()) {
        value = withContext(Dispatchers.IO) { SpaceRuntime.launchableApps(context) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New space") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it.take(CloakSpace.LABEL_MAX) },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Find an app") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("${selected.size} selected · separate accounts · fresh app data")
                LazyColumn(modifier = Modifier.fillMaxWidth().height(260.dp)) {
                    items(apps.filter { it.label.contains(query, ignoreCase = true) }, key = { it.packageName }) { app ->
                        CloakRow(
                            title = app.label,
                            supporting = app.packageName,
                            onClick = {
                                selected = if (app.packageName in selected) selected - app.packageName else selected + app.packageName
                            },
                            showChevron = false,
                            trailing = { Checkbox(checked = app.packageName in selected, onCheckedChange = null) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onCreate(label.trim(), apps.map { it.packageName }.filter { it in selected }) },
                enabled = label.isNotBlank() && selected.isNotEmpty() && selected.size <= CloakSpace.PACKAGES_MAX,
            ) { Text("Create space") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun spaceSupporting(space: CloakSpace): String = when {
    space.ready -> "${space.packages.size} apps · Android user ${space.androidUserId}"
    space.state == CloakSpace.STATE_CREATING -> "Setup unfinished"
    else -> "Removal unfinished"
}

private fun appLabel(context: Context, packageName: String): String = runCatching {
    context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(packageName, 0)).toString()
}.getOrDefault(packageName)
