package dev.cyclone.cloak.ui

import org.json.JSONObject

/** Only allowlisted descriptive metadata is displayed. Seeds and identifiers are never interpolated. */
object EngineIdentitySummary {
    fun labels(profile: JSONObject, metadata: JSONObject?): List<String> = listOf(
        "Engine version: ${metadata?.optString("engine_version") ?: "not recorded"} · Profile schema version: ${profile.optString("schema_version")}",
        "Template ID: ${metadata?.optString("template") ?: profile.optJSONObject("meta")?.optString("template")?.ifBlank { "not recorded" } ?: "not recorded"} · Source confidence: ${metadata?.optString("source_confidence")?.ifBlank { "not recorded" } ?: "not recorded"}",
    )
}
