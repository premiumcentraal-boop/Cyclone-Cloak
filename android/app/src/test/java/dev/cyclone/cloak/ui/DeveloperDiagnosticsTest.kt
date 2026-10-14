package dev.cyclone.cloak.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import dev.cyclone.cloak.diagnostics.LogBus
import dev.cyclone.cloak.diagnostics.Transition
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h1200dp-mdpi")
class DeveloperDiagnosticsTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun entryOpensPlainPanelWithExportControls() {
        compose.setContent { CloakTheme { DeveloperDiagnosticsEntry(null) } }
        compose.onNodeWithText("Developer diagnostics").performClick()
        compose.onNodeWithText("Subsystem").assertIsDisplayed()
        compose.onNodeWithText("Copy diagnostics").assertIsDisplayed()
        compose.onNodeWithText("Save diagnostics bundle").assertIsDisplayed()
    }
    @Test fun filtersShowExplicitEmptyStateAndCloseWorks() {
        var closed = false
        compose.setContent { CloakTheme { DeveloperDiagnosticsPanel(null) { closed = true } } }
        compose.onNodeWithText("Subsystem").performTextInput("absent-subsystem")
        compose.onNodeWithText("No matching events").assertIsDisplayed()
        compose.onNodeWithText("Close").performClick()
        assertTrue(closed)
    }
    @Test fun exceptionContentsNeverAppearInList() {
        LogBus.event("exceptions", "ui-test", Transition.FAILED, error = IllegalArgumentException("private-account-content"))
        LogBus.registry.flush()
        compose.setContent { CloakTheme { DeveloperDiagnosticsPanel(null) {} } }
        compose.onNodeWithText("Action id").performTextInput("ui-test")
        compose.onNodeWithText("IllegalArgumentException", substring = true).assertIsDisplayed()
        compose.onNodeWithText("private-account-content", substring = true).assertDoesNotExist()
    }
}
