package dev.cyclone.cloak.playevidence

import dev.cyclone.cloak.forge.StrictJson
import org.json.JSONObject
import java.time.Instant

/** Sensitive ciphertext container. Never include in reports, diagnostics, or toString(). */
class VerificationBundle private constructor(private val encoded: String) {
    override fun toString(): String = "VerificationBundle(REDACTED)"
    fun export(): ByteArray = encoded.toByteArray(Charsets.UTF_8)
    companion object {
        const val MAX_BYTES = 65536
        fun requestHash(b: EvidenceBinding): String = EvidenceReport.digest(
            "${b.requestId}\n${b.profileId}\n${b.profileHash}\n${b.androidUserId}\n${b.artifactHash}".toByteArray(Charsets.UTF_8))
        fun retain(token: String, binding: EvidenceBinding, at: Instant, explicitConsent: Boolean): VerificationBundle? {
            if (!explicitConsent) return null
            val body = JSONObject().put("request_id", binding.requestId).put("profile_id", binding.profileId)
                .put("profile_hash", binding.profileHash).put("artifact_hash", binding.artifactHash)
                .put("android_user_id", binding.androidUserId).put("received_at", at.toString())
                .put("token_digest", EvidenceReport.digest(token.toByteArray(Charsets.UTF_8))).put("token", token)
            return parse(JSONObject().put("format", 1).put("request_hash", requestHash(binding)).put("bundle", body).toString(), at)
        }
        fun parse(text: String, now: Instant = Instant.now(), expected: EvidenceBinding? = null): VerificationBundle {
            require(text.toByteArray(Charsets.UTF_8).size in 1..MAX_BYTES) { "BUNDLE_SIZE" }
            StrictJson(text).check()
            val root = JSONObject(text)
            require(root.keys().asSequence().toSet() == setOf("format", "request_hash", "bundle")) { "BUNDLE_FIELDS" }
            require(root.get("format") is Int && root.getInt("format") == 1) { "BUNDLE_FORMAT" }
            val b = root.getJSONObject("bundle")
            require(b.keys().asSequence().toSet() == setOf("request_id", "profile_id", "profile_hash", "artifact_hash",
                "android_user_id", "received_at", "token_digest", "token")) { "BUNDLE_FIELDS" }
            require(b.keys().asSequence().filter { it != "android_user_id" }.all { b.get(it) is String }
                && root.get("request_hash") is String) { "BUNDLE_TYPES" }
            require(b.get("android_user_id") is Int) { "BUNDLE_USER" }
            val binding = EvidenceBinding(b.getString("request_id"), b.getString("profile_id"), b.getString("profile_hash"),
                b.getString("artifact_hash"), b.getInt("android_user_id"))
            require(expected == null || expected == binding) { "BUNDLE_BINDING" }
            require(root.getString("request_hash") == requestHash(binding)) { "BUNDLE_REQUEST_HASH" }
            val raw = b.getString("token")
            require(Regex("[A-Za-z0-9._~+/=-]{1,60000}").matches(raw)) { "BUNDLE_TOKEN" }
            require(b.getString("token_digest") == EvidenceReport.digest(raw.toByteArray(Charsets.UTF_8))) { "BUNDLE_TOKEN_DIGEST" }
            val time = b.getString("received_at")
            require(Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}Z").matches(time)) { "BUNDLE_TIMESTAMP" }
            val at = Instant.parse(time)
            require(!at.isAfter(now) && !at.isBefore(now.minusSeconds(86400))) { "BUNDLE_FRESHNESS" }
            return VerificationBundle(root.toString())
        }
    }
}
