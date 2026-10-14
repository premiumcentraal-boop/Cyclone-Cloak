package dev.cyclone.cloak.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.cyclone.cloak.cyclone.CompanionPresence
import dev.cyclone.cloak.cyclone.CompanionState

@Composable
internal fun CompanionPresenceCard(rows: List<CompanionPresence>, checking: Boolean, onCheck: () -> Unit) {
    CloakGroup {
        Column(Modifier.fillMaxWidth().padding(CloakSpace.inner), verticalArrangement = Arrangement.spacedBy(CloakSpace.item)) {
            Text("Companion copies", style = MaterialTheme.typography.titleMedium)
            Text("Read-only checks. Other users need root access; write access is verified inside each profile's Cloak.",
                style = MaterialTheme.typography.bodySmall)
            Button(onClick = onCheck, enabled = !checking) { Text(if (checking) "Checking companion copies…" else "Check companion copies") }
            if (rows.isEmpty()) Text("Reconnect to Cyclone to load profiles.", style = MaterialTheme.typography.bodySmall)
            rows.forEach { row ->
                Text("${row.label} · Android user ${row.user ?: "not assigned"}", style = MaterialTheme.typography.titleSmall)
                CloakStatusPill(row.state.label, when (row.state) {
                    CompanionState.READY -> CloakStatusTone.READY
                    CompanionState.MISSING, CompanionState.STORAGE_UNAVAILABLE -> CloakStatusTone.ERROR
                    else -> CloakStatusTone.PENDING
                })
                Text("Installed: ${row.facts.installed.label} · Launchable: ${row.facts.launchable.label}", style = MaterialTheme.typography.bodySmall)
                Text("Private directory: ${row.facts.storageExists.label} · Writable: ${row.facts.storageWritable.label}", style = MaterialTheme.typography.bodySmall)
                Text(row.reason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
