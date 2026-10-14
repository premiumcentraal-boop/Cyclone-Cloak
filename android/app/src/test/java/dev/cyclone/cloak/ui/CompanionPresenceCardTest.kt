package dev.cyclone.cloak.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import dev.cyclone.cloak.cyclone.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h900dp-mdpi")
class CompanionPresenceCardTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun missingCopyShowsActionableReasonAndCheckAction() {
        var checked = false
        compose.setContent { CloakTheme { CompanionPresenceCard(listOf(CompanionPresence("work", "Work", 16,
            CompanionFacts(installed = PresenceCheck.NO))), false) { checked = true } } }
        compose.onNodeWithText("Work · Android user 16").assertIsDisplayed()
        compose.onNodeWithText("missing").assertIsDisplayed()
        compose.onNodeWithText("Open this profile through Cyclone to provision Cloak, or add it through the profile app selection.").assertIsDisplayed()
        compose.onNodeWithText("Check companion copies").performClick()
        assertTrue(checked)
    }
    @Test fun stoppedUserIsShownSeparatelyFromMissingPackage() {
        compose.setContent { CloakTheme { CompanionPresenceCard(listOf(CompanionPresence("work", "Work", 16,
            CompanionFacts(PresenceCheck.YES, PresenceCheck.NO, PresenceCheck.NO))), true) {} } }
        compose.onNodeWithText("not launchable").assertIsDisplayed()
        compose.onNodeWithText("Checking companion copies…").assertIsNotEnabled()
        compose.onNodeWithText("Writable: not verified", substring = true).assertIsDisplayed()
    }
}
