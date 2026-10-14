package dev.cyclone.cloak.ui

import android.app.Application
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import dev.cyclone.cloak.diagnostics.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class BindingRunCardTest {
    @get:Rule val compose = createComposeRule()
    private val run = BindingRun(timestamp = 100, operation = RunOperation.BIND, targets = listOf(
        RunTarget("Cyclone_0123456789abcdef", 16, listOf("org.example.app"), "0123456789abcdef")),
        outcome = RunOutcome.FAILED, reasons = listOf("ROOT_TIMEOUT"), durationMs = 42)
    @Test fun failureExpandsToTargetAndReasonAndRetryAction() {
        var retries = 0
        compose.setContent { CloakTheme { BindingRunCard(run, true, { retries++ }, {}) } }
        compose.onNodeWithText("bind · failed").assertIsDisplayed()
        compose.onNodeWithText("Review run details").performClick()
        compose.onNodeWithText("Android user: 16", substring = true).assertIsDisplayed()
        compose.onNodeWithText("org.example.app", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Reasons: ROOT_TIMEOUT").assertIsDisplayed()
        compose.onNodeWithText("Retry reconciliation").performClick()
        assertEquals(1, retries)
    }
    @Test fun readyRowHasExportButNoRetry() {
        var exports = 0
        compose.setContent { CloakTheme { BindingRunCard(run.copy(outcome = RunOutcome.READY), true, {}, { exports++ }) } }
        compose.onNodeWithText("Retry reconciliation").assertDoesNotExist()
        compose.onNodeWithText("Download debug bundle").performClick()
        assertEquals(1, exports)
    }
    @Test fun needsAttentionShowsStateAndBusyDisablesActions() {
        compose.setContent { CloakTheme { BindingRunCard(run.copy(outcome = RunOutcome.NEEDS_ATTENTION), false, {}, {}) } }
        compose.onNodeWithText("bind · needs-attention").assertIsDisplayed()
        compose.onNodeWithText("Retry reconciliation").assertIsNotEnabled()
        compose.onNodeWithText("Download debug bundle").assertIsNotEnabled()
    }
}
