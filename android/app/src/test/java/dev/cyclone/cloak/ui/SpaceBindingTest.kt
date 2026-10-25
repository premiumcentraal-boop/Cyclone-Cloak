package dev.cyclone.cloak.ui

import dev.cyclone.cloak.cyclone.CycloneProfile
import dev.cyclone.cloak.cyclone.ProfilesSnapshot
import dev.cyclone.cloak.data.CloakBinding
import dev.cyclone.cloak.data.CloakSpace
import dev.cyclone.cloak.forge.CloakForge
import dev.cyclone.cloak.forge.TestPhones
import dev.cyclone.cloak.root.RootDoctorCode
import dev.cyclone.cloak.root.RootDoctorResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpaceBindingTest {
    private val identity = CloakForge.forgeProfile("Pixel profile", "cd".repeat(32), TestPhones["pixel_7"])
    private val identityId = identity.getString("id")
    private val space = CloakSpace("Cloak_0123456789abcdef", "Work", 10, listOf("com.example.app"), identityId,
        CloakSpace.STATE_READY, 1L, 1L)
    private val creating = space.copy(id = "Cloak_fedcba9876543210", state = CloakSpace.STATE_CREATING)
    private val cyclone = CycloneProfile("Cyclone_bbbbbbbbbbbbbbbb", "Profile B", "profile", "ready", 11, listOf("com.example.app"))

    private fun input(canBind: Boolean, ownProfileId: String? = null) = BindingInputs(
        listOf(cyclone) + BindingFlow.spaceProfiles(listOf(space, creating)),
        listOf(identityId to identity), emptyList(), canBind, ownProfileId,
    )

    private fun spaceDraft(packages: Set<String> = setOf("com.example.app")) = BindingDraft(
        step = BindingStep.APP, profileId = space.id, packageNames = packages, identityId = identityId,
    )

    @Test
    fun onlyReadySpacesBecomeBindingTargets() {
        val targets = BindingFlow.spaceProfiles(listOf(space, creating))
        assertEquals(listOf(space.id), targets.map { it.id })
        assertTrue(targets.single().bindable)
        assertTrue(targets.single().isSpace)
    }

    @Test
    fun aSpaceIsNeverPartOfCyclonesRegistry() {
        assertTrue(ProfilesSnapshot(null, listOf(CycloneProfile.forSpace(space))).registry.isEmpty())
    }

    @Test
    fun aSpaceBindingDoesNotNeedTheConnectorButACycloneOneDoes() {
        val spaceFindings = BindingFlow.findings(spaceDraft(), input(canBind = false)).map { it.code }
        assertFalse(spaceFindings.contains("CONNECTOR_NOT_READY"))
        val cycloneFindings = BindingFlow.findings(
            BindingDraft(step = BindingStep.APP, profileId = cyclone.id, packageNames = setOf("com.example.app")),
            input(canBind = false),
        ).map { it.code }
        assertTrue(cycloneFindings.contains("CONNECTOR_NOT_READY"))
    }

    @Test
    fun aSpaceCannotBeBoundFromInsideAnotherProfile() {
        val codes = BindingFlow.findings(spaceDraft(), input(canBind = true, ownProfileId = cyclone.id)).map { it.code }
        assertTrue(codes.contains("PROFILE_OUT_OF_SCOPE"))
    }

    @Test
    fun aSpaceOnlyOffersItsOwnApps() {
        val codes = BindingFlow.findings(spaceDraft(setOf("com.example.other")), input(canBind = true)).map { it.code }
        assertTrue(codes.contains("APP_MISSING"))
    }

    @Test
    fun aSpaceIsCompleteOnceItsAppsPublishAndReportReady() {
        val draft = spaceDraft()
        val records = listOf(CloakBinding(space.id, 10, "com.example.app", identityId, 1, true, 1L, "ready"))
        val outcome = BindingFlow.spaceCompletion(draft, RootDoctorResult(RootDoctorCode.READY), records)
        assertTrue(outcome.ready)
        assertTrue(outcome.saved)
    }

    @Test
    fun aSpaceIsNotReadyWhenPublicationFails() {
        val draft = spaceDraft()
        val records = listOf(CloakBinding(space.id, 10, "com.example.app", identityId, 1, true, 1L, "ready"))
        val outcome = BindingFlow.spaceCompletion(draft, RootDoctorResult(RootDoctorCode.PUBLISH_FAILED), records)
        assertFalse(outcome.ready)
        assertTrue(outcome.saved)
    }

    @Test
    fun aSpaceIsNotReadyWhileAnAppStillReportsPending() {
        val draft = spaceDraft()
        val records = listOf(CloakBinding(space.id, 10, "com.example.app", identityId, 1, true, 1L, "pending"))
        assertFalse(BindingFlow.spaceCompletion(draft, RootDoctorResult(RootDoctorCode.READY), records).ready)
    }

    @Test
    fun aSpaceBindingThatWasNotSavedIsReportedAsSuch() {
        val outcome = BindingFlow.spaceCompletion(spaceDraft(), RootDoctorResult(RootDoctorCode.READY), emptyList())
        assertFalse(outcome.saved)
        assertFalse(outcome.ready)
    }
}
