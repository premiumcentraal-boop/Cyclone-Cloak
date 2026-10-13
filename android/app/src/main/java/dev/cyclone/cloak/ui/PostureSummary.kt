package dev.cyclone.cloak.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.cyclone.cloak.data.PostureRecord
import dev.cyclone.cloak.data.PostureState
import dev.cyclone.cloak.data.LocalPostureDecision
import java.time.Duration
import java.time.Instant

object PostureSummary {
    fun reportId(record: PostureRecord?): String? = record?.reportDigest?.take(12)

    fun freshnessWarnings(record: PostureRecord, profileId: String, profileDigest: String, artifactDigest: String?, now: Instant): List<String> = buildList {
        if (record.profileId != profileId) add("The report is from another profile.")
        if (record.profileDigest != null && record.profileDigest != profileDigest) add("Stale report: the saved profile SHA-256 changed.")
        if (record.artifactDigest != null && record.artifactDigest != artifactDigest) add("The report artifact differs from the current bundled artifact.")
        record.checkedAt?.let {
            val age = Duration.between(Instant.parse(it), now)
            if (age > LocalPostureDecision.maxAge) add("Stale report: older than 7 days.")
            if (age < Duration.ofMinutes(-1)) add("The report timestamp is in the future.")
        }
    }
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
