package dev.cyclone.cloak.play

import org.junit.Assert.*
import org.junit.Test
import java.time.Duration
import java.time.Instant

class ServiceSurfaceIsolationTest {
    private val record = SurfaceFixtures.record()
    private val binding = ServiceSurfaceBinding(record, "com.android.vending", "b".repeat(64))
    private val context = PlayReadinessContext(record.profileId, record.profileHash, 0, binding.artifactHash,
        Instant.parse("2026-10-19T12:01:00Z"))
    private val evidence = ServiceSurfaceEvidence(binding, SurfaceFixtures.observed(record))
    private fun evaluate(e: ServiceSurfaceEvidence? = evidence, inventory: List<GoogleSurfaceRecord> = listOf(record),
        confirmed: Boolean = false, review: Boolean = false) = ServiceSurfaceIsolation.evaluate(binding, context, inventory, e, confirmed, review)

    @Test fun isolatedAssignmentRequiresEveryObservation() {
        assertEquals(ServiceIsolationState.ISOLATED, evaluate().state)
        assertTrue(evaluate().permitsReady)
        assertEquals(ServiceIsolationState.UNKNOWN, evaluate(null).state)
        val o = evidence.observation
        for (partial in listOf(o.copy(privateDataBoundary = null), o.copy(accountMode = null), o.copy(backupSync = null), o.copy(packageStates = emptyMap())))
            assertEquals(ServiceIsolationState.UNKNOWN, evaluate(evidence.copy(observation = partial)).state)
    }
    @Test fun confirmedSharingNeedsCompleteEvidence() {
        val inventory = listOf(record, SurfaceFixtures.record("peer", 17))
        assertEquals(ServiceIsolationState.CONFLICT, evaluate(inventory = inventory).state)
        assertEquals(ServiceIsolationState.SHARED_CONFIRMED, evaluate(inventory = inventory, confirmed = true).state)
        assertEquals(ServiceIsolationState.UNKNOWN, evaluate(null, inventory, true).state)
    }
    @Test fun importedSharedFlagIsNotConsent() {
        val shared = SurfaceFixtures.change(record) { it.getJSONObject("account").put("shared", true) }
        val b = binding.copy(assignment = shared)
        assertEquals(ServiceIsolationState.CONFLICT, ServiceSurfaceIsolation.evaluate(b, context,
            listOf(shared, SurfaceFixtures.record("peer", 17))).state)
    }
    @Test fun staleAndFutureEvidenceCannotPass() {
        assertEquals(ServiceIsolationState.STALE, evaluate(evidence.copy(observation = evidence.observation.copy(checkedAt = context.now.minus(Duration.ofDays(2))))).state)
        assertEquals(listOf("SSI_FUTURE"), evaluate(evidence.copy(observation = evidence.observation.copy(checkedAt = context.now.plusSeconds(1)))).reasons)
        val boundary = context.copy(now = evidence.observation.checkedAt.plus(context.freshness))
        assertTrue(ServiceSurfaceIsolation.evaluate(binding, boundary, listOf(record), evidence).permitsReady)
    }
    @Test fun everyBindingDimensionMustMatch() {
        for (foreign in listOf(binding.copy(artifactHash = "c".repeat(64)), binding.copy(packageName = "com.google.android.gms"),
            binding.copy(assignment = record.copy(profileId = "other")), binding.copy(assignment = record.copy(profileHash = "c".repeat(64))),
            binding.copy(assignment = record.copy(androidUserId = 17)))) {
            assertEquals(listOf("SSI_BINDING_MISMATCH"), evaluate(evidence.copy(binding = foreign)).reasons)
        }
        for (o in listOf(evidence.observation.copy(profileId = "other"), evidence.observation.copy(profileHash = "c".repeat(64)), evidence.observation.copy(androidUserId = 17)))
            assertEquals(ServiceIsolationState.NEEDS_REVIEW, evaluate(evidence.copy(observation = o)).state)
    }
    @Test fun currentBindingMismatchDoesNotReuseOldEvidence() {
        for (ctx in listOf(context.copy(profileId = "other"), context.copy(profileHash = "c".repeat(64)), context.copy(artifactHash = "c".repeat(64)), context.copy(androidUserId = 17)))
            assertEquals(ServiceIsolationState.NEEDS_REVIEW, ServiceSurfaceIsolation.evaluate(binding, ctx, listOf(record), evidence).state)
    }
    @Test fun changedAssignmentNeedsReviewEvenWhenBodyHashWasNotChanged() {
        val old = binding.copy(assignment = record.copy(active = false))
        assertEquals(listOf("SSI_ASSIGNMENT_CHANGED"), evaluate(evidence.copy(binding = old)).reasons)
    }
    @Test fun forbiddenAndContradictoryRulesCannotPassWithoutEvidence() {
        val bad = record.copy(packages = record.packages.map { if (it.packageName == "com.android.vending") it.copy(requirement = PackageRequirement.FORBIDDEN) else it })
        assertEquals(ServiceIsolationState.CONFLICT, ServiceSurfaceIsolation.evaluate(binding.copy(assignment = bad), context, listOf(bad)).state)
        val outside = binding.copy(packageName = "org.example.outside")
        assertEquals(ServiceIsolationState.CONFLICT, ServiceSurfaceIsolation.evaluate(outside, context, listOf(record)).state)
        val o = evidence.observation
        assertEquals(ServiceIsolationState.CONFLICT, evaluate(evidence.copy(observation = o.copy(packageStates = o.packageStates + ("org.example.forbidden" to "enabled")))).state)
    }
    @Test fun inactiveAndMalformedInventoryNeverPass() {
        assertEquals(ServiceIsolationState.UNKNOWN, evaluate(inventory = emptyList()).state)
        assertEquals(ServiceIsolationState.UNKNOWN, evaluate(inventory = listOf(record, record)).state)
        assertEquals(ServiceIsolationState.UNKNOWN, ServiceSurfaceIsolation.evaluate(binding, context, listOf(record), evidence, readable = false).state)
        assertEquals(ServiceIsolationState.UNKNOWN, evaluate(evidence.copy(observation = evidence.observation.copy(source = ""))).state)
        val inactive = record.copy(active = false)
        val b = binding.copy(assignment = inactive)
        assertEquals(ServiceIsolationState.UNKNOWN, ServiceSurfaceIsolation.evaluate(b, context, listOf(inactive), evidence.copy(binding = b)).state)
    }
    @Test fun explicitReviewCannotClearItself() {
        assertEquals(ServiceIsolationState.NEEDS_REVIEW, evaluate(review = true).state)
        assertEquals(ServiceIsolationState.NEEDS_REVIEW, evaluate(null, review = true).state)
    }
    @Test fun gateNeverPromotesMissingPlayEvidence() {
        for (state in ServiceIsolationState.entries) {
            val result = ServiceIsolationResult(state, emptyList())
            val verdict = ServiceSurfaceIsolation.gate("READY", listOf(result))
            assertEquals(result.permitsReady, verdict == "READY")
            assertNotEquals("READY", ServiceSurfaceIsolation.gate("UNKNOWN", listOf(result)))
            assertEquals("BLOCKED", ServiceSurfaceIsolation.gate("BLOCKED", listOf(result)))
        }
        assertEquals("UNKNOWN", ServiceSurfaceIsolation.gate("READY", emptyList()))
        assertEquals("NEEDS REVIEW", ServiceSurfaceIsolation.gate("NEEDS REVIEW", emptyList()))
    }
    @Test fun gateChecksAllAppsAndMessagesAreSafe() {
        val good = evaluate()
        val bad = ServiceIsolationResult(ServiceIsolationState.STALE, listOf("SSI_STALE"))
        assertEquals("NEEDS REVIEW", ServiceSurfaceIsolation.gate("READY", listOf(good, bad)))
        assertTrue(bad.messages.single().contains("expired"))
        assertFalse(bad.messages.toString().contains(record.slotId!!))
    }
}
