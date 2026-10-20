package dev.cyclone.cloak.play

import dev.cyclone.cloak.forge.StrictJson
import org.json.JSONObject
import java.security.PublicKey
import java.security.Signature
import java.security.interfaces.RSAPublicKey
import java.time.Duration
import java.time.Instant
import java.util.Base64

/** The signed document is a backend assertion, not a Google-signed offline token. */
class IntegrityVerdict internal constructor(val provider: String, val requestId: String,
    val requestHash: String, val profileId: String, val profileHash: String, val artifactHash: String,
    val androidUserId: Int, val timestamp: Instant, val decodedAt: Instant, val appRecognition: String,
    val appLicensing: String, val deviceRecognition: Set<String>, val evidenceDigest: String)

enum class IntegrityState { NO_REQUEST, TOKEN_RECEIVED, DECODED_AVAILABLE, PASS, DEGRADED, FAIL, NEEDS_REVIEW }
data class IntegrityResult(val state: IntegrityState, val reason: String, val verdict: IntegrityVerdict? = null) {
    val description: String get() = when (reason) {
        "IV_NO_REQUEST" -> "No Integrity request is recorded."
        "IV_TOKEN_ONLY" -> "Token received; an authenticated decoded verdict is unavailable."
        "IV_PASS" -> "Authenticated backend verdict meets the local device/app/licensing policy."
        "IV_DEGRADED" -> "Authenticated verdict reports basic integrity only."
        "IV_FAIL" -> "Authenticated verdict does not meet the local device/app/licensing policy."
        "IV_UNEVALUATED" -> "Decoded verdict is available but required verdict fields are unevaluated."
        "IV_STALE" -> "The verdict or request receipt is outside the freshness window."
        "IV_FUTURE" -> "A verdict or receipt timestamp is in the future."
        "IV_AUTHENTICATION" -> "The backend signature is invalid or its provider key is not configured."
        "IV_MALFORMED" -> "The verdict is malformed, incomplete or contains unsupported fields."
        "IV_STORAGE" -> "The private Integrity evidence record is unreadable."
        "IV_PROFILE_MISMATCH" -> "The verdict or request belongs to a different profile or profile body."
        "IV_USER_MISMATCH" -> "The verdict or request belongs to a different Android user."
        "IV_ARTIFACT_MISMATCH" -> "The verdict or request belongs to a different app/module artifact."
        "IV_REQUEST_MISMATCH" -> "The verdict request ID does not match the saved local request."
        "IV_REQUEST_HASH_MISMATCH" -> "The request hash does not match the saved request and its profile/user/artifact binding."
        "IV_NO_RECEIPT" -> "Import the matching token-receipt diagnostics report before a decoded verdict."
        "IV_RECEIPT_MISMATCH" -> "The verdict receipt does not match the current local diagnostics evidence."
        else -> "The verdict does not match the saved request, profile, Android user or artifact."
    }
}

/** Captured from the validated local envelope, never taken from the imported verdict. */
data class IntegrityRequest(val requestId: String, val profileId: String, val profileHash: String,
    val artifactHash: String, val androidUserId: Int, val receivedAt: Instant?, val tokenDigest: String?) {
    init {
        require(listOf(requestId, profileId).all { Regex("[A-Za-z0-9][A-Za-z0-9._:-]{0,95}").matches(it) })
        require(listOfNotNull(profileHash, artifactHash, tokenDigest).all { Regex("[0-9a-f]{64}").matches(it) })
        require(androidUserId in 0..21474 && (receivedAt == null) == (tokenDigest == null))
    }
    val requestHash: String get() = dev.cyclone.cloak.playevidence.EvidenceReport.digest(
        "$requestId\n$profileId\n$profileHash\n$androidUserId\n$artifactHash".toByteArray(Charsets.UTF_8))
    companion object {
        fun fromEnvelope(context: PlayReadinessContext, text: String): IntegrityRequest {
            val envelope = dev.cyclone.cloak.playevidence.EvidenceReport.parse(text)
            require(envelope.getString("profile_id") == context.profileId && envelope.getString("profile_hash") == context.profileHash)
            require(envelope.getString("artifact_hash") == context.artifactHash && envelope.getInt("android_user_id") == context.androidUserId)
            val rows = envelope.getJSONArray("rows")
            val row = (0 until rows.length()).map { rows.getJSONObject(it) }.single { it.getString("id") == "integrity.api" }
            val received = row.getString("reason") == "TOKEN_RECEIVED"
            val metadata = row.getJSONObject("metadata")
            val request = IntegrityRequest(envelope.getString("request_id"), context.profileId, context.profileHash, context.artifactHash,
                context.androidUserId, if (received) PlayReadinessReport.timestamp(metadata.getString("received_at")) else null,
                if (received) metadata.getString("response_digest") else null)
            if (received && metadata.getString("request_hash") != request.requestHash) throw IntegrityVerdicts.Rejected("IV_REQUEST_HASH_MISMATCH")
            return request
        }
    }
}

/** Runtime trust pins must be provisioned independently of report import. Empty means reject all. */
fun interface IntegrityTrust { fun key(provider: String, keyId: String): PublicKey? }

/** Implement on the trusted server: Google decodeIntegrityToken, validate requestDetails, then sign.
 * No networking, credentials, encrypted-token persistence or local decoding is implemented here.
 */
interface IntegrityBackendAdapter {
    fun authenticatedVerdict(request: IntegrityRequest): String?
}

class IntegrityPlayReadinessProvider(private val request: IntegrityRequest?, private val verdict: String?,
    private val trust: IntegrityTrust) : PlayReadinessProvider {
    override val id = "local.play.integrity.bridge"
    override fun evaluate(context: PlayReadinessContext, report: String?, requestReview: Boolean): PlayReadinessResult =
        IntegrityVerdicts.readiness(LocalPlayReadinessProvider(id).evaluate(context, report, requestReview),
            IntegrityVerdicts.evaluate(context, request, verdict, trust))
}

object IntegrityVerdicts {
    const val MAX_BYTES = 32768
    class Rejected(val code: String) : IllegalArgumentException(code)
    private val id = Regex("[A-Za-z0-9][A-Za-z0-9._:-]{0,95}")
    private val hash = Regex("[0-9a-f]{64}")
    private fun reject(code: String): Nothing = throw Rejected(code)
    private fun string(j: JSONObject, key: String): String {
        require(j.get(key) is String); return j.getString(key)
    }
    private fun base64(text: String): ByteArray {
        val bytes = Base64.getDecoder().decode(text)
        require(Base64.getEncoder().encodeToString(bytes) == text)
        return bytes
    }
    fun parse(text: String, trust: IntegrityTrust): IntegrityVerdict {
        try {
            require(text.toByteArray(Charsets.UTF_8).size <= MAX_BYTES)
            StrictJson(text).check()
            val outer = JSONObject(text)
            PlayReadinessReport.keys(outer, setOf("format", "algorithm", "provider", "key_id", "payload", "signature"))
            require(outer.get("format") == 1 && string(outer, "algorithm") == "RS256")
            val provider = string(outer, "provider"); val keyId = string(outer, "key_id")
            require(id.matches(provider) && id.matches(keyId))
            require(string(outer, "payload").length in 4..24000 && string(outer, "signature").length in 344..2048)
            val bytes = base64(string(outer, "payload"))
            val key = trust.key(provider, keyId) as? RSAPublicKey ?: reject("IV_AUTHENTICATION")
            if (key.modulus.bitLength() < 2048) reject("IV_AUTHENTICATION")
            val valid = try { Signature.getInstance("SHA256withRSA").run {
                initVerify(key); update(bytes); verify(base64(string(outer, "signature")))
            } } catch (_: Exception) { false }
            if (!valid) reject("IV_AUTHENTICATION")
            val payload = Charsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                .decode(java.nio.ByteBuffer.wrap(bytes)).toString()
            StrictJson(payload).check()
            val j = JSONObject(payload)
            PlayReadinessReport.keys(j, setOf("format", "provider", "request_id", "request_hash", "profile_id",
                "profile_hash", "artifact_hash", "android_user_id", "timestamp", "decoded_at", "app_recognition",
                "app_licensing", "device_recognition"))
            require(j.get("format") == 1 && string(j, "provider") == provider)
            for (field in listOf("request_id", "profile_id")) require(id.matches(string(j, field)))
            for (field in listOf("request_hash", "profile_hash", "artifact_hash")) require(hash.matches(string(j, field)))
            require(j.get("android_user_id") is Int && j.getInt("android_user_id") in 0..21474)
            val at = PlayReadinessReport.timestamp(string(j, "timestamp"))
            val decoded = PlayReadinessReport.timestamp(string(j, "decoded_at"))
            require(decoded >= at)
            val app = string(j, "app_recognition"); val license = string(j, "app_licensing")
            require(app in setOf("PLAY_RECOGNIZED", "UNRECOGNIZED_VERSION", "UNEVALUATED"))
            require(license in setOf("LICENSED", "UNLICENSED", "UNEVALUATED"))
            val a = j.getJSONArray("device_recognition")
            require(a.length() <= 4)
            val labels = (0 until a.length()).map { require(a.get(it) is String); a.getString(it) }
            require(labels.distinct().size == labels.size && labels.all { it in setOf("MEETS_BASIC_INTEGRITY",
                "MEETS_DEVICE_INTEGRITY", "MEETS_STRONG_INTEGRITY", "MEETS_VIRTUAL_INTEGRITY") })
            return IntegrityVerdict(provider, j.getString("request_id"), j.getString("request_hash"), j.getString("profile_id"),
                j.getString("profile_hash"), j.getString("artifact_hash"), j.getInt("android_user_id"), at, decoded,
                app, license, labels.toSet(), dev.cyclone.cloak.playevidence.EvidenceReport.digest(bytes))
        } catch (error: Rejected) { throw error } catch (_: Exception) { reject("IV_MALFORMED") }
    }
    fun evaluate(context: PlayReadinessContext, request: IntegrityRequest?, text: String?, trust: IntegrityTrust): IntegrityResult {
        if (request == null) return IntegrityResult(if (text == null) IntegrityState.NO_REQUEST else IntegrityState.NEEDS_REVIEW,
            if (text == null) "IV_NO_REQUEST" else "IV_REQUEST_MISMATCH")
        fun review(code: String) = IntegrityResult(IntegrityState.NEEDS_REVIEW, code)
        if (request.profileId != context.profileId || request.profileHash != context.profileHash) return review("IV_PROFILE_MISMATCH")
        if (request.androidUserId != context.androidUserId) return review("IV_USER_MISMATCH")
        if (request.artifactHash != context.artifactHash) return review("IV_ARTIFACT_MISMATCH")
        val receipt = request.receivedAt
        if (receipt == null || request.tokenDigest == null) return if (text == null) IntegrityResult(IntegrityState.NO_REQUEST, "IV_NO_REQUEST") else review("IV_NO_RECEIPT")
        if (receipt > context.now) return review("IV_FUTURE")
        if (Duration.between(receipt, context.now) > context.freshness) return review("IV_STALE")
        if (text == null) return IntegrityResult(IntegrityState.TOKEN_RECEIVED, "IV_TOKEN_ONLY")
        val v = try { parse(text, trust) } catch (e: Rejected) { return review(e.code) }
        if (v.requestId != request.requestId) return review("IV_REQUEST_MISMATCH")
        if (v.profileId != context.profileId || v.profileHash != context.profileHash) return review("IV_PROFILE_MISMATCH")
        if (v.androidUserId != context.androidUserId) return review("IV_USER_MISMATCH")
        if (v.artifactHash != context.artifactHash) return review("IV_ARTIFACT_MISMATCH")
        if (v.requestHash != request.requestHash) return review("IV_REQUEST_HASH_MISMATCH")
        if (v.timestamp > context.now || v.decodedAt > context.now) return review("IV_FUTURE")
        if (Duration.between(v.timestamp, context.now) > context.freshness || Duration.between(v.decodedAt, context.now) > context.freshness)
            return review("IV_STALE")
        // Token cannot be issued after its locally recorded receipt.
        if (v.timestamp > receipt || v.decodedAt < receipt) return review("IV_RECEIPT_MISMATCH")
        val state = when {
            v.appRecognition == "UNEVALUATED" || v.appLicensing == "UNEVALUATED" -> IntegrityState.DECODED_AVAILABLE
            v.appRecognition != "PLAY_RECOGNIZED" || v.appLicensing != "LICENSED" -> IntegrityState.FAIL
            "MEETS_DEVICE_INTEGRITY" in v.deviceRecognition -> IntegrityState.PASS
            "MEETS_BASIC_INTEGRITY" in v.deviceRecognition -> IntegrityState.DEGRADED
            else -> IntegrityState.FAIL
        }
        return IntegrityResult(state, when (state) { IntegrityState.PASS -> "IV_PASS"; IntegrityState.DEGRADED -> "IV_DEGRADED"
            IntegrityState.FAIL -> "IV_FAIL"; else -> "IV_UNEVALUATED" }, v)
    }

    /** Only retain or downgrade; Integrity never fills missing required local observations. */
    fun readiness(base: PlayReadinessResult, integrity: IntegrityResult): PlayReadinessResult {
        if (integrity.state == IntegrityState.NO_REQUEST || integrity.state == IntegrityState.TOKEN_RECEIVED || integrity.state == IntegrityState.PASS) return base
        val state = when {
            integrity.state == IntegrityState.NEEDS_REVIEW || base.state == PlayReadinessState.UNKNOWN -> PlayReadinessState.UNKNOWN
            base.state == PlayReadinessState.BLOCKED || integrity.state == IntegrityState.FAIL -> PlayReadinessState.BLOCKED
            integrity.state == IntegrityState.DECODED_AVAILABLE -> PlayReadinessState.UNKNOWN
            else -> PlayReadinessState.DEGRADED
        }
        val record = JSONObject(base.contractRecordJson).put("state", state.name)
            .put("reason_codes", org.json.JSONArray(listOf(if (integrity.state == IntegrityState.NEEDS_REVIEW) "PS_REVIEW_REQUIRED" else "PS_EXPECTATION_CONFLICT")))
        return base.copy(state = state, review = if (integrity.state == IntegrityState.NEEDS_REVIEW) PlayReviewState.NEEDS_REVIEW else base.review,
            findings = base.findings + PlayReadinessFinding("integrity", integrity.reason),
            contractRecordJson = dev.cyclone.cloak.forge.PlaySurfaceContract.encodeReadiness(record))
    }
}
