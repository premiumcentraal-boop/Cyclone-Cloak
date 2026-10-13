package dev.cyclone.cloak.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.cyclone.cloak.data.PostureRecord
import dev.cyclone.cloak.data.PostureState

object PostureSummary {
    fun label(record: PostureRecord?): String = "Local compatibility posture: " + when (record?.state) {
        null -> "not recorded"
        PostureState.UNKNOWN -> "Unknown"
        PostureState.PASS -> "Pass"
        PostureState.DEGRADED -> "Degraded"
        PostureState.FAILED -> "Failed"
        PostureState.NEEDS_ROTATION -> "Needs rotation"
    }
}

@Composable
internal fun PostureSummaryLine(record: PostureRecord?, modifier: Modifier = Modifier) {
    Text(PostureSummary.label(record), modifier = modifier, style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
}
