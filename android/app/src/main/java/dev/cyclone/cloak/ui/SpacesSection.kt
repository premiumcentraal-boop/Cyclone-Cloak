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
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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

/**
 * Cloak's own spaces, listed above the Cyclone profiles. Creating a space leads straight into binding its apps to its
 * own device profile, so the two steps read as one flow. Creating and removing spaces is limited to Main.
 */
@Composable
internal fun SpacesSection(
    vm: CloakViewModel,
    state: CloakState,
    query: String = "",
    openCreate: Boolean = false,
    onCreateOpened: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val status by SpaceRuntime.status.collectAsState()
    val spaces = remember(status) { runCatching { SpaceRuntime.spaces(context) }.getOrDefault(emptyList()) }
    val shown = spaces.filter { CloakSearch.matches(query, it.label, *it.packages.toTypedArray()) }
    val current = remember(status) { SpaceRuntime.current(context) }
    val available = SpaceRuntime.available()
    val idle = !status.busy && state.busy == null
    var creating by rememberSaveable { mutableStateOf(false) }
    var deleting by rememberSaveable { mutableStateOf<String?>(null) }
    // Home's "New profile" arrives as openCreate. Open the dialog once, then clear the request.
    LaunchedEffect(openCreate) {
        if (openCreate) {
            if (available) creating = true
            onCreateOpened()
        }
    }

    fun openSpace(id: String, packageName: String) {
        scope.launch(Dispatchers.IO) { SpaceRuntime.open(context, id, packageName) }
    }

    fun resumeSpace(id: String) {
        scope.launch(Dispatchers.IO) {
            SpaceRuntime.resume(context, id)
            vm.reloadSpaces()
            vm.startSpaceBinding(id)
        }
    }

    CloakSectionHeader("Spaces", spaces.size)
    if (!available) CloakNotice("Spaces need Android 13 or newer, and are managed from Main.")
    if (status.message.isNotBlank()) {
        CloakNotice(status.message, tone = if (status.busy) CloakStatusTone.PENDING else null)
    }
    CloakActionButton("New space", Icons.Rounded.Add, { creating = true }, Modifier.fillMaxWidth(),
        enabled = available && idle)
    if (current != null) {
        CloakActionButton("Back to Main", Icons.Rounded.Home, { runCatching { SpaceRuntime.backToMain(context) } },
            Modifier.fillMaxWidth(), outlined = true, enabled = idle)
    }
    shown.forEach { space ->
        CloakGroup {
            CloakDisclosure(title = space.label, supporting = spaceSupporting(space, state.bindings.count {
                it.profileId == space.id && it.enabled })) {
                if (space.ready) {
                    CloakRow(
                        title = "Bind apps",
                        supporting = "Choose this space's device profile and apps",
                        onClick = if (idle && available) ({ vm.startSpaceBinding(space.id) }) else null,
                        showChevron = false,
                    )
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
                        onClick = if (!idle || !available) null else ({ deleting = space.id }),
                        showChevron = false,
                    )
                } else if (space.state == CloakSpace.STATE_CREATING) {
                    CloakRow(
                        title = "Finish setup",
                        supporting = "Picks up where setup stopped. Nothing is created twice.",
                        onClick = if (!idle || !available) null else ({ resumeSpace(space.id) }),
                        showChevron = false,
                    )
                } else {
                    CloakRow(title = "Removal did not finish", supporting = "Try deleting it again.",
                        onClick = if (!idle || !available) null else ({ deleting = space.id }), showChevron = false)
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
                scope.launch(Dispatchers.IO) {
                    val id = SpaceRuntime.create(context, label, packages)
                    vm.reloadSpaces()
                    // Straight into binding: the space's apps and its own device profile are already chosen.
                    id?.let(vm::startSpaceBinding)
                }
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
                    scope.launch(Dispatchers.IO) {
                        SpaceRuntime.delete(context, id)
                        vm.reloadSpaces()
                    }
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
    // Cloak and, when installed, Cyclone come with every space. They are shown locked and count against the app limit.
    val essentials = remember(context) { SpaceRuntime.essentials(context) }
    val room = CloakSpace.PACKAGES_MAX - essentials.size
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
                Text("Essentials · added to every space", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                CloakGroup {
                    essentials.forEachIndexed { index, packageName ->
                        if (index > 0) CloakGroupDivider()
                        CloakRow(
                            title = appLabel(context, packageName),
                            supporting = "Always included",
                            icon = Icons.Rounded.Lock,
                            showChevron = false,
                            trailing = { Checkbox(checked = true, onCheckedChange = null, enabled = false) },
                        )
                    }
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Find an app") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    if (selected.size > room) "A space holds up to ${CloakSpace.PACKAGES_MAX} apps, Essentials included."
                    else "${selected.size} selected · separate accounts · fresh app data",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (selected.size > room) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LazyColumn(modifier = Modifier.fillMaxWidth().height(260.dp)) {
                    items(apps.filter { it.packageName !in essentials && it.label.contains(query, ignoreCase = true) }, key = { it.packageName }) { app ->
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
                enabled = label.isNotBlank() && selected.isNotEmpty() && selected.size <= room,
            ) { Text("Create space") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun spaceSupporting(space: CloakSpace, bound: Int): String = when {
    space.ready -> "${space.packages.size} apps · $bound bound · Android user ${space.androidUserId}"
    space.state == CloakSpace.STATE_CREATING -> "Setup unfinished"
    else -> "Removal unfinished"
}

private fun appLabel(context: Context, packageName: String): String = runCatching {
    context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(packageName, 0)).toString()
}.getOrDefault(packageName)
