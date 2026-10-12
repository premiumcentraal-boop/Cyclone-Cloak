package dev.cyclone.cloak.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import dev.cyclone.cloak.cyclone.CycloneProfile
import dev.cyclone.cloak.data.CloakBinding
import dev.cyclone.cloak.forge.CloakForge
import dev.cyclone.cloak.forge.TestPhones
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h900dp-mdpi")
class BindingFlowScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val profile = CycloneProfile("Cyclone_bbbbbbbbbbbbbbbb", "Work", "profile", "ready", 11, listOf("com.example.app"))
    private fun input(): BindingInputs {
        val identity = CloakForge.forgeProfile("Pixel profile", "cd".repeat(32), TestPhones["pixel_7"])
        return BindingInputs(listOf(profile), listOf(identity.getString("id") to identity), emptyList(), true, null)
    }
    private fun review(input: BindingInputs): BindingDraft {
        val draft = BindingDraft(BindingStep.REVIEW, profile.id, setOf("com.example.app"), input.identities.first().first)
        return draft.copy(reviewStamp = BindingFlow.stamp(draft, input))
    }
    @Test fun selectionStepsLeadToSummaryAndExplicitConfirmation() {
        val input = input(); val draft = mutableStateOf(BindingDraft()); var confirmed = 0
        compose.setContent {
            CloakTheme {
                BindingFlowScreen(draft.value, input, emptyList(), false,
                    onProfile = { draft.value = BindingFlow.chooseProfile(draft.value, it) },
                    onApps = { draft.value = BindingFlow.selectApps(draft.value, it) },
                    onIdentity = { draft.value = draft.value.copy(identityId = it) }, onGenerate = {},
                    onNext = { draft.value = BindingFlow.next(draft.value, input) }, onBack = {},
                    onAcknowledge = { draft.value = draft.value.copy(acknowledged = it) },
                    onConfirm = { confirmed++; draft.value = draft.value.copy(step = BindingStep.RESULT,
                        outcome = BindingOutcome(true, true, "The binding is ready.")) }, onRetry = {}, onRefresh = {}, onClose = {})
            }
        }
        compose.onNodeWithText("Continue").assertIsNotEnabled()
        compose.onNodeWithText("Work · user 11 · ready").performClick()
        compose.onNodeWithText("Continue").performClick()
        compose.onNodeWithText("com.example.app").performClick()
        compose.onNodeWithText("Continue").performClick()
        compose.onNodeWithText("Pixel profile").performClick()
        compose.onNodeWithText("Continue").performClick()
        compose.onNodeWithText("Final binding review").assertIsDisplayed()
        compose.onNodeWithText("Cyclone profile: Work").assertIsDisplayed()
        compose.onNodeWithText("App package: com.example.app").assertIsDisplayed()
        compose.onNodeWithText("Android user: 11").assertIsDisplayed()
        compose.onNodeWithText("Device profile: Pixel profile").assertIsDisplayed()
        assertEquals(0, confirmed)
        // Valid profiles can still carry nonblocking coherence warnings.
        compose.onAllNodes(isToggleable()).fetchSemanticsNodes().takeIf { it.isNotEmpty() }?.let {
            compose.onNode(isToggleable()).performScrollTo().performClick()
        }
        compose.onNodeWithText("Confirm binding").performScrollTo().assertIsEnabled().performClick()
        compose.onNodeWithText("Binding ready").assertIsDisplayed()
        assertEquals(1, confirmed)
    }
    @Test fun sharedIdentityWarningRequiresAcknowledgement() {
        val original = input(); val selected = review(original)
        val input = original.copy(bindings = listOf(CloakBinding(profile.id, 11, "com.example.other", selected.identityId!!, 0, true, 100)))
        val draft = mutableStateOf(review(input)); var confirmed = 0
        compose.setContent {
            CloakTheme { BindingFlowScreen(draft.value, input, emptyList(), false, {}, {}, {}, {}, {}, {},
                { draft.value = draft.value.copy(acknowledged = it) }, { confirmed++ }, {}, {}, {}) }
        }
        compose.onNodeWithText("identity: This compatibility profile is already bound elsewhere. Choose or generate another profile for separate identities, or acknowledge sharing it.")
            .performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Confirm binding").performScrollTo().assertIsNotEnabled()
        compose.onNode(isToggleable()).performScrollTo().performClick()
        compose.onNodeWithText("Confirm binding").performScrollTo().assertIsEnabled().performClick()
        assertEquals(1, confirmed)
    }
    @Test fun unreadyProfileShowsReasonAndBlocksConfirmation() {
        val original = input(); val input = original.copy(profiles = listOf(profile.copy(state = "in_trash", androidUserId = null)))
        var confirmed = 0
        compose.setContent {
            CloakTheme { BindingFlowScreen(review(input), input, emptyList(), false, {}, {}, {}, {}, {}, {}, {}, { confirmed++ }, {}, {}, {}) }
        }
        compose.onNodeWithText("profile: This profile has no Android user assignment. Finish setup in Cyclone, then refresh.")
            .performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Confirm binding").performScrollTo().assertIsNotEnabled()
        assertEquals(0, confirmed)
    }
    @Test fun savedFailureOffersRetryAndDoesNotConfirmAgain() {
        val input = input(); val draft = mutableStateOf(review(input).copy(step = BindingStep.RESULT,
            outcome = BindingOutcome(false, true, "Binding saved locally. Cyclone rate-limited the update.")))
        var retried = 0; var confirmed = 0
        compose.setContent {
            CloakTheme { BindingFlowScreen(draft.value, input, emptyList(), false, {}, {}, {}, {}, {}, {}, {}, { confirmed++ },
                { retried++; draft.value = draft.value.copy(outcome = BindingOutcome(true, true, "The binding is ready.")) }, {}, {}) }
        }
        compose.onNodeWithText("Saved · needs attention").assertIsDisplayed()
        compose.onNodeWithText("Retry reconciliation").performClick()
        compose.onNodeWithText("Binding ready").assertIsDisplayed()
        compose.onNodeWithText("Retry reconciliation").assertDoesNotExist()
        assertEquals(1, retried); assertEquals(0, confirmed)
    }
    @Test fun runningAndChangedReviewDisableConfirmationAndOfferFreshReview() {
        val original = input(); val draft = mutableStateOf(review(original).copy(running = true))
        val input = mutableStateOf(original)
        compose.setContent {
            CloakTheme { BindingFlowScreen(draft.value, input.value, emptyList(), false, {}, {}, {}, {},
                { draft.value = draft.value.copy(reviewStamp = BindingFlow.stamp(draft.value, input.value), acknowledged = false) },
                {}, {}, {}, {}, {}, {}) }
        }
        compose.onNodeWithText("Confirm binding").performScrollTo().assertIsNotEnabled()
        compose.runOnIdle { draft.value = draft.value.copy(running = false); input.value = original.copy(profiles = listOf(profile.copy(androidUserId = 21))) }
        compose.onNodeWithText("Confirm binding").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Review latest summary").performScrollTo().performClick()
        compose.onNodeWithText("Android user: 21").performScrollTo().assertIsDisplayed()
    }
    @Test fun allAppsAndIndividualCheckboxesCarryTheFullSelectionToReview() {
        val original = input()
        val input = original.copy(profiles = listOf(profile.copy(packages = listOf("com.example.app", "com.example.other"))))
        val draft = mutableStateOf(BindingDraft(step = BindingStep.APP, profileId = profile.id, identityId = input.identities.single().first))
        compose.setContent {
            CloakTheme { BindingFlowScreen(draft.value, input, emptyList(), false, {},
                { draft.value = BindingFlow.selectApps(draft.value, it) }, {}, {},
                { draft.value = BindingFlow.next(draft.value, input) }, {},
                { draft.value = draft.value.copy(acknowledged = it) }, {}, {}, {}, {}) }
        }
        compose.onNodeWithText("Continue").assertIsNotEnabled()
        compose.onNodeWithText("All apps (2)").performClick().assertIsOn()
        compose.onNodeWithText("com.example.app").assertIsOn()
        compose.onNodeWithText("com.example.other").assertIsOn().performClick().assertIsOff()
        compose.onNodeWithText("All apps (2)").assertIsOff().performClick()
        compose.onNodeWithText("Continue").performClick()
        compose.onNodeWithText("Step 3 of 5 · Choose device profile").assertIsDisplayed()
        compose.onNodeWithText("Selected device profile: Pixel profile").assertIsDisplayed()
        compose.onNodeWithText("Continue").performScrollTo().performClick()
        compose.onNodeWithText("All apps (2)").assertIsDisplayed()
        compose.onNodeWithText("App package: com.example.app").assertIsDisplayed()
        compose.onNodeWithText("App package: com.example.other").assertIsDisplayed()
        compose.onNodeWithText("Device profile: Pixel profile").assertIsDisplayed()
    }
    @Test fun profilesEntryRequiresVisibleDeviceProfileSelectionForMultipleApps() {
        val original = input()
        val input = original.copy(profiles = listOf(profile.copy(packages = listOf("com.example.app", "com.example.other"))))
        val draft = mutableStateOf(BindingDraft())
        compose.setContent {
            CloakTheme { BindingFlowScreen(draft.value, input, emptyList(), false,
                { draft.value = BindingFlow.chooseProfile(draft.value, it) },
                { draft.value = BindingFlow.selectApps(draft.value, it) },
                { draft.value = draft.value.copy(identityId = it) }, {},
                { draft.value = BindingFlow.next(draft.value, input) }, {}, {}, {}, {}, {}, {}) }
        }
        compose.onNodeWithText("Work · user 11 · ready").performClick()
        compose.onNodeWithText("Continue").performClick()
        compose.onNodeWithText("All apps (2)").performClick()
        compose.onNodeWithText("Continue").performClick()
        compose.onNodeWithText("Choose a saved device profile before review.").assertIsDisplayed()
        compose.onNodeWithText("Continue").assertIsNotEnabled()
        compose.onNodeWithText("Pixel profile").assertIsDisplayed().performClick()
        compose.onNodeWithText("Continue").performClick()
        compose.onNodeWithText("All apps (2)").assertIsDisplayed()
        compose.onNodeWithText("Device profile: Pixel profile").assertIsDisplayed()
    }
    @Test fun allAppsToggleClearsSelectionAndBusyStateDisablesAppChanges() {
        val original = input()
        val input = original.copy(profiles = listOf(profile.copy(packages = listOf("com.example.app", "com.example.other"))))
        val busy = mutableStateOf(false)
        val draft = mutableStateOf(BindingDraft(step = BindingStep.APP, profileId = profile.id))
        compose.setContent {
            CloakTheme { BindingFlowScreen(draft.value, input, emptyList(), busy.value, {},
                { draft.value = BindingFlow.selectApps(draft.value, it) }, {}, {}, {}, {}, {}, {}, {}, {}, {}) }
        }
        compose.onNodeWithText("All apps (2)").performClick().performClick().assertIsOff()
        compose.onNodeWithText("com.example.app").assertIsOff()
        compose.onNodeWithText("com.example.other").assertIsOff()
        compose.onNodeWithText("Continue").assertIsNotEnabled()
        compose.runOnIdle { busy.value = true }
        compose.onNodeWithText("All apps (2)").assertIsNotEnabled()
        compose.onNodeWithText("com.example.app").assertIsNotEnabled()
    }

}
