package dev.cyclone.cloak.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Five matrix columns arranged as compact rows for a phone-width screen. */
@Composable
internal fun CoverageMatrix(coverage: CompatibilityCoverage?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Compatibility coverage", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (coverage == null) {
                Text("Coverage data unavailable. No verification claims can be shown.", color = MaterialTheme.colorScheme.error)
            } else {
                Text(coverage.baseline, style = MaterialTheme.typography.bodySmall)
                Text(coverage.scopeNote, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                coverage.rows.forEach { row ->
                    HorizontalDivider()
                    Text(row.area, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Implementation", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(row.implementation.label, style = MaterialTheme.typography.bodySmall)
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Test status", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(row.testStatus.label, style = MaterialTheme.typography.bodySmall,
                                color = if (row.testStatus in setOf(CoverageTestStatus.DEVICE_FAILED, CoverageTestStatus.DEVICE_FAILED_AND_BLOCKED,
                                        CoverageTestStatus.UNIT_PASSED_DEVICE_FAILED)) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary)
                        }
                    }
                    Text("Last verified (UTC): ${row.lastVerifiedUtc ?: "not verified"}", style = MaterialTheme.typography.bodySmall)
                    if (row.verifiedReads.isNotEmpty()) {
                        Text("${row.verifiedReads.size} selected baseline reads verified; area coverage remains partial.", style = MaterialTheme.typography.bodySmall)
                    }
                    Text("Note: ${row.note}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
