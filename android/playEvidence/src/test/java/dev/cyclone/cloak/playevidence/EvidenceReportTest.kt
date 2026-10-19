package dev.cyclone.cloak.playevidence

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.time.Instant

class EvidenceReportTest {
    private val binding = EvidenceBinding("request-1", "profile-1", "a".repeat(64), "b".repeat(64), 17)
    private val now = Instant.parse("2026-10-19T12:00:00Z")
    private class Reader : SurfaceReader {
        var missing = false
        var denied = false
        var disabled = false
        var visible = false
        var code = 0
        var user = 17
        override fun packageInfo(name: String): JSONObject? {
            if (denied) throw SecurityException("PRIVATE_ERROR_MUST_NOT_ESCAPE")
            if (missing) return null
            return JSONObject().put("package_name", name).put("version_name", "1.2.3").put("version_code", 123)
                .put("enabled", !disabled).put("installer", "com.android.vending").put("installer_reason", "PACKAGE_OBSERVED")
        }
        override fun playServicesCode(): Int { if (denied) throw SecurityException(); return code }
        override fun webProvider() = if (missing) null else packageInfo("com.google.android.webview")
        override fun settingsResolvable() = !missing
        override fun visibleGoogleAccount(): Boolean { if (denied) throw SecurityException(); return visible }
        override fun androidUser() = user
    }
    private fun capture(reader: Reader = Reader(), hash: String? = binding.profileHash) =
        SurfaceCollector.collect(binding, hash, reader, now = now)
    private fun row(j: JSONObject, id: String): JSONObject = j.getJSONArray("rows").let { a ->
        (0 until a.length()).map { a.getJSONObject(it) }.single { it.getString("id") == id }
    }
    private fun reject(block: () -> Unit) { assertThrows(IllegalArgumentException::class.java, block) }

    @Test fun completeReportRoundTrip() {
        val report = capture()
        assertEquals(16, report.getJSONArray("rows").length())
        assertTrue(report.similar(EvidenceReport.parse(report.toString())))
        assertEquals("PASS", row(report, "play_services.availability").getString("status"))
        assertEquals("OBSERVED", row(report, "play_store.availability").getString("status"))
    }
    @Test fun packagesAreObservationsNotServiceVerdicts() {
        val report = capture(Reader().apply { code = 2 })
        assertEquals("OBSERVED", row(report, "package.com.google.android.gms").getString("status"))
        assertEquals("FAIL", row(report, "play_services.availability").getString("status"))
        assertEquals("unknown", report.getJSONObject("readiness_report").getString("play_protect"))
    }
    @Test fun permissionDenialIsExplicitAndRedacted() {
        val report = capture(Reader().apply { denied = true })
        assertEquals("PERMISSION_DENIED", row(report, "account.association").getString("reason"))
        assertEquals("BLOCKED", row(report, "package.com.android.vending").getString("status"))
        assertFalse(report.toString().contains("PRIVATE_ERROR"))
    }
    @Test fun missingPackagesDoNotProveSystemWideAbsence() {
        val report = capture(Reader().apply { missing = true })
        assertEquals("MISSING", row(report, "package.com.google.android.gms").getString("status"))
        assertEquals("unknown", report.getJSONObject("readiness_report").getJSONObject("play_services").getString("state"))
    }
    @Test fun disabledPackagesStayBlocked() {
        val report = capture(Reader().apply { disabled = true })
        assertEquals("BLOCKED", row(report, "play_store.availability").getString("status"))
        assertEquals("PACKAGE_LIMITATION", report.getJSONObject("readiness_report").getJSONArray("warnings").getString(0))
    }
    @Test fun accountVisibilityDoesNotProveNoAccount() {
        val report = capture()
        assertEquals("unknown", report.getJSONObject("readiness_report").getString("google_account"))
        val visible = capture(Reader().apply { visible = true })
        assertEquals("associated", visible.getJSONObject("readiness_report").getString("google_account"))
        assertFalse(visible.toString().contains("account_name"))
    }
    @Test fun profileMismatchIsFailure() {
        assertEquals("FAIL", row(capture(hash = "c".repeat(64)), "profile.binding").getString("status"))
    }
    @Test fun missingProfileIsMissing() {
        assertEquals("MISSING", row(capture(hash = null), "profile.binding").getString("status"))
    }
    @Test fun actualUserIsReportedOnMismatch() {
        val report = capture(Reader().apply { user = 0 })
        assertEquals(0, report.getInt("android_user_id"))
        assertEquals("USER_MISMATCH", row(report, "process.android_user").getString("reason"))
    }
    @Test fun noAutomaticIntegrityOrSettingsAccessClaim() {
        val report = capture()
        assertEquals("USER_NOT_REQUESTED", row(report, "integrity.api").getString("reason"))
        assertEquals("NOT_RUN", row(report, "integrity.verdict").getString("status"))
        assertEquals("unknown", report.getJSONObject("readiness_report").getString("google_settings_access"))
    }
    @Test fun actualTokenOnlyRetainsDigestAndReceiptMetadata() {
        val token = IntegrityEvidence.receipt("SECRET_ENCRYPTED_TOKEN", "d".repeat(64), now)
        val report = SurfaceCollector.collect(binding, binding.profileHash, Reader(), token, now)
        assertFalse(report.toString().contains("SECRET_ENCRYPTED_TOKEN"))
        assertEquals(EvidenceReport.digest("SECRET_ENCRYPTED_TOKEN".toByteArray()), token.metadata.getString("response_digest"))
        assertEquals("NOT_RUN", row(report, "integrity.verdict").getString("status"))
    }
    @Test fun integrityFailureRemainsExplicitLimitation() {
        val api = EvidenceRow("integrity.api", EvidenceStatus.BLOCKED, "INTEGRITY_API_ERROR", JSONObject().put("api_code", -2))
        val report = SurfaceCollector.collect(binding, binding.profileHash, Reader(), api, now)
        assertEquals("BLOCKED", row(report, "integrity.api").getString("status"))
        assertEquals("unknown", report.getJSONObject("readiness_report").getString("integrity_api"))
        assertEquals("NOT_RUN", row(report, "integrity.verdict").getString("status"))
    }
    @Test fun futureReceiptAndEmptyTokenRejected() {
        reject { IntegrityEvidence.receipt("", binding.profileHash, now) }
        val api = IntegrityEvidence.receipt("opaque", binding.profileHash, now.plusSeconds(1))
        reject { SurfaceCollector.collect(binding, binding.profileHash, Reader(), api, now) }
    }
    @Test fun rejectsUnknownAndDuplicateFields() {
        reject { EvidenceReport.parse(capture().put("token", "secret").toString()) }
        reject { EvidenceReport.parse(capture().toString().replaceFirst("\"format\":1", "\"format\":1,\"format\":1")) }
    }
    @Test fun rejectsDuplicateAndMissingRows() {
        val report = capture()
        report.getJSONArray("rows").put(1, report.getJSONArray("rows").get(0))
        reject { EvidenceReport.parse(report.toString()) }
        val missing = capture(); missing.getJSONArray("rows").remove(0)
        reject { EvidenceReport.parse(missing.toString()) }
    }
    @Test fun rejectsPromotedUnmeasuredStates() {
        for (id in listOf("play_protect.state", "google_settings.access", "integrity.verdict", "account.association")) {
            val report = capture(); row(report, id).put("status", "PASS")
            reject { EvidenceReport.parse(report.toString()) }
        }
    }
    @Test fun rejectsProjectionAndWarningTampering() {
        val report = capture(); report.getJSONObject("readiness_report").put("play_protect", "enabled")
        reject { EvidenceReport.parse(report.toString()) }
        val other = capture(); other.getJSONArray("warnings").put("API_ERROR")
        reject { EvidenceReport.parse(other.toString()) }
    }
    @Test fun rejectsUnrelatedSensitiveMetadata() {
        val report = capture(); row(report, "account.association").getJSONObject("metadata").put("account_name", "private")
        reject { EvidenceReport.parse(report.toString()) }
        val scoped = capture(); row(scoped, "account.association").getJSONObject("metadata").put("version_name", "private")
        reject { EvidenceReport.parse(scoped.toString()) }
    }
    @Test fun rejectsMalformedTypesAndBindings() {
        reject { EvidenceBinding("../escape", "profile", binding.profileHash, binding.artifactHash, 0) }
        reject { EvidenceBinding("request", "profile", "short", binding.artifactHash, 0) }
        reject { EvidenceReport.parse(capture().put("android_user_id", "17").toString()) }
        reject { EvidenceReport.parse(capture().put("timestamp", "yesterday").toString()) }
    }
    @Test fun sharedSyntheticFixtureParses() {
        val report = EvidenceReport.parse(File(requireNotNull(System.getProperty("evidence.fixture"))).readText())
        assertEquals("synthetic-profile", report.getString("profile_id"))
    }
}
