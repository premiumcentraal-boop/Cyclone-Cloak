package dev.cyclone.cloak.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import dev.cyclone.cloak.cyclone.ConnectionStatus
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
internal fun ConnectionStatusChip(state: ConnectionStatus, onRetry: () -> Unit) {
    var details by remember { mutableStateOf(false) }
    Column {
        TextButton(onClick = { details = true }) { Text(state.label, style = MaterialTheme.typography.labelSmall) }
        state.cached?.let {
            Text("Last seen " + DateTimeFormatter.ofPattern("MM-dd HH:mm").withZone(ZoneId.systemDefault())
                .format(Instant.ofEpochMilli(it.seenAt)), style = MaterialTheme.typography.labelSmall)
        }
    }
    if (details) AlertDialog(onDismissRequest = { details = false }, title = { Text(state.label) },
        text = { Text(state.detail) },
        confirmButton = { if (state.state != dev.cyclone.cloak.cyclone.ConnectionState.RETRYING)
            TextButton(onClick = { details = false; onRetry() }) { Text("Retry") } },
        dismissButton = { TextButton(onClick = { details = false }) { Text("Close") } })
}
