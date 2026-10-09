package dev.cyclone.cloak.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.cyclone.cloak.cyclone.bindingKey

@Composable
internal fun ProfilesScreen(state: CloakState, vm: CloakViewModel) {
    val pills = state.pills
    val openTargets = state.openTargets
    val mainTarget = openTargets.firstOrNull { it.isOwner }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            CloakHeroCard(
                title = if (state.ownProfileId == null) "Cyclone profiles" else "This Cyclone profile",
                body = state.report?.message?.takeIf { it.isNotBlank() }
                    ?: "Each Cyclone profile is its own Android user. Bind an identity to a profile and every app in it sees that phone.",
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
        if (state.bindings.isEmpty()) {
            item {
                Text(
                    "Nothing is bound yet. In Identities, open an identity and choose Bind to a profile.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(state.bindings.sortedWith(compareBy({ it.profileId }, { it.packageName })), key = { it.profileId + "/" + it.packageName }) { binding ->
            CloakBindingCard(
                binding = binding,
                profileLabel = state.registry.firstOrNull { it.id == binding.profileId }?.label ?: binding.profileId,
                issue = state.report?.issues?.get(bindingKey(binding.profileId, binding.packageName)),
                onToggle = vm::toggleBinding,
                onRemove = vm::removeBinding,
            )
        }
    }
}
