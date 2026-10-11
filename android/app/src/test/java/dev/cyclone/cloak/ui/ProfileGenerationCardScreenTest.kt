package dev.cyclone.cloak.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.cyclone.cloak.forge.ProfileValidator
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
class ProfileGenerationCardScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun selectionGeneratesOnceAndRunningDisablesFurtherTaps() {
        val result = mutableStateOf<ProfileGeneration.Result?>(null)
        val selected = mutableListOf<String>()
        compose.setContent {
            CloakTheme {
                ProfileGenerationCard(listOf(TestPhones["pixel_7"], TestPhones["pixel_8"]), false, result.value,
                    onGenerate = { selected += it; result.value = ProfileGeneration.Result.Running }, onDismiss = {})
            }
        }
        compose.onNodeWithText("Pixel 8").performClick().assertIsSelected()
        compose.onNodeWithText("Generate profile").assertIsEnabled().performClick()
        assertEquals(listOf("pixel_8"), selected)
        compose.onNodeWithText("Generating profile…").assertIsNotEnabled()
        compose.onNodeWithText("Pixel 7").assertIsNotEnabled()
    }

    @Test
    fun validationFailureShowsTheFieldReasonAndDoesNotClaimSaved() {
        val result = ProfileGeneration.Result.Rejected(listOf(
            ProfileValidator.Finding("error", "DISPLAY_FIELD", "display.width must be a positive integer", "display.width"),
        ))
        compose.setContent {
            CloakTheme { ProfileGenerationCard(listOf(TestPhones["pixel_7"]), false, result, {}, {}) }
        }
        compose.onNodeWithText("Not saved").assertIsDisplayed()
        compose.onNodeWithText("Validation failed. Fix the phone template before generating again.").assertIsDisplayed()
        compose.onNodeWithText("• display.width must be a positive integer").assertIsDisplayed()
        compose.onNodeWithText("Generate profile").assertIsEnabled()
    }

    @Test
    fun savedAndStorageFailureStatesAreExplicitAndDismissible() {
        val result = mutableStateOf<ProfileGeneration.Result?>(ProfileGeneration.Result.Saved("id", "Pixel 7 profile", emptyList()))
        compose.setContent {
            CloakTheme { ProfileGenerationCard(listOf(TestPhones["pixel_7"]), false, result.value, {}, { result.value = null }) }
        }
        compose.onNodeWithText("Saved Pixel 7 profile. Find it in Identities.").assertIsDisplayed()
        compose.runOnIdle { result.value = ProfileGeneration.Result.Failed("SAVE_FAILED", "Could not save the profile. Check local storage and try again.") }
        compose.onNodeWithText("Failed").assertIsDisplayed()
        compose.onNodeWithText("Could not save the profile. Check local storage and try again.").assertIsDisplayed()
        compose.onNodeWithText("Dismiss result").performClick()
        compose.runOnIdle { assertEquals(null, result.value) }
    }

    @Test
    fun missingTemplatesAndBusyWorkDisableGeneration() {
        val busy = mutableStateOf(false)
        val phones = mutableStateOf(emptyList<dev.cyclone.cloak.forge.PhoneTemplate>())
        compose.setContent {
            CloakTheme { ProfileGenerationCard(phones.value, busy.value, null, {}, {}) }
        }
        compose.onNodeWithText("No phone templates available. Import or build a phone first.").assertIsDisplayed()
        compose.onNodeWithText("Generate profile").assertIsNotEnabled()
        compose.runOnIdle { phones.value = listOf(TestPhones["pixel_7"]); busy.value = true }
        compose.onNodeWithText("Generate profile").assertIsNotEnabled()
    }
}
