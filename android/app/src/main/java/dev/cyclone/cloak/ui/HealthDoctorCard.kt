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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
internal fun HealthDoctorCard(diagnostics: HealthDiagnostics, onCopy: (String) -> Unit) {
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
            diagnostics.checks.forEach { check ->
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
