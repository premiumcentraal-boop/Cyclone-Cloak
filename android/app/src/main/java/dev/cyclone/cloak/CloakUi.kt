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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.platform.LocalClipboardManager
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
    val fleetCount = mutableStateOf(20)
    val fleetTemplate = mutableStateOf("pixel_7")
    val newIdentityName = mutableStateOf("")
    val creatingIdentity = mutableStateOf(false)
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
    onCreateIdentity: (String, String) -> Unit,
    onForgeFleet: (Int) -> Unit,
    onFleetBind: () -> Unit,
    onExportFleet: () -> Unit,
    onImportFleet: () -> Unit,
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
                    title = "Give every profile its own device",
                    body = "Create or import an identity, review its values, then bind it to a Cyclone profile.",
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
                CloakCreationCard(
                    count = model.fleetCount.value,
                    template = model.fleetTemplate.value,
                    name = model.newIdentityName.value,
                    creating = model.creatingIdentity.value,
                    onNameChange = { value -> if (value.length <= 64) model.newIdentityName.value = value },
                    onCountChange = { model.fleetCount.value = it },
                    onTemplateChange = { model.fleetTemplate.value = it },
                    onCreate = { onCreateIdentity(model.newIdentityName.value, model.fleetTemplate.value) },
                    onForge = { onForgeFleet(model.fleetCount.value) },
                    onFleetBind = onFleetBind,
                    onExport = onExportFleet,
                    onImport = onImportFleet,
                )
            }

            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CloakActionButton("Import identity", Icons.Rounded.Add, onImport, Modifier.weight(1.2f), outlined = true)
                    CloakActionButton("Refresh", Icons.Rounded.Refresh, onReload, Modifier.weight(0.8f), outlined = true)
                }
            }

            item { CloakSectionTitle("Device identities", model.cloakProfiles.size) }
            if (model.cloakProfiles.isEmpty()) {
                item {
                    CloakEmptyState(
                        title = "No identities yet",
                        body = "Generate a device identity above or import a Cyclone Cloak profile.",
                        icon = Icons.Rounded.Add,
                    )
                }
            } else {
                items(model.cloakProfiles, key = { it.first }) { (id, profile) ->
                    CloakIdentityCard(
                        id = id,
                        profile = profile,
                        selected = model.selectedCloakProfile.value == id,
                        onSelect = { onSelectCloak(id) },
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

            item {
                val identityName = model.selectedCloakProfile.value
                    ?.let { selected -> model.cloakProfiles.firstOrNull { it.first == selected }?.second?.optString("name") }
                    .orEmpty()
                val cycloneName = model.selectedCycloneProfile.value?.optString("label").orEmpty()
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        when {
                            identityName.isNotBlank() && cycloneName.isNotBlank() -> "$identityName → $cycloneName"
                            identityName.isNotBlank() -> "Choose a Cyclone profile to finish binding"
                            cycloneName.isNotBlank() -> "Choose a device identity to finish binding"
                            else -> "Choose a device identity and a Cyclone profile"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    CloakActionButton("Bind selected identity", Icons.Rounded.ChevronRight, onApply, Modifier.fillMaxWidth())
                }
            }

            item { CloakSectionTitle("Current bindings", model.bindings.size) }
            if (model.bindings.isEmpty()) {
                item {
                    Text(
                        "No apps are bound yet. Select an identity and Cyclone profile above, then bind them.",
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
        RootDoctorCode.MODULE_BUNDLE_INVALID,
        RootDoctorCode.MODULE_INSTALL_FAILED,
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

@Composable
private fun CloakCreationCard(
    count: Int,
    template: String,
    name: String,
    creating: Boolean,
    onNameChange: (String) -> Unit,
    onCountChange: (Int) -> Unit,
    onTemplateChange: (String) -> Unit,
    onCreate: () -> Unit,
    onForge: () -> Unit,
    onFleetBind: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Create a device", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "Make a new device identity directly on this phone. Each one gets its own fresh identifier set.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = name,
                onValueChange = onNameChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Device name") },
                placeholder = { Text("For example, Work Pixel") },
                singleLine = true,
                supportingText = { Text("${name.length}/64 characters") },
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("pixel_4" to "Pixel 4", "pixel_7" to "Pixel 7", "galaxy_s23" to "Galaxy S23").forEach { (id, label) ->
                    if (id == template) {
                        Button(onClick = { onTemplateChange(id) }, modifier = Modifier.weight(1f), shape = MaterialTheme.shapes.small) {
                            Text(label)
                        }
                    } else {
                        OutlinedButton(onClick = { onTemplateChange(id) }, modifier = Modifier.weight(1f), shape = MaterialTheme.shapes.small) {
                            Text(label)
                        }
                    }
                }
            }
            Text(
                when (template) { "pixel_4" -> "Google · Android 13 · flame" "pixel_7" -> "Google · Android 13 · panther" else -> "Samsung · Android 13 · S23 family" },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Text(
                "Identifiers are generated per profile. They are not readings of your phone’s physical identifiers.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = onCreate,
                enabled = name.isNotBlank() && !creating,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.small,
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null)
                Text(if (creating) "Creating device…" else "Create device identity", modifier = Modifier.padding(start = 8.dp))
            }
            HorizontalDivider()
            Text("Create a fleet", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                "Generate multiple uniquely seeded identities from the same device family.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = { onCountChange((count - 1).coerceIn(1, CloakFleet.MAX_FLEET_SIZE)) }, shape = MaterialTheme.shapes.small) {
                    Icon(Icons.Rounded.Remove, contentDescription = "Fewer identities")
                }
                Text(
                    "$count identities",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                Button(onClick = { onCountChange((count + 1).coerceIn(1, CloakFleet.MAX_FLEET_SIZE)) }, shape = MaterialTheme.shapes.small) {
                    Icon(Icons.Rounded.Add, contentDescription = "More identities")
                }
            }
            Button(onClick = onForge, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.small) {
                Text("Generate $count identities")
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onFleetBind, modifier = Modifier.weight(1f), shape = MaterialTheme.shapes.small) {
                    Text("Bind fleet")
                }
                OutlinedButton(onClick = onExport, modifier = Modifier.weight(1f), shape = MaterialTheme.shapes.small) {
                    Text("Export fleet")
                }
                OutlinedButton(onClick = onImport, modifier = Modifier.weight(1f), shape = MaterialTheme.shapes.small) {
                    Text("Import fleet")
                }
            }
        }
    }
}

@Composable
private fun CloakIdentityCard(
    id: String,
    profile: JSONObject,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    val expanded = remember(id) { mutableStateOf(false) }
    val device = profile.optJSONObject("device")
    val deviceSummary = listOf(
        device.profileValue("manufacturer"),
        device.profileValue("model"),
        device.profileValue("version_release").let { if (it == "Not included") "" else "Android $it" },
    ).filter(String::isNotBlank).joinToString(" · ")

    Card(
        onClick = onSelect,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(profile.optString("name").ifBlank { id }, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        deviceSummary.ifBlank { "Device details not included" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        if (selected) "Selected for binding" else "Tap to select",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (selected) Icon(Icons.Rounded.CheckCircle, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary)
                IconButton(
                    onClick = { expanded.value = !expanded.value },
                    modifier = Modifier.size(44.dp),
                ) {
                    Icon(
                        if (expanded.value) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                        contentDescription = if (expanded.value) "Hide profile details" else "Show profile details",
                    )
                }
            }
            if (expanded.value) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surface,
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(
                            "These are the values stored in this Cloak profile. Bound apps may see these values; they are separate from your phone’s physical identifiers.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        CloakDetailGroup("Device identity", listOf(
                            "Manufacturer" to device.profileValue("manufacturer"),
                            "Brand" to device.profileValue("brand"),
                            "Model" to device.profileValue("model"),
                            "Product" to device.profileValue("product"),
                            "Device codename" to device.profileValue("device"),
                            "Hardware" to device.profileValue("hardware"),
                            "Build fingerprint" to device.profileValue("fingerprint"),
                            "Android release" to device.profileValue("version_release"),
                            "SDK level" to device.profileValue("sdk_int"),
                            "Security patch" to device.profileValue("security_patch"),
                            "Build ID" to device.profileValue("build_id"),
                            "Build increment" to device.profileValue("version_incremental"),
                            "Bootloader" to device.profileValue("bootloader"),
                            "Baseband" to device.profileValue("baseband"),
                        ))
                        val identifiers = profile.optJSONObject("identifiers")
                        CloakDetailGroup("Device identifiers", listOf(
                            "Android ID" to identifiers.profileValue("android_id"),
                            "Advertising ID" to identifiers.profileValue("advertising_id"),
                            "App Set ID" to identifiers.profileValue("app_set_id"),
                            "Wi-Fi MAC" to identifiers.profileValue("mac"),
                            "Bluetooth MAC" to identifiers.profileValue("bt_mac"),
                            "IMEI · SIM 1" to identifiers.profileValue("imei_primary"),
                            "IMEI · SIM 2" to identifiers.profileValue("imei_secondary"),
                            "SIM serial" to identifiers.profileValue("sim_serial"),
                            "GSF ID" to identifiers.profileValue("gsf_id"),
                            "Widevine ID" to identifiers.profileValue("widevine_id"),
                            "Device serial" to identifiers.profileValue("serial"),
                        ))
                        CloakDetailGroup("SIM & network", listOf(
                            "SIM slots" to profile.optJSONObject("telephony").profileValue("sim_slot_count"),
                            "Carrier" to profile.optJSONObject("telephony").profileValue("carrier_name"),
                            "Mobile country code" to profile.optJSONObject("telephony").profileValue("mcc"),
                            "Mobile network code" to profile.optJSONObject("telephony").profileValue("mnc"),
                            "Network type" to profile.optJSONObject("telephony").profileValue("network_type"),
                            "Egress hint" to profile.optJSONObject("network").profileValue("egress_hint"),
                        ))
                        CloakDetailGroup("Display & locale", listOf(
                            "Resolution" to profile.optJSONObject("display").let { display ->
                                val width = display.profileValue("width")
                                val height = display.profileValue("height")
                                if (width == "Not included" || height == "Not included") "Not included" else "$width × $height"
                            },
                            "Density" to profile.optJSONObject("display").profileValue("density"),
                            "Refresh rate" to profile.optJSONObject("display").profileValue("refresh_rate_hz").let {
                                if (it == "Not included") it else "$it Hz"
                            },
                            "Language" to profile.optJSONObject("locale").profileValue("language"),
                            "Country" to profile.optJSONObject("locale").profileValue("country"),
                            "Time zone" to profile.optJSONObject("locale").profileValue("timezone"),
                            "Browser user agent" to profile.optJSONObject("ua").profileValue("value"),
                        ))
                    }
                }
            }
        }
    }
}

@Composable
private fun CloakDetailGroup(title: String, values: List<Pair<String, String>>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        values.forEach { (label, value) -> CloakDetailRow(label, value) }
    }
}

@Composable
private fun CloakDetailRow(label: String, value: String) {
    val clipboard = LocalClipboardManager.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(vertical = 5.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            SelectionContainer {
                Text(value, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
            }
        }
        IconButton(
            onClick = { clipboard.setText(AnnotatedString(value)) },
            modifier = Modifier.size(40.dp),
        ) {
            Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy $label")
        }
    }
}

private fun JSONObject?.profileValue(key: String): String {
    val value = this?.opt(key)?.takeUnless { it == JSONObject.NULL }?.toString().orEmpty()
    return value.takeIf { it.isNotBlank() } ?: "Not included"
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
