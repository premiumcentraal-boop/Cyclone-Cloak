package dev.cyclone.cloak.playevidence

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class VerificationBundleTest {
    private val now = Instant.parse("2026-10-20T12:00:00Z")
    private val binding = EvidenceBinding("request-1", "profile-1", "a".repeat(64), "b".repeat(64), 17)
    private fun bundle() = requireNotNull(VerificationBundle.retain("synthetic-token", binding, now, true))
    private fun reject(text: String, at: Instant = now, expected: EvidenceBinding? = binding) {
        try { VerificationBundle.parse(text, at, expected); fail("Accepted invalid export") }
        catch (_: IllegalArgumentException) { }
        catch (_: org.json.JSONException) { }
    }
    @Test fun noRetentionWithoutConsent() { assertNull(VerificationBundle.retain("synthetic-token", binding, now, false)) }
    @Test fun explicitRetentionRoundTrip() {
        val b = bundle()
        assertArrayEquals(b.export(), VerificationBundle.parse(b.export().toString(Charsets.UTF_8), now, binding).export())
    }
    @Test fun tokenNeverInDisplayStringOrReceipt() {
        assertFalse(bundle().toString().contains("synthetic-token"))
        assertFalse(IntegrityEvidence.receipt("synthetic-token", VerificationBundle.requestHash(binding), now).json().toString().contains("synthetic-token"))
    }
    @Test fun exactAllowlist() {
        val root = JSONObject(bundle().export().toString(Charsets.UTF_8))
        assertEquals(setOf("format", "request_hash", "bundle"), root.keys().asSequence().toSet())
        assertEquals(8, root.getJSONObject("bundle").length())
        reject(root.put("logs", "forbidden").toString())
    }
    @Test fun tamperedTokenRejected() {
        val root = JSONObject(bundle().export().toString(Charsets.UTF_8))
        root.getJSONObject("bundle").put("token", "different-token")
        reject(root.toString())
    }
    @Test fun requestHashRejected() {
        reject(JSONObject(bundle().export().toString(Charsets.UTF_8)).put("request_hash", "c".repeat(64)).toString())
    }
    @Test fun incompleteRejected() {
        val root = JSONObject(bundle().export().toString(Charsets.UTF_8))
        root.getJSONObject("bundle").remove("received_at")
        reject(root.toString())
    }
    @Test fun duplicateMalformedOversizedRejected() {
        reject("{\"format\":1,\"format\":1}")
        reject("invalid")
        reject("x".repeat(65537))
    }
    @Test fun expiredAndFutureRejected() {
        val text = bundle().export().toString(Charsets.UTF_8)
        reject(text, now.plusSeconds(86401))
        reject(text, now.minusSeconds(1))
    }
    @Test fun foreignBindingRejected() {
        reject(bundle().export().toString(Charsets.UTF_8), expected = binding.copy(androidUserId = 0))
    }
}
