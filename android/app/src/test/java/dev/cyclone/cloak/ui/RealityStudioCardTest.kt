package dev.cyclone.cloak.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
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
class RealityStudioCardTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val phone = TestPhones["pixel_7"]
    @Test fun repairPreviewNeedsConfirmationAndDoesNotSaveOnTap() {
        val p = CloakForge.forgeProfile("Studio", "ab".repeat(32), phone, "0.4")
        p.getJSONObject("reality").getJSONObject("memory").getJSONObject("values").remove("ram_gib")
        var saved = 0
        compose.setContent { CloakTheme { Column(Modifier.verticalScroll(rememberScrollState())) {
            RealityStudioCard(p, listOf(phone), false) { saved++ }
        } } }
        compose.onNodeWithText("Reality Studio").performClick()
        compose.onNodeWithText("Preview missing-field repair").performScrollTo().performClick()
        assertEquals(0, saved)
        compose.onNodeWithText("Review Reality changes (3)").performScrollTo().performClick()
        compose.onNodeWithText("Confirm local Reality changes").assertIsDisplayed()
        compose.onNodeWithText("Confirm and save identity").performClick()
        assertEquals(1, saved)
    }
    @Test fun radioValuesAreHiddenUntilExplicitRevealAndLegacyIsReadOnly() {
        val p = CloakForge.forgeProfile("Studio", "ab".repeat(32), phone, "0.4")
        compose.setContent { CloakTheme { Column(Modifier.verticalScroll(rememberScrollState())) {
            RealityStudioCard(p, listOf(phone), false) {}
        } } }
        compose.onNodeWithText("Reality Studio").performClick()
        compose.onNodeWithText("radio", useUnmergedTree = true).performScrollTo().performClick()
        compose.onNodeWithText(p.getJSONObject("reality").getJSONObject("radio").getJSONObject("values").getString("wifi_name"), substring = true).assertDoesNotExist()
        compose.onNodeWithText("Edit wifi_name").performScrollTo().performClick()
        compose.onNodeWithText("Reveal private value to edit").assertIsDisplayed()
        compose.onNodeWithText("Validate draft edit").assertIsNotEnabled()
    }
    @Test fun oldIdentityIsNotMigratedByOpeningStudio() {
        val p = CloakForge.forgeProfile("Old", "ab".repeat(32), phone)
        compose.setContent { CloakTheme { RealityStudioCard(p, listOf(phone), false) {} } }
        compose.onNodeWithText("Reality Studio").performClick()
        compose.onNodeWithText("Schema 0.3: read-only.", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Preview missing-field repair").assertDoesNotExist()
    }
    @Test fun contradictoryScalarShowsExactReasonWithoutSave() {
        val p = CloakForge.forgeProfile("Studio", "ab".repeat(32), phone, "0.4")
        var saves = 0
        compose.setContent { CloakTheme { Column(Modifier.verticalScroll(rememberScrollState())) {
            RealityStudioCard(p, listOf(phone), false) { saves++ }
        } } }
        compose.onNodeWithText("Reality Studio").performClick()
        compose.onNodeWithText("memory", useUnmergedTree = true).performScrollTo().performClick()
        compose.onNodeWithText("Edit ram_gib").performScrollTo().performClick()
        compose.onNodeWithText("Value").performTextReplacement("2")
        compose.onNodeWithText("Validate draft edit").performClick()
        compose.onAllNodesWithText("REALITY_STORAGE", substring = true).onFirst().assertExists()
        assertEquals(0, saves)
    }
}
