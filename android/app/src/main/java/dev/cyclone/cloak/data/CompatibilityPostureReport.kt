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

data class PostureReportCheck(val id: String, val status: PostureCheckStatus, val message: String?) {
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
    val artifactDigest: String,
    val checks: List<PostureReportCheck>,
    val rawVerdict: String? = null,
) {
    val passIds get() = checks.filter { it.status == PostureCheckStatus.PASS }.map { it.id }
    val warningIds get() = checks.filter { it.status == PostureCheckStatus.WARNING }.map { it.id }
    val failureIds get() = checks.filter { it.status == PostureCheckStatus.FAIL }.map { it.id }

    init {
        require(providerId.isNotBlank() && providerId.length <= 128 && providerId.none(Char::isISOControl)) { "Invalid posture provider ID." }
        Instant.parse(checkedAt)
        require(profileId.isNotBlank() && profileId.length <= 128 && profileId.none(Char::isISOControl)) { "Invalid posture profile ID." }
        require(Regex("[0-9a-f]{64}").matches(profileDigest)) { "Profile digest must be lowercase SHA-256." }
        require(Regex("[0-9a-f]{64}").matches(artifactDigest)) { "Artifact digest must be lowercase SHA-256." }
        require(checks.isNotEmpty() && checks.size <= 1000) { "Posture report must contain one to 1000 checks." }
        require(checks.map { it.id }.toSet().size == checks.size) { "Posture check IDs must be unique." }
        rawVerdict?.let {
            require(it.isNotBlank() && it.length <= 256 && it.none(Char::isISOControl)) { "Invalid raw posture verdict." }
        }
    }

    companion object {
        fun parse(json: JSONObject): CompatibilityPostureReport {
            val required = setOf("format", "provider_id", "checked_at", "profile_id", "profile_sha256", "artifact_digest", "checks")
            val optional = setOf("raw_verdict")
            require(json.keys().asSequence().toSet().let { keys -> keys.containsAll(required) && keys.all { it in required || it in optional } }) { "Malformed local posture report." }
            require(json.opt("format") == 1) { "Unsupported local posture report format." }
            fun text(key: String): String = json.opt(key) as? String ?: throw IllegalArgumentException("Invalid local posture $key.")
            val rawChecks = json.getJSONArray("checks")
            val checks = (0 until rawChecks.length()).map { index ->
                val row = rawChecks.getJSONObject(index)
                val allowed = setOf("id", "status", "message")
                require(row.keys().asSequence().toSet().let { keys -> keys.containsAll(setOf("id", "status")) && keys.all { it in allowed } }) { "Malformed local posture check." }
                val message = if (row.isNull("message")) null else row.optString("message")
                PostureReportCheck(row.getString("id"), PostureCheckStatus.valueOf(row.getString("status")), message)
            }
            return CompatibilityPostureReport(
                text("provider_id"), text("checked_at"), text("profile_id"), text("profile_sha256"),
                text("artifact_digest"), checks, if (json.has("raw_verdict")) text("raw_verdict") else null,
            )
        }
    }
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
        val artifactUnknown = currentArtifactDigest == null
        val drift = when {
            profileChanged -> "The saved identity changed after this report."
            artifactUnknown -> "The current artifact digest is unavailable, so artifact freshness is unknown."
            report.artifactDigest != currentArtifactDigest -> "The current artifact changed after this report."
            Duration.between(Instant.parse(report.checkedAt), now) > maxAge -> "This local report is older than 7 days."
            Duration.between(now, Instant.parse(report.checkedAt)) > Duration.ofMinutes(1) -> "This local report has a future timestamp."
            else -> null
        }
        val passes = report.passIds.size
        val warnings = report.warningIds.size
        val failures = report.failureIds.size
        val state = when {
            profileChanged -> PostureState.NEEDS_ROTATION
            artifactUnknown -> PostureState.UNKNOWN
            drift != null -> PostureState.NEEDS_ROTATION
            failures > 0 -> PostureState.FAILED
            warnings > 0 -> PostureState.DEGRADED
            passes > 0 -> PostureState.PASS
            else -> PostureState.UNKNOWN
        }
        val reason = when {
            drift != null -> drift
            failures > 0 -> "Local checks failed: $failures failed, $warnings warned, $passes passed."
            warnings > 0 -> "Local checks need review: $warnings warned, $passes passed."
            passes > 0 -> "Local checks passed: $passes passed."
            else -> "The local report contained no definite check result."
        } + " Local report only; no platform verdict."
        return PostureResult(state, checkedAt, "local-report", reason, report.providerId,
            report.artifactDigest, report.profileDigest)
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
