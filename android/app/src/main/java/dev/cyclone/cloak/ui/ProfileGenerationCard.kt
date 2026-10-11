package dev.cyclone.cloak.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.cyclone.cloak.forge.PhoneTemplate

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ProfileGenerationCard(
    phones: List<PhoneTemplate>,
    busy: Boolean,
    result: ProfileGeneration.Result?,
    onGenerate: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    val selected = phones.firstOrNull { it.id == selectedId } ?: phones.firstOrNull()
    val running = result == ProfileGeneration.Result.Running
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("New profile", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("Choose a phone. Generate a validated profile and save it in Identities.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                phones.forEach { phone ->
                    FilterChip(
                        selected = phone.id == selected?.id,
                        onClick = { selectedId = phone.id; onDismiss() },
                        enabled = !busy && !running,
                        label = { Text(phone.label) },
                    )
                }
            }
            if (selected == null) Text("No phone templates available. Import or build a phone first.")
            else Text(selected.summary, style = MaterialTheme.typography.labelMedium)
            Button(
                onClick = { selected?.let { onGenerate(it.id) } },
                enabled = selected != null && !busy && !running,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.small,
            ) { Text(if (running) "Generating profile…" else "Generate profile") }
            when (result) {
                is ProfileGeneration.Result.Saved -> {
                    CloakStatusPill("Saved", CloakStatusTone.READY)
                    Text("Saved ${result.name}. Find it in Identities.")
                    if (result.findings.isNotEmpty()) FindingsList(result.findings)
                }
                is ProfileGeneration.Result.NeedsReview -> {
                    Text("Not saved. Review duplicate identifiers before saving.")
                }
                is ProfileGeneration.Result.Rejected -> {
                    CloakStatusPill("Not saved", CloakStatusTone.ERROR)
                    Text("Validation failed. Fix the phone template before generating again.")
                    FindingsList(result.findings)
                }
                is ProfileGeneration.Result.Failed -> {
                    CloakStatusPill("Failed", CloakStatusTone.ERROR)
                    Text(result.message)
                }
                else -> Unit
            }
            if (result != null && !running) TextButton(onClick = onDismiss) { Text("Dismiss result") }
        }
    }
}
