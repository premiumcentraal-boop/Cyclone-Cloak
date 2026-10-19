package dev.cyclone.cloak.play

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

internal object SurfaceFixtures {
    val hash = "a".repeat(64)
    fun record(id: String = "profile-a", user: Int? = 0, slot: String = "slot-a", active: Boolean = true): GoogleSurfaceRecord {
        val s = JSONObject(SurfaceFixtures::class.java.classLoader!!.getResource("play-surface.json")!!.readText()).getJSONObject("surface")
        s.getJSONObject("account").put("mode", "dedicated").put("slot_id", slot).put("provisioning_state", "ready")
            .put("allowed_packages", org.json.JSONArray(listOf("com.google.android.gms", "com.android.vending", "com.google.android.webview")))
        val services = s.getJSONObject("services")
        services.getJSONObject("play_services").put("state", "enabled")
        services.getJSONObject("play_store").put("state", "enabled")
        services.getJSONObject("web_provider").put("package_name", "com.google.android.webview").put("state", "enabled")
        services.put("backup_sync", "disabled")
        return GoogleSurfaceRecord(id, user, hash, s.toString(), listOf(
            PackageRule("com.google.android.gms", PackageRole.SURFACE, PackageRequirement.REQUIRED),
            PackageRule("com.android.vending", PackageRole.SURFACE, PackageRequirement.REQUIRED),
            PackageRule("com.google.android.webview", PackageRole.SURFACE, PackageRequirement.REQUIRED),
            PackageRule("org.example.control", PackageRole.CONTROL, PackageRequirement.OPTIONAL),
            PackageRule("org.example.forbidden", PackageRole.CONTROL, PackageRequirement.FORBIDDEN)), active)
    }
    fun observed(r: GoogleSurfaceRecord = record()) = GoogleSurfaceObservation(r.profileId, r.androidUserId ?: 0, r.profileHash,
        "local-test", Instant.parse("2026-10-19T12:00:00Z"), "dedicated", r.slotId, "disabled",
        r.packages.associate { it.packageName to if (it.requirement == PackageRequirement.REQUIRED) "enabled" else "missing" }, true,
        mapOf("com.google.android.gms" to "25.40.30"))
    fun change(r: GoogleSurfaceRecord, block: (JSONObject) -> Unit): GoogleSurfaceRecord =
        r.copy(surfaceJson = r.surface.also(block).toString())
}

class GoogleSurfaceIsolationTest {
    private val r get() = SurfaceFixtures.record()
    private fun evaluate(record: GoogleSurfaceRecord = r, o: GoogleSurfaceObservation? = SurfaceFixtures.observed(record),
        inventory: List<GoogleSurfaceRecord> = listOf(record), confirmed: Boolean = false) =
        GoogleSurfaceIsolation.evaluate(record, record.profileId, record.androidUserId, record.profileHash, inventory, o, confirmed)

    @Test fun completeMatchingLocalEvidenceIsReady() {
        val result = evaluate()
        assertEquals(IsolationState.READY, result.state)
        assertEquals(listOf("account_association", "android_user", "app_data_boundary", "backup_sync", "package_visibility", "profile_hash"), result.checks.map { it.id })
        assertTrue(result.checks.all { it.state == IsolationCheckState.PASS })
    }
    @Test fun expectationsAloneNeverPass() {
        val result = evaluate(o = null)
        assertEquals(IsolationState.UNKNOWN, result.state)
        assertTrue(result.checks.all { it.state == IsolationCheckState.UNKNOWN })
    }
    @Test fun eachMissingObservationPreventsReady() {
        val o = SurfaceFixtures.observed()
        for (missing in listOf(o.copy(accountMode = null), o.copy(backupSync = null), o.copy(packageStates = emptyMap()), o.copy(privateDataBoundary = null)))
            assertEquals(IsolationState.UNKNOWN, evaluate(o = missing).state)
    }
    @Test fun missingAndroidUserNeverDefaultsToOwner() {
        assertEquals("GS_MISSING_USER", evaluate(r.copy(androidUserId = null), o = null).checks.single().reason)
    }
    @Test fun changedProfileHashConflicts() {
        val result = GoogleSurfaceIsolation.evaluate(r, r.profileId, 0, "b".repeat(64), listOf(r))
        assertEquals(IsolationState.CONFLICT, result.state)
        assertEquals("GS_PROFILE_CHANGED", result.checks.single().reason)
    }
    @Test fun foreignProfileAndAndroidUserEvidenceConflicts() {
        val o = SurfaceFixtures.observed()
        for (other in listOf(o.copy(profileId = "foreign"), o.copy(androidUserId = 17), o.copy(profileHash = "b".repeat(64))))
            assertEquals("GS_FOREIGN_EVIDENCE", evaluate(o = other).checks.single().reason)
        assertEquals(IsolationState.CONFLICT, GoogleSurfaceIsolation.evaluate(r, r.profileId, 17, r.profileHash, listOf(r)).state)
    }
    @Test fun slotReuseRequiresExplicitReviewEvenIfContractSharedFlagIsSet() {
        val a = SurfaceFixtures.change(r) { it.getJSONObject("account").put("shared", true) }
        val peers = listOf(a, SurfaceFixtures.record("profile-b", 17))
        assertEquals(IsolationState.CONFLICT, evaluate(a, inventory = peers).state)
        assertEquals(IsolationState.SHARED_ACCOUNT_CONFIRMED, evaluate(a, inventory = peers, confirmed = true).state)
        assertEquals(IsolationState.UNKNOWN, evaluate(a, o = null, inventory = peers, confirmed = true).state)
    }
    @Test fun inactiveAssignmentsDoNotReserveSlotsOrClaimReady() {
        assertEquals(IsolationState.READY, evaluate(inventory = listOf(r, SurfaceFixtures.record("profile-b", 17, active = false))).state)
        val inactive = evaluate(r.copy(active = false))
        assertEquals(IsolationState.UNKNOWN, inactive.state)
        assertEquals("GS_INACTIVE", inactive.checks.single { it.id == "configuration" }.reason)
    }
    @Test fun accountAssociationMismatchAndSetupStates() {
        assertEquals(IsolationState.CONFLICT, evaluate(o = SurfaceFixtures.observed().copy(accountSlotId = "another-slot")).state)
        for (p in listOf("unprovisioned", "needs_review", "blocked")) {
            val needs = SurfaceFixtures.change(r) { it.getJSONObject("account").put("provisioning_state", p) }
            assertEquals(IsolationState.NEEDS_ACCOUNT, evaluate(needs).state)
        }
    }
    @Test fun noneModeIsExplicitAbsenceNotMissingEvidence() {
        val noAccount = SurfaceFixtures.change(r) { it.getJSONObject("account").put("mode", "none").put("slot_id", JSONObject.NULL).put("shared", false) }
        assertEquals(IsolationState.READY, evaluate(noAccount, SurfaceFixtures.observed(noAccount).copy(accountMode = "none", accountSlotId = null)).state)
    }
    @Test fun packageScopeMismatchNeedsReview() {
        val changed = r.copy(packages = r.packages.filterNot { it.packageName == "com.android.vending" })
        assertEquals(IsolationState.NEEDS_PACKAGE_REVIEW, evaluate(changed).state)
        val control = r.copy(packages = r.packages.map { if (it.packageName == "com.android.vending") it.copy(role = PackageRole.CONTROL) else it })
        assertEquals(IsolationState.NEEDS_PACKAGE_REVIEW, evaluate(control).state)
    }
    @Test fun requiredForbiddenOptionalAndServiceExpectations() {
        val o = SurfaceFixtures.observed()
        for ((p, value) in listOf("com.google.android.gms" to "missing", "org.example.forbidden" to "disabled", "com.android.vending" to "disabled"))
            assertEquals(IsolationState.NEEDS_PACKAGE_REVIEW, evaluate(o = o.copy(packageStates = o.packageStates + (p to value))).state)
        assertEquals(IsolationState.READY, evaluate(o = o.copy(packageStates = o.packageStates + ("org.example.control" to "enabled"))).state)
    }
    @Test fun unknownServiceExpectationCannotBecomeReady() {
        val unknown = SurfaceFixtures.change(r) { it.getJSONObject("services").getJSONObject("web_provider").put("state", "unknown") }
        assertEquals(IsolationState.UNKNOWN, evaluate(unknown).state)
    }
    @Test fun ordinaryAppAccountScopeIsDistinctFromGoogleSurface() {
        val scoped = SurfaceFixtures.change(r) { it.getJSONObject("account").put("allowed_packages", org.json.JSONArray(listOf("org.example.control"))) }
        assertEquals(IsolationState.READY, evaluate(scoped).state)
    }
    @Test fun declaredPackageVersionsNeedEvidenceAndMismatchNeedsReview() {
        val o = SurfaceFixtures.observed()
        assertEquals(IsolationState.UNKNOWN, evaluate(o = o.copy(packageVersions = emptyMap())).state)
        assertEquals(IsolationState.NEEDS_PACKAGE_REVIEW, evaluate(o = o.copy(packageVersions = mapOf("com.google.android.gms" to "older"))).state)
    }
    @Test fun inconsistentPackageConfigurationNeedsReviewWithoutObservation() {
        val bad = r.copy(packages = r.packages.map { if (it.packageName == "com.android.vending") it.copy(requirement = PackageRequirement.FORBIDDEN) else it })
        assertEquals(IsolationState.NEEDS_PACKAGE_REVIEW, evaluate(bad, o = null).state)
    }
    @Test fun malformedAndIncompleteInventoryCannotClaimIsolation() {
        assertEquals("GS_MALFORMED_RECORD", evaluate(inventory = listOf(r, r)).checks.single().reason)
        assertEquals(IsolationState.UNKNOWN, evaluate(inventory = emptyList()).state)
        assertEquals(IsolationState.UNKNOWN, evaluate(inventory = listOf(r.copy(profileHash = "b".repeat(64)))).state)
    }
    @Test fun backupAndBoundaryFailuresAreConflicts() {
        val o = SurfaceFixtures.observed()
        for (bad in listOf(o.copy(backupSync = "enabled"), o.copy(privateDataBoundary = false)))
            assertEquals(IsolationState.CONFLICT, evaluate(o = bad).state)
    }
    @Test fun malformedObservationNeverPromotes() {
        val o = SurfaceFixtures.observed()
        for (bad in listOf(o.copy(source = ""), o.copy(accountMode = "alien"), o.copy(backupSync = "alien"),
            o.copy(packageStates = mapOf("com.google.android.gms" to "alien")), o.copy(accountSlotId = null)))
            assertEquals("GS_MALFORMED_EVIDENCE", evaluate(o = bad).checks.single().reason)
    }
    @Test fun codecRoundtripAndStrictRejection() {
        val text = GoogleSurfaceCodec.encode(r)
        assertTrue(JSONObject(text).similar(JSONObject(GoogleSurfaceCodec.encode(GoogleSurfaceCodec.parse(text)))))
        val bad = listOf(text.replace("\"format\":1", "\"format\":1,\"format\":1"), text + " trailing",
            JSONObject(text).put("token", "DO_NOT_ECHO").toString(), JSONObject(text).put("android_user_id", "17").toString(),
            JSONObject(text).put("profile_hash", "DO_NOT_ECHO").toString(), JSONObject(text).put("format", 2).toString(),
            " ".repeat(GoogleSurfaceCodec.MAX_BYTES + 1), JSONObject(text).put("active", 1).toString())
        bad.forEach { assertFalse(assertThrows(GoogleSurfaceCodec.InvalidRecord::class.java) { GoogleSurfaceCodec.parse(it) }.message!!.contains("DO_NOT_ECHO")) }
    }
    @Test fun duplicatePackageAndMalformedRecordsRejected() {
        for (bad in listOf(r.copy(packages = r.packages + r.packages.first()), r.copy(androidUserId = -1), r.copy(profileId = ""), r.copy(surfaceJson = "{}")))
            assertThrows(GoogleSurfaceCodec.InvalidRecord::class.java) { GoogleSurfaceCodec.encode(bad) }
    }
    @Test fun legacyProfilesStayAbsentAndUnchanged() {
        for (version in listOf("0.2", "0.3", "0.4")) {
            val old = JSONObject().put("schema_version", version).put("name", "legacy")
            val before = old.toString()
            assertNull(GoogleSurfaceRecord.fromProfile("old", 0, old, emptyList()))
            assertEquals(before, old.toString())
        }
    }
    @Test fun fromProfileBindsCanonicalBodyAndDoesNotMutate() {
        val p = JSONObject().put("play_surface", r.surface)
        val before = p.toString()
        val record = GoogleSurfaceRecord.fromProfile("profile-a", 17, p, r.packages)!!
        assertEquals(17, record.androidUserId)
        assertEquals(dev.cyclone.cloak.data.EngineMetadataStore.digest(p), record.profileHash)
        assertEquals(before, p.toString())
    }
    @Test fun everyReviewStateAndReasonHasReadableSafeFeedback() {
        assertEquals(6, IsolationState.entries.size)
        for (code in listOf("GS_MATCH", "GS_MISSING_USER", "GS_PROFILE_CHANGED", "GS_FOREIGN_EVIDENCE", "GS_NO_EVIDENCE", "GS_ACCOUNT_REQUIRED", "GS_ACCOUNT_CONFLICT", "GS_SHARED_REVIEW", "GS_PACKAGE_REVIEW", "GS_BACKUP_CONFLICT", "GS_DATA_BOUNDARY", "GS_INVENTORY_UNREADABLE", "GS_MALFORMED_EVIDENCE"))
            assertTrue(IsolationCheck("check", IsolationCheckState.UNKNOWN, code).message.length > 20)
    }
}
