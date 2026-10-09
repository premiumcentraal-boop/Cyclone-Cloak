package dev.cyclone.cloak.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
internal fun HealthScreen(state: CloakState, vm: CloakViewModel) {
    val context = LocalContext.current
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        CloakRootDoctorCard(
            result = state.rootDoctor,
            onCheck = vm::runRootDoctor,
            onOpenMagisk = { openMagisk(context) },
            onGetModule = { openModuleDownload(context) },
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) {
            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val headline = state.report?.headline ?: "Checking Cyclone"
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Cyclone connector", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    CloakStatusPill(headline, statusTone(headline))
                }
                state.report?.message?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("Startup provider · ${state.providerState}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                state.report?.gate?.let { gate ->
                    Text(
                        "Allowed: ${gate.granted.sorted().joinToString(", ").ifBlank { "nothing yet" }}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                CloakActionButton("Reconnect to Cyclone", Icons.Rounded.Refresh, vm::refresh, Modifier.fillMaxWidth(), outlined = true)
            }
        }
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
