package dev.cyclone.cloak.data

import android.content.Context
import android.os.Build
import org.json.JSONObject
import java.io.InputStream
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import java.util.zip.ZipInputStream

/** One local self-test row. Only these rows drive the posture decision. */
enum class PostureCheckStatus { PASS, WARNING, FAIL, UNKNOWN }

enum class PostureCheckCause { NONE, PERMISSION, ENVIRONMENT, HARDWARE_UNAVAILABLE, PROBE_NOT_RUN, CALLBACK_NOT_REACHED, MISMATCH, UNKNOWN }

data class PostureReportCheck(
    val id: String,
    val status: PostureCheckStatus,
    val message: String?,
    val cause: PostureCheckCause? = null,
) {
    init {
        require(id.isNotBlank() && id.length <= 128 && id.none(Char::isISOControl)) { "Invalid posture check ID." }
        message?.let { require(it.isNotBlank() && it.length <= 512 && it.none(Char::isISOControl)) { "Invalid posture check message." } }
    }
}

data class CompatibilityPostureReport(
    val providerId: String,
    val checkedAt: String,
    val profileId: String,
    val profileDigest: String,
    val artifactDigest: String?,
    val checks: List<PostureReportCheck>,
    val rawVerdict: String? = null,
    val sequence: Long? = null,
    val claimedDigest: String? = null,
) {
    val passIds get() = checks.filter { it.status == PostureCheckStatus.PASS }.map { it.id }
    val warningIds get() = checks.filter { it.status == PostureCheckStatus.WARNING }.map { it.id }
    val failureIds get() = checks.filter { it.status == PostureCheckStatus.FAIL }.map { it.id }

    init {
        require(providerId.isNotBlank() && providerId.length <= 128 && providerId.none(Char::isISOControl)) { "Invalid posture provider ID." }
        Instant.parse(checkedAt)
        require(profileId.isNotBlank() && profileId.length <= 128 && profileId.none(Char::isISOControl)) { "Invalid posture profile ID." }
        require(Regex("[0-9a-f]{64}").matches(profileDigest)) { "Profile digest must be lowercase SHA-256." }
        artifactDigest?.let { require(Regex("[0-9a-f]{64}").matches(it)) { "Artifact digest must be lowercase SHA-256." } }
        require(checks.isNotEmpty() && checks.size <= 1000) { "Posture report must contain one to 1000 checks." }
        require(checks.map { it.id }.toSet().size == checks.size) { "Posture check IDs must be unique." }
        rawVerdict?.let {
            require(it.isNotBlank() && it.length <= 256 && it.none(Char::isISOControl)) { "Invalid raw posture verdict." }
        }
        sequence?.let { require(it in 0..9_007_199_254_740_991L) { "Invalid posture report sequence." } }
        claimedDigest?.let { require(Regex("[0-9a-f]{64}").matches(it)) { "Report digest must be lowercase SHA-256." } }
    }

    companion object {
        fun parse(json: JSONObject): CompatibilityPostureReport {
            val required = setOf("format", "provider_id", "checked_at", "profile_id", "profile_sha256", "artifact_digest", "checks")
            val optional = setOf("raw_verdict", "sequence", "report_digest")
            require(json.keys().asSequence().toSet().let { keys -> keys.containsAll(required) && keys.all { it in required || it in optional } }) { "Malformed local posture report." }
            require(json.opt("format") == 1) { "Unsupported local posture report format." }
            fun text(key: String): String = json.opt(key) as? String ?: throw IllegalArgumentException("Invalid local posture $key.")
            val rawChecks = json.getJSONArray("checks")
            val checks = (0 until rawChecks.length()).map { index ->
                val row = rawChecks.getJSONObject(index)
                val allowed = setOf("id", "status", "message", "cause")
                require(row.keys().asSequence().toSet().let { keys -> keys.containsAll(setOf("id", "status")) && keys.all { it in allowed } }) { "Malformed local posture check." }
                val message = if (row.isNull("message")) null else row.optString("message")
                val cause = if (row.has("cause") && !row.isNull("cause")) {
                    runCatching { PostureCheckCause.valueOf(row.getString("cause")) }.getOrNull()
                        ?: throw IllegalArgumentException("Invalid local posture cause.")
                } else null
                PostureReportCheck(row.getString("id"), PostureCheckStatus.valueOf(row.getString("status")), message, cause)
            }
            val report = CompatibilityPostureReport(
                text("provider_id"), text("checked_at"), text("profile_id"), text("profile_sha256"),
                text("artifact_digest"), checks,
                if (json.has("raw_verdict") && !json.isNull("raw_verdict")) text("raw_verdict") else null,
                optionalNumber(json, "sequence"),
                if (json.has("report_digest") && !json.isNull("report_digest")) text("report_digest") else null,
            )
            report.claimedDigest?.let { claimed ->
                require(claimed == report.digest()) { "Report digest does not match report content." }
            }
            return report
        }

        private fun optionalNumber(json: JSONObject, key: String): Long? {
            if (!json.has(key) || json.isNull(key)) return null
            return when (val value = json.opt(key)) {
                is Int -> value.toLong()
                is Long -> value
                else -> throw IllegalArgumentException("Invalid local posture $key.")
            }
        }
    }

    /** Canonical local report body used for provenance and replay detection. */
    fun canonicalJson(): JSONObject {
        val checks = org.json.JSONArray()
        for (check in this.checks) {
            val row = JSONObject()
                .put("id", check.id)
                .put("status", check.status.name)
                .put("message", check.message ?: JSONObject.NULL)
            check.cause?.let { cause -> row.put("cause", cause.name) }
            checks.put(row)
        }
        return JSONObject()
            .put("format", 1)
            .put("provider_id", providerId)
            .put("checked_at", checkedAt)
            .put("profile_id", profileId)
            .put("profile_sha256", profileDigest)
            .put("artifact_digest", artifactDigest ?: JSONObject.NULL)
            .put("checks", checks)
            .put("raw_verdict", rawVerdict ?: JSONObject.NULL)
            .put("sequence", sequence ?: JSONObject.NULL)
    }

    fun digest(): String = EngineMetadataStore.digest(canonicalJson())
}

/** Maps explicit local observations only. Missing evidence never becomes PASS. */
object LocalPostureDecision {
    val maxAge: Duration = Duration.ofDays(7)

    fun evaluate(
        report: CompatibilityPostureReport,
        currentProfile: JSONObject,
        now: Instant,
        currentArtifactDigest: String?,
    ): PostureResult {
        require(currentProfile.optString("id") == report.profileId) { "Local posture report is for a different profile ID." }
        val actualProfileDigest = EngineMetadataStore.digest(currentProfile)
        val checkedAt = report.checkedAt
        val profileChanged = report.profileDigest != actualProfileDigest
        val artifactUnknown = currentArtifactDigest == null || report.artifactDigest == null
        val drift = when {
            profileChanged -> "The saved identity changed after this report."
            report.artifactDigest == null -> "The report artifact digest is unavailable or inconsistent, so artifact freshness is unknown."
            artifactUnknown -> "The current artifact digest is unavailable, so artifact freshness is unknown."
            report.artifactDigest != currentArtifactDigest -> "The current artifact changed after this report."
            Duration.between(Instant.parse(report.checkedAt), now) > maxAge -> "This local report is older than 7 days."
            Duration.between(now, Instant.parse(report.checkedAt)) > Duration.ofMinutes(1) -> "This local report has a future timestamp."
            else -> null
        }
        val passes = report.passIds.size
        val warnings = report.warningIds.size
        val failures = report.failureIds.size
        val unknowns = report.checks.count { it.status == PostureCheckStatus.UNKNOWN }
        val state = when {
            profileChanged -> PostureState.NEEDS_ROTATION
            artifactUnknown -> PostureState.UNKNOWN
            drift != null -> PostureState.NEEDS_ROTATION
            failures > 0 -> PostureState.FAILED
            warnings > 0 -> PostureState.DEGRADED
            unknowns > 0 -> PostureState.UNKNOWN
            passes > 0 -> PostureState.PASS
            else -> PostureState.UNKNOWN
        }
        val reason = when {
            drift != null -> drift
            failures > 0 -> "Local checks failed: $failures failed, $warnings warned, $passes passed."
            warnings > 0 -> "Local checks need review: $warnings warned, $unknowns unknown, $passes passed."
            unknowns > 0 -> "Local checks are inconclusive: $unknowns unknown, $passes passed."
            passes > 0 -> "Local checks passed: $passes passed."
            else -> "The local report contained no definite check result."
        } + " Local report only; no platform verdict."
        return PostureResult(state, checkedAt, "local-report", reason, report.providerId,
            report.artifactDigest, report.profileDigest, report.digest(), report.sequence)
    }
}

/** Connects one loaded report to the currently saved profile and bundled artifact. */
class LocalReportPostureProvider(
    private val profileFor: (String) -> JSONObject?,
    private val reportFor: (String) -> CompatibilityPostureReport?,
    private val artifactDigestFor: (String) -> String? = { null },
    private val now: () -> Instant = Instant::now,
) : CompatibilityPostureProvider {
    override fun evaluate(profileId: String): PostureResult {
        val report = reportFor(profileId) ?: return PostureResult(source = "local-report",
            providerId = "local-report", reason = "No local posture report has been loaded for this identity.")
        val profile = profileFor(profileId) ?: return PostureResult(PostureState.NEEDS_ROTATION, report.checkedAt,
            "local-report", "The saved identity is missing, so this report cannot be reused.", report.providerId,
            report.artifactDigest, report.profileDigest)
        return runCatching { LocalPostureDecision.evaluate(report, profile, now(), artifactDigestFor(profileId)) }
            .getOrElse { error ->
                PostureResult(source = "local-report", providerId = report.providerId,
                    reason = "This local report could not be used: ${error.message ?: "invalid report"}.")
            }
    }
}

/** Hashes the bundled module payload so posture can detect an artifact change without a platform call. */
object PostureArtifacts {
    private val supportedAbis = setOf("arm64-v8a", "armeabi-v7a")

    fun bundledModuleDigest(context: Context, abi: String? = defaultAbi()): String? {
        if (abi == null) return null
        return runCatching {
            val input = context.assets.open("cyclone-cloak-module.zip")
            try {
                ZipInputStream(input).use { zip ->
                    var entry = zip.nextEntry
                    var artifactDigest: String? = null
                    while (entry != null) {
                        if (entry.name == "zygisk/$abi.so") {
                            artifactDigest = hash(zip)
                            break
                        }
                        entry = zip.nextEntry
                    }
                    artifactDigest
                }
            } finally {
                runCatching { input.close() }
            }
        }.getOrNull()
    }

    private fun defaultAbi(): String? = Build.SUPPORTED_ABIS.firstOrNull { it in supportedAbis }

    private fun hash(input: InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
