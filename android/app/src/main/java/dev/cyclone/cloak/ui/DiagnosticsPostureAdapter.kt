package dev.cyclone.cloak.ui

import dev.cyclone.cloak.data.CompatibilityPostureReport
import dev.cyclone.cloak.data.EngineMetadataStore
import dev.cyclone.cloak.data.LocalPostureDecision
import dev.cyclone.cloak.data.PostureCheckStatus
import dev.cyclone.cloak.data.PostureRecord
import dev.cyclone.cloak.data.PostureReportCheck
import dev.cyclone.cloak.data.PostureResult
import dev.cyclone.cloak.forge.RuntimeEvidence
import dev.cyclone.cloak.forge.StrictJson
import org.json.JSONObject
import java.time.Duration
import java.time.Instant

data class DiagnosticsPosturePreview(val report: CompatibilityPostureReport, val result: PostureResult,
    val warnings: List<String>, val canSave: Boolean) {
    val record: PostureRecord get() = result.recordFor(report.profileId)
}

data class DiagnosticsReview(val diagnostics: AdaptedDiagnostics, val preview: DiagnosticsPosturePreview) {
    sealed interface SaveResult {
        data object Saved : SaveResult
        data class ReviewChanged(val review: DiagnosticsReview) : SaveResult
        data object Blocked : SaveResult
    }
    fun save(currentProfile: JSONObject, currentArtifactDigest: String?, now: Instant, saveRecord: (PostureRecord) -> Unit): SaveResult {
        val current = diagnostics.preview(currentProfile, currentArtifactDigest, now)
        if (current != preview) return SaveResult.ReviewChanged(copy(preview = current))
        if (!current.canSave) return SaveResult.Blocked
        saveRecord(current.record)
        return SaveResult.Saved
    }
}

/** An in-memory, value-free adaptation. Raw diagnostics and identity values are never persisted. */
class AdaptedDiagnostics internal constructor(
    val report: CompatibilityPostureReport,
    val sourceProfileId: String?,
    val sourceProfileDigest: String?,
) {
    fun preview(currentProfile: JSONObject, currentArtifactDigest: String?, now: Instant): DiagnosticsPosturePreview {
        val warnings = mutableListOf<String>()
        val differentProfile = currentProfile.optString("id") != report.profileId ||
            (sourceProfileId != null && sourceProfileId != report.profileId)
        if (differentProfile) warnings += "This diagnostics report is from another profile. Select the matching identity."
        val changedBody = report.profileDigest != EngineMetadataStore.digest(currentProfile) ||
            (sourceProfileDigest != null && sourceProfileDigest != report.profileDigest)
        if (changedBody) warnings += "Stale profile evidence: this report does not match the current saved profile SHA-256. Load fresh diagnostics."
        if (report.artifactDigest == null) warnings += "Report artifact and metadata are missing or disagree. No artifact digest will be saved."
        else if (currentArtifactDigest == null) warnings += "The current bundled artifact digest is unavailable."
        else if (report.artifactDigest != currentArtifactDigest) warnings += "This report is from a different artifact than the current bundled module. It does not verify the installed module."
        val age = Duration.between(Instant.parse(report.checkedAt), now)
        if (age > LocalPostureDecision.maxAge) warnings += "Stale report: diagnostics are older than 7 days."
        if (age < Duration.ofMinutes(-1)) warnings += "The report timestamp is in the future."
        val result = if (differentProfile) PostureResult(source = "local-diagnostics", reason = warnings.first())
            else LocalPostureDecision.evaluate(report, currentProfile, now, currentArtifactDigest).copy(source = "local-diagnostics")
        val effective = if (changedBody && !differentProfile) result.copy(state = dev.cyclone.cloak.data.PostureState.NEEDS_ROTATION,
            reason = "Stale profile evidence: the current profile SHA-256 does not match. Local evidence only; no platform verdict.") else result
        return DiagnosticsPosturePreview(report, effective, warnings.toList(), !differentProfile && !changedBody)
    }
}

object DiagnosticsPostureAdapter {
    fun mapStatus(status: String): PostureCheckStatus = when (status) {
        "VERIFIED" -> PostureCheckStatus.PASS
        "FAIL" -> PostureCheckStatus.FAIL
        "PASS", "PARTIAL", "BLOCKED", "INCOMPLETE", "UNMAPPED", "OBSERVED", "MATCH_ONLY", "not verified",
        "UNSTABLE", "CONTROL_CHANGED" -> PostureCheckStatus.WARNING
        else -> throw IllegalArgumentException("Unsupported diagnostics status.")
    }

    fun convert(text: String, selectedProfile: JSONObject): AdaptedDiagnostics {
        require(text.length <= 2_000_000) { "Diagnostics file is too large." }
        StrictJson(text).check()
        val parser = org.json.JSONTokener(text)
        val json = parser.nextValue() as? JSONObject ?: throw IllegalArgumentException("Diagnostics must be an object.")
        require(parser.nextClean() == '\u0000') { "Trailing diagnostics data." }
        val metadata = json.optJSONObject("metadata") ?: throw IllegalArgumentException("Diagnostics metadata is missing.")
        val timestamp = metadata.opt("measured_at_utc") as? String ?: throw IllegalArgumentException("Diagnostics timestamp is missing.")
        Instant.parse(timestamp)
        val runs = json.optJSONArray("runs") ?: throw IllegalArgumentException("Diagnostics runs are missing.")
        for (i in 0 until runs.length()) {
            val run = runs.getJSONObject(i)
            require(run.opt("run") is String && run.opt("package") is String) { "Malformed diagnostics run." }
            val rows = run.getJSONArray("rows")
            require(rows.length() in 1..1000) { "Invalid diagnostics row count." }
            for (j in 0 until rows.length()) {
                val row = rows.getJSONObject(j)
                require(row.opt("id") is String && row.optString("id").length in 1..120 && row.opt("status") is String) { "Malformed diagnostics row." }
            }
        }
        fun digest(value: Any?): String? {
            if (value == null || value == JSONObject.NULL) return null
            require(value is String && Regex("[0-9a-f]{64}").matches(value)) { "Malformed diagnostics digest." }
            return value
        }
        val artifact = digest(json.opt("artifact"))
        val metadataArtifact = digest(metadata.opt("module_arm64_sha256"))
        val agreedArtifact = artifact?.takeIf { it == metadataArtifact }
        fun claim(key: String): String? {
            val values = listOf(json, metadata).filter { it.has(key) }.map {
                it.opt(key) as? String ?: throw IllegalArgumentException("Malformed diagnostics profile binding.")
            }
            require(values.distinct().size <= 1 && values.all { it.isNotBlank() && it.length <= 128 && it.none(Char::isISOControl) }) { "Conflicting diagnostics profile binding." }
            return values.firstOrNull()
        }
        val sourceId = claim("profile_id")
        val sourceDigest = claim("profile_sha256")?.also { digest(it) }
        val states = RuntimeEvidence.readStatuses(json)
        require(states.isNotEmpty() && states.size <= 990) { "Diagnostics must contain one to 990 reads." }
        val checks = states.map { (id, status) -> PostureReportCheck("read:$id", mapStatus(status), "Diagnostics proof: $status.") }.toMutableList()
        if (sourceId == null || sourceDigest == null) checks += PostureReportCheck("context.profile_scope", PostureCheckStatus.WARNING,
            "Probe fixture evidence has no complete saved-profile binding. Association with the selected identity is local only.")
        if (agreedArtifact == null) checks += PostureReportCheck("context.artifact", PostureCheckStatus.WARNING,
            "Report artifact and metadata do not establish a matching digest.")
        val profileId = selectedProfile.getString("id")
        val report = CompatibilityPostureReport("runtime-diagnostics", timestamp, profileId,
            EngineMetadataStore.digest(selectedProfile), agreedArtifact, checks.toList())
        return AdaptedDiagnostics(report, sourceId, sourceDigest)
    }
}
