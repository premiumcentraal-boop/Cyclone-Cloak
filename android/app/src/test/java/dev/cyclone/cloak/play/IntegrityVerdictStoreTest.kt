package dev.cyclone.cloak.play

import android.content.Context
import dev.cyclone.cloak.playevidence.EvidenceBinding
import dev.cyclone.cloak.playevidence.EvidenceReport
import dev.cyclone.cloak.playevidence.EvidenceRow
import dev.cyclone.cloak.playevidence.EvidenceStatus
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class IntegrityVerdictStoreTest {
    private lateinit var app: Context
    private lateinit var store: IntegrityVerdictStore
    @Before fun setup() {
        app = RuntimeEnvironment.getApplication()
        app.getSharedPreferences("play-integrity-bridge-v1", 0).edit().clear().commit()
        store = IntegrityVerdictStore(app, IntegrityFixtures.trust)
    }
    private fun envelope(token: Boolean = true, requestId: String = "request-a"): String {
        val context = ReadinessFixtures.context()
        val original = JSONObject(javaClass.classLoader!!.getResource("play_evidence_report.json")!!.readText())
        val a = original.getJSONArray("rows")
        val req = IntegrityFixtures.request().copy(requestId = requestId)
        val rows = (0 until a.length()).map { a.getJSONObject(it) }.map { row ->
            val id = row.getString("id")
            when (id) {
                "profile.binding" -> EvidenceRow(id, EvidenceStatus.PASS, "PROFILE_HASH_MATCHED", JSONObject().put("actual_profile_hash", context.profileHash))
                "process.android_user" -> EvidenceRow(id, EvidenceStatus.PASS, "USER_MATCHED", JSONObject().put("android_user_id", 17))
                "integrity.api" -> if (token) EvidenceRow(id, EvidenceStatus.OBSERVED, "TOKEN_RECEIVED", JSONObject()
                    .put("response_digest", req.tokenDigest).put("received_at", req.receivedAt.toString()).put("request_hash", req.requestHash)
                    .put("provider_id", "public.play.integrity")) else EvidenceRow(id, EvidenceStatus.NOT_RUN, "USER_NOT_REQUESTED")
                else -> EvidenceRow(id, EvidenceStatus.valueOf(row.getString("status")), row.getString("reason"), row.getJSONObject("metadata"), row.getLong("duration_ms"))
            }
        }
        return EvidenceReport.build(EvidenceBinding(requestId, context.profileId, context.profileHash, context.artifactHash, 17), rows, context.now).toString()
    }
    @Test fun saveReadAndRejectedImportPreservesPriorVerdict() {
        val c = ReadinessFixtures.context()
        store.recordRequest(c, envelope())
        assertEquals(IntegrityState.TOKEN_RECEIVED, store.read(c).state)
        assertEquals(IntegrityState.PASS, store.save(c, IntegrityFixtures.signed()).state)
        val before = app.getSharedPreferences("play-integrity-bridge-v1", 0).getString(c.profileId, null)
        assertEquals(store.read(c).verdict!!.evidenceDigest, JSONObject(before!!).getString("evidence_digest"))
        assertThrows(IntegrityVerdicts.Rejected::class.java) { store.save(c, IntegrityFixtures.signed(IntegrityFixtures.payload().put("android_user_id", 0))) }
        assertEquals(before, app.getSharedPreferences("play-integrity-bridge-v1", 0).getString(c.profileId, null))
        assertEquals(IntegrityState.PASS, store.read(c).state)
    }
    @Test fun noRequestAndNewRequestNeverReuseOldVerdict() {
        val c = ReadinessFixtures.context()
        assertEquals(IntegrityState.NO_REQUEST, store.read(c).state)
        store.recordRequest(c, envelope())
        store.save(c, IntegrityFixtures.signed())
        store.recordRequest(c, envelope(requestId = "new-request"))
        assertEquals(IntegrityState.TOKEN_RECEIVED, store.read(c).state)
        store.recordRequest(c, envelope(token = false))
        assertEquals(IntegrityState.NO_REQUEST, store.read(c).state)
    }
    @Test fun corruptRecordAndArtifactChangeNeedReview() {
        val c = ReadinessFixtures.context()
        app.getSharedPreferences("play-integrity-bridge-v1", 0).edit().putString(c.profileId, "{").commit()
        assertEquals("IV_STORAGE", store.read(c).reason)
        store.recordRequest(c, envelope())
        assertEquals(IntegrityState.TOKEN_RECEIVED, store.read(c).state)
        store.save(c, IntegrityFixtures.signed())
        assertEquals("IV_ARTIFACT_MISMATCH", store.read(c.copy(artifactHash = "d".repeat(64))).reason)
        assertEquals("IV_STALE", store.read(c.copy(now = c.now.plusSeconds(90000))).reason)
    }
    @Test fun noConfiguredTrustRejectsSignedImport() {
        val c = ReadinessFixtures.context()
        store.recordRequest(c, envelope())
        val rejecting = IntegrityVerdictStore(app, IntegrityTrust { _, _ -> null })
        assertEquals("IV_AUTHENTICATION", assertThrows(IntegrityVerdicts.Rejected::class.java) { rejecting.save(c, IntegrityFixtures.signed()) }.code)
    }
    @Test fun localReportReceiptCannotBeSilentlyReplaced() {
        val c = ReadinessFixtures.context()
        store.recordRequest(c, envelope())
        store.save(c, IntegrityFixtures.signed())
        val projection = JSONObject(envelope()).getJSONObject("readiness_report")
        projection.getJSONObject("integrity_response").put("response_digest", "d".repeat(64))
        assertEquals("IV_RECEIPT_MISMATCH", store.read(c, projection.toString()).reason)
    }
    @Test fun independentlyConfiguredPublicKeyWorksAndRevocationIsEffective() {
        val config = java.io.File(app.filesDir, "play-integrity-trust-v1.json")
        val trust = LocalIntegrityTrust(app.filesDir)
        assertNull(trust.key("test.backend", "test-key"))
        config.writeText(JSONObject().put("format", 1).put("keys", org.json.JSONArray().put(JSONObject()
            .put("provider", "test.backend").put("key_id", "test-key")
            .put("public_key", java.util.Base64.getEncoder().encodeToString(IntegrityFixtures.pair.public.encoded)))).toString())
        try {
            val c = ReadinessFixtures.context()
            store.recordRequest(c, envelope())
            val pinned = IntegrityVerdictStore(app, trust)
            pinned.save(c, IntegrityFixtures.signed())
            assertEquals(IntegrityState.PASS, pinned.read(c).state)
            config.writeText("{}")
            assertEquals("IV_AUTHENTICATION", pinned.read(c).reason)
        } finally { config.delete() }
    }
    @Test fun modifiedStoredDigestIsNotHiddenAsValidEvidence() {
        val c = ReadinessFixtures.context()
        store.recordRequest(c, envelope()); store.save(c, IntegrityFixtures.signed())
        val prefs = app.getSharedPreferences("play-integrity-bridge-v1", 0)
        val corrupt = JSONObject(prefs.getString(c.profileId, null)!!).put("evidence_digest", "d".repeat(64))
        prefs.edit().putString(c.profileId, corrupt.toString()).commit()
        assertEquals("IV_STORAGE", store.read(c).reason)
    }
    @Test fun changedReceiptHashRejectedBeforeWriting() {
        val c = ReadinessFixtures.context()
        val j = JSONObject(envelope())
        val a = j.getJSONArray("rows")
        for (i in 0 until a.length()) if (a.getJSONObject(i).getString("id") == "integrity.api")
            a.getJSONObject(i).getJSONObject("metadata").put("request_hash", "d".repeat(64))
        assertThrows(IntegrityVerdicts.Rejected::class.java) { store.recordRequest(c, j.toString()) }
        assertEquals(IntegrityState.NO_REQUEST, store.read(c).state)
    }
    @Test fun sidecarNeverStoresCiphertextAndDoesNotTouchBindingStore() {
        val bindingFile = java.io.File(app.noBackupFilesDir, "cloak-bindings-v1.json")
        val before = bindingFile.takeIf { it.exists() }?.readBytes()
        val c = ReadinessFixtures.context()
        store.recordRequest(c, envelope()); store.save(c, IntegrityFixtures.signed())
        val text = app.getSharedPreferences("play-integrity-bridge-v1", 0).getString(c.profileId, null)!!
        assertFalse(text.contains("integrity_token")); assertFalse(text.contains("raw_token"))
        assertArrayEquals(before, bindingFile.takeIf { it.exists() }?.readBytes())
        assertEquals("IV_REQUEST_MISMATCH", IntegrityVerdicts.evaluate(c, null, IntegrityFixtures.signed(), IntegrityFixtures.trust).reason)
    }
}
