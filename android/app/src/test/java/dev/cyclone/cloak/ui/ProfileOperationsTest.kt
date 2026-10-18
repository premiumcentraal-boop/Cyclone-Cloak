package dev.cyclone.cloak.ui

import dev.cyclone.cloak.cyclone.*
import dev.cyclone.cloak.data.*
import dev.cyclone.cloak.forge.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ProfileOperationsTest {
    @Test fun oneTapBindAllReusesOneDeviceButRequiresReviewForSharingOrMixedDevices() {
        val value = CloakForge.forgeProfile("Device", "ab".repeat(32), TestPhones["pixel_7"])
        val id = value.getString("id")
        val profile = CycloneProfile("Cyclone_bbbbbbbbbbbbbbbb", "Work", "profile", "ready", 11, listOf("com.example.one", "com.example.two"))
        val binding = CloakBinding(profile.id, 11, "com.example.one", id, 0, true, 100)
        val input = BindingInputs(listOf(profile), listOf(id to value), listOf(binding), true, null)
        val draft = ProfileOperations.draft(profile, input, ProfileAction.BIND_ALL)
        assertTrue(ProfileOperations.oneTapBindAll(draft, input))
        val shared = input.copy(bindings = input.bindings + binding.copy(profileId = "Cyclone_cccccccccccccccc"))
        assertFalse(ProfileOperations.oneTapBindAll(draft, shared))
        val mixed = input.copy(bindings = input.bindings + binding.copy(packageName = "com.example.two", cloakProfileId = "other"))
        assertFalse(ProfileOperations.oneTapBindAll(ProfileOperations.draft(profile, mixed, ProfileAction.BIND_ALL), mixed))
    }
    @Test fun repeatedCreationPicksAnUnusedPrefixWithoutChangingExistingNames() {
        val library = listOf(JSONObject().put("name", "Device Pixel 7 1"), JSONObject().put("name", "Device 2 Pixel 7 1"))
        assertEquals("Device 3", ProfileOperations.availablePrefix("", library))
        assertEquals("Team", ProfileOperations.availablePrefix(" Team ", library))
        assertEquals("Device Pixel 7 1", library.first().getString("name"))
    }
    @Test fun readinessNeverInfersLifecycleOrCompatibilitySuccessFromLocalCoherence() {
        val value = CloakForge.forgeProfile("Test device", "ab".repeat(32), TestPhones["pixel_7"])
        val target = CycloneProfile("Cyclone_bbbbbbbbbbbbbbbb", "Work", "profile", "ready", 11, listOf("com.example.one"))
        val state = CloakState(identities = listOf(value.getString("id") to value),
            bindings = listOf(CloakBinding(target.id, 11, "com.example.one", value.getString("id"), 0, true, 100)))
        val readiness = profileReadiness(state, target, "0.11.0-alpha.24", emptyList())
        assertFalse(readiness.ready)
        assertTrue(readiness.rows.single { it.first == "Last successful lifecycle check" }.second.startsWith("Not recorded"))
        assertTrue(readiness.rows.single { it.first == "Compatibility posture" }.second.contains("not recorded"))
        assertTrue(readiness.rows.single { it.first == "Module/app version match" }.second.startsWith("Not checked"))
    }
    @Test fun replacementRejectsInheritedBindingsWithoutWriting() {
        val target = CycloneProfile("Cyclone_bbbbbbbbbbbbbbbb", "Work", "profile", "ready", 11, listOf("com.example.one"))
        val binding = CloakBinding(target.id, 11, "com.example.one", "old", 0, true, 100, origin = CloakBinding.ORIGIN_MAIN)
        val input = BindingInputs(listOf(target), emptyList(), listOf(binding), true, null)
        val draft = ProfileOperations.draft(target, input, ProfileAction.REPLACE)
        assertTrue(BindingFlow.findings(draft, input).any { it.code == "MANAGED_BY_MAIN" && it.isError })
        assertEquals(listOf(binding), input.bindings)
    }
}
