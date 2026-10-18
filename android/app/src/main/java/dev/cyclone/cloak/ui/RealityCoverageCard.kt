package dev.cyclone.cloak.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import dev.cyclone.cloak.forge.RealityCoverage
import org.json.JSONObject

@Composable
fun RealityCoverageCard(profile: JSONObject, evidence: Map<String, String> = emptyMap()) {
    val summary = RealityCoverage.summarize(profile)
    val runtime = summary.runtime
    val links = dev.cyclone.cloak.forge.RealityRuntimeLink.evaluate(profile)
    CloakDisclosure("Reality coverage", supporting = "Local coherence and descriptive coverage; no platform verdict") {
        Text("Local coherence: ${summary.score?.let { "$it/100" } ?: "not recorded"} · Schema ${summary.schema}", style = MaterialTheme.typography.bodySmall)
        Text("Missing optional sections: ${summary.missing.joinToString().ifEmpty { "none" }}", style = MaterialTheme.typography.bodySmall)
        Text("Validation warnings/errors: ${summary.warnings}", style = MaterialTheme.typography.bodySmall)
        Text("Runtime contract: ${runtime?.presence ?: "invalid local description"}", style = MaterialTheme.typography.bodySmall)
        runtime?.let { readiness ->
            Text(readiness.counts.entries.joinToString(" · ") { "${it.key.name.lowercase().replace('_', '-')} ${it.value}" }, style = MaterialTheme.typography.bodySmall)
            Text("Local coherence does not mean runtime enforcement. Contract status is capability, not device verification.", style = MaterialTheme.typography.bodySmall)
            readiness.missingSurfaces.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
        Text("Linked parity: ${links.count { it.parityPassed == true }}/10 matched · ${links.count { it.state == dev.cyclone.cloak.forge.RealityRuntimeLink.State.MISMATCHED }} mismatched", style = MaterialTheme.typography.bodySmall)
        links.filter { it.state == dev.cyclone.cloak.forge.RealityRuntimeLink.State.MISMATCHED }.forEach {
            Text("MISMATCHED: ${it.fieldPath} → ${it.basePath}", style = MaterialTheme.typography.bodySmall)
        }
        Text("Local parity is not a platform verdict.", style = MaterialTheme.typography.bodySmall)
        Text("Provenance: ${summary.provenance.joinToString().ifEmpty { "not recorded" }}", style = MaterialTheme.typography.bodySmall)
        Text("Read-only coverage: ${summary.rows.size} fields · device verification UNVERIFIED. Coherence does not imply physical hardware support.", style = MaterialTheme.typography.bodySmall)
        if (evidence.keys.any { it.startsWith("reality.") }) Text("Loaded probe fixture evidence only; this saved identity is not verified.", style = MaterialTheme.typography.bodySmall)
        summary.rows.map { row -> row.copy(state = when (evidence[row.field]) {
            "MATCH_ONLY" -> "match-only"; "OBSERVED" -> "observed"; "BLOCKED" -> "blocked"
            "INCOMPLETE" -> "missing"; "FAIL" -> "missing (comparison failed)"; else -> row.state
        }) }.groupBy { it.field.substringAfter("reality.").substringBefore('.') }.forEach { (section, rows) ->
            Text("$section: ${rows.groupingBy { it.state }.eachCount().entries.joinToString { "${it.value} ${it.key}" }}", style = MaterialTheme.typography.bodySmall)
        }
    }
}
