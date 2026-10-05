package dev.cyclone.cloak

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
) {
    Scaffold(topBar = { TopAppBar(title = { Text("Cyclone Cloak") }) }) { inner ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                ) {
                    Text(
                        model.status.value,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }

            item {
                Card(shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.padding(14.dp)) {
                        Text("Connector", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Provider: ${model.providerState.value}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            model.message.value,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onReload) {
                        Icon(Icons.Rounded.Refresh, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Reload")
                    }
                    OutlinedButton(onClick = onImport) {
                        Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Import")
                    }
                    Button(onClick = onApply) {
                        Icon(Icons.Rounded.ChevronRight, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Bind")
                    }
                }
            }

            item {
                Text("Cloak identities", style = MaterialTheme.typography.titleMedium)
            }

            if (model.cloakProfiles.isEmpty()) {
                item {
                    Text(
                        "No device identities imported yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(model.cloakProfiles) { (id, profile) ->
                    val selected = model.selectedCloakProfile.value == id
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectCloak(id) },
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                        ),
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .background(MaterialTheme.colorScheme.primary, CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    "C",
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    style = MaterialTheme.typography.titleSmall,
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(profile.optString("name", id), style = MaterialTheme.typography.titleSmall)
                                Text(
                                    if (selected) "Selected" else "Tap to select",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (selected) {
                                Icon(Icons.Rounded.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }

            item {
                Text("Cyclone profiles", style = MaterialTheme.typography.titleMedium)
            }

            if (model.profiles.isEmpty()) {
                item {
                    Text(
                        "No Cyclone profiles found yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(model.profiles) { profile ->
                    val selected = model.selectedCycloneProfile.value?.optString("id") == profile.optString("id")
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectProfile(profile) },
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                        ),
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .background(MaterialTheme.colorScheme.primary, CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    "C",
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    style = MaterialTheme.typography.titleSmall,
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(profile.optString("label", profile.optString("id")), style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "${profile.optJSONArray("packages")?.length() ?: 0} apps · user ${profile.optInt("androidUserId", 0)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (selected) {
                                Icon(Icons.Rounded.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        }
    }
}
