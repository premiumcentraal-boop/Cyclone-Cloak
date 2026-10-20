package dev.cyclone.cloak.play

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.Signature
import java.time.Duration
import java.util.Base64

/** Synthetic payloads signed only with ephemeral test keys; no real verdict is claimed. */
internal object IntegrityFixtures {
    val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
    val trust = IntegrityTrust { p, k -> if (p == "test.backend" && k == "test-key") pair.public else null }
    fun request() = IntegrityRequest("request-a", "profile-a", "a".repeat(64), "b".repeat(64), 17,
        ReadinessFixtures.now.minusSeconds(10), "c".repeat(64))
    fun payload() = JSONObject().put("format", 1).put("provider", "test.backend")
        .put("request_id", "request-a").put("request_hash", request().requestHash).put("profile_id", "profile-a")
        .put("profile_hash", "a".repeat(64)).put("artifact_hash", "b".repeat(64)).put("android_user_id", 17)
        .put("timestamp", ReadinessFixtures.now.minusSeconds(12).toString()).put("decoded_at", ReadinessFixtures.now.minusSeconds(8).toString())
        .put("app_recognition", "PLAY_RECOGNIZED").put("app_licensing", "LICENSED")
        .put("device_recognition", JSONArray(listOf("MEETS_DEVICE_INTEGRITY")))
    fun signed(j: JSONObject = payload()): String {
        val bytes = j.toString().toByteArray(Charsets.UTF_8)
        val signature = Signature.getInstance("SHA256withRSA").run { initSign(pair.private); update(bytes); sign() }
        return JSONObject().put("format", 1).put("algorithm", "RS256").put("provider", "test.backend").put("key_id", "test-key")
            .put("payload", Base64.getEncoder().encodeToString(bytes)).put("signature", Base64.getEncoder().encodeToString(signature)).toString()
    }
}

class IntegrityVerdictTest {
    private fun result(j: JSONObject = IntegrityFixtures.payload()) = IntegrityVerdicts.evaluate(
        ReadinessFixtures.context(), IntegrityFixtures.request(), IntegrityFixtures.signed(j), IntegrityFixtures.trust)
    @Test fun validAuthenticatedVerdictParsesAndBinds() {
        val result = result()
        assertEquals(IntegrityState.PASS, result.state)
        assertEquals("test.backend", result.verdict!!.provider)
        assertTrue(Regex("[0-9a-f]{64}").matches(result.verdict.evidenceDigest))
    }
    @Test fun unsignedOrUnknownProviderIsNeverAccepted() {
        assertThrows(IntegrityVerdicts.Rejected::class.java) { IntegrityVerdicts.parse(IntegrityFixtures.payload().toString(), IntegrityFixtures.trust) }
        val r = IntegrityVerdicts.evaluate(ReadinessFixtures.context(), IntegrityFixtures.request(), IntegrityFixtures.signed(), IntegrityTrust { _, _ -> null })
        assertEquals("IV_AUTHENTICATION", r.reason)
    }
    @Test fun payloadTamperingFailsSignatureVerification() {
        val outer = JSONObject(IntegrityFixtures.signed()).put("payload", Base64.getEncoder().encodeToString(IntegrityFixtures.payload().put("android_user_id", 0).toString().toByteArray()))
        assertEquals("IV_AUTHENTICATION", IntegrityVerdicts.evaluate(ReadinessFixtures.context(), IntegrityFixtures.request(), outer.toString(), IntegrityFixtures.trust).reason)
    }
    @Test fun malformedIncompleteUnknownAndSecretFieldsAreRejected() {
        val good = IntegrityFixtures.payload()
        for (key in good.keys().asSequence().toList()) {
            val changed = JSONObject(good.toString()).also { it.remove(key) }
            assertEquals(key, "IV_MALFORMED", result(changed).reason)
        }
        for ((key, value) in listOf("token" to "must-not-store", "format" to 2, "app_licensing" to "FUTURE_VALUE",
            "android_user_id" to "17", "timestamp" to "2026-02-30T00:00:00Z"))
            assertEquals(key, "IV_MALFORMED", result(IntegrityFixtures.payload().put(key, value)).reason)
    }
    @Test fun duplicateJsonKeysAndDuplicateDeviceLabelsAreRejected() {
        assertThrows(IntegrityVerdicts.Rejected::class.java) { IntegrityVerdicts.parse("{\"format\":1,\"format\":1}", IntegrityFixtures.trust) }
        assertEquals("IV_MALFORMED", result(IntegrityFixtures.payload().put("device_recognition", JSONArray(listOf("MEETS_DEVICE_INTEGRITY", "MEETS_DEVICE_INTEGRITY")))).reason)
    }
    @Test fun staleVerdictIsRejected() {
        val old = ReadinessFixtures.now.minus(Duration.ofDays(2)).toString()
        assertEquals("IV_STALE", result(IntegrityFixtures.payload().put("timestamp", old).put("decoded_at", old)).reason)
    }
    @Test fun futureVerdictIsRejected() {
        assertEquals("IV_FUTURE", result(IntegrityFixtures.payload().put("decoded_at", ReadinessFixtures.now.plusSeconds(1).toString())).reason)
    }
    @Test fun foreignProfileIdIsRejected() { assertEquals("IV_PROFILE_MISMATCH", result(IntegrityFixtures.payload().put("profile_id", "another")).reason) }
    @Test fun foreignProfileHashIsRejected() { assertEquals("IV_PROFILE_MISMATCH", result(IntegrityFixtures.payload().put("profile_hash", "d".repeat(64))).reason) }
    @Test fun foreignUserIsRejected() { assertEquals("IV_USER_MISMATCH", result(IntegrityFixtures.payload().put("android_user_id", 0)).reason) }
    @Test fun foreignArtifactIsRejected() { assertEquals("IV_ARTIFACT_MISMATCH", result(IntegrityFixtures.payload().put("artifact_hash", "d".repeat(64))).reason) }
    @Test fun foreignRequestIdIsRejected() { assertEquals("IV_REQUEST_MISMATCH", result(IntegrityFixtures.payload().put("request_id", "another")).reason) }
    @Test fun foreignRequestHashIsRejected() { assertEquals("IV_REQUEST_HASH_MISMATCH", result(IntegrityFixtures.payload().put("request_hash", "d".repeat(64))).reason) }
    @Test fun allDecodedStatesAreDistinct() {
        assertEquals(IntegrityState.DEGRADED, result(IntegrityFixtures.payload().put("device_recognition", JSONArray(listOf("MEETS_BASIC_INTEGRITY")))).state)
        assertEquals(IntegrityState.FAIL, result(IntegrityFixtures.payload().put("device_recognition", JSONArray())).state)
        assertEquals(IntegrityState.FAIL, result(IntegrityFixtures.payload().put("app_licensing", "UNLICENSED")).state)
        assertEquals(IntegrityState.FAIL, result(IntegrityFixtures.payload().put("app_recognition", "UNRECOGNIZED_VERSION")).state)
        assertEquals(IntegrityState.DECODED_AVAILABLE, result(IntegrityFixtures.payload().put("app_licensing", "UNEVALUATED")).state)
    }
    @Test fun noRequestAndTokenOnlyAreNotDecodedVerdicts() {
        assertEquals(IntegrityState.NO_REQUEST, IntegrityVerdicts.evaluate(ReadinessFixtures.context(), null, null, IntegrityFixtures.trust).state)
        assertEquals(IntegrityState.TOKEN_RECEIVED, IntegrityVerdicts.evaluate(ReadinessFixtures.context(), IntegrityFixtures.request(), null, IntegrityFixtures.trust).state)
        assertEquals(IntegrityState.NEEDS_REVIEW, IntegrityVerdicts.evaluate(ReadinessFixtures.context(), null, IntegrityFixtures.signed(), IntegrityFixtures.trust).state)
    }
    @Test fun unavailableReceiptCannotAcceptVerdict() {
        val req = IntegrityFixtures.request().copy(receivedAt = null, tokenDigest = null)
        assertEquals("IV_NO_RECEIPT", IntegrityVerdicts.evaluate(ReadinessFixtures.context(), req, IntegrityFixtures.signed(), IntegrityFixtures.trust).reason)
    }
    @Test fun staleReceiptAndChangedCurrentBindingRequireReview() {
        val context = ReadinessFixtures.context().copy(now = ReadinessFixtures.now.plus(Duration.ofDays(2)))
        assertEquals("IV_STALE", IntegrityVerdicts.evaluate(context, IntegrityFixtures.request(), null, IntegrityFixtures.trust).reason)
        assertEquals("IV_USER_MISMATCH", IntegrityVerdicts.evaluate(context.copy(androidUserId = 0), IntegrityFixtures.request(), null, IntegrityFixtures.trust).reason)
    }
    @Test fun passingVerdictNeverPromotesUnknownLocalEvidence() {
        val unknown = LocalPlayReadinessProvider().evaluate(ReadinessFixtures.context(), null)
        for (state in IntegrityState.entries) {
            assertEquals(state.name, PlayReadinessState.UNKNOWN, IntegrityVerdicts.readiness(unknown, IntegrityResult(state, "IV_PASS")).state)
        }
    }
    @Test fun providerDowngradesButDoesNotFabricateLocalEvidence() {
        fun eval(j: JSONObject) = IntegrityPlayReadinessProvider(IntegrityFixtures.request(), IntegrityFixtures.signed(j), IntegrityFixtures.trust)
            .evaluate(ReadinessFixtures.context(), ReadinessFixtures.report().toString())
        assertEquals(PlayReadinessState.READY, eval(IntegrityFixtures.payload()).state)
        assertEquals(PlayReadinessState.DEGRADED, eval(IntegrityFixtures.payload().put("device_recognition", JSONArray(listOf("MEETS_BASIC_INTEGRITY")))).state)
        assertEquals(PlayReadinessState.BLOCKED, eval(IntegrityFixtures.payload().put("device_recognition", JSONArray())).state)
        assertEquals(PlayReadinessState.UNKNOWN, eval(IntegrityFixtures.payload().put("app_licensing", "UNEVALUATED")).state)
        val rejected = eval(IntegrityFixtures.payload().put("profile_id", "foreign"))
        assertEquals(PlayReviewState.NEEDS_REVIEW, rejected.review)
        assertEquals(PlayReadinessState.UNKNOWN, rejected.state)
    }
    @Test fun futureReceiptAndOversizedInputAreRejected() {
        val req = IntegrityFixtures.request().copy(receivedAt = ReadinessFixtures.now.plusSeconds(1))
        assertEquals("IV_FUTURE", IntegrityVerdicts.evaluate(ReadinessFixtures.context(), req, null, IntegrityFixtures.trust).reason)
        assertThrows(IntegrityVerdicts.Rejected::class.java) { IntegrityVerdicts.parse(" ".repeat(IntegrityVerdicts.MAX_BYTES + 1), IntegrityFixtures.trust) }
    }
    @Test fun signatureEnvelopeCannotSelectAnAlgorithmOrItsOwnKey() {
        for ((key, value) in listOf("algorithm" to "none", "format" to 2, "public_key" to "not-a-trust-anchor")) {
            val envelope = JSONObject(IntegrityFixtures.signed()).put(key, value)
            assertEquals(key, "IV_MALFORMED", IntegrityVerdicts.evaluate(ReadinessFixtures.context(), IntegrityFixtures.request(), envelope.toString(), IntegrityFixtures.trust).reason)
        }
    }
    @Test fun requestAndDecodeTimesRespectReceiptAndConfigurableFreshness() {
        val future = ReadinessFixtures.now.plusSeconds(1).toString()
        assertEquals("IV_FUTURE", result(IntegrityFixtures.payload().put("timestamp", future).put("decoded_at", future)).reason)
        assertEquals("IV_RECEIPT_MISMATCH", result(IntegrityFixtures.payload().put("decoded_at", ReadinessFixtures.now.minusSeconds(11).toString())).reason)
        assertEquals("IV_STALE", IntegrityVerdicts.evaluate(ReadinessFixtures.context().copy(freshness = Duration.ofSeconds(5)),
            IntegrityFixtures.request(), IntegrityFixtures.signed(), IntegrityFixtures.trust).reason)
    }
}
