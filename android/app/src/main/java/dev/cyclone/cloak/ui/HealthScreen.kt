package dev.cyclone.cloak.ui

import android.content.Context
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.cyclone.cloak.root.RootDoctorCode

@Composable
internal fun HealthScreen(state: CloakState, vm: CloakViewModel) {
    val context = LocalContext.current
    val coverage = remember(context) {
        runCatching {
            context.assets.open("compatibility-coverage.json").bufferedReader().use { CompatibilityCoverage.parse(it.readText()) }
        }.getOrNull()
    }
    @Suppress("DEPRECATION")
    val appVersion = remember(context) { runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() }
    val diagnostics = HealthDiagnostics.assess(ReadinessDoctorFacts.fromState(state, appVersion))
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(CloakSpace.item),
    ) {
        CloakNotice("Local diagnostics. These checks run on this device and are kept apart from saved identities and bindings.")
        CloakSectionHeader("Readiness")
        HealthDoctorCard(diagnostics) { report ->
            context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Cyclone Cloak diagnostics", report))
            Toast.makeText(context, "Redacted diagnostics copied", Toast.LENGTH_SHORT).show()
        }
        CloakSectionHeader("Check and repair")
        CloakGroup {
            Column(Modifier.fillMaxWidth().padding(CloakSpace.inner), verticalArrangement = Arrangement.spacedBy(CloakSpace.item)) {
                Text("Root Doctor checks setup, may queue a matching module repair, and publishes profile state. It does not restart the device.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                CloakActionButton("Check & repair", Icons.Rounded.Build, vm::runRootDoctor, Modifier.fillMaxWidth(),
                    enabled = state.rootDoctor.code != RootDoctorCode.CHECKING && state.busy == null)
            }
            CloakGroupDivider()
            CloakRow("Reconnect to Cyclone", icon = Icons.Rounded.Refresh, onClick = vm::refresh, showChevron = false)
            CloakGroupDivider()
            CloakRow("Open Magisk", icon = Icons.AutoMirrored.Rounded.OpenInNew, onClick = { openMagisk(context) }, showChevron = false)
            if (state.rootDoctor.code != RootDoctorCode.READY) {
                CloakGroupDivider()
                CloakRow("Get module ZIP", icon = Icons.Rounded.Download, onClick = { openModuleDownload(context) }, showChevron = false)
            }
        }
        CloakSectionHeader("Library review")
        UniquenessDiagnostics(state, vm::reviewIdentity)
        LibraryQualityCard(LibraryQuality.assess(state, java.time.Instant.now()),
            state.identities.associate { (id, profile) -> id to profile.optString("name") }, state.busy != null, vm::reviewIdentity)
        CloakSectionHeader("Coverage")
        CoverageMatrix(coverage)
    }
}

private fun openMagisk(context: Context) {
    val intent = context.packageManager.getLaunchIntentForPackage("com.topjohnwu.magisk")
    if (intent == null) {
        Toast.makeText(context, "Magisk app not found. Install Magisk, then check root setup again.", Toast.LENGTH_LONG).show()
        return
    }
    context.startActivity(intent)
}

private fun openModuleDownload(context: Context) {
    @Suppress("DEPRECATION")
    val version = context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
    val asset = "https://github.com/premiumcentraal-boop/Cyclone-Cloak/releases/download/v$version/cyclone-cloak-$version.zip"
    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(asset)))
}
