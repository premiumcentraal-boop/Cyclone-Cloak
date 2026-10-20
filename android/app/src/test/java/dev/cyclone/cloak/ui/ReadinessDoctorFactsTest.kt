package dev.cyclone.cloak.ui

import dev.cyclone.cloak.data.CloakBinding
import dev.cyclone.cloak.forge.CloakForge
import dev.cyclone.cloak.forge.TestPhones
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ReadinessDoctorFactsTest {
    @Test fun liveConnectionDoesNotClaimPendingReconciliationHasPassed() {
        val gate = dev.cyclone.cloak.cyclone.CycloneGate(true, 3,
            setOf("profiles.read", "profiles.apps.read", "profiles.config", "profiles.startup"), emptySet())
        val report = dev.cyclone.cloak.cyclone.SyncReport("Connected", "Checking bindings", gate = gate,
            reconciliationPending = true)
        val model = HealthDiagnostics.assess(ReadinessDoctorFacts.fromState(state().copy(report = report), null))
        assertEquals(DoctorStatus.PASS, model.checks.single { it.kind == DoctorCheckKind.CONNECTOR }.status)
        val sync = model.checks.single { it.kind == DoctorCheckKind.SYNC }
        assertEquals(DoctorStatus.WARN, sync.status)
        assertEquals(listOf(DoctorReason.SYNC_CHECKING), sync.reasons)
    }
    private val profile = CloakForge.forgeProfile("PRIVATE USER CONTENT", "ab".repeat(32), TestPhones["pixel_7"])
    private val id = profile.getString("id")
    private val binding = CloakBinding("Cyclone_0123456789abcdef", 11, "com.private.app", id, 1, true, 1, "ready")
    private fun state() = CloakState(identities = listOf(id to profile), identityInventoryKnown = true, bindings = listOf(binding))

    @Test fun adapterFindsDuplicateValuesTargetsAndMismatchedIdsWithoutMutation() {
        val before = profile.toString()
        val copy = JSONObject(profile.toString()).put("id", "other")
        val state = state().copy(identities = listOf(id to profile, "other" to copy), bindings = listOf(binding, binding))
        val facts = ReadinessDoctorFacts.fromState(state, "0.10.0-rc.1")
        assertTrue(facts.conflictingIdentities)
        assertTrue(facts.bindings.all { it.duplicateRecord && it.duplicateIdentifiers })
        assertEquals(before, profile.toString())
        val mismatch = ReadinessDoctorFacts.fromState(state().copy(identities = listOf(id to copy)), "0.10.0-rc.1")
        assertFalse(mismatch.bindings.single().identityIdMatches)
    }

    @Test fun missingCorruptAndRemoteFilesRemainDistinct() {
        val missing = ReadinessDoctorFacts.fromState(state().copy(identities = emptyList()), null).bindings.single()
        assertFalse(missing.identityPresent)
        assertEquals(false, missing.identityReadable)
        val corrupt = ReadinessDoctorFacts.fromState(state().copy(identities = emptyList(), unreadableIdentityIds = setOf(id)), null)
        assertTrue(corrupt.unreadableIdentities)
        assertTrue(corrupt.bindings.single().identityPresent)
        assertEquals(false, corrupt.bindings.single().identityReadable)
        val remote = ReadinessDoctorFacts.fromState(state().copy(bindings = listOf(binding.copy(origin = CloakBinding.ORIGIN_MAIN))), null).bindings.single()
        assertTrue(remote.remoteIdentity)
        assertNull(remote.identityReadable)
        assertNull(ReadinessDoctorFacts.fromState(CloakState(bindings = listOf(binding)), null).bindings.single().identityReadable)
    }

    @Test fun reportDoesNotExportIdentityJsonOrAppUserContent() {
        val copy = JSONObject(profile.toString()).put("private_key", "PRIVATE-KEY-MARKER").put("token", "TOKEN-MARKER")
        val result = HealthDiagnostics.assess(ReadinessDoctorFacts.fromState(state().copy(identities = listOf(id to copy)), "0.10.0-rc.1"))
        val report = result.redactedReport()
        val secrets = listOf(profile.getString("seed"), profile.getString("name"), binding.packageName,
            "PRIVATE-KEY-MARKER", "TOKEN-MARKER") + profile.getJSONObject("identifiers").keySet().map { profile.getJSONObject("identifiers").getString(it) }
        secrets.forEach { assertFalse(it, report.contains(it)) }
    }
}
