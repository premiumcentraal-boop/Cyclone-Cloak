package dev.cyclone.cloak.ui

import dev.cyclone.cloak.cyclone.CycloneGate
import dev.cyclone.cloak.cyclone.ProfilesSnapshot
import dev.cyclone.cloak.cyclone.SyncReport
import dev.cyclone.cloak.data.CloakBinding
import dev.cyclone.cloak.forge.CloakForge
import dev.cyclone.cloak.forge.TestPhones
import dev.cyclone.cloak.root.RootDoctorCode
import dev.cyclone.cloak.root.RootDoctorResult
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HomeAttentionTest {
    private val b = "Cyclone_bbbbbbbbbbbbbbbb"
    private val gate = CycloneGate(true, 3, setOf("profiles.read", "profiles.apps.read", "profiles.config"), emptySet())
    private val snapshot = ProfilesSnapshot.parse(JSONObject().put("current", "owner").put("profiles", JSONArray()
        .put(JSONObject().put("id", "owner").put("label", "This phone").put("kind", "owner").put("state", "ready"))
        .put(JSONObject().put("id", b).put("label", "Work").put("kind", "profile").put("state", "ready").put("androidUserId", 11))))
    private val work = CloakForge.forgeProfile("Work Pixel", "ab".repeat(32), TestPhones["pixel_7"])

    private fun ready(vararg bindings: CloakBinding) = CloakState(
        phones = TestPhones.all,
        identities = listOf(work.getString("id") to work),
        bindings = bindings.toList(),
        report = SyncReport("Connected", "", gate, snapshot),
        rootDoctor = RootDoctorResult(RootDoctorCode.READY),
    )

    private fun binding(state: String = "ready", profile: String = b) = CloakBinding(profile, 11, "com.example.bank", work.getString("id"), 0, true, 1, state)

    @Test fun loadingUntilCycloneAnswers() {
        assertTrue(HomeAttention.loading(CloakState()))
        assertTrue(HomeAttention.items(CloakState()).none { it.title.startsWith("Cyclone") })
    }

    @Test fun readyStateNeedsNothing() {
        val state = ready(binding())
        assertEquals(emptyList<AttentionItem>(), HomeAttention.items(state))
        assertNull(HomeAttention.nextStep(state))
    }

    @Test fun connectorProblemsPointToProfiles() {
        val state = ready(binding()).copy(report = SyncReport.failed("NOT_APPROVED", null))
        val item = HomeAttention.items(state).first()
        assertEquals("Cyclone: Needs approval", item.title)
        assertEquals(CloakDestination.PROFILES, item.destination)
    }

    @Test fun permissionBlockedIsShown() {
        val state = ready(binding()).copy(report = SyncReport("Connected", "Allow it", gate.copy(granted = emptySet()), snapshot))
        assertEquals("Binding permissions not granted", HomeAttention.items(state).first().title)
    }

    @Test fun rootAndBindingProblemsAreListed() {
        val state = ready(binding("module update needs reboot"), binding(profile = "Cyclone_gone"))
            .copy(rootDoctor = RootDoctorResult(RootDoctorCode.MODULE_MISSING))
        val titles = HomeAttention.items(state).map { it.title }
        assertTrue(titles.toString(), "Root Doctor: Module missing" in titles)
        assertTrue(titles.toString(), "1 bound app isn't ready" in titles)
        assertTrue(titles.toString(), "1 binding points to a missing Cyclone profile" in titles)
    }

    @Test fun nextStepFollowsCreationFlow() {
        val empty = ready().copy(identities = emptyList())
        assertEquals(CloakDestination.IDENTITIES, HomeAttention.nextStep(empty)?.destination)
        assertEquals(CloakDestination.PROFILES, HomeAttention.nextStep(ready())?.destination)
    }
}
