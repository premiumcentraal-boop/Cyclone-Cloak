package dev.cyclone.cloak.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun LibraryQualityCard(summary: LibraryQualitySummary, profileNames: Map<String, String>, busy: Boolean, onReview: (String) -> Unit) {
    var expanded by rememberSaveable { mutableStateOf<String?>(null) }
    val labels = mapOf(LibraryQualityArea.READABLE to "Readable saved profiles", LibraryQualityArea.UNREADABLE to "Unreadable saved profiles",
        LibraryQualityArea.DUPLICATES to "Duplicate identifier groups", LibraryQualityArea.VALIDATION_ERRORS to "Profiles with validation errors",
        LibraryQualityArea.VALIDATION_WARNINGS to "Profiles with validation warnings", LibraryQualityArea.MISSING_RECORDS to "Missing local compatibility records",
        LibraryQualityArea.ATTENTION_RECORDS to "Stale or attention-state local records", LibraryQualityArea.BOUND to "Profiles bound to apps",
        LibraryQualityArea.UNBOUND to "Profiles with no bindings")
    Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Library quality", style = MaterialTheme.typography.titleMedium)
            Text("Read-only local summary. Identifier values are hidden. Local compatibility records are not platform verdicts.",
                style = MaterialTheme.typography.bodySmall)
            if (summary.row(LibraryQualityArea.READABLE).count == 0) Text("No readable saved profiles to assess.")
            summary.rows.forEach { row ->
                if (row.area == LibraryQualityArea.UNREADABLE && row.count == null) {
                    Text("Saved-profile inventory is not yet known.", style = MaterialTheme.typography.bodySmall)
                    return@forEach
                }
                Text("${labels.getValue(row.area)}: ${row.count ?: "inventory not known"}", style = MaterialTheme.typography.bodySmall)
                if (row.profileIds.isNotEmpty()) {
                    TextButton(onClick = { expanded = if (expanded == row.area.name) null else row.area.name }, enabled = !busy) {
                        Text(if (expanded == row.area.name) "Hide profiles" else "Show profiles (${row.profileIds.size})")
                    }
                    if (expanded == row.area.name) row.profileIds.forEach { id ->
                        val name = profileNames[id]?.takeIf { it.isNotBlank() } ?: "Unnamed profile"
                        summary.attention.firstOrNull { it.profileId == id }?.takeIf { row.area == LibraryQualityArea.ATTENTION_RECORDS }?.let {
                            Text("$name: recorded ${it.recordedState.name.lowercase(java.util.Locale.ROOT).replace('_', ' ')}${if (it.freshnessWarning) " · freshness warning" else ""}",
                                style = MaterialTheme.typography.bodySmall)
                        }
                        TextButton(onClick = { onReview(id) }, enabled = !busy) { Text("Review $name") }
                    }
                }
            }
        }
    }
}
