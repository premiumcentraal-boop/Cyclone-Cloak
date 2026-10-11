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
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.cyclone.cloak.data.CloakBinding
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
    val profiles = state.report?.snapshot?.registry?.associateBy { it.id }
    val identityIds = state.identities.map { it.first }.toSet()
    val diagnostics = HealthDiagnostics.assess(DoctorFacts(state.rootDoctor, appVersion, state.report?.headline,
        state.syncing, state.report?.gate, state.providerState, state.bindings.map { binding ->
            val profile = profiles?.get(binding.profileId)
            BindingDoctorFacts(
                displayContext = "${profile?.label ?: binding.profileId} · ${binding.packageName}",
                enabled = binding.enabled, health = binding.state,
                profilePresent = profiles?.containsKey(binding.profileId), profileReady = profile?.bindable,
                userMatches = profile?.androidUserId?.let { it == binding.androidUserId },
                appListed = profile?.packages?.contains(binding.packageName),
                identityPresent = binding.origin == CloakBinding.ORIGIN_MAIN || binding.cloakProfileId in identityIds,
                issuePresent = state.report?.issues?.containsKey("${binding.profileId}\n${binding.packageName}") == true,
            )
        }))
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        HealthDoctorCard(diagnostics) { report ->
            context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Cyclone Cloak diagnostics", report))
            Toast.makeText(context, "Redacted diagnostics copied", Toast.LENGTH_SHORT).show()
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) {
            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Check and repair", style = MaterialTheme.typography.titleMedium)
                Text("Root Doctor checks setup, may queue a matching module repair, and publishes profile state. It does not restart the device.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = vm::runRootDoctor, enabled = state.rootDoctor.code != RootDoctorCode.CHECKING && state.busy == null,
                    modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.small) { Text("Check & repair") }
                TextButton(onClick = { openMagisk(context) }) { Text("Open Magisk") }
                if (state.rootDoctor.code != RootDoctorCode.READY) {
                    TextButton(onClick = { openModuleDownload(context) }) { Text("Get module ZIP") }
                }
                CloakActionButton("Reconnect to Cyclone", Icons.Rounded.Refresh, vm::refresh, Modifier.fillMaxWidth(), outlined = true)
            }
        }
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
