package dev.cyclone.cloak

import com.cyclone.connector.client.CycloneConnectorException
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val B = "Cyclone_bbbbbbbbbbbbbbbb"
private const val C = "Cyclone_cccccccccccccccc"
private const val APP = "com.example.app"
private const val APP2 = "com.example.video"

class CycloneSyncTest {
    private fun cyclone(vararg extra: FakeCyclone.Profile) = FakeCyclone().apply {
        profiles += FakeCyclone.Profile(B, "Profile B", 11, listOf(APP, APP2))
        profiles += FakeCyclone.Profile(C, "Profile C", 12, listOf(APP))
        profiles += extra
    }

    private fun engine(api: CycloneApi, local: CloakLocal) =
        CycloneSyncEngine(api, local, BindingReconciler(api, sleep = {}))

    // ---- gating on minor and granted --------------------------------------------------------------------------

    @Test
    fun gateEnablesFeaturesByMinorAndGrantedScopes() {
        val all = CycloneGate(true, 3, FakeCyclone.ALL_SCOPES, emptySet())
        assertTrue(all.bindings && all.rootStatus && all.openRequests && all.events && all.startup)

        val minor1 = all.copy(minor = 1)
        assertTrue(minor1.bindings)
        assertFalse(minor1.rootStatus)
        assertFalse(minor1.openRequests)

        val minor2 = all.copy(minor = 2)
        assertTrue(minor2.rootStatus)
        assertFalse(minor2.openRequests)

        val noRoot = all.copy(granted = all.granted - "device.root.read" - "profiles.open.request")
        assertFalse(noRoot.rootStatus)
        assertFalse(noRoot.openRequests)
        assertFalse(all.copy(granted = all.granted - "profiles.apps.read").bindings)
        assertFalse(all.copy(approved = false).bindings)
        assertFalse(all.copy(approved = false).profiles)
    }

    @Test
    fun unapprovedCloakShowsOnlyTheApproveLine() {
        val api = cyclone().apply { approved = false }
        val report = engine(api, FakeLocal(0, listOf(binding(B, 11, APP)))).sync()
        assertEquals("Needs approval", report.headline)
        assertEquals(OpenRequests.APPROVE_LINE, report.message)
        assertEquals(listOf("hello"), api.calls)
    }

    @Test
    fun withoutConfigScopeNothingIsWrittenAndTheOwnerIsTold() {
        val api = cyclone().apply { granted = granted - "profiles.config" }
        val report = engine(api, FakeLocal(0, listOf(binding(B, 11, APP)))).sync()
        assertTrue(report.message.contains("profiles.config"))
        assertFalse(api.calls.any { it.startsWith("config.") })
    }

    @Test
    fun aRefusedHelloIsReportedByCode() {
        val api = cyclone().apply { failNext["hello"] = ArrayDeque(listOf("INTERNAL")) }
        val report = engine(api, FakeLocal(0)).sync()
        assertEquals("Connector failed", report.headline)
        assertTrue(report.message.startsWith("INTERNAL"))
    }

    // ---- profiles and placement ---------------------------------------------------------------------------------

    @Test
    fun aMissingOrStringUserNumberIsNeverUserZero() {
        val result = JSONObject().put("profiles", JSONArray()
            .put(JSONObject().put("id", B).put("kind", "profile").put("state", "ready").put("androidUserId", JSONObject.NULL))
            .put(JSONObject().put("id", C).put("kind", "profile").put("state", "ready").put("androidUserId", "12")))
        val snapshot = ProfilesSnapshot.parse(result)
        assertTrue(snapshot.profiles.all { it.androidUserId == null && !it.bindable })
    }

    @Test
    fun placementIsMainUnlessThisUserIsACycloneProfile() {
        val snapshot = ProfilesSnapshot.parse(cyclone().profiles())
        assertTrue(Placement.of(0, snapshot).isMain)
        assertEquals(C, (Placement.of(12, snapshot) as Placement.InProfile).profile.id)
    }

    // ---- reconcile and health -----------------------------------------------------------------------------------

    @Test
    fun mainWritesEveryProfilesBindingsWithHealthCycloneCanRead() {
        val api = cyclone()
        val local = FakeLocal(0, listOf(
            binding(B, 11, APP, state = "ready"),
            binding(B, 11, APP2, state = "module disabled"),
            binding(C, 12, APP, cloak = "galaxy", state = "root approval needed"),
        ))
        local.profilesById["pixel-8-work"] = CloakForge.forgeProfile("Work phone", "ab".repeat(32), "pixel_7")
        val report = engine(api, local).sync()

        assertEquals("Connected", report.headline)
        assertEquals(
            setOf("$B/$APP" to "pixel-8-work", "$B/$APP2" to "pixel-8-work", "$C/$APP" to "galaxy"),
            api.readBindings().toSet(),
        )
        assertEquals("ready", api.stateOf(B, 11, APP))
        assertEquals("failed", api.stateOf(B, 11, APP2))
        assertEquals("degraded", api.stateOf(C, 12, APP))
        val summary = CycloneReader.summary(api.config[tupleKey(B, 11, APP)]!!, B, 11, APP)!!
        assertEquals("Work phone", summary["name"])
        assertEquals("Pixel 7", summary["model"])
        assertEquals(setOf(tupleKey(B, 11, APP), tupleKey(B, 11, APP2), tupleKey(C, 12, APP)), local.pushed)
    }

    @Test
    fun aSecondSyncWithNothingChangedOnlyReads() {
        val api = cyclone()
        val local = FakeLocal(0, listOf(binding(B, 11, APP)))
        engine(api, local).sync()
        api.calls.clear()
        val report = engine(api, local).sync()
        assertEquals(0, report.reconcile!!.written)
        assertEquals(0, report.reconcile!!.statuses)
        assertFalse(api.calls.contains("config.set.v1"))
    }

    @Test
    fun healthChangesAreReportedWithoutRewritingTheValue() {
        val api = cyclone()
        val local = FakeLocal(0, listOf(binding(B, 11, APP, state = "ready")))
        engine(api, local).sync()
        local.store = listOf(binding(B, 11, APP, state = "Zygisk status unknown"))
        val report = engine(api, local).sync()
        assertEquals("degraded", api.stateOf(B, 11, APP))
        assertEquals(0, report.reconcile!!.written)
        assertEquals(1, report.reconcile!!.statuses)
    }

    @Test
    fun removingOrDisablingABindingClearsItInCyclone() {
        val api = cyclone()
        val local = FakeLocal(0, listOf(binding(B, 11, APP), binding(B, 11, APP2)))
        engine(api, local).sync()
        local.store = listOf(binding(B, 11, APP, enabled = false))
        val report = engine(api, local).sync()
        assertEquals(2, report.reconcile!!.cleared)
        assertTrue(api.readBindings().isEmpty())
        assertEquals(emptySet<String>(), local.pushed)
    }

    @Test
    fun firstRunSweepsStaleCloakBindingsCycloneStillHolds() {
        val api = cyclone()
        // Written by an older Cloak whose local record is gone.
        api.setConfig(B, 11, APP2, JSONObject().put("cloakProfileId", "old"))
        api.calls.clear()
        val local = FakeLocal(0, listOf(binding(B, 11, APP)))
        engine(api, local).sync()
        assertEquals(listOf("$B/$APP" to "pixel-8-work"), api.readBindings())
    }

    @Test
    fun anAppMissingFromCyclonesListIsReportedNotSent() {
        val api = cyclone()
        val local = FakeLocal(0, listOf(binding(C, 12, APP2)))
        val report = engine(api, local).sync()
        assertEquals(ReconcilePlanner.NOT_LISTED, report.issues[bindingKey(C, APP2)])
        assertFalse(api.calls.contains("config.set.v1"))
    }

    @Test
    fun mainIsNeverBoundAndTrashedProfilesKeepTheirBindings() {
        val api = cyclone(FakeCyclone.Profile("Cyclone_dddddddddddddddd", "D", 13, listOf(APP), state = "in_trash"))
        val trashed = binding("Cyclone_dddddddddddddddd", 13, APP)
        val local = FakeLocal(0, listOf(trashed))
        engine(api, local).sync()
        assertEquals(listOf(trashed), local.store)
        assertTrue(api.config.isEmpty())
        val plan = ReconcilePlanner.plan(Placement.Main, ProfilesSnapshot.parse(api.profiles()), local.store) { JSONObject() }
        assertTrue(plan.desired.none { it.profileId == CycloneProfile.OWNER })
        assertTrue(plan.sweep.none { it.id == CycloneProfile.OWNER })
    }

    @Test
    fun aProfileRestoredUnderANewUserNumberKeepsItsBindingsAndRepublishes() {
        val api = cyclone()
        val local = FakeLocal(0, listOf(binding(B, 11, APP)))
        engine(api, local).sync()
        // Restored as user 21; Cyclone moved its own tuple.
        api.profiles[0] = api.profiles[0].copy(user = 21)
        api.config.remove(tupleKey(B, 11, APP))
        api.emit("profile.restored", B)
        engine(api, local).sync()

        assertEquals(21, local.store.single().androidUserId)
        assertEquals(1, local.republished)
        assertEquals(listOf("$B/$APP" to "pixel-8-work"), api.readBindings())
        assertEquals(setOf(tupleKey(B, 21, APP)), local.pushed)
    }

    @Test
    fun movedBindingsWaitForRootDoctorWhenRootIsntAllowedInTheBackground() {
        val api = cyclone()
        val local = FakeLocal(0, listOf(binding(B, 9, APP))).apply { root = false }
        val report = engine(api, local).sync()
        assertEquals(11, local.store.single().androidUserId)
        assertEquals(0, local.republished)
        assertTrue(report.message.contains("Root Doctor"))
    }

    @Test
    fun aRemovedProfileIsForgotten() {
        val api = cyclone()
        val local = FakeLocal(0, listOf(binding(B, 11, APP), binding(C, 12, APP)))
        engine(api, local).sync()
        api.profiles.removeAll { it.id == C }
        api.config.remove(tupleKey(C, 12, APP))
        api.emit("profile.removed", C)
        engine(api, local).sync()
        assertEquals(listOf(B), local.store.map { it.profileId })
        assertEquals(setOf(tupleKey(B, 11, APP)), local.pushed)
    }

    @Test
    fun cloakInAProfileWritesOnlyItsOwnProfileAndMirrorsMain() {
        val api = cyclone()
        val summary = CloakIdentity.summary("pixel-8-work", CloakForge.forgeProfile("Work phone", "cd".repeat(32), "pixel_7"))
        val index = JSONObject().put("schemaVersion", 2).put("entries", JSONObject()
            .put("12/$APP", JSONObject().put("profileId", C).put("cloakProfileId", "pixel-8-work").put("key", "k")
                .put("publisher", 0).put("summary", summary))
            .put("11/$APP", JSONObject().put("profileId", B).put("cloakProfileId", "other").put("key", "k").put("publisher", 0)))
        val local = FakeLocal(12, listOf(binding(B, 11, APP))).apply { published = RootDoctorResult(RootDoctorCode.READY) to index.toString() }
        val report = engine(api, local).sync()

        assertEquals(C, (report.placement as Placement.InProfile).profile.id)
        assertEquals(listOf("$C/$APP" to "pixel-8-work"), api.readBindings())
        assertEquals("ready", api.stateOf(C, 12, APP))
        assertEquals("Work phone", CycloneReader.summary(api.config[tupleKey(C, 12, APP)]!!, C, 12, APP)!!["name"])
        val mirrored = local.store.single { it.profileId == C }
        assertEquals(CloakBinding.ORIGIN_MAIN, mirrored.origin)
    }

    @Test
    fun aMirrorThatCantReadRootMarksMainsBindingsDegraded() {
        val api = cyclone()
        val local = FakeLocal(12, listOf(binding(C, 12, APP, origin = CloakBinding.ORIGIN_MAIN, state = "ready")))
            .apply { published = RootDoctorResult(RootDoctorCode.ROOT_REQUIRED) to null }
        engine(api, local).sync()
        assertEquals("root approval needed", local.store.single().state)
        assertEquals("degraded", api.stateOf(C, 12, APP))
    }

    // ---- the error table (§6.3) ---------------------------------------------------------------------------------

    @Test
    fun rateLimitedAndInternalAreRetriedOnceWithBackoff() {
        val api = cyclone().apply { failNext["config.get.v1"] = ArrayDeque(listOf("RATE_LIMITED")) }
        val sleeps = mutableListOf<Long>()
        val result = BindingReconciler(api, sleep = { sleeps += it }).reconcile(
            listOf(DesiredBinding(B, 11, APP, JSONObject().put("cloakProfileId", "x"), "ready")), emptySet(), emptyList(),
        )
        assertTrue(1_100L in sleeps)
        assertTrue(result.issues.isEmpty())
        assertEquals(1, result.written)

        val twice = cyclone().apply { failNext["config.get.v1"] = ArrayDeque(listOf("INTERNAL", "INTERNAL")) }
        val again = BindingReconciler(twice, sleep = {}).reconcile(
            listOf(DesiredBinding(B, 11, APP, JSONObject().put("cloakProfileId", "x"), "ready")), emptySet(), emptyList(),
        )
        assertEquals(2, twice.calls.count { it == "config.get.v1" })
        assertTrue(again.issues.getValue(bindingKey(B, APP)).contains("INTERNAL"))
    }

    @Test
    fun notApprovedMidRunStopsAndKeepsTheBookkeeping() {
        val api = cyclone().apply { failNext["config.get.v1"] = ArrayDeque(listOf("NOT_APPROVED")) }
        val result = BindingReconciler(api, sleep = {}).reconcile(
            listOf(DesiredBinding(B, 11, APP, JSONObject().put("cloakProfileId", "x"), "ready")),
            setOf(tupleKey(C, 12, APP)), emptyList(),
        )
        assertEquals("NOT_APPROVED", result.stoppedBy)
        assertTrue(tupleKey(C, 12, APP) in result.pushed)
        assertEquals(1, api.calls.size)
    }

    @Test
    fun noSuchProfileAndBadRequestAreReportedPerBinding() {
        val api = cyclone()
        val result = BindingReconciler(api, sleep = {}).reconcile(
            listOf(
                DesiredBinding(B, 99, APP, JSONObject().put("cloakProfileId", "x"), "ready"),
                DesiredBinding(C, 12, "com.not.listed", JSONObject().put("cloakProfileId", "x"), "ready"),
                DesiredBinding(C, 12, APP, JSONObject().put("cloakProfileId", "x"), "ready"),
            ),
            emptySet(), emptyList(),
        )
        assertEquals("profile isn't ready in Cyclone", result.issues[bindingKey(B, APP)])
        assertEquals(ReconcilePlanner.NOT_LISTED, result.issues[bindingKey(C, "com.not.listed")])
        assertEquals(setOf(tupleKey(C, 12, APP)), result.pushed)
    }

    @Test
    fun callsArePacedUnderCyclonesRateLimit() {
        val api = cyclone()
        val sleeps = mutableListOf<Long>()
        BindingReconciler(api, sleep = { sleeps += it }, paceMs = 70).reconcile(
            listOf(DesiredBinding(B, 11, APP, JSONObject().put("cloakProfileId", "x"), "ready")), emptySet(), emptyList(),
        )
        // get, set, status: two gaps of 70 ms → never more than ~14 calls a second.
        assertEquals(listOf(70L, 70L), sleeps)
    }

    // ---- events -------------------------------------------------------------------------------------------------

    @Test
    fun eventsAreDeduplicatedBySeqAndPagedToTheEnd() {
        val api = cyclone()
        api.emit("profile.created", B)
        api.emit("profile.switched", C)
        api.emit("profile.updated", B)
        val first = CycloneEvents.pull(api, CycloneEvents.Cursor(0, 0))
        assertEquals(listOf(1L, 2L, 3L), first.events.map { it.getLong("seq") })
        assertTrue(first.helloAgain && first.reconcile)
        assertEquals(C, first.switchedTo)

        // Delivered at least once: a replay from an older cursor yields nothing new.
        val replay = CycloneEvents.pull(api, CycloneEvents.Cursor(1, first.cursor.lastSeq))
        assertTrue(replay.events.isEmpty())
        assertFalse(replay.reconcile)
    }

    @Test
    fun aJournalThatStartedOverIsAReset() {
        val api = cyclone()
        api.emit("profile.updated", B)
        val batch = CycloneEvents.pull(api, CycloneEvents.Cursor(since = 40, lastSeq = 40))
        assertTrue(batch.reset)
        assertTrue(batch.reconcile)
    }

    @Test
    fun unknownEventTypesAskForNothing() {
        val api = cyclone()
        api.emit("profile.recolored", B)
        val batch = CycloneEvents.pull(api, CycloneEvents.Cursor(0, 0))
        assertEquals(1, batch.events.size)
        assertFalse(batch.reconcile)
        assertFalse(batch.helloAgain)
    }

    @Test
    fun aSwitchSaysHelloAgainSoACarriedApprovalIsSeen() {
        val api = cyclone().apply { granted = granted - "profiles.config" }
        val local = FakeLocal(0, listOf(binding(B, 11, APP)))
        api.emit("profile.switched", "owner")
        // The approval for profiles.config lands with the switch.
        val engine = engine(object : CycloneApi by api {
            override fun events(since: Long): JSONObject = api.events(since).also { api.granted = FakeCyclone.ALL_SCOPES }
        }, local)
        val report = engine.sync()
        assertEquals(2, api.calls.count { it == "hello" })
        assertTrue(report.gate!!.bindings)
        assertEquals(listOf("$B/$APP" to "pixel-8-work"), api.readBindings())
    }

    // ---- open requests ------------------------------------------------------------------------------------------

    @Test
    fun openRequestsAreNeverRetried() {
        for (code in listOf("BUSY", "RATE_LIMITED", "ALREADY_OPEN", "NO_SUCH_PROFILE", "INTERNAL")) {
            val api = cyclone().apply { openAnswer = { throw CycloneConnectorException(code, "x") } }
            val outcome = OpenRequests.ask(api, ProfilesSnapshot.parse(api.profiles()).byId(B)!!)
            assertEquals(1, api.calls.count { it == "profiles.open.request.v1" })
            assertEquals(code == "NO_SUCH_PROFILE", outcome.refreshProfiles)
        }
        assertEquals("Cyclone is busy; try again in a moment.", OpenRequests.outcomeFor("BUSY", "B").message)
    }

    @Test
    fun openTargetsExcludeTheProfileInFrontAndOfferMainFromAProfile() {
        val api = cyclone().apply { current = C }
        val snapshot = ProfilesSnapshot.parse(api.profiles())
        assertEquals(listOf(B), OpenRequests.targets(snapshot, Placement.Main).map { it.id })
        val inC = Placement.of(12, snapshot)
        assertEquals(listOf("owner", B), OpenRequests.targets(snapshot, inC).map { it.id })
    }

    // ---- pills --------------------------------------------------------------------------------------------------

    @Test
    fun cloaksPillFollowsBindingHealthAndCyclonesRootFacts() {
        val b = CycloneProfile(B, "B", "profile", "ready", 11, listOf(APP))
        assertEquals("Native", CloakPills.forProfile(b, emptyList(), true)!!.label)
        assertEquals("Rooted ✓", CloakPills.forProfile(b, listOf(binding(B, 11, APP)), true)!!.label)
        assertEquals("Rooted", CloakPills.forProfile(b, listOf(binding(B, 11, APP)), null)!!.label)
        assertEquals("Rooted !", CloakPills.forProfile(b, listOf(binding(B, 11, APP)), false)!!.label)
        val broken = CloakPills.forProfile(b, listOf(binding(B, 11, APP), binding(B, 11, APP2, state = "module missing")), true)!!
        assertEquals("Rooted !", broken.label)
        assertEquals("module missing", broken.reason)
        assertNull(CloakPills.forProfile(b.copy(state = "setting_up"), listOf(binding(B, 11, APP)), true))
        assertNull(CloakPills.forProfile(b.copy(state = "in_trash"), listOf(binding(B, 11, APP)), true))
        assertEquals("Native", CloakPills.forProfile(b, listOf(binding(B, 11, APP, enabled = false)), true)!!.label)
    }

    @Test
    fun healthMapsOntoCyclonesFourStates() {
        assertEquals("ready", CloakHealth.cycloneState("ready"))
        assertEquals("unknown", CloakHealth.cycloneState("pending"))
        assertEquals("failed", CloakHealth.cycloneState("missing"))
        assertEquals("failed", CloakHealth.cycloneState("Zygisk disabled"))
        assertEquals("degraded", CloakHealth.cycloneState("root approval needed"))
        assertEquals("degraded", CloakHealth.cycloneState("module update needs reboot"))
        assertEquals("failed", CloakHealth.worst(listOf("ready", "degraded", "failed")))
        for (code in RootDoctorCode.values()) {
            val state = CloakHealth.cycloneState(CloakResolver.localState(RootDoctorResult(code)))
            assertTrue(state in setOf("ready", "unknown", "degraded", "failed"))
        }
    }

    @Test
    fun rootFactsIgnoreUnknownFields() {
        val facts = CloakPills.rootFacts(JSONObject().put("version", 1).put("future", 1).put("profiles", JSONArray()
            .put(JSONObject().put("id", B).put("rootProven", true).put("extra", "x"))
            .put(JSONObject().put("id", C).put("rootProven", JSONObject.NULL))))
        assertEquals(mapOf(B to true, C to null), facts)
    }
}
