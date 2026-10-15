package dev.cyclone.cloak.cyclone

import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject
import com.cyclone.connector.client.CycloneConnectorException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class ReconnectCoordinatorTest {
    private class Scheduler : ReconnectScheduler {
        val jobs = ArrayDeque<Pair<Long, () -> Unit>>()
        override fun schedule(delayMs: Long, task: () -> Unit) { jobs.addLast(delayMs to task) }
        fun runNext() { jobs.removeFirst().second() }
    }
    private fun success(snapshot: ProfilesSnapshot? = null) = ReconnectOutcome(SyncReport("Connected", "Verified live.",
        gate = CycloneGate(true, 3, setOf("profiles.read"), emptySet()), snapshot = snapshot), "ready")
    private fun transient() = ReconnectOutcome(SyncReport.failed("CYCLONE_NOT_ANSWERING", "Open Cyclone and retry."))

    @Test fun backoffIsExponentialJitteredAndCapped() {
        assertEquals(listOf(250L, 500L, 1000L, 2000L), (1..4).map { ReconnectBackoff.delayMs(it, 0.5) })
        assertTrue(ReconnectBackoff.delayMs(1, 0.0) < ReconnectBackoff.delayMs(1, 1.0))
        assertEquals(4000L, ReconnectBackoff.delayMs(100, 1.0))
        assertTrue(ReconnectBackoff.delayMs(0, -1.0) > 0)
    }

    @Test fun burstBeforeStartOpensOneAttempt() {
        val scheduler = Scheduler()
        var calls = 0
        val c = ReconnectCoordinator({ calls++; success() }, scheduler)
        repeat(100) { c.trigger(ReconnectReason.REGISTRY_CHANGE) }
        assertEquals(1, scheduler.jobs.size)
        scheduler.runNext()
        assertEquals(1, calls)
        assertEquals(ConnectionPhase.CONNECTED, c.state.value.phase)
        assertTrue(scheduler.jobs.isEmpty())
    }

    @Test fun concurrentTriggersNeverRunParallelSessionsAndCoalesceOneFollowUp() {
        val scheduler = Scheduler()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        var calls = 0
        val c = ReconnectCoordinator({ calls++; entered.countDown(); check(release.await(3, TimeUnit.SECONDS)); success() }, scheduler)
        c.trigger(ReconnectReason.PROCESS_START)
        val worker = Thread { scheduler.runNext() }.apply { start() }
        assertTrue(entered.await(3, TimeUnit.SECONDS))
        val triggers = (1..20).map { Thread { c.trigger(ReconnectReason.REGISTRY_CHANGE) }.apply { start() } }
        triggers.forEach { it.join() }
        assertEquals(1, calls)
        release.countDown(); worker.join()
        assertEquals(1, scheduler.jobs.size)
        scheduler.runNext()
        assertEquals(2, calls)
        assertTrue(scheduler.jobs.isEmpty())
    }

    @Test fun retriesStopAndStormDoesNotResetBudget() {
        val scheduler = Scheduler()
        var calls = 0
        val c = ReconnectCoordinator({ calls++; transient() }, scheduler, sample = { 0.5 })
        c.trigger(ReconnectReason.PROCESS_START)
        repeat(5) { attempt ->
            assertEquals(if (attempt == 0) 0L else ReconnectBackoff.delayMs(attempt, 0.5), scheduler.jobs.first().first)
            repeat(20) { c.trigger(ReconnectReason.REGISTRY_CHANGE) }
            scheduler.runNext()
        }
        assertEquals(5, calls)
        assertTrue(scheduler.jobs.isEmpty())
        assertEquals(ConnectionPhase.PAUSED, c.state.value.phase)
        assertTrue(c.state.value.detail.contains("Retry"))
    }

    @Test fun successfulAttemptStopsRetriesAndPersistsCache() {
        val scheduler = Scheduler()
        val snapshot = ProfilesSnapshot(null, emptyList())
        var saved: CachedRegistry? = null
        val c = ReconnectCoordinator({ success(snapshot) }, scheduler, now = { 1000 }, save = { saved = it })
        c.trigger(ReconnectReason.SELF_UPDATE); scheduler.runNext()
        assertEquals(CachedRegistry(snapshot, 1000), saved)
        assertEquals(saved, c.state.value.cached)
        assertTrue(scheduler.jobs.isEmpty())
    }

    @Test fun failureKeepsCachedRowsAndNeverRestoresCachedApproval() {
        val cache = CachedRegistry(ProfilesSnapshot(null, listOf(CycloneProfile("Cyclone_0123456789abcdef", "Phone", "profile", "ready", 16, listOf("org.example.app")))), 10)
        val scheduler = Scheduler()
        val c = ReconnectCoordinator({ transient() }, scheduler, cache = cache)
        assertEquals(cache, c.state.value.cached)
        assertNull(c.state.value.report)
        c.trigger(ReconnectReason.RESUME); scheduler.runNext()
        assertEquals(cache, c.state.value.cached)
        assertNull(c.state.value.report?.gate)
    }

    @Test fun corruptCacheAndFutureTimestampFailSafely() {
        assertNull(RegistryCacheCodec.restore(null, 10, 20))
        assertNull(RegistryCacheCodec.restore("oops", 10, 20))
        assertNull(RegistryCacheCodec.restore("{}", 10, 20))
        assertNull(RegistryCacheCodec.restore("{\"profiles\":[7]}", 10, 20))
        assertNull(RegistryCacheCodec.restore("{\"profiles\":[]}", 100000, 20))
    }

    @Test fun registryRoundTripPreservesOptionalFieldsWithoutProfileBody() {
        val cache = CachedRegistry(ProfilesSnapshot("owner", listOf(CycloneProfile("owner", "Main", "owner", "ready", 0, null))), 100)
        val text = RegistryCacheCodec.encode(cache)
        assertEquals(cache, RegistryCacheCodec.restore(text, 100, 200))
        assertFalse(text.contains("seed"))
    }

    @Test fun majorMismatchIdentifiesOutdatedSide() {
        fun mismatch(major: Int): CycloneConnectorException = try {
            ConnectorHandshake.validate(JSONObject().put("contract", "cyclone.connector/$major"))
            error("expected mismatch")
        } catch (e: CycloneConnectorException) { e }
        assertEquals("CLOAK_NEEDS_UPDATE", mismatch(2).code)
        assertTrue(mismatch(2).message!!.contains("Update Cyclone Cloak"))
        assertEquals("CYCLONE_NEEDS_UPDATE", mismatch(0).code)
        assertTrue(mismatch(0).message!!.contains("Update Cyclone."))
    }

    @Test fun compatibleAndLegacyHandshakesStayCompatible() {
        val old = JSONObject().put("minor", 3)
        assertSame(old, ConnectorHandshake.validate(old))
        assertEquals("cyclone.connector/1.3", ConnectorHandshake.validate(JSONObject().put("contract", "cyclone.connector/1.3")).getString("contract"))
    }

    @Test fun needsUpdateAndApprovalDoNotRetryAndSayWhatToDo() {
        for (result in listOf(
            ReconnectOutcome(SyncReport.failed("CLOAK_NEEDS_UPDATE", "Update Cyclone Cloak.")),
            ReconnectOutcome(SyncReport("Needs approval", OpenRequests.APPROVE_LINE)),
            ReconnectOutcome(SyncReport.failed("CYCLONE_NOT_FOUND", "missing"), installed = false))) {
            val scheduler = Scheduler()
            val c = ReconnectCoordinator({ result }, scheduler)
            c.trigger(ReconnectReason.PROCESS_START); scheduler.runNext()
            assertFalse(c.state.value.phase == ConnectionPhase.RECONNECTING)
            assertTrue(scheduler.jobs.isEmpty())
            assertTrue(c.state.value.detail.isNotBlank())
        }
    }

    @Test fun eachAttemptLogsTriggerNumberAndOutcomeAndReleasesWake() {
        val scheduler = Scheduler()
        val events = mutableListOf<Triple<ReconnectReason, Int, String>>()
        var finished = 0
        val c = ReconnectCoordinator({ success() }, scheduler, log = { r, n, o -> events.add(Triple(r,n,o)) })
        c.trigger(ReconnectReason.MODULE_STATE_CHANGE) { finished++ }; scheduler.runNext()
        assertEquals(1, finished)
        assertEquals(listOf("STARTED", "CONNECTED"), events.map { it.third })
        assertTrue(events.all { it.first == ReconnectReason.MODULE_STATE_CHANGE && it.second == 1 })
    }
    @Test fun expiredConfirmationWaitsForUserWithoutAutomaticRepeat() {
        val scheduler = Scheduler()
        var calls = 0
        val c = ReconnectCoordinator({ calls++; ReconnectOutcome(SyncReport.failed("CONFIRMATION_EXPIRED", "private details")) }, scheduler)
        c.trigger(ReconnectReason.PROCESS_START); scheduler.runNext()
        assertEquals(ConnectionState.WAITING_FOR_USER, c.state.value.state)
        assertTrue(c.state.value.detail.contains("expired"))
        assertFalse(c.state.value.detail.contains("private details"))
        assertEquals(1, calls); assertTrue(scheduler.jobs.isEmpty())
    }
    @Test fun rootOptOutDoesNotEvenConsultPreviousApproval() {
        var checks = 0
        assertFalse(CycloneBridge.rootAllowed(false, true) { checks++; true })
        assertFalse(CycloneBridge.rootAllowed(false, false) { checks++; true })
        assertEquals(0, checks)
        assertTrue(CycloneBridge.rootAllowed(true, false) { checks++; true })
        assertEquals(1, checks)
    }
    @Test fun stateHasOneOperatorMeaningAcrossAllPhases() {
        assertEquals(ConnectionState.CONNECTED, ConnectionStatus(ConnectionPhase.CONNECTED).state)
        assertEquals(ConnectionState.RETRYING, ConnectionStatus(ConnectionPhase.RECONNECTING).state)
        assertEquals(ConnectionState.FAILED, ConnectionStatus(ConnectionPhase.PAUSED).state)
        for (phase in listOf(ConnectionPhase.NEEDS_UPDATE, ConnectionPhase.NOT_INSTALLED, ConnectionPhase.NEEDS_APPROVAL))
            assertEquals(ConnectionState.WAITING_FOR_USER, ConnectionStatus(phase).state)
    }
}
