package dev.cyclone.cloak.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
internal fun HealthDoctorCard(diagnostics: HealthDiagnostics, onCopy: (String) -> Unit) {
    var requestedPage by remember { mutableIntStateOf(0) }
    val pageSize = 20
    val lastPage = ((diagnostics.checks.size - 1) / pageSize).coerceAtLeast(0)
    val page = requestedPage.coerceIn(0, lastPage)
    Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Readiness Doctor v3", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            CloakStatusPill(diagnostics.verdict.label, when (diagnostics.verdict) {
                Readiness.READY -> CloakStatusTone.READY
                Readiness.DEGRADED -> CloakStatusTone.PENDING
                Readiness.FAILED -> CloakStatusTone.ERROR
            })
            Text(diagnostics.summary, style = MaterialTheme.typography.bodySmall)
            Text("${diagnostics.enabledBindings} enabled bindings · ${diagnostics.disabledBindings} disabled", style = MaterialTheme.typography.bodySmall)
            Text("Readiness uses the last reported checks for enabled bindings. Disabled bindings are excluded. Hook verification is recorded in the coverage matrix below.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = { onCopy(diagnostics.redactedReport()) }, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.small) {
                Text("Copy diagnostics")
            }
            Text("The copied report includes check results, counts and release versions. Private data and raw errors are omitted.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (lastPage > 0) {
                Text("Checks ${page * pageSize + 1}–${minOf((page + 1) * pageSize, diagnostics.checks.size)} of ${diagnostics.checks.size}",
                    style = MaterialTheme.typography.bodySmall)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = { requestedPage = page - 1 }, enabled = page > 0) { Text("Previous checks") }
                    TextButton(onClick = { requestedPage = page + 1 }, enabled = page < lastPage) { Text("Next checks") }
                }
            }
            diagnostics.checks.drop(page * pageSize).take(pageSize).forEach { check ->
                HorizontalDivider()
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(check.kind.label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                    CloakStatusPill(check.status.label, when (check.status) {
                        DoctorStatus.PASS -> CloakStatusTone.READY
                        DoctorStatus.WARN -> CloakStatusTone.PENDING
                        DoctorStatus.FAIL -> CloakStatusTone.ERROR
                    })
                }
                check.displayContext?.let { Text(it, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold) }
                if (check.kind == DoctorCheckKind.VERSION) {
                    Text("App: ${diagnostics.appVersion ?: "not confirmed"}\nInstalled module: ${diagnostics.installedModuleVersion ?: "not confirmed"}",
                        style = MaterialTheme.typography.bodySmall)
                }
                check.reasons.distinct().forEach { reason ->
                    Text(reason.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
