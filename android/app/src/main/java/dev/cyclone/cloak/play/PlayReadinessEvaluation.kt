package dev.cyclone.cloak.play

import dev.cyclone.cloak.forge.PlaySurfaceContract
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant

enum class PlayReadinessState { READY, DEGRADED, BLOCKED, UNKNOWN }
enum class PlayReviewState { CURRENT, NEEDS_REVIEW }
data class PlayReadinessContext(
    val profileId: String, val profileHash: String, val androidUserId: Int, val artifactHash: String,
    val now: Instant, val freshness: Duration = Duration.ofHours(24),
) {
    init {
        require(Regex("[A-Za-z0-9][A-Za-z0-9._:-]{0,95}").matches(profileId)) { "PR_INVALID_CONTEXT" }
        require(listOf(profileHash, artifactHash).all { Regex("[0-9a-f]{64}").matches(it) }) { "PR_INVALID_CONTEXT" }
        require(androidUserId in 0..21474 && freshness > Duration.ZERO && freshness <= Duration.ofDays(365)) { "PR_INVALID_CONTEXT" }
        PlayReadinessReport.timestamp(now.truncatedTo(java.time.temporal.ChronoUnit.SECONDS).toString())
    }
}
data class PlayReadinessFinding(val field: String, val code: String) {
    val message: String get() = when (code) {
        "PR_PROFILE_MISMATCH" -> "The report belongs to a different profile or saved profile body."
        "PR_USER_MISMATCH" -> "The report belongs to a different Android user."
        "PR_ARTIFACT_MISMATCH" -> "The report was measured against a different app/module artifact."
        "PR_STALE" -> "The report is outside the configured freshness window."
        "PR_FUTURE_REPORT" -> "The report timestamp is later than the current evaluation time."
        "PR_MALFORMED_REPORT" -> "The report is malformed or incomplete."
        "PR_NO_EVIDENCE" -> "Required local evidence is missing or unknown."
        "PR_PACKAGE_BLOCK" -> "A required package is disabled, missing or blocked."
        "PR_PERMISSION_LIMITATION" -> "A permission denial limits the local observation."
        "PR_SERVICE_BLOCK" -> "A supplied account or settings observation reports a block."
        "PR_LIMITATION" -> "A supplied local observation requires review."
        "PR_REPORTED_FAILURE" -> "The report includes an explicit local failure."
        "PR_REPORTED_WARNING" -> "The report includes an explicit local warning."
        "PR_USER_REVIEW" -> "The user requested review; a new explicit review is required."
        "PR_STORE_UNREADABLE" -> "Stored local readiness evidence is unreadable."
        else -> "Local readiness evidence requires review."
    }
}
data class PlayReadinessResult(
    val state: PlayReadinessState,
    val review: PlayReviewState,
    val findings: List<PlayReadinessFinding>,
    val contractRecordJson: String,
    val accepted: Boolean,
    /** These are observations, never readiness passes or authenticated platform verdicts. */
    val optionalObservationFields: List<String>,
)
interface PlayReadinessProvider {
    val id: String
    fun evaluate(context: PlayReadinessContext, report: String? = null, requestReview: Boolean = false): PlayReadinessResult
}
class DefaultPlayReadinessProvider : PlayReadinessProvider {
    override val id = "local.play.none"
    override fun evaluate(context: PlayReadinessContext, report: String?, requestReview: Boolean) =
        LocalPlayReadinessProvider(id).evaluate(context, null, requestReview)
}
/** No IO, Google calls or attestation requests. Only caller-supplied evidence is evaluated. */
class LocalPlayReadinessProvider(override val id: String = "local.play.report") : PlayReadinessProvider {
    init { require(Regex("[A-Za-z0-9][A-Za-z0-9._:-]{0,95}").matches(id)) { "PR_INVALID_PROVIDER" } }
    override fun evaluate(context: PlayReadinessContext, report: String?, requestReview: Boolean): PlayReadinessResult {
        val findings = mutableListOf<PlayReadinessFinding>()
        fun add(field: String, code: String) { findings += PlayReadinessFinding(field, code) }
        val parsed = if (report == null) null else try { PlayReadinessReport.parse(report) }
            catch (_: PlayReadinessReport.InvalidReport) { add("report", "PR_MALFORMED_REPORT"); null }
        val j = parsed?.json
        var rejected = false
        if (j == null) { if (report == null) add("report", "PR_NO_EVIDENCE"); rejected = true }
        else {
            if (j.getString("profile_id") != context.profileId || j.getString("profile_hash") != context.profileHash) add("profile", "PR_PROFILE_MISMATCH")
            if (j.getInt("android_user_id") != context.androidUserId) add("android_user_id", "PR_USER_MISMATCH")
            if (j.getString("artifact_hash") != context.artifactHash) add("artifact_hash", "PR_ARTIFACT_MISMATCH")
            val measured = PlayReadinessReport.timestamp(j.getString("timestamp"))
            if (measured > context.now) add("timestamp", "PR_FUTURE_REPORT")
            else if (Duration.between(measured, context.now) > context.freshness) add("timestamp", "PR_STALE")
            rejected = findings.isNotEmpty()
        }
        val reviewRequired = requestReview || findings.any { it.code != "PR_NO_EVIDENCE" }
        if (requestReview) add("review", "PR_USER_REVIEW")
        var blocked = false
        var limited = false
        var missing = j == null
        if (j != null && !rejected) {
            for (field in listOf("play_services", "play_store", "web_provider", "play_protect", "google_account", "google_settings_access")) {
                val value = j.get(field)
                val state = if (value is JSONObject) value.getString("state") else value as String
                when {
                    state == "unknown" -> { missing = true; add(field, "PR_NO_EVIDENCE") }
                    state == "permission_denied" -> { limited = true; add(field, "PR_PERMISSION_LIMITATION") }
                    state == "blocked" || (value is JSONObject && state in setOf("disabled", "missing")) -> {
                        blocked = true; add(field, if (value is JSONObject) "PR_PACKAGE_BLOCK" else "PR_SERVICE_BLOCK")
                    }
                    state in setOf("disabled", "unavailable", "none") -> { limited = true; add(field, "PR_LIMITATION") }
                }
            }
            if (j.getJSONArray("failures").length() > 0) { blocked = true; add("failures", "PR_REPORTED_FAILURE") }
            if (j.getJSONArray("warnings").length() > 0) { limited = true; add("warnings", "PR_REPORTED_WARNING") }
            if (j.getString("integrity_api") in setOf("blocked", "permission_denied")) {
                limited = true; add("integrity_api", if (j.getString("integrity_api") == "permission_denied") "PR_PERMISSION_LIMITATION" else "PR_LIMITATION")
            }
        }
        val state = when {
            rejected || missing || requestReview -> PlayReadinessState.UNKNOWN
            blocked -> PlayReadinessState.BLOCKED
            limited -> PlayReadinessState.DEGRADED
            else -> PlayReadinessState.READY
        }
        val reasons = when {
            state == PlayReadinessState.READY -> listOf("PS_LOCAL_CHECKS_PASSED")
            findings.any { it.code == "PR_PROFILE_MISMATCH" || it.code == "PR_USER_MISMATCH" } -> listOf("PS_PROFILE_MISMATCH", "PS_REVIEW_REQUIRED")
            findings.any { it.code == "PR_ARTIFACT_MISMATCH" } -> listOf("PS_ARTIFACT_MISMATCH", "PS_REVIEW_REQUIRED")
            reviewRequired -> listOf("PS_REVIEW_REQUIRED")
            missing -> listOf("PS_NO_EVIDENCE")
            else -> listOf("PS_EXPECTATION_CONFLICT")
        }
        val record = JSONObject().put("format", 1).put("profile_id", j?.getString("profile_id") ?: context.profileId)
            .put("profile_hash", j?.getString("profile_hash") ?: context.profileHash)
            .put("artifact_hash", j?.getString("artifact_hash") ?: context.artifactHash)
            .put("provider_id", id).put("state", state.name).put("reason_codes", JSONArray(reasons))
            .put("evidence_digest", parsed?.text?.toByteArray(Charsets.UTF_8)?.let { bytes -> MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) } } ?: JSONObject.NULL)
            .put("report_timestamp", j?.getString("timestamp") ?: context.now.truncatedTo(java.time.temporal.ChronoUnit.SECONDS).toString())
        return PlayReadinessResult(state, if (reviewRequired) PlayReviewState.NEEDS_REVIEW else PlayReviewState.CURRENT,
            findings.distinct().sortedWith(compareBy({ it.field }, { it.code })), PlaySurfaceContract.encodeReadiness(record),
            !rejected, if (j == null || rejected) emptyList() else listOf("integrity_api") + if (j.has("integrity_response")) listOf("integrity_response") else emptyList())
    }
}
