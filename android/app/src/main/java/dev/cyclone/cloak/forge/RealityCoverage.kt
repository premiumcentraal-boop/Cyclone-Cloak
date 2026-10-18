package dev.cyclone.cloak.forge

import org.json.JSONObject

/** Local coherence is distinct from runtime evidence. A hash match never becomes verified here. */
object RealityCoverage {
    data class Row(val field: String, val state: String, val mismatch: Boolean = false)
    data class Summary(val score: Int?, val missing: List<String>, val warnings: Int, val schema: String,
        val provenance: List<String>, val rows: List<Row>)
    fun summarize(profile: JSONObject, report: JSONObject? = null): Summary {
        val reality = profile.optJSONObject("reality")
        val findings = ProfileValidator.validateProfile(profile)
        val errors = findings.count { it.severity == ProfileValidator.ERROR }
        val fields = DeviceReality.rules.getJSONObject("properties")
        val paths = DeviceReality.sections.sorted().flatMap { section ->
            fields.getJSONObject(section).getJSONObject("properties").getJSONObject("values").getJSONObject("properties")
                .keys().asSequence().toList().sorted().map { "reality.$section.$it" }
        }
        val readings = mutableMapOf<String, JSONObject>()
        report?.optJSONObject("device_reality")?.let { extension ->
            require(extension.opt("format") == 1) { "Unsupported Reality report." }
            val rows = extension.getJSONArray("rows")
            require(rows.length() <= paths.size) { "Too many Reality rows." }
            for (i in 0 until rows.length()) {
                val row = rows.getJSONObject(i); val id = row.getString("id"); val status = row.optString("status")
                require(id in paths && !readings.containsKey(id) && status in setOf("PASS", "FAIL", "BLOCKED", "MISSING", "UNMAPPED", "OBSERVED")) { "Malformed Reality row." }
                for (key in listOf("actual_sha256", "expected_sha256")) require(row.isNull(key) || Regex("[0-9a-f]{64}").matches(row.getString(key))) { "Malformed Reality digest." }
                if (status in setOf("PASS", "FAIL")) {
                    require(!row.isNull("actual_sha256") && !row.isNull("expected_sha256")) { "Comparison lacks evidence." }
                    require((row.getString("actual_sha256") == row.getString("expected_sha256")) == (status == "PASS")) { "Comparison disagrees with digests." }
                }
                readings[id] = row
            }
        }
        val missing = DeviceReality.sections.sorted().filter { reality?.optJSONObject(it)?.optString("availability") != "known" }
        val provenance = DeviceReality.sections.mapNotNull { reality?.optJSONObject(it)?.optString("source") }
            .filter { it in setOf("derived", "public reference", "approximated", "needs verification") }.distinct().sorted()
        return Summary(if (reality == null) null else maxOf(0, 100 - errors * 10), missing,
            findings.size + readings.values.count { it.optString("status") == "FAIL" },
            profile.optString("schema_version"), provenance, paths.map { id ->
                val status = readings[id]?.optString("status")
                Row(id, when (status) { "PASS" -> "match-only"; "OBSERVED" -> "observed"; "BLOCKED" -> "blocked"
                    "MISSING", "FAIL" -> "missing"; else -> "unmapped" }, status == "FAIL")
            })
    }
}
