package dev.cyclone.cloak.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.HealthAndSafety
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/** The dashboard: what needs attention, the next setup step, and a way into each working screen. */
@Composable
internal fun HomeScreen(state: CloakState, onNavigate: (CloakDestination) -> Unit, onRefresh: () -> Unit) {
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
                CloakGroupDivider()
                CloakRow("Refresh from Cyclone", icon = Icons.Rounded.Refresh, onClick = onRefresh, showChevron = false)
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
                Shortcut(Icons.Rounded.PhoneAndroid, "Phones", "${state.phones.size} templates · ${state.phones.count { !it.builtIn }} your own") { onNavigate(CloakDestination.PHONES) }
                CloakGroupDivider()
                Shortcut(Icons.Rounded.Badge, "Identities", "${state.identities.size} saved") { onNavigate(CloakDestination.IDENTITIES) }
                CloakGroupDivider()
                Shortcut(Icons.Rounded.Layers, "Profiles", "${state.registry.size} Cyclone profiles · ${state.bindings.size} bound apps") { onNavigate(CloakDestination.PROFILES) }
                CloakGroupDivider()
                Shortcut(Icons.Rounded.HealthAndSafety, "Health", "Root Doctor: ${state.rootDoctor.title}") { onNavigate(CloakDestination.HEALTH) }
            }
        }
    }
}

@Composable
private fun Shortcut(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, supporting: String, onClick: () -> Unit) =
    CloakRow(title, supporting = supporting, icon = icon, onClick = onClick)
