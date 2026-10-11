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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Values stay hidden; this local view is intentionally separate from copied diagnostics. */
@Composable
internal fun UniquenessDiagnostics(state: CloakState, onReview: (String) -> Unit) {
    val duplicates = state.uniqueness.duplicates
    Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Local identifier duplicates", style = MaterialTheme.typography.titleMedium)
            Text("Checked against ${state.identities.size} readable saved profiles in this Android user. Blank or malformed identifier entries are skipped; values are hidden.",
                style = MaterialTheme.typography.bodySmall)
            if (duplicates.isEmpty()) Text("No duplicate identifier values found.")
            duplicates.forEach { duplicate ->
                Text("${duplicate.field}: shared by ${duplicate.profiles.size} profiles", style = MaterialTheme.typography.titleSmall)
                duplicate.profiles.forEach { id ->
                    val name = state.identities.firstOrNull { it.first == id }?.second?.optString("name").orEmpty().ifBlank { "Unnamed profile" }
                    TextButton(onClick = { onReview(id) }, enabled = state.busy == null) { Text("Review $name") }
                }
            }
        }
    }
}
