package dev.cyclone.cloak.ui

import org.json.JSONObject
import java.time.LocalDate

internal enum class CoverageImplementation(val key: String, val label: String) {
    PARTIAL("partial", "Partial"),
    NOT_IMPLEMENTED("not_implemented", "Not implemented"),
}

internal enum class CoverageTestStatus(val key: String, val label: String) {
    PARTIAL_DEVICE_PROOF("partial_device_proof", "Partial device proof"),
    DEVICE_FAILED("device_failed", "Device test failed"),
    DEVICE_FAILED_AND_BLOCKED("device_failed_and_blocked", "Device test failed / blocked"),
    UNIT_PASSED_DEVICE_FAILED("unit_passed_device_failed", "Callback tests passed; device failed / blocked"),
    UNMAPPED("unmapped", "Unmapped; not verified"),
}

internal data class CompatibilityArea(
    val id: String,
    val area: String,
    val implementation: CoverageImplementation,
    val testStatus: CoverageTestStatus,
    val lastVerifiedUtc: String?,
    val note: String,
    val verifiedReads: List<String>,
)

/** Bundled diagnostic facts, independent of app/profile state and the live root check. */
internal data class CompatibilityCoverage(val baseline: String, val scopeNote: String, val rows: List<CompatibilityArea>) {
    companion object {
        val REQUIRED_AREAS = setOf("build", "properties", "telephony", "android_id", "serial", "advertising_id",
            "display", "locale_timezone", "wifi", "bluetooth", "camera", "sensors", "drm")

        fun parse(text: String): CompatibilityCoverage {
            val data = JSONObject(text)
            require(data.getInt("format") == 1) { "Unsupported coverage format" }
            require(data.getString("device_evidence").isNotBlank()) { "Coverage evidence is required" }
            fun JSONObject.nonBlank(key: String) = getString(key).also { require(it.isNotBlank()) { "Empty $key" } }
            val entries = data.getJSONArray("rows")
            val rows = (0 until entries.length()).map { index ->
                val row = entries.getJSONObject(index)
                val verified = row.getJSONArray("verified_reads").let { reads ->
                    (0 until reads.length()).map { reads.getString(it).also { id -> require(id.isNotBlank()) } }
                }
                val status = CoverageTestStatus.entries.first { it.key == row.getString("test_status") }
                val date = if (row.isNull("last_verified_utc")) null else row.nonBlank("last_verified_utc").also { LocalDate.parse(it) }
                require((date != null) == verified.isNotEmpty()) { "Verification date requires named verified reads" }
                require((status == CoverageTestStatus.PARTIAL_DEVICE_PROOF) == verified.isNotEmpty()) { "Proof status requires named verified reads" }
                val testedReads = row.getJSONArray("device_reads")
                val tested = (0 until testedReads.length()).map { testedReads.getString(it) }.toSet()
                require(tested.containsAll(verified)) { "Verified reads must be tested reads" }
                if (status == CoverageTestStatus.UNIT_PASSED_DEVICE_FAILED) {
                    require(row.getJSONArray("unit_evidence").length() > 0) { "Callback results require test evidence" }
                }
                CompatibilityArea(row.nonBlank("id"), row.nonBlank("area"),
                    CoverageImplementation.entries.first { it.key == row.getString("implementation_status") },
                    status, date, row.nonBlank("note"), verified)
            }
            require(rows.size == REQUIRED_AREAS.size && rows.map { it.id }.toSet() == REQUIRED_AREAS) { "Coverage areas must be complete and unique" }
            return CompatibilityCoverage(data.nonBlank("baseline"), data.nonBlank("scope_note"), rows)
        }
    }
}
