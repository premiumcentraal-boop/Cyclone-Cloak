package dev.cyclone.cloak.play

import android.content.Context
import dev.cyclone.cloak.forge.StrictJson
import org.json.JSONObject
import java.io.File
import java.security.KeyFactory
import java.security.PublicKey
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

/** Public pins only, installed independently by a trusted local runtime configurator.
 * A report can never introduce its own trust key. Missing/invalid config trusts nobody.
 */
class LocalIntegrityTrust(private val directory: File) : IntegrityTrust {
    override fun key(provider: String, keyId: String): PublicKey? = runCatching {
        val file = File(directory, "play-integrity-trust-v1.json")
        require(file.length() in 1..16384)
        val text = file.inputStream().use { it.readBounded(16384) }.toString(Charsets.UTF_8)
        StrictJson(text).check()
        val j = JSONObject(text)
        PlayReadinessReport.keys(j, setOf("format", "keys")); require(j.get("format") == 1)
        val keys = j.getJSONArray("keys"); require(keys.length() in 1..8)
        val entries = (0 until keys.length()).map { keys.getJSONObject(it).also { entry ->
            PlayReadinessReport.keys(entry, setOf("provider", "key_id", "public_key"))
            for (name in listOf("provider", "key_id", "public_key")) require(entry.get(name) is String)
            require(Regex("[A-Za-z0-9][A-Za-z0-9._:-]{0,95}").matches(entry.getString("provider")))
            require(Regex("[A-Za-z0-9][A-Za-z0-9._:-]{0,95}").matches(entry.getString("key_id")))
        } }
        require(entries.map { it.getString("provider") to it.getString("key_id") }.distinct().size == entries.size)
        val entry = entries.singleOrNull { it.getString("provider") == provider && it.getString("key_id") == keyId }
            ?: return@runCatching null
        KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(entry.getString("public_key"))))
    }.getOrNull()
}

/** Separate, app-private safe evidence sidecar; no token/profile/binding/publication writer. */
class IntegrityVerdictStore(context: Context,
    private val trust: IntegrityTrust = LocalIntegrityTrust(context.filesDir)) {
    private val preferences = context.getSharedPreferences("play-integrity-bridge-v1", Context.MODE_PRIVATE)
    companion object { private val lock = Any() }
    private data class Stored(val request: IntegrityRequest, val envelope: String?)
    private fun digest(envelope: String): String = dev.cyclone.cloak.playevidence.EvidenceReport.digest(
        Base64.getDecoder().decode(JSONObject(envelope).getString("payload")))
    private fun requestJson(r: IntegrityRequest): JSONObject = JSONObject().put("request_id", r.requestId)
        .put("profile_id", r.profileId).put("profile_hash", r.profileHash).put("artifact_hash", r.artifactHash)
        .put("android_user_id", r.androidUserId).put("received_at", r.receivedAt?.toString() ?: JSONObject.NULL)
        .put("token_digest", r.tokenDigest ?: JSONObject.NULL)
    private fun load(id: String): Stored? {
        val text = preferences.getString(id, null) ?: return null
        require(text.toByteArray(Charsets.UTF_8).size <= IntegrityVerdicts.MAX_BYTES * 2)
        StrictJson(text).check()
        val j = JSONObject(text)
        PlayReadinessReport.keys(j, setOf("format", "request", "envelope", "evidence_digest")); require(j.get("format") == 1)
        val r = j.getJSONObject("request")
        PlayReadinessReport.keys(r, setOf("request_id", "profile_id", "profile_hash", "artifact_hash", "android_user_id", "received_at", "token_digest"))
        for (name in listOf("request_id", "profile_id", "profile_hash", "artifact_hash")) require(r.get(name) is String)
        require(r.get("android_user_id") is Int)
        require(r.getString("profile_id") == id)
        val request = IntegrityRequest(r.getString("request_id"), id, r.getString("profile_hash"), r.getString("artifact_hash"),
            r.getInt("android_user_id"), if (r.isNull("received_at")) null else {
                require(r.get("received_at") is String); PlayReadinessReport.timestamp(r.getString("received_at"))
            }, if (r.isNull("token_digest")) null else { require(r.get("token_digest") is String); r.getString("token_digest") })
        val envelope = if (j.isNull("envelope")) null else { require(j.get("envelope") is String); j.getString("envelope") }
        require(if (envelope == null) j.isNull("evidence_digest") else j.get("evidence_digest") is String && j.getString("evidence_digest") == digest(envelope))
        return Stored(request, envelope)
    }
    @android.annotation.SuppressLint("UseKtx") // KTX edit returns Unit; retain the checked synchronous commit result.
    private fun write(id: String, stored: Stored) {
        val text = JSONObject().put("format", 1).put("request", requestJson(stored.request))
            .put("envelope", stored.envelope ?: JSONObject.NULL)
            .put("evidence_digest", stored.envelope?.let(::digest) ?: JSONObject.NULL).toString()
        require(text.toByteArray(Charsets.UTF_8).size <= IntegrityVerdicts.MAX_BYTES * 2)
        check(preferences.edit().putString(id, text).commit()) { "IV_SAVE_FAILED" }
    }
    /** Called only after explicit accepted diagnostics save. No ciphertext is present in the envelope. */
    fun recordRequest(context: PlayReadinessContext, text: String) = synchronized(lock) {
        PlayEvidenceImport.projection(text, context)
        val request = IntegrityRequest.fromEnvelope(context, text)
        // Explicitly reviewed valid diagnostics may repair this sidecar, never profile data.
        val prior = runCatching { load(context.profileId) }.getOrNull()
        write(context.profileId, Stored(request, prior?.takeIf { it.request == request }?.envelope))
    }
    fun read(context: PlayReadinessContext, localReport: String? = null): IntegrityResult = synchronized(lock) {
        try {
            val stored = load(context.profileId)
            if (stored != null && localReport != null) {
                val receipt = PlayReadinessReport.parse(localReport).json.optJSONObject("integrity_response")
                if (stored.request.tokenDigest != receipt?.getString("response_digest") ||
                    stored.request.receivedAt?.toString() != receipt?.getString("received_at")) {
                    return@synchronized IntegrityResult(IntegrityState.NEEDS_REVIEW, "IV_RECEIPT_MISMATCH")
                }
            }
            IntegrityVerdicts.evaluate(context, stored?.request, stored?.envelope, trust)
        } catch (_: Exception) { IntegrityResult(IntegrityState.NEEDS_REVIEW, "IV_STORAGE") }
    }
    /** Authenticate and bind again immediately before committing. Rejections preserve old evidence. */
    fun save(context: PlayReadinessContext, text: String): IntegrityResult = synchronized(lock) {
        val stored = load(context.profileId) ?: throw IntegrityVerdicts.Rejected("IV_NO_RECEIPT")
        val result = IntegrityVerdicts.evaluate(context, stored.request, text, trust)
        if (result.state == IntegrityState.NEEDS_REVIEW || result.verdict == null) throw IntegrityVerdicts.Rejected(result.reason)
        write(context.profileId, stored.copy(envelope = text))
        result
    }
}
