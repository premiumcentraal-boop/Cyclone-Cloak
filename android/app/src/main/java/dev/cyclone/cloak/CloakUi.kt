package dev.cyclone.cloak

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.json.JSONObject

class CloakUiModel {
    val status = mutableStateOf("Checking Cyclone")
    val providerState = mutableStateOf("not registered")
    val message = mutableStateOf("")
    val profiles = mutableStateListOf<JSONObject>()
    val cloakProfiles = mutableStateListOf<Pair<String, JSONObject>>()
    val selectedCloakProfile = mutableStateOf<String?>(null)
    val selectedCycloneProfile = mutableStateOf<JSONObject?>(null)
    val bindings = mutableStateListOf<CloakBinding>()
    val rootDoctor = mutableStateOf(RootDoctorResult(RootDoctorCode.NOT_CHECKED))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloakUi(
    model: CloakUiModel,
    onImport: () -> Unit,
    onReload: () -> Unit,
    onApply: () -> Unit,
    onSelectCloak: (String) -> Unit,
    onSelectProfile: (JSONObject) -> Unit,
    onToggleBinding: (CloakBinding) -> Unit,
    onRemoveBinding: (CloakBinding) -> Unit,
    onRootDoctor: () -> Unit,
    onOpenMagisk: () -> Unit,
    onGetModule: () -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                title = {
                    Row(horizontalArrangement = Arrangement.spacedBy(11.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary) {
                            Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                                Text("C", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                            }
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                            Text("Cyclone Cloak", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("IDENTITY MANAGER", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                },
                actions = {
                    CloakStatusPill(model.status.value, statusTone(model.status.value))
                },
            )
        },
    ) { inner ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(inner),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                CloakHeroCard(
                    title = "One identity for every profile",
                    body = "Import a device identity, then bind it to the apps in a Cyclone profile.",
                )
            }

            item {
                CloakRootDoctorCard(
                    result = model.rootDoctor.value,
                    onCheck = onRootDoctor,
                    onOpenMagisk = onOpenMagisk,
                    onGetModule = onGetModule,
                )
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                ) {
                    Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text("Cyclone connector", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text("Provider · ${model.providerState.value}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            CloakStatusPill(model.providerState.value, statusTone(model.providerState.value))
                        }
                        if (model.message.value.isNotBlank()) {
                            Text(model.message.value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CloakActionButton("Import profile", Icons.Rounded.Add, onImport, Modifier.weight(1.2f), outlined = true)
                    CloakActionButton("Reload", Icons.Rounded.Refresh, onReload, Modifier.weight(0.8f), outlined = true)
                }
            }
            item {
                CloakActionButton("Bind identity", Icons.Rounded.ChevronRight, onApply, Modifier.fillMaxWidth())
            }

            item { CloakSectionTitle("Bindings", model.bindings.size) }
            if (model.bindings.isEmpty()) {
                item {
                    Text(
                        "No bindings yet. Select a Cyclone profile above, then tap Bind identity.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(model.bindings, key = { it.profileId + "/" + it.androidUserId + "/" + it.packageName }) { binding ->
                    CloakBindingCard(
                        binding = binding,
                        onToggle = onToggleBinding,
                        onRemove = onRemoveBinding,
                    )
                }
            }
            item { CloakSectionTitle("Cloak identities", model.cloakProfiles.size) }
            if (model.cloakProfiles.isEmpty()) {
                item {
                    CloakEmptyState(
                        title = "No identities yet",
                        body = "Import a Cyclone Cloak profile to get started.",
                        icon = Icons.Rounded.Add,
                    )
                }
            } else {
                items(model.cloakProfiles, key = { it.first }) { (id, profile) ->
                    val selected = model.selectedCloakProfile.value == id
                    CloakChoiceCard(
                        title = profile.optString("name", id),
                        subtitle = if (selected) "Selected for binding" else "Tap to select",
                        selected = selected,
                        onClick = { onSelectCloak(id) },
                    )
                }
            }

            item { CloakSectionTitle("Cyclone profiles", model.profiles.size) }
            if (model.profiles.isEmpty()) {
                item {
                    CloakEmptyState(
                        title = "No Cyclone profiles found",
                        body = "Check that Cyclone is installed and approved, then reload.",
                        icon = Icons.Rounded.CheckCircle,
                    )
                }
            } else {
                items(model.profiles, key = { it.optString("id") }) { profile ->
                    val selected = model.selectedCycloneProfile.value?.optString("id") == profile.optString("id")
                    val appCount = profile.optJSONArray("packages")?.length() ?: 0
                    val userId = profile.optInt("androidUserId", 0)
                    CloakChoiceCard(
                        title = profile.optString("label", profile.optString("id")),
                        subtitle = "$appCount ${if (appCount == 1) "app" else "apps"} · user $userId",
                        selected = selected,
                        onClick = { onSelectProfile(profile) },
                    )
                }
            }
        }
    }
}

@Composable
private fun CloakRootDoctorCard(
    result: RootDoctorResult,
    onCheck: () -> Unit,
    onOpenMagisk: () -> Unit,
    onGetModule: () -> Unit,
) {
    val tone = when (result.code) {
        RootDoctorCode.READY -> CloakStatusTone.READY
        RootDoctorCode.NOT_CHECKED, RootDoctorCode.CHECKING -> CloakStatusTone.PENDING
        else -> CloakStatusTone.ERROR
    }
    val showModuleLink = result.code in setOf(
        RootDoctorCode.NOT_CHECKED,
        RootDoctorCode.MODULE_MISSING,
        RootDoctorCode.MODULE_DISABLED,
        RootDoctorCode.MODULE_PENDING_REMOVAL,
        RootDoctorCode.MODULE_OUTDATED,
    )
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Root Doctor", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                CloakStatusPill(result.title, tone)
            }
            Text(result.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(
                onClick = onCheck,
                enabled = result.code != RootDoctorCode.CHECKING,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.small,
            ) {
                Text(if (result.code == RootDoctorCode.CHECKING) "Checking…" else if (result.code == RootDoctorCode.NOT_CHECKED) "Check & repair" else "Check again")
            }
            if (result.code != RootDoctorCode.READY && result.code != RootDoctorCode.CHECKING) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onOpenMagisk, modifier = Modifier.weight(1f)) { Text("Open Magisk") }
                    if (showModuleLink) {
                        TextButton(onClick = onGetModule, modifier = Modifier.weight(1f)) { Text("Get module ZIP") }
                    }
                }
            }
        }
    }
}

private fun statusTone(status: String): CloakStatusTone = when {
    status.equals("Connected", ignoreCase = true) || status.equals("ready", ignoreCase = true) -> CloakStatusTone.READY
    status.contains("checking", ignoreCase = true) || status.contains("approval", ignoreCase = true) || status.equals("not registered", ignoreCase = true) -> CloakStatusTone.PENDING
    else -> CloakStatusTone.ERROR
}

@Composable
private fun CloakBindingCard(
    binding: CloakBinding,
    onToggle: (CloakBinding) -> Unit,
    onRemove: (CloakBinding) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (binding.enabled) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(binding.packageName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "user ${binding.androidUserId} · ${binding.state}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (binding.state !in setOf("ready", "disabled")) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                TextButton(onClick = { onToggle(binding) }) { Text(if (binding.enabled) "Off" else "On") }
                TextButton(onClick = { onRemove(binding) }) { Text("Remove") }
            }
        }
    }
}
