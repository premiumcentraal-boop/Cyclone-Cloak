package dev.cyclone.cloak.forge

import org.json.JSONArray
import org.json.JSONObject

/** Versioned capability description, never an instruction to the runtime. */
object RealityRuntimeContract {
    enum class Status { APPLIED, PARTIAL, PENDING, DIAGNOSTIC_ONLY, HOST_ONLY, UNSUPPORTED }
    fun table(): JSONArray = JSONObject(RealityRuntimeTable.json).getJSONArray("fields")
    private fun present(value: Any?, parts: List<String>): Boolean {
        if (parts.isEmpty()) return value != null && value != JSONObject.NULL
        val head = parts.first(); val tail = parts.drop(1)
        if (head.endsWith("[]")) {
            val items = (value as? JSONObject)?.optJSONArray(head.dropLast(2)) ?: return false
            return (0 until items.length()).any { present(items.opt(it), tail) }
        }
        return present((value as? JSONObject)?.opt(head), tail)
    }
    fun generate(profile: JSONObject): JSONObject {
        val schema = profile.optString("schema_version")
        require(schema in setOf("0.2", "0.3", "0.4")) { "UNSUPPORTED_PROFILE_SCHEMA" }
        val result = JSONObject().put("format", 2).put("schema_version", schema)
        if (!profile.has("reality")) return result.put("presence", "not present").put("fields", JSONArray())
        val projected = JSONObject(profile.toString())
        val reality = projected.optJSONObject("reality") ?: throw IllegalArgumentException("INVALID_REALITY")
        reality.keys().asSequence().toList().filter { it !in DeviceReality.sections && it != "inventory_id" }.forEach { reality.remove(it) }
        val links = RealityRuntimeLink.evaluate(projected).associateBy { it.fieldPath }
        require(DeviceReality.validate(projected).none { it.code != "REALITY_LINK" || it.field !in links }) { "INVALID_REALITY" }
        val rows = table()
        for (i in 0 until rows.length()) rows.getJSONObject(i).let { row ->
            val link = links[row.getString("field_path")]
            row.put("present", link?.realityPresent ?: present(projected, row.getString("field_path").split('.')))
                .put("parity_state", link?.state?.name ?: "NOT_REQUIRED").put("parity_passed", link?.parityPassed ?: JSONObject.NULL)
            if (link != null) row.put("status", if (link.parityPassed == true) "APPLIED" else "PARTIAL")
        }
        return result.put("presence", "present").put("fields", rows)
    }
    fun validate(contract: JSONObject) {
        require(contract.keys().asSequence().toSet() == setOf("format", "schema_version", "presence", "fields")) { "MALFORMED_CONTRACT" }
        require(contract.opt("format") in setOf(1, 2) && contract.optString("schema_version") in setOf("0.2", "0.3", "0.4")) { "UNSUPPORTED_CONTRACT" }
        val rows = contract.getJSONArray("fields")
        if (contract.optString("presence") == "not present" && rows.length() == 0) return
        val expected = if (contract.getInt("format") == 1) JSONObject(RealityRuntimeTable.legacyJson).getJSONArray("fields") else table()
        require(contract.optString("presence") == "present" && contract.optString("schema_version") == "0.4" && rows.length() == expected.length()) { "INCOMPLETE_CONTRACT" }
        for (i in 0 until rows.length()) {
            val row = JSONObject(rows.getJSONObject(i).toString())
            val present = row.remove("present")
            require(present is Boolean) { "CONTRACT_TABLE_MISMATCH" }
            val source = expected.getJSONObject(i)
            if (contract.getInt("format") == 2) {
                val state = row.remove("parity_state"); val passed = row.remove("parity_passed")
                if (!source.isNull("linked_base_path")) {
                    require(state in setOf("MATCHED", "MISMATCHED", "NOT_PRESENT", "BASE_MISSING")) { "INVALID_PARITY" }
                    val expectedPass: Any = when (state) { "MATCHED" -> true; "MISMATCHED" -> false; else -> JSONObject.NULL }
                    require(passed == expectedPass && present == (state != "NOT_PRESENT") &&
                        row.optString("status") == if (passed == true) "APPLIED" else "PARTIAL") { "UNPROVEN_APPLIED" }
                    row.put("status", source.getString("status"))
                } else require(state == "NOT_REQUIRED" && passed == JSONObject.NULL) { "UNEXPECTED_PARITY" }
            }
            require(RealityJson.digest(row) == RealityJson.digest(source)) { "CONTRACT_TABLE_MISMATCH" }
        }
    }
    data class Readiness(val presence: String, val counts: Map<Status, Int>, val missingSurfaces: List<String>, val links: List<RealityRuntimeLink.Link>)
    fun validateObservation(report: JSONObject) {
        require(report.opt("format") == 1) { "UNSUPPORTED_RUNTIME_REPORT" }
        val rows = report.getJSONArray("rows")
        if (report.optString("presence") == "not present" && rows.length() == 0) return
        val fields = table()
        require(report.optString("presence") == "present" && rows.length() == fields.length()) { "INCOMPLETE_RUNTIME_REPORT" }
        for (i in 0 until rows.length()) {
            val row = rows.getJSONObject(i); val field = fields.getJSONObject(i); val status = row.optString("observation_status")
            require(row.opt("field_path") == field.opt("field_path") && row.opt("surface") == field.opt("surface")) { "UNKNOWN_RUNTIME_SURFACE" }
            require(status in setOf("PASS", "FAIL", "OBSERVED", "BLOCKED", "MISSING", "UNMAPPED") && row.opt("duration_ms") is Number && row.getDouble("duration_ms") >= 0 && row.optString("reason").isNotBlank()) { "MALFORMED_RUNTIME_OBSERVATION" }
            require(row.isNull("digest") || Regex("[0-9a-f]{64}").matches(row.getString("digest"))) { "MALFORMED_RUNTIME_DIGEST" }
            if (status in setOf("PASS", "FAIL")) {
                require(!row.isNull("digest") && Regex("[0-9a-f]{64}").matches(row.optString("expected_digest")) &&
                    (row.getString("digest") == row.getString("expected_digest")) == (status == "PASS")) { "UNPROVEN_RUNTIME_COMPARISON" }
            }
        }
    }
    fun readiness(profile: JSONObject): Readiness {
        val contract = generate(profile); val rows = contract.getJSONArray("fields")
        val entries = (0 until rows.length()).map { rows.getJSONObject(it) }
        return Readiness(contract.getString("presence"), Status.entries.associateWith { status -> entries.count { it.getString("status") == status.name } },
            entries.filter { it.getString("status") in setOf("PENDING", "PARTIAL") }.take(5).map { it.getString("field_path") + ": " + if (it.isNull("surface")) "no mapped surface" else it.getString("surface") }, RealityRuntimeLink.evaluate(profile))
    }
}
