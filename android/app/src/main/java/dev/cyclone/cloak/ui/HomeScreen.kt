package dev.cyclone.cloak.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.HealthAndSafety
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.cyclone.cloak.root.RootDoctorCode

/** What a Home action asks the app to do. The two "New" actions also open the screen where the thing is made. */
internal enum class QuickAction { NEW_PROFILE, NEW_IDENTITY, REPAIR, REFRESH }

private class QuickTile(val action: QuickAction, val label: String, val icon: ImageVector)

private val quickTiles = listOf(
    QuickTile(QuickAction.NEW_PROFILE, "New profile", Icons.Rounded.Add),
    QuickTile(QuickAction.NEW_IDENTITY, "New identity", Icons.Rounded.Badge),
    QuickTile(QuickAction.REPAIR, "Repair", Icons.Rounded.Build),
    QuickTile(QuickAction.REFRESH, "Refresh", Icons.Rounded.Refresh),
)

/** The dashboard: instant actions, the working profiles, what needs attention, and the next setup step. */
@Composable
internal fun HomeScreen(state: CloakState, onNavigate: (CloakDestination) -> Unit, onQuick: (QuickAction) -> Unit) {
    val attention = HomeAttention.items(state)
    val loading = HomeAttention.loading(state)
    val next = HomeAttention.nextStep(state)
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("home"),
        contentPadding = PaddingValues(horizontal = CloakSpace.gutter, vertical = CloakSpace.inner),
        verticalArrangement = Arrangement.spacedBy(CloakSpace.item),
    ) {
        item {
            CloakHeroCard(
                title = when {
                    loading -> "Checking Cyclone…"
                    attention.isEmpty() -> "Everything looks ready"
                    attention.size == 1 -> "1 thing needs attention"
                    else -> "${attention.size} things need attention"
                },
                body = when {
                    loading -> "Reading profiles and bindings from Cyclone."
                    attention.isEmpty() -> "Cyclone is connected and every bound app is in place."
                    else -> "Tap an item to go where it can be fixed."
                },
            )
        }
        item {
            Row(Modifier.fillMaxWidth().testTag("home-quick"), horizontalArrangement = Arrangement.SpaceEvenly) {
                quickTiles.forEach { tile ->
                    QuickActionTile(tile, enabled = quickEnabled(tile.action, state)) { onQuick(tile.action) }
                }
            }
        }

        if (state.registry.isNotEmpty()) {
            item { CloakSectionHeader("Working profiles", state.registry.size) }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(state.registry.filter { it.state != "in_trash" }, key = { it.id }) { profile ->
                        Card(onClick = { onNavigate(CloakDestination.PROFILES) },
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            shape = MaterialTheme.shapes.large) {
                            Column(Modifier.width(248.dp).padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(if (profile.id == state.ownProfileId) "Profile · In use" else "Cyclone profile",
                                    style = MaterialTheme.typography.labelMedium, color = CloakGlass.muted)
                                CloakProfileAvatar(profile.id, profile.label, profile.id == state.ownProfileId, profile.state != "ready")
                                Text(profile.label, fontSize = 32.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold)
                                Text("${profile.packages?.size ?: 0} apps", color = CloakGlass.muted)
                            }
                        }
                    }
                }
            }
        }
        if (next != null) item {
            CloakActionButton(next.title, Icons.Rounded.ChevronRight, { onNavigate(next.destination) },
                modifier = Modifier.fillMaxWidth())
        }

        item { CloakSectionHeader("Needs attention", attention.size.takeIf { !loading }) }
        item {
            CloakGroup(Modifier.testTag("home-attention")) {
                when {
                    loading -> CloakRow("Waiting for Cyclone", supporting = "This usually takes a moment.", showChevron = false) {
                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    }
                    attention.isEmpty() -> CloakRow("Nothing needs attention", supporting = "Checks re-run whenever Cyclone reports a change.",
                        icon = Icons.Rounded.CheckCircle, tone = CloakStatusTone.READY)
                    else -> attention.forEachIndexed { index, item ->
                        if (index > 0) CloakGroupDivider()
                        CloakRow(item.title, supporting = item.detail, tone = item.tone,
                            icon = if (item.tone == CloakStatusTone.ERROR) Icons.Rounded.ErrorOutline else Icons.Rounded.WarningAmber,
                            onClick = { onNavigate(item.destination) })
                    }
                }
            }
        }

        item { CloakSectionHeader(if (next == null) "Setup complete" else "Next step") }
        item {
            CloakGroup(Modifier.testTag("home-steps")) {
                HomeAttention.steps(state).forEachIndexed { index, step ->
                    if (index > 0) CloakGroupDivider()
                    val current = step == next
                    CloakRow(
                        "${index + 1}. ${step.title}",
                        supporting = when { step.done -> "Done"; current -> "Up next"; else -> null },
                        icon = if (step.done) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                        tone = if (step.done) CloakStatusTone.READY else if (current) null else CloakStatusTone.PENDING,
                        onClick = { onNavigate(step.destination) },
                    )
                }
            }
        }

        item { CloakSectionHeader("Library") }
        item {
            CloakGroup {
                Shortcut(Icons.Rounded.Badge, "Identities", "${state.identities.size} saved · ${state.phones.size} phone profiles") { onNavigate(CloakDestination.IDENTITIES) }
                CloakGroupDivider()
                Shortcut(Icons.Rounded.Layers, "Profiles", "${state.registry.size} Cyclone profiles · ${state.bindings.size} bound apps") { onNavigate(CloakDestination.PROFILES) }
                CloakGroupDivider()
                Shortcut(Icons.Rounded.HealthAndSafety, "Health", "Readiness checks, repair and duplicate review") { onNavigate(CloakDestination.HEALTH) }
            }
        }
    }
}

/** Repair waits for a running check. Refresh waits for a running sync. The rest wait for any running action. */
private fun quickEnabled(action: QuickAction, state: CloakState): Boolean = when (action) {
    QuickAction.REFRESH -> !state.syncing
    QuickAction.REPAIR -> state.busy == null && state.rootDoctor.code != RootDoctorCode.CHECKING
    QuickAction.NEW_PROFILE, QuickAction.NEW_IDENTITY -> state.busy == null
}

@Composable
private fun QuickActionTile(tile: QuickTile, enabled: Boolean, onClick: () -> Unit) {
    Column(
        Modifier
            .testTag("quick-${tile.action.name.lowercase()}")
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer) {
            Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) { Icon(tile.icon, contentDescription = null) }
        }
        Text(tile.label, style = MaterialTheme.typography.labelLarge, maxLines = 1,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else CloakGlass.muted)
    }
}

@Composable
private fun Shortcut(icon: ImageVector, title: String, supporting: String, onClick: () -> Unit) =
    CloakRow(title, supporting = supporting, icon = icon, onClick = onClick)
