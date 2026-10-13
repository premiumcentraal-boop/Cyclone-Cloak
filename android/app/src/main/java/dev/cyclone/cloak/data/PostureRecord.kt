package dev.cyclone.cloak.data

import org.json.JSONObject
import java.time.Instant

/** Neutral local compatibility state. PASS is a recorded local result, never a platform verdict. */
enum class PostureState { UNKNOWN, PASS, DEGRADED, FAILED, NEEDS_ROTATION }

data class PostureRecord(
    val profileId: String,
    val state: PostureState,
    val checkedAt: String?,
    val source: String,
    val reason: String,
    val providerId: String? = null,
    val artifactDigest: String? = null,
    val profileDigest: String? = null,
    val reportDigest: String? = null,
    val sequence: Long? = null,
) {
    init {
        require(profileId.isNotBlank() && profileId.length <= 128 && profileId.none(Char::isISOControl)) { "Invalid posture profile ID." }
        require(source.isNotBlank() && source.length <= 128 && source.none(Char::isISOControl)) { "Invalid posture source." }
        require(reason.isNotBlank() && reason.length <= 1024 && reason.none(Char::isISOControl)) { "Invalid posture reason." }
        checkedAt?.let { Instant.parse(it) }
        providerId?.let { require(it.isNotBlank() && it.length <= 128 && it.none(Char::isISOControl)) { "Invalid posture provider ID." } }
        artifactDigest?.let { require(Regex("[0-9a-f]{64}").matches(it)) { "Artifact digest must be lowercase SHA-256." } }
        profileDigest?.let { require(Regex("[0-9a-f]{64}").matches(it)) { "Profile digest must be lowercase SHA-256." } }
        reportDigest?.let { require(Regex("[0-9a-f]{64}").matches(it)) { "Report digest must be lowercase SHA-256." } }
        sequence?.let { require(it >= 0) { "Report sequence must not be negative." } }
    }

    fun toJson(): JSONObject = JSONObject().put("format", 1).put("profile_id", profileId).put("state", state.name)
        .put("checked_at", checkedAt ?: JSONObject.NULL).put("source", source).put("reason", reason)
        .put("provider_id", providerId ?: JSONObject.NULL).put("artifact_digest", artifactDigest ?: JSONObject.NULL)
        .put("profile_digest", profileDigest ?: JSONObject.NULL)
        .put("report_digest", reportDigest ?: JSONObject.NULL)
        .put("sequence", sequence ?: JSONObject.NULL)

    companion object {
        fun fromJson(json: JSONObject): PostureRecord {
            val required = setOf("format", "profile_id", "state", "checked_at", "source", "reason")
            require(json.keys().asSequence().toSet().let { it.containsAll(required) && it.all { key -> key in required || key in setOf("provider_id", "artifact_digest", "profile_digest", "report_digest", "sequence") } }) { "Malformed posture record." }
            require(json.opt("format") == 1) { "Unsupported posture record format." }
            fun text(key: String): String = json.opt(key) as? String ?: throw IllegalArgumentException("Invalid posture $key.")
            fun optional(key: String): String? = if (!json.has(key) || json.isNull(key)) null else text(key)
            val sequence = if (!json.has("sequence") || json.isNull("sequence")) null else when (val value = json.opt("sequence")) {
                is Int -> value.toLong(); is Long -> value; else -> throw IllegalArgumentException("Invalid posture sequence.")
            }
            return PostureRecord(text("profile_id"), PostureState.valueOf(text("state")), optional("checked_at"), text("source"),
                text("reason"), optional("provider_id"), optional("artifact_digest"), optional("profile_digest"),
                optional("report_digest"), sequence)
        }
    }
}

data class PostureResult(
    val state: PostureState = PostureState.UNKNOWN,
    val checkedAt: String? = null,
    val source: String = "local",
    val reason: String = "No compatibility posture result is available.",
    val providerId: String? = null,
    val artifactDigest: String? = null,
    val profileDigest: String? = null,
    val reportDigest: String? = null,
    val sequence: Long? = null,
) {
    fun recordFor(profileId: String): PostureRecord = PostureRecord(profileId, state, checkedAt, source, reason, providerId, artifactDigest, profileDigest, reportDigest, sequence)
}

/** Providers supply explicit local results; they must not infer or fabricate platform verdicts.
 * No result is represented by UNKNOWN, with checkedAt null. Calling a provider never saves a record.
 */
interface CompatibilityPostureProvider {
    fun evaluate(profileId: String): PostureResult
}

class LocalCompatibilityPostureProvider : CompatibilityPostureProvider {
    override fun evaluate(profileId: String): PostureResult = PostureResult(providerId = "local-unknown")
}

/** Pure transitions only. Callers decide whether to persist; no identity reset or reseed is performed. */
object PostureRotation {
    fun markNeedsRotation(profileId: String, reason: String, checkedAt: String? = null): PostureRecord =
        PostureRecord(profileId, PostureState.NEEDS_ROTATION, checkedAt, "local-rotation", reason)

    fun afterExplicitReset(record: PostureRecord, resetCompleted: Boolean, checkedAt: String? = null): PostureRecord {
        require(resetCompleted) { "A completed explicit local reset or reseed is required." }
        return if (record.state != PostureState.NEEDS_ROTATION) record else
            PostureRecord(record.profileId, PostureState.UNKNOWN, checkedAt, "local-reset",
                "Explicit local reset completed; compatibility posture has not been checked.",
                providerId = null, artifactDigest = null, profileDigest = null, reportDigest = null, sequence = null)
    }
}
