package dev.cyclone.cloak.ui

/* Cards shared by the screens. */

import dev.cyclone.cloak.data.*
import dev.cyclone.cloak.forge.*
import dev.cyclone.cloak.cyclone.*
import dev.cyclone.cloak.root.*
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
import androidx.compose.runtime.mutableStateMapOf
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

@Composable
internal fun CloakRootDoctorCard(
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
internal fun CloakIdentityCard(
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
                        if (selected) "Tap to close" else "Tap for bind, rename and delete",
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
internal fun CloakDetailGroup(title: String, values: List<Pair<String, String>>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        values.forEach { (label, value) -> CloakDetailRow(label, value) }
    }
}

@Composable
internal fun CloakDetailRow(label: String, value: String) {
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

internal fun JSONObject?.profileValue(key: String): String {
    val value = this?.opt(key)?.takeUnless { it == JSONObject.NULL }?.toString().orEmpty()
    return value.takeIf { it.isNotBlank() } ?: "Not included"
}

internal fun statusTone(status: String): CloakStatusTone = when {
    status.equals("Connected", ignoreCase = true) || status.equals("ready", ignoreCase = true) -> CloakStatusTone.READY
    status.contains("checking", ignoreCase = true) || status.contains("approval", ignoreCase = true) || status.equals("not registered", ignoreCase = true) -> CloakStatusTone.PENDING
    else -> CloakStatusTone.ERROR
}

@Composable
internal fun CloakProfileCard(
    title: String,
    subtitle: String,
    pill: CloakPill?,
    selected: Boolean,
    canOpen: Boolean,
    onClick: () -> Unit,
    onOpen: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        CloakChoiceCard(title = title, subtitle = subtitle, selected = selected, onClick = onClick)
        if (pill != null || canOpen) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (pill != null) {
                        CloakStatusPill(
                            pill.label,
                            when (pill.tone) {
                                CloakPillTone.READY -> CloakStatusTone.READY
                                CloakPillTone.ATTENTION -> CloakStatusTone.ERROR
                                CloakPillTone.NEUTRAL -> CloakStatusTone.PENDING
                            },
                        )
                        pill.reason?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                if (canOpen) TextButton(onClick = onOpen) { Text("Open in Cyclone") }
            }
        }
    }
}

@Composable
internal fun CloakBindingCard(
    binding: CloakBinding,
    profileLabel: String,
    issue: String?,
    onToggle: (CloakBinding) -> Unit,
    onRemove: (CloakBinding) -> Unit,
) {
    val mirrored = binding.origin == CloakBinding.ORIGIN_MAIN
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
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(binding.packageName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "$profileLabel · ${binding.state}" + if (mirrored) " · managed by Cloak in Main" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (binding.state !in setOf("ready", "disabled", "pending", "unknown")) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                if (issue != null) {
                    Text("Cyclone: $issue", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
            if (!mirrored) {
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    TextButton(onClick = { onToggle(binding) }) { Text(if (binding.enabled) "Off" else "On") }
                    TextButton(onClick = { onRemove(binding) }) { Text("Remove") }
                }
            }
        }
    }
}
