package dev.cyclone.cloak.cyclone

import dev.cyclone.cloak.data.CloakBindingStore
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** State transitions against the production planner/engine and Cyclone-shaped envelopes. No Android device needed. */
class ReconciliationBehaviorTest {
    @Test fun liveRegistryIsReportedBeforeAnyBindingOrPublicationMutation() {
        val api = api(); val local = local()
        val records = local.store
        var callbacks = 0
        val result = CycloneSyncEngine(api, local, BindingReconciler(api, sleep = {})).sync { progress ->
            callbacks++
            assertEquals("Connected", progress.headline)
            assertTrue(progress.approved)
            assertNotNull(progress.snapshot)
            assertNull(progress.reconcile)
            assertTrue(progress.reconciliationPending)
            assertEquals(records, local.store)
            assertEquals(0, local.republished)
            assertFalse(api.calls.any { it.startsWith("config.") })
        }
        assertEquals(1, callbacks)
        assertEquals(1, result.reconcile!!.written)
        assertFalse(result.reconciliationPending)
    }

    @Test fun missingApprovalCannotEmitLiveConnectionProgress() {
        val api = api().apply { approved = false }
        var callbacks = 0
        CycloneSyncEngine(api, local()).sync { callbacks++ }
        assertEquals(0, callbacks)
    }

    @Test fun ambiguousBadRequestPreservesRecordsAndDoesNotInventMissingAppReason() {
        val api = api(); val local = local(); seed(api, local)
        val records = local.store; val remote = api.config.getValue(tuple).toString()
        api.failNext["config.get.v1"] = ArrayDeque(listOf("BAD_REQUEST"))
        val failed = sync(api, local)
        assertEquals(records, local.store)
        assertEquals(remote, api.config.getValue(tuple).toString())
        assertEquals(setOf(tuple), local.pushed)
        assertTrue(failed.issues.getValue(key).contains("BAD_REQUEST"))
        assertFalse(failed.issues.getValue(key).contains("app not in"))
    }
    private val profile = "Cyclone_bbbbbbbbbbbbbbbb"
    private val other = "Cyclone_cccccccccccccccc"
    private val app = "com.example.app"
    private val key get() = bindingKey(profile, app)
    private val tuple get() = tupleKey(profile, 11, app)
    private fun api() = FakeCyclone().apply {
        profiles += FakeCyclone.Profile(profile, "B", 11, listOf(app))
    }
    private fun local() = FakeLocal(0, listOf(binding(profile, 11, app).copy(revision = 7, updatedAt = 123)))
    private fun sync(api: CycloneApi, local: FakeLocal, sleeps: MutableList<Long> = mutableListOf()) =
        CycloneSyncEngine(api, local, BindingReconciler(api, sleep = { sleeps += it })).sync()
    private fun seed(api: FakeCyclone, local: FakeLocal) {
        assertTrue(sync(api, local).issues.isEmpty())
        api.calls.clear()
    }
    private fun assertWarning(report: SyncReport) = assertFalse(report.issues.getValue(key).isBlank())

    @Test fun androidUserMovePreservesIdentityAndMetadataAndIsIdempotent() {
        val api = api(); val local = local(); seed(api, local)
        val before = local.store.single()
        api.profiles[0] = api.profiles[0].copy(user = 21)
        // Cyclone's restore can migrate the tuple independently of Cloak.
        api.config.remove(tuple)
        val report = sync(api, local)
        assertTrue(report.issues.isEmpty())
        assertEquals(before.copy(androidUserId = 21), local.store.single())
        assertEquals(1, local.republished)
        assertEquals(setOf(tupleKey(profile, 21, app)), local.pushed)
        assertEquals(before.cloakProfileId, CycloneReader.cloakProfileId(api.getConfig(profile, 21, app), profile, 21, app))
        api.calls.clear()
        assertEquals(0, sync(api, local).reconcile!!.written)
        assertEquals(1, local.republished)
        assertFalse(api.calls.contains("config.set.v1"))
    }
    @Test fun failedReplacementAfterUserMoveRetainsOldTupleUntilRetrySucceeds() {
        for (operation in listOf("config.get.v1", "config.set.v1", "config.status.v1")) {
            val api = api(); val local = local(); seed(api, local)
            val old = api.config.getValue(tuple).toString()
            api.profiles[0] = api.profiles[0].copy(user = 21)
            api.failNext[operation] = ArrayDeque(listOf("INTERNAL", "INTERNAL"))
            val failed = sync(api, local)
            assertWarning(failed)
            assertEquals(21, local.store.single().androidUserId)
            assertEquals(old, api.config.getValue(tuple).toString())
            assertTrue(local.pushed!!.contains(tuple))
            if (operation == "config.status.v1") assertTrue(local.pushed!!.contains(tupleKey(profile, 21, app)))
            assertTrue(sync(api, local).issues.isEmpty())
            assertEquals(setOf(tupleKey(profile, 21, app)), local.pushed)
            assertEquals("pixel-8-work", CycloneReader.cloakProfileId(api.getConfig(profile, 21, app), profile, 21, app))
            // Cyclone rejects the retired user tuple; confirmed absence removes only its bookkeeping.
        }
    }
    @Test fun trashedProfileWithChangedUserPreservesLastKnownPlacement() {
        val api = api(); val local = local(); seed(api, local)
        val before = local.store
        api.profiles[0] = api.profiles[0].copy(user = 21, state = "in_trash")
        assertWarning(sync(api, local))
        assertEquals(before, local.store)
        assertTrue(local.liveUsers.contains(11))
        assertEquals(setOf(tuple), local.pushed)
        assertFalse(api.calls.any { it.startsWith("config.") })
    }
    @Test fun duplicatesWithChangedRegistryUserKeepOldPublisherInLiveUsers() {
        val api = api(); val local = local(); seed(api, local)
        local.store += local.store.single().copy(cloakProfileId = "conflict")
        val before = local.store
        api.profiles[0] = api.profiles[0].copy(user = 21)
        assertWarning(sync(api, local))
        assertEquals(before, local.store)
        assertTrue(local.liveUsers.containsAll(listOf(11, 21)))
        assertEquals(setOf(tuple), local.pushed)
        assertFalse(api.calls.any { it.startsWith("config.") })
    }
    @Test fun matchingValueWithFailedHealthUpdateIsTrackedOnFirstRun() {
        val api = api(); val local = local(); seed(api, local)
        local.pushed = null
        api.config.getValue(tuple).put("state", "unknown")
        api.failNext["config.status.v1"] = ArrayDeque(listOf("INTERNAL", "INTERNAL"))
        assertWarning(sync(api, local))
        assertEquals(setOf(tuple), local.pushed)
        assertFalse(api.calls.contains("config.set.v1"))
    }
    @Test fun ambiguousFirstRunSweepReportsWarningAndRetainsRetryBookkeeping() {
        val api = api(); val local = local(); seed(api, local)
        val old = api.config.getValue(tuple).toString()
        api.failNext["config.get.v1"] = ArrayDeque(listOf("INTERNAL", "INTERNAL"))
        val result = BindingReconciler(api, sleep = {}).reconcile(emptyList(), null,
            listOf(CycloneProfile(profile, "B", "profile", "ready", 11, listOf(app))))
        assertTrue(result.issues.getValue(key).contains("INTERNAL"))
        assertEquals(setOf(tuple), result.pushed)
        assertEquals(old, api.config.getValue(tuple).toString())
        assertFalse(api.calls.contains("config.set.v1"))
    }
    @Test fun connectorLossOnRateLimitRetryStopsWithoutClearingOldTuple() {
        val api = api(); val local = local(); seed(api, local)
        api.profiles[0] = api.profiles[0].copy(user = 21)
        api.failNext["config.get.v1"] = ArrayDeque(listOf("RATE_LIMITED", "NOT_A_CONNECTOR"))
        val failed = sync(api, local)
        assertEquals("NOT_A_CONNECTOR", failed.reconcile!!.stoppedBy)
        assertEquals(setOf(tuple), local.pushed)
        assertFalse(api.calls.contains("config.set.v1"))
    }
    @Test fun confirmedRemovalForgetsOnlyThatProfilesBindingsAndNeverDeletesIdentityFiles() {
        val api = api().apply { profiles += FakeCyclone.Profile(other, "C", 12, listOf(app)) }
        val local = local().apply {
            store += binding(other, 12, app)
            profilesById["pixel-8-work"] = JSONObject().put("name", "retained identity")
        }
        seed(api, local)
        api.profiles.removeAll { it.id == profile }; api.config.remove(tuple); api.emit("profile.removed", profile)
        assertTrue(sync(api, local).issues.isEmpty())
        assertEquals(listOf(other), local.store.map { it.profileId })
        assertFalse(local.liveUsers.contains(11))
        assertEquals(setOf(tupleKey(other, 12, app)), local.pushed)
        assertEquals("retained identity", local.profilesById.getValue("pixel-8-work").getString("name"))
    }
    @Test fun missingSnapshotEntryWithoutRemovalEventKeepsRecordsAndBookkeeping() {
        val api = api(); val local = local(); seed(api, local)
        val records = local.store; val remote = api.config.getValue(tuple).toString()
        api.profiles.clear()
        repeat(2) {
            assertWarning(sync(api, local))
            assertEquals(records, local.store)
            assertTrue(local.liveUsers.contains(11))
            assertEquals(setOf(tuple), local.pushed)
            assertEquals(remote, api.config.getValue(tuple).toString())
        }
        assertFalse(api.calls.any { it.startsWith("config.") })
        assertEquals(0, local.republished)
    }
    @Test fun oldRemovalEventCannotDeleteAProfilePresentInTheCurrentSnapshot() {
        val api = api(); val local = local(); seed(api, local)
        val records = local.store
        api.emit("profile.removed", profile); api.emit("profile.restored", profile)
        assertTrue(sync(api, local).issues.isEmpty())
        assertEquals(records, local.store)
        assertEquals(setOf(tuple), local.pushed)
        assertEquals(0, local.republished)
    }
    @Test fun trashedProfileKeepsItsLocalAndRemoteBindingUntilRestore() {
        val api = api(); val local = local(); seed(api, local)
        val records = local.store; val remote = api.config.getValue(tuple).toString()
        api.profiles[0] = api.profiles[0].copy(state = "in_trash"); api.emit("profile.trashed", profile)
        val paused = sync(api, local)
        assertTrue(paused.issues.getValue(key).contains("Recently deleted"))
        assertEquals(records, local.store); assertEquals(setOf(tuple), local.pushed)
        assertEquals(remote, api.config.getValue(tuple).toString())
        assertFalse(api.calls.any { it.startsWith("config.") })
        api.profiles[0] = api.profiles[0].copy(state = "ready"); api.emit("profile.restored", profile)
        val resumed = sync(api, local)
        assertTrue(resumed.issues.isEmpty()); assertEquals(0, resumed.reconcile!!.written)
    }
    @Test fun missingPackageWarnsWithoutDiscardingItsPreviouslyWrittenRecord() {
        val api = api(); val local = local(); seed(api, local)
        val records = local.store; val remote = api.config.getValue(tuple).toString()
        api.profiles[0] = api.profiles[0].copy(packages = emptyList())
        assertEquals(ReconcilePlanner.NOT_LISTED, sync(api, local).issues[key])
        assertEquals(records, local.store); assertEquals(setOf(tuple), local.pushed)
        assertEquals(remote, api.config.getValue(tuple).toString())
        assertFalse(api.calls.any { it.startsWith("config.") })
        api.profiles[0] = api.profiles[0].copy(packages = listOf(app))
        assertTrue(sync(api, local).issues.isEmpty())
    }
    @Test fun unavailableAppListWarnsAndDoesNotSweepAnExistingTuple() {
        val api = api(); val local = local(); seed(api, local)
        val missingList = object : CycloneApi by api {
            override fun profiles() = api.profiles().also {
                it.getJSONArray("profiles").getJSONObject(1).remove("packages")
            }
        }
        assertWarning(sync(missingList, local))
        assertEquals(setOf(tuple), local.pushed)
        assertFalse(api.calls.any { it.startsWith("config.") })
    }
    @Test fun unknownAndroidUserWarnsWithoutMovingToUserZeroOrClearingRemote() {
        val api = api(); val local = local(); seed(api, local)
        val before = local.store
        api.profiles[0] = api.profiles[0].copy(user = null)
        assertWarning(sync(api, local))
        assertEquals(before, local.store); assertEquals(setOf(tuple), local.pushed)
        assertEquals(0, local.republished)
        assertFalse(api.calls.any { it.startsWith("config.") })
    }
    @Test fun duplicateLocalRecordsArePreservedAndNeverChooseAnArbitraryIdentity() {
        for (conflicting in listOf(false, true)) {
            val api = api(); val local = local(); seed(api, local)
            local.store += local.store.single().copy(cloakProfileId = if (conflicting) "other-phone" else "pixel-8-work")
            val before = local.store; val remote = api.config.getValue(tuple).toString()
            assertTrue(sync(api, local).issues.getValue(key).contains("duplicate"))
            assertEquals(before, local.store); assertEquals(setOf(tuple), local.pushed)
            assertEquals(remote, api.config.getValue(tuple).toString())
            assertFalse(api.calls.any { it.startsWith("config.") })
        }
    }
    @Test fun firstRunDuplicateRecordsDoNotSweepTheExistingRemoteBinding() {
        val api = api(); val local = local(); seed(api, local)
        local.pushed = null; local.store += local.store.single().copy(cloakProfileId = "conflict")
        assertWarning(sync(api, local))
        assertEquals("pixel-8-work", api.readBindings().single().second)
        assertFalse(api.calls.any { it.startsWith("config.") })
    }
    @Test fun equalTimestampDuplicatesSurviveDiskParsingAndRoundTripAndSurfaceWarning() {
        val records = listOf(binding(profile, 11, app, cloak = "first"), binding(profile, 21, app, cloak = "second"))
        val parsed = CloakBindingStore.parse(CloakBindingStore.serialize(records))
        assertEquals(records, parsed)
        assertEquals(records, CloakBindingStore.parse(CloakBindingStore.serialize(parsed)))
        val local = FakeLocal(0, parsed); val api = api()
        assertWarning(sync(api, local))
        assertEquals(records, local.store); assertTrue(api.config.isEmpty())
    }
    @Test fun rateLimitedWriteRetriesOnceAfterBackoffAndThenSucceeds() {
        val api = api().apply { failNext["config.set.v1"] = ArrayDeque(listOf("RATE_LIMITED")) }
        val local = local(); val sleeps = mutableListOf<Long>()
        val report = sync(api, local, sleeps)
        assertTrue(report.issues.isEmpty()); assertEquals(1, report.reconcile!!.written)
        assertEquals(2, api.calls.count { it == "config.set.v1" })
        assertEquals(1, sleeps.count { it == 1_100L })
        assertEquals(setOf(tuple), local.pushed)
        assertEquals("pixel-8-work", api.readBindings().single().second)
    }
    @Test fun exhaustedRateLimitPreservesOldValueAndRecordsForNextSync() {
        val api = api(); val local = local(); seed(api, local)
        local.store = listOf(local.store.single().copy(cloakProfileId = "new-phone"))
        api.failNext["config.set.v1"] = ArrayDeque(listOf("RATE_LIMITED", "RATE_LIMITED"))
        val records = local.store; val sleeps = mutableListOf<Long>()
        val failed = sync(api, local, sleeps)
        assertTrue(failed.issues.getValue(key).contains("RATE_LIMITED"))
        assertEquals(records, local.store); assertEquals(setOf(tuple), local.pushed)
        assertEquals(2, api.calls.count { it == "config.set.v1" })
        assertEquals(1, sleeps.count { it == 1_100L })
        assertEquals("pixel-8-work", api.readBindings().single().second)
        assertTrue(sync(api, local).issues.isEmpty())
        assertEquals("new-phone", api.readBindings().single().second)
    }
    @Test fun failedWriteKeepsTheLocalChoiceAndPreviousRemoteValue() {
        val api = api(); val local = local(); seed(api, local)
        local.store = listOf(local.store.single().copy(cloakProfileId = "new-phone"))
        api.failNext["config.set.v1"] = ArrayDeque(listOf("BUSY"))
        val report = sync(api, local)
        assertTrue(report.issues.getValue(key).contains("BUSY"))
        assertEquals(0, report.reconcile!!.written)
        assertEquals(1, api.calls.count { it == "config.set.v1" })
        assertEquals("new-phone", local.store.single().cloakProfileId)
        assertEquals("pixel-8-work", api.readBindings().single().second)
        assertEquals(setOf(tuple), local.pushed)
    }
    @Test fun failedClearKeepsDisabledRecordAndBookkeepingAndReportsWarning() {
        val api = api(); val local = local(); seed(api, local)
        local.store = listOf(local.store.single().copy(enabled = false))
        val records = local.store
        api.failNext["config.set.v1"] = ArrayDeque(listOf("BUSY"))
        val failed = sync(api, local)
        assertTrue(failed.issues.getValue(key).contains("clear"))
        assertTrue(failed.issues.getValue(key).contains("BUSY"))
        assertEquals(records, local.store); assertEquals(setOf(tuple), local.pushed)
        assertEquals("pixel-8-work", api.readBindings().single().second)
        val retried = sync(api, local)
        assertTrue(retried.issues.isEmpty()); assertEquals(1, retried.reconcile!!.cleared)
        assertTrue(api.readBindings().isEmpty()); assertEquals(emptySet<String>(), local.pushed)
        assertEquals(records, local.store)
    }
    @Test fun failedReadAfterSuccessfulWriteKeepsTheChoiceAndBookkeeping() {
        val api = api(); val local = local(); seed(api, local)
        val records = local.store
        api.failNext["config.get.v1"] = ArrayDeque(listOf("INTERNAL", "INTERNAL"))
        val failed = sync(api, local)
        assertTrue(failed.issues.getValue(key).contains("INTERNAL"))
        assertEquals(records, local.store); assertEquals(setOf(tuple), local.pushed)
        assertFalse(api.calls.contains("config.set.v1"))
        assertTrue(sync(api, local).issues.isEmpty())
    }
    @Test fun valueWriteWithFailedHealthUpdateIsRetriedWithoutRewritingTheValue() {
        val api = api().apply { failNext["config.status.v1"] = ArrayDeque(listOf("INTERNAL", "INTERNAL")) }
        val local = local(); val records = local.store
        val failed = sync(api, local)
        assertTrue(failed.issues.getValue(key).contains("INTERNAL"))
        assertEquals(1, failed.reconcile!!.written); assertEquals(0, failed.reconcile!!.statuses)
        assertEquals(setOf(tuple), local.pushed)
        assertEquals(records, local.store)
        assertEquals("pixel-8-work", api.readBindings().single().second)
        api.calls.clear()
        val retried = sync(api, local)
        assertTrue(retried.issues.isEmpty()); assertEquals(0, retried.reconcile!!.written)
        assertEquals(1, retried.reconcile!!.statuses); assertEquals("ready", api.stateOf(profile, 11, app))
        assertFalse(api.calls.contains("config.set.v1"))
    }
    @Test fun successfulWriteIsConfirmedByStrictReadBackAndNextSyncIsReadOnly() {
        val api = api(); val local = local()
        val report = sync(api, local)
        assertTrue(report.issues.isEmpty()); assertEquals(setOf(tuple), local.pushed)
        val expected = local.summaryFor(local.store.single())
        val held = api.getConfig(profile, 11, app)
        assertTrue(sameJson(expected, CycloneReader.value(held, profile, 11, app)))
        assertEquals("ready", held.getString("state"))
        held.getJSONObject("value").put("cloakProfileId", "mutated-read-copy")
        assertEquals("pixel-8-work", CycloneReader.cloakProfileId(api.getConfig(profile, 11, app), profile, 11, app))
        api.calls.clear()
        val next = sync(api, local)
        assertTrue(next.issues.isEmpty()); assertEquals(0, next.reconcile!!.written)
        assertEquals(0, next.reconcile!!.statuses)
        assertTrue(api.calls.contains("config.get.v1"))
        assertFalse(api.calls.contains("config.set.v1")); assertFalse(api.calls.contains("config.status.v1"))
    }
}
