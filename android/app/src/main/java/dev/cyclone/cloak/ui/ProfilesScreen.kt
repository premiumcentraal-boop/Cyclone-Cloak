package dev.cyclone.cloak.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.cyclone.cloak.cyclone.bindingKey

@Composable
internal fun ProfilesScreen(state: CloakState, vm: CloakViewModel) {
    val pills = state.pills
    val openTargets = state.openTargets
    val mainTarget = openTargets.firstOrNull { it.isOwner }
    var profileFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var packageQuery by rememberSaveable { mutableStateOf("") }
    val profiles = state.registry.associateBy { it.id }
    val knownProfileIds = state.report?.snapshot?.registry?.map { it.id }?.toSet()
    val visibleBindings = filterBindings(state.bindings, profileFilter, packageQuery)
    val profileChoices = (profiles.keys + state.bindings.map { it.profileId }).sorted()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            CloakHeroCard(
                title = if (state.ownProfileId == null) "Cyclone profiles" else "This Cyclone profile",
                body = state.report?.message?.takeIf { it.isNotBlank() }
                    ?: "Each Cyclone profile is its own Android user. Choose a device profile and select the apps to bind, or choose All apps.",
            )
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CloakActionButton("Refresh", Icons.Rounded.Refresh, vm::refresh, Modifier.weight(1f), outlined = true)
                CloakActionButton("Bind all", Icons.Rounded.Layers, vm::bulkBind, Modifier.weight(1f))
            }
        }
        item {
            Text(
                "Bind all gives every ready profile without bindings the next unused identity.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        mainTarget?.let { main ->
            item {
                OutlinedButton(onClick = { vm.openInCyclone(main) }, enabled = !state.opening, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.small) {
                    Text("Open ${main.label} in Cyclone")
                }
            }
        }

        item { OutlinedButton(onClick = { vm.startBinding() }, enabled = state.busy == null && !state.syncing, modifier = Modifier.fillMaxWidth()) { Text("Bind a device profile") } }

        item { CloakSectionTitle("Profiles", state.registry.size) }
        if (state.registry.isEmpty()) {
            item {
                CloakEmptyState(
                    title = "No Cyclone profiles found",
                    body = "Check that Cyclone is installed and Cyclone Cloak is approved in Cyclone → Settings → Connectors, then refresh.",
                    icon = Icons.Rounded.CheckCircle,
                )
            }
        }
        items(state.registry, key = { it.id }) { profile ->
            val appCount = profile.packages?.size ?: 0
            val where = when {
                profile.state == "in_trash" -> "in Recently deleted"
                profile.state != "ready" -> "setting up"
                profile.androidUserId == null -> "no Android user yet"
                else -> "user ${profile.androidUserId}"
            }
            val identity = state.bindings.firstOrNull { it.profileId == profile.id && it.enabled }?.cloakProfileId
                ?.let { id -> state.identities.firstOrNull { it.first == id }?.second?.optString("name") ?: id }
            val managedElsewhere = state.ownProfileId != null && state.ownProfileId != profile.id
            CloakProfileCard(
                title = profile.label,
                subtitle = listOfNotNull(
                    "$appCount ${if (appCount == 1) "app" else "apps"} · $where",
                    identity?.let { "as $it" },
                    if (managedElsewhere) "bound from Cloak in Main" else null,
                ).joinToString(" · "),
                pill = pills[profile.id],
                selected = false,
                canOpen = openTargets.any { it.id == profile.id } && !state.opening,
                onClick = {},
                onOpen = { vm.openInCyclone(profile) },
            )
        }

        item { CloakSectionTitle("Bound apps", state.bindings.size) }
        if (state.bindings.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    BindingFilters(
                        profileFilter = profileFilter,
                        profileChoices = profileChoices,
                        profileLabel = { profiles[it]?.label ?: it },
                        onProfileChange = { profileFilter = it },
                        packageQuery = packageQuery,
                        onPackageChange = { packageQuery = it },
                        onClear = { profileFilter = null; packageQuery = "" },
                    )
                    Text("Showing ${visibleBindings.size} of ${state.bindings.size} bindings", style = MaterialTheme.typography.bodySmall)
                    if (knownProfileIds == null) {
                        Text("Profile availability not checked. Refresh to load Cyclone profiles.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (visibleBindings.isEmpty()) {
                        Text("No bindings match these filters.", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
        if (state.bindings.isEmpty()) {
            item {
                Text(
                    "Nothing is bound yet. Choose Bind a device profile, then select apps or All apps.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        // Duplicate records must remain visible for review instead of colliding on a LazyColumn key.
        itemsIndexed(visibleBindings, key = { index, binding -> "${binding.profileId}/${binding.packageName}/$index" }) { _, binding ->
            CloakBindingCard(
                binding = binding,
                profileLabel = profiles[binding.profileId]?.label ?: binding.profileId,
                issue = state.report?.issues?.get(bindingKey(binding.profileId, binding.packageName)),
                profileMissing = bindingProfileMissing(binding.profileId, knownProfileIds),
                identityLabel = state.identities.firstOrNull { it.first == binding.cloakProfileId }?.second?.optString("name")
                    ?.takeIf { it.isNotBlank() } ?: binding.cloakProfileId,
                onToggle = vm::toggleBinding,
                onRemove = vm::removeBinding,
            )
        }
    }
}

@Composable
internal fun BindingFilters(
    profileFilter: String?,
    profileChoices: List<String>,
    profileLabel: (String) -> String,
    onProfileChange: (String?) -> Unit,
    packageQuery: String,
    onPackageChange: (String) -> Unit,
    onClear: () -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Filter bindings", style = MaterialTheme.typography.titleMedium)
        Box {
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                Text(profileFilter?.let { "Cyclone profile: ${profileLabel(it)}" } ?: "All Cyclone profiles")
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(text = { Text("All Cyclone profiles") }, onClick = {
                    onProfileChange(null)
                    expanded = false
                })
                profileChoices.forEach { id ->
                    DropdownMenuItem(text = {
                        Column {
                            Text(profileLabel(id))
                            Text(id, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }, onClick = {
                        onProfileChange(id)
                        expanded = false
                    })
                }
            }
        }
        OutlinedTextField(
            value = packageQuery,
            onValueChange = onPackageChange,
            label = { Text("App package filter") },
            placeholder = { Text("For example, com.example") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        if (profileFilter != null || packageQuery.isNotEmpty()) {
            TextButton(onClick = onClear) { Text("Clear filters") }
        }
    }
}
