package dev.cyclone.cloak.ui

import dev.cyclone.cloak.cyclone.CycloneGate
import dev.cyclone.cloak.root.PublicationReason
import dev.cyclone.cloak.root.RootDoctorCode
import dev.cyclone.cloak.root.RootDoctorResult
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class HealthDiagnosticsTest {
    private val version = "0.10.0-alpha.1"
    private val gate = CycloneGate(true, 3, setOf("profiles.read", "profiles.apps.read", "profiles.config", "profiles.startup"), emptySet())
    private val binding = BindingDoctorFacts("Work / com.example.chat", true, "ready", true, true, true, true, true, false)
    private fun facts() = DoctorFacts(RootDoctorResult(RootDoctorCode.READY, "v$version"), version, "Connected", false, gate, "ready", listOf(binding))
    private fun check(diagnostics: HealthDiagnostics, kind: DoctorCheckKind) = diagnostics.checks.first { it.kind == kind }

    @Test fun publicationContentionIsReadableAndDoesNotClaimSuccess() {
        val result = HealthDiagnostics.assess(facts().copy(root = RootDoctorResult(
            RootDoctorCode.PUBLISH_FAILED, "v$version", publicationReason = PublicationReason.LOCKED)))
        assertEquals(Readiness.DEGRADED, result.verdict)
        assertEquals(listOf(DoctorReason.PUBLICATION_LOCKED), check(result, DoctorCheckKind.ROOT).reasons)
        assertTrue(result.redactedReport().contains("PUBLICATION_LOCKED"))
    }
    @Test fun recoveredPublicationIsReadyAndAppearsInRedactedReport() {
        val result = HealthDiagnostics.assess(facts().copy(root = RootDoctorResult(
            RootDoctorCode.READY, "v$version", publicationReason = PublicationReason.RECOVERED)))
        assertEquals(Readiness.READY, result.verdict)
        assertTrue(check(result, DoctorCheckKind.ROOT).reasons.contains(DoctorReason.PUBLICATION_RECOVERED))
        assertTrue(result.redactedReport().contains("PUBLICATION_RECOVERED"))
    }

    @Test fun staleLockRecoveryIsReportedWithoutOwnerDetails() {
        val result = HealthDiagnostics.assess(facts().copy(root = RootDoctorResult(
            RootDoctorCode.READY, "v$version", diagnostic = "private-owner-123",
            publicationReason = PublicationReason.RECOVERED, staleLockDetected = true)))
        assertEquals(Readiness.READY, result.verdict)
        assertTrue(check(result, DoctorCheckKind.ROOT).reasons.contains(DoctorReason.PUBLICATION_STALE_LOCK))
        assertTrue(result.redactedReport().contains("PUBLICATION_STALE_LOCK"))
        assertFalse(result.redactedReport().contains("private-owner-123"))
    }

    @Test fun readyRequiresAllApplicableChecksToPass() {
        val result = HealthDiagnostics.assess(facts())
        assertEquals(Readiness.READY, result.verdict)
        assertTrue(result.checks.all { it.status == DoctorStatus.PASS })
        assertEquals(version, result.installedModuleVersion)
    }

    @Test fun queuedModuleNeverClaimsAnInstalledVersionMatch() {
        val result = HealthDiagnostics.assess(facts().copy(root = RootDoctorResult(RootDoctorCode.MODULE_REBOOT_REQUIRED, "v$version")))
        assertEquals(Readiness.DEGRADED, result.verdict)
        assertEquals(DoctorStatus.WARN, check(result, DoctorCheckKind.VERSION).status)
        assertNull(result.installedModuleVersion)
        assertTrue(JSONObject(result.redactedReport()).isNull("installed_module_version"))
    }

    @Test fun mismatchedOrUnconfirmedVersionsCannotBeReady() {
        val mismatch = HealthDiagnostics.assess(facts().copy(root = RootDoctorResult(RootDoctorCode.READY, "v0.9.0-alpha.1")))
        assertEquals(Readiness.FAILED, mismatch.verdict)
        assertEquals(DoctorStatus.FAIL, check(mismatch, DoctorCheckKind.VERSION).status)
        val unknown = HealthDiagnostics.assess(facts().copy(root = RootDoctorResult(RootDoctorCode.READY)))
        assertEquals(Readiness.DEGRADED, unknown.verdict)
        assertEquals(DoctorReason.VERSION_UNKNOWN, check(unknown, DoctorCheckKind.VERSION).reasons.single())
    }

    @Test fun allRootCodesHaveAReadableReasonAndOnlyReadyPasses() {
        RootDoctorCode.entries.forEach { code ->
            val result = HealthDiagnostics.assess(facts().copy(root = RootDoctorResult(code, "v$version")))
            val root = check(result, DoctorCheckKind.ROOT)
            assertTrue(code.name, root.reasons.single().message.isNotBlank())
            assertEquals(code.name, code == RootDoctorCode.READY, root.status == DoctorStatus.PASS)
            if (code != RootDoctorCode.READY) assertNotEquals(Readiness.READY, result.verdict)
        }
    }

    @Test fun disabledBindingsDoNotDegradeOtherwiseReadyEnabledBindings() {
        val disabled = binding.copy(enabled = false, health = "publish failed", identityPresent = false, profilePresent = false)
        val result = HealthDiagnostics.assess(facts().copy(bindings = listOf(binding, disabled)))
        assertEquals(Readiness.READY, result.verdict)
        val row = result.checks.last()
        assertEquals(DoctorStatus.WARN, row.status)
        assertFalse(row.affectsReadiness)
        assertEquals(listOf(DoctorReason.BINDING_DISABLED), row.reasons)
        assertEquals(Readiness.DEGRADED, HealthDiagnostics.assess(facts().copy(bindings = listOf(disabled))).verdict)
    }

    @Test fun missingProfileFailsButUnknownProfileOnlyWarns() {
        assertEquals(Readiness.FAILED, HealthDiagnostics.assess(facts().copy(bindings = listOf(binding.copy(profilePresent = false)))).verdict)
        val unknown = HealthDiagnostics.assess(facts().copy(bindings = listOf(binding.copy(profilePresent = null))))
        assertEquals(Readiness.DEGRADED, unknown.verdict)
        assertEquals(listOf(DoctorReason.PROFILE_UNKNOWN), unknown.checks.last().reasons)
    }

    @Test fun bindingFailuresAndPendingConditionsHaveSpecificReasons() {
        val cases = listOf(
            binding.copy(health = "module missing") to DoctorReason.MODULE_MISSING,
            binding.copy(health = "module update needs reboot") to DoctorReason.REBOOT_PENDING,
            binding.copy(appListed = false) to DoctorReason.APP_NOT_LISTED,
            binding.copy(identityPresent = false) to DoctorReason.IDENTITY_MISSING,
            binding.copy(userMatches = false) to DoctorReason.USER_CHANGED,
            binding.copy(profileReady = false) to DoctorReason.PROFILE_NOT_READY,
            binding.copy(appListed = null) to DoctorReason.APP_LIST_UNKNOWN,
            binding.copy(issuePresent = true) to DoctorReason.BINDING_ISSUE,
        )
        cases.forEach { (row, expected) ->
            val result = HealthDiagnostics.assess(facts().copy(bindings = listOf(row)))
            assertTrue(expected.name, expected in result.checks.last().reasons)
            assertNotEquals(Readiness.READY, result.verdict)
        }
    }

    @Test fun connectorPermissionsProviderAndRefreshCanPreventReadiness() {
        val cases = listOf(facts().copy(syncing = true), facts().copy(connectorHeadline = null, gate = null),
            facts().copy(connectorHeadline = "Cyclone not found"), facts().copy(gate = gate.copy(approved = false)),
            facts().copy(gate = gate.copy(granted = setOf("profiles.read"))), facts().copy(providerState = "failed"))
        cases.forEach { assertNotEquals(Readiness.READY, HealthDiagnostics.assess(it).verdict) }
    }

    @Test fun oneFailedEnabledBindingWinsOverReadyAndDegradedBindings() {
        val result = HealthDiagnostics.assess(facts().copy(bindings = listOf(binding,
            binding.copy(health = "pending"), binding.copy(health = "publish failed"))))
        assertEquals(Readiness.FAILED, result.verdict)
        assertEquals(3, result.enabledBindings)
    }

    @Test fun copiedReportUsesAnAllowlistAndCannotLeakPrivateInputStrings() {
        val privateValues = listOf("secret-seed-123", "123456789012345", "aabbccddeeff0011", "aa:bb:cc:dd:ee:ff",
            "private-fingerprint", "private-advertising-id", "private-profile-label", "private-package", "private-serial")
        val secret = privateValues.joinToString(" / ")
        val input = facts().copy(root = RootDoctorResult(RootDoctorCode.PUBLISH_FAILED, secret, secret),
            appVersion = secret, connectorHeadline = secret, providerState = secret,
            bindings = listOf(binding.copy(displayContext = secret, health = secret)))
        val result = HealthDiagnostics.assess(input)
        assertEquals(secret, result.checks.last().displayContext)
        val report = result.redactedReport()
        privateValues.forEach { assertFalse(it, report.contains(it)) }
        val json = JSONObject(report)
        assertEquals(setOf("format", "scope", "verdict", "app_version", "installed_module_version", "enabled_bindings", "disabled_bindings", "checks"), json.keys().asSequence().toSet())
        assertTrue(json.isNull("app_version"))
        assertTrue(json.isNull("installed_module_version"))
        for (i in 0 until json.getJSONArray("checks").length()) {
            assertEquals(setOf("check", "status", "binding_number", "affects_readiness", "reasons"), json.getJSONArray("checks").getJSONObject(i).keys().asSequence().toSet())
        }
    }
    @Test fun v3SplitsPublicationSynchronizationAndBindingAspects() {
        val result = HealthDiagnostics.assess(facts())
        assertTrue(result.checks.map { it.kind }.containsAll(listOf(DoctorCheckKind.PUBLICATION,
            DoctorCheckKind.SYNC, DoctorCheckKind.LOCAL_RECORDS, DoctorCheckKind.ENABLED,
            DoctorCheckKind.PROFILE, DoctorCheckKind.IDENTITY, DoctorCheckKind.RECORDS)))
        assertEquals("cyclone-cloak.readiness-doctor/3", JSONObject(result.redactedReport()).getString("format"))
        assertTrue(result.summary.endsWith("0 warning · 0 fail"))
    }

    @Test fun staleConflictingAndUnreadableRecordsHaveDistinctReasons() {
        val examples = listOf(
            binding.copy(duplicateRecord = true) to DoctorReason.DUPLICATE_BINDING,
            binding.copy(duplicateIdentifiers = true) to DoctorReason.LOCAL_CONFLICT,
            binding.copy(identityIdMatches = false) to DoctorReason.IDENTITY_ID_MISMATCH,
            binding.copy(identityReadable = false) to DoctorReason.IDENTITY_UNREADABLE,
            binding.copy(identityCoherent = false) to DoctorReason.IDENTITY_INVALID,
            binding.copy(identityReadable = null) to DoctorReason.IDENTITY_UNKNOWN,
            binding.copy(remoteIdentity = true, identityPresent = false, identityReadable = null) to DoctorReason.IDENTITY_REMOTE)
        examples.forEach { (row, reason) ->
            val result = HealthDiagnostics.assess(facts().copy(bindings = listOf(row)))
            assertTrue(reason.name, reason in result.checks.last().reasons)
            assertNotEquals(reason.name, Readiness.READY, result.verdict)
            assertTrue(result.checks.filter { it.status != DoctorStatus.PASS }.all { it.reasons.isNotEmpty() && it.reasons.all { r -> r.message.isNotBlank() } })
        }
    }

    @Test fun mixedSummaryFailsOnlyForApplicableFailures() {
        val result = HealthDiagnostics.assess(facts().copy(bindings = listOf(binding,
            binding.copy(duplicateRecord = true), binding.copy(identityPresent = false),
            binding.copy(enabled = false, identityPresent = false))))
        assertEquals(Readiness.FAILED, result.verdict)
        assertEquals(3, result.enabledBindings)
        assertEquals(1, result.disabledBindings)
        val rows = result.checks.filter { it.affectsReadiness }
        assertTrue(rows.any { it.status == DoctorStatus.PASS })
        assertTrue(rows.any { it.status == DoctorStatus.WARN })
        assertTrue(rows.any { it.status == DoctorStatus.FAIL })
        assertEquals("${rows.count { it.status == DoctorStatus.PASS }} pass · ${rows.count { it.status == DoctorStatus.WARN }} warning · ${rows.count { it.status == DoctorStatus.FAIL }} fail", result.summary)
        val disabled = HealthDiagnostics.assess(facts().copy(bindings = listOf(binding,
            binding.copy(enabled = false, identityPresent = false, duplicateRecord = true))))
        assertEquals(Readiness.READY, disabled.verdict)
        assertTrue(disabled.checks.any { !it.affectsReadiness && it.status == DoctorStatus.FAIL })
    }

    @Test fun publicationLockCannotMaskFatalRootFailureAndSyncIssuesWarn() {
        val result = HealthDiagnostics.assess(facts().copy(root = RootDoctorResult(
            RootDoctorCode.MODULE_MISSING, "v$version", publicationReason = PublicationReason.LOCKED)))
        assertEquals(Readiness.FAILED, result.verdict)
        val sync = HealthDiagnostics.assess(facts().copy(syncIssues = true))
        assertEquals(Readiness.DEGRADED, sync.verdict)
        assertEquals(listOf(DoctorReason.SYNC_ISSUES), check(sync, DoctorCheckKind.SYNC).reasons)
    }

    @Test fun incompleteLibraryAndUnusedCorruptRecordsWarnWithoutChangingBindings() {
        for (input in listOf(facts().copy(localInventoryKnown = false), facts().copy(unreadableIdentities = true),
            facts().copy(conflictingIdentities = true), facts().copy(mismatchedIdentities = true))) {
            val original = input.bindings.toList()
            assertEquals(Readiness.DEGRADED, HealthDiagnostics.assess(input).verdict)
            assertEquals(original, input.bindings)
        }
    }

    @Test fun warningSerializationAndRedactionIncludeNoRawUserContentOrCredentials() {
        val secrets = listOf("seed-marker", "identifier-marker", "-----BEGIN PRIVATE KEY-----",
            "Bearer token-marker", "user-content-marker")
        val raw = secrets.joinToString("\n")
        val result = HealthDiagnostics.assess(facts().copy(appVersion = raw, providerState = raw,
            root = RootDoctorResult(RootDoctorCode.PUBLISH_FAILED, raw, raw),
            bindings = listOf(binding.copy(displayContext = raw, health = raw, duplicateRecord = true))))
        val text = result.redactedReport()
        secrets.forEach { assertFalse(it, text.contains(it)) }
        assertTrue(text.contains("\"warning\""))
        assertFalse(text.contains("displayContext"))
        assertFalse(text.contains("diagnostic\""))
    }

}
