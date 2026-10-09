package dev.cyclone.cloak.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.cyclone.cloak.forge.PhoneDraft
import dev.cyclone.cloak.forge.ProfileValidator

private val RELEASES = listOf("11", "12", "13", "14", "15", "16")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PhoneEditorScreen(
    editor: PhoneEditor,
    busy: Boolean,
    onChange: (PhoneDraft) -> Unit,
    onSave: () -> Unit,
    onClose: () -> Unit,
) {
    val d = editor.draft
    val findings = editor.findings
    val byField = findings.filter { it.field != null }.groupBy { it.field!! }
    fun problem(path: String): String? = byField[path]?.joinToString("\n") { it.message }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(if (editor.isNew) "Build a phone" else "Edit ${d.label.ifBlank { "phone" }}") },
                navigationIcon = {
                    IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    Button(onClick = onSave, enabled = editor.canSave && !busy, modifier = Modifier.padding(end = 8.dp)) { Text("Save") }
                },
            )
        },
    ) { inner ->
        Column(
            Modifier.fillMaxSize().padding(inner).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            editor.note?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            StatusLine(findings)

            Section("Name") {
                Field("Name in Cloak", d.label, { onChange(d.copy(label = it)) }, hint = "For example, Pixel 8 Germany")
            }

            Section("Device") {
                Field("Manufacturer", d.manufacturer, { onChange(d.copy(manufacturer = it)) }, problem("device.manufacturer"), "Google")
                Field("Brand", d.brand, { onChange(d.copy(brand = it)) }, problem("device.brand"), "google")
                Field("Model", d.model, { onChange(d.copy(model = it)) }, problem("device.model"), "Pixel 8")
                Field("Product name", d.product, { onChange(d.copy(product = it)) }, problem("device.product"), "shiba")
                Field("Device codename", d.device, { onChange(d.copy(device = it)) }, problem("device.device"), "shiba")
                Field("Hardware", d.hardware, { onChange(d.copy(hardware = it)) }, problem("device.hardware"), "zuma")
            }

            Section("Android build") {
                Text("Android version", style = MaterialTheme.typography.labelLarge)
                Chips(RELEASES, d.versionRelease) { onChange(d.withRelease(it)) }
                Field("Android version", d.versionRelease, { onChange(d.withRelease(it)) }, problem("device.version_release"), "14")
                Field("SDK level", d.sdkInt, { onChange(d.copy(sdkInt = it)) }, problem("device.sdk_int"), "34", number = true)
                Field("Security patch", d.securityPatch, { onChange(d.copy(securityPatch = it)) }, problem("device.security_patch"), "2024-03-05")
                Field("Build ID", d.buildId, { onChange(d.copy(buildId = it)) }, problem("device.build_id"), "AP1A.240305.019")
                Field("Build number (incremental)", d.versionIncremental, { onChange(d.copy(versionIncremental = it)) }, problem("device.version_incremental"), "11445699")
                Field(
                    "Build date (Unix seconds)", d.buildDateUtc, { onChange(d.copy(buildDateUtc = it)) }, problem("device.build_date_utc"),
                    "Empty: the security patch day", number = true,
                )
                Field("First API level", d.firstApiLevel, { onChange(d.copy(firstApiLevel = it)) }, problem("device.first_api_level"), "Empty: the SDK level", number = true)
                Field("Bootloader", d.bootloader, { onChange(d.copy(bootloader = it)) }, null, "Optional")
                Field("Baseband", d.baseband, { onChange(d.copy(baseband = it)) }, null, "Optional")
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Build fingerprint from the fields above", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "A real fingerprint is made from them. Turn off only to paste one from a real phone.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = d.autoFingerprint, onCheckedChange = { auto ->
                        onChange(d.copy(autoFingerprint = auto, fingerprint = if (!auto && d.fingerprint.isBlank()) d.composedFingerprint else d.fingerprint))
                    })
                }
                if (d.autoFingerprint) {
                    Text(d.composedFingerprint, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                    problem("device.fingerprint")?.let { ErrorText(it) }
                } else {
                    Field("Build fingerprint", d.fingerprint, { onChange(d.copy(fingerprint = it)) }, problem("device.fingerprint"), "brand/product/device:14/…:user/release-keys")
                }
            }

            Section("Mobile network") {
                Field("Carrier", d.carrierName, { onChange(d.copy(carrierName = it)) }, problem("telephony.carrier_name"), "Vodafone")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Field("MCC", d.mcc, { onChange(d.copy(mcc = it)) }, problem("telephony.mcc"), "262", number = true, modifier = Modifier.weight(1f))
                    Field("MNC", d.mnc, { onChange(d.copy(mnc = it)) }, problem("telephony.mnc"), "02", number = true, modifier = Modifier.weight(1f))
                }
                Text("Network", style = MaterialTheme.typography.labelLarge)
                Chips(ProfileValidator.NETWORK_TYPES, d.networkType) { onChange(d.copy(networkType = it)) }
                problem("telephony.network_type")?.let { ErrorText(it) }
                Text("SIM slots", style = MaterialTheme.typography.labelLarge)
                Chips(listOf("1", "2"), d.simSlots) { onChange(d.copy(simSlots = it)) }
                problem("telephony.sim_slot_count")?.let { ErrorText(it) }
                Field("Region hint", d.egressHint, { onChange(d.copy(egressHint = it)) }, null, "Optional, for example eu-central")
            }

            Section("Screen") {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Field("Width", d.width, { onChange(d.copy(width = it)) }, problem("display.width"), "1080", number = true, modifier = Modifier.weight(1f))
                    Field("Height", d.height, { onChange(d.copy(height = it)) }, problem("display.height"), "2400", number = true, modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Field("Density (dpi)", d.density, { onChange(d.copy(density = it)) }, problem("display.density"), "420", number = true, modifier = Modifier.weight(1f))
                    Field("Refresh (Hz)", d.refreshRate, { onChange(d.copy(refreshRate = it)) }, problem("display.refresh_rate_hz"), "120", number = true, modifier = Modifier.weight(1f))
                }
                Text("Size class", style = MaterialTheme.typography.labelLarge)
                Chips(ProfileValidator.DISPLAY_CLASSES, d.screenClass) { onChange(d.copy(screenClass = it)) }
                problem("display.screen_size_class")?.let { ErrorText(it) }
            }

            Section("Language and region") {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Field("Language", d.language, { onChange(d.copy(language = it)) }, problem("locale.language"), "de", modifier = Modifier.weight(1f))
                    Field("Country", d.country, { onChange(d.copy(country = it)) }, problem("locale.country"), "DE", modifier = Modifier.weight(1f))
                }
                Field("Time zone", d.timezone, { onChange(d.copy(timezone = it)) }, problem("locale.timezone"), "Europe/Berlin")
            }

            if (!editor.isNew) {
                Text(
                    "Identities already made from this phone keep their own copy; changes apply to new identities.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Button(onClick = onSave, enabled = editor.canSave && !busy, modifier = Modifier.fillMaxWidth()) {
                Text(if (editor.canSave) "Save phone" else "Fix the marked fields to save")
            }
        }
    }
}

@Composable
private fun StatusLine(findings: List<ProfileValidator.Finding>) {
    val errors = findings.count { it.isError }
    val warnings = findings.size - errors
    val (label, tone) = when {
        errors > 0 -> "$errors to fix" to CloakStatusTone.ERROR
        warnings > 0 -> "Holds together · $warnings to check" to CloakStatusTone.PENDING
        else -> "Holds together" to CloakStatusTone.READY
    }
    CloakStatusPill(label, tone)
    // Say where: the marked fields may be far down the form.
    val where = findings.filter { it.isError }.mapNotNull { it.field }.distinct().map(::fieldName)
    if (where.isNotEmpty()) {
        Text("To fix: ${where.joinToString(", ")}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }
    // Findings with no field of their own (rare) are listed here so nothing is hidden.
    findings.filter { it.field == null }.takeIf { it.isNotEmpty() }?.let { FindingsList(it) }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

@Composable
private fun Field(
    label: String,
    value: String,
    onValue: (String) -> Unit,
    problem: String? = null,
    hint: String = "",
    number: Boolean = false,
    modifier: Modifier = Modifier.fillMaxWidth(),
) {
    OutlinedTextField(
        value = value,
        onValueChange = { if (it.length <= 200) onValue(it) },
        modifier = modifier,
        label = { Text(label) },
        placeholder = { if (hint.isNotEmpty()) Text(hint) },
        singleLine = true,
        isError = problem != null,
        supportingText = problem?.let { { Text(it) } },
        keyboardOptions = if (number) KeyboardOptions(keyboardType = KeyboardType.Number) else KeyboardOptions.Default,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Chips(options: List<String>, selected: String, onSelect: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            FilterChip(selected = option == selected, onClick = { onSelect(option) }, label = { Text(option) })
        }
    }
}

@Composable
private fun ErrorText(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
}

/** Findings as plain lines, errors first. */
@Composable
internal fun FindingsList(findings: List<ProfileValidator.Finding>) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        findings.sortedBy { if (it.isError) 0 else 1 }.forEach { finding ->
            Text(
                (if (finding.isError) "• " else "◦ ") + finding.message,
                style = MaterialTheme.typography.bodySmall,
                color = if (finding.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** "display.refresh_rate_hz" → "screen refresh". */
private fun fieldName(path: String): String = when (path) {
    "device.manufacturer" -> "manufacturer"
    "device.brand" -> "brand"
    "device.model" -> "model"
    "device.product" -> "product name"
    "device.device" -> "device codename"
    "device.hardware" -> "hardware"
    "device.fingerprint" -> "fingerprint"
    "device.version_release" -> "Android version"
    "device.sdk_int" -> "SDK level"
    "device.security_patch" -> "security patch"
    "device.build_id" -> "build ID"
    "device.version_incremental" -> "build number"
    "device.build_date_utc" -> "build date"
    "device.first_api_level" -> "first API level"
    "telephony.carrier_name" -> "carrier"
    "telephony.mcc" -> "MCC"
    "telephony.mnc" -> "MNC"
    "telephony.network_type" -> "network"
    "telephony.sim_slot_count" -> "SIM slots"
    "display.width" -> "screen width"
    "display.height" -> "screen height"
    "display.density" -> "density"
    "display.refresh_rate_hz" -> "screen refresh"
    "display.screen_size_class" -> "size class"
    "locale.language" -> "language"
    "locale.country" -> "country"
    "locale.timezone" -> "time zone"
    else -> path.substringAfter('.')
}
