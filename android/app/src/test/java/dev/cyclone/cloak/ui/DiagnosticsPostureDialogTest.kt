package dev.cyclone.cloak.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import dev.cyclone.cloak.data.*
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
class DiagnosticsPostureDialogTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private fun preview(canSave: Boolean = true) = DiagnosticsPosturePreview(
        CompatibilityPostureReport("runtime-diagnostics", "2026-10-13T12:00:00Z", "profile-1", "a".repeat(64), null,
            listOf(PostureReportCheck("read:wifi.ssid", PostureCheckStatus.WARNING, "Diagnostics proof: INCOMPLETE."))),
        PostureResult(reason = "Local evidence only; no platform verdict."), listOf("Stale report: diagnostics are older than 7 days."), canSave)
    @Test fun previewRequiresExplicitSaveAndDisplaysEvidenceWarning() {
        var saves = 0
        compose.setContent { CloakTheme { DiagnosticsPostureDialog(preview(), false, { saves++ }, {}) } }
        assertEquals(0, saves)
        compose.onNodeWithText("Preview local compatibility evidence").assertIsDisplayed()
        compose.onNodeWithText("Stale report: diagnostics are older than 7 days.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("read:wifi.ssid: WARNING").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Save local posture").assertIsEnabled().performClick()
        assertEquals(1, saves)
    }
    @Test fun mismatchedProfileCannotSave() {
        compose.setContent { CloakTheme { DiagnosticsPostureDialog(preview(false), false, {}, {}) } }
        compose.onNodeWithText("Save local posture").assertIsNotEnabled()
        compose.onNodeWithText("Cancel").assertIsEnabled()
    }
    @Test fun busyPreviewDisablesActions() {
        compose.setContent { CloakTheme { DiagnosticsPostureDialog(preview(), true, {}, {}) } }
        compose.onNodeWithText("Save local posture").assertIsNotEnabled()
        compose.onNodeWithText("Cancel").assertIsNotEnabled()
    }
    @Test fun saveErrorIsVisibleAndCancelRemainsAvailable() {
        compose.setContent { CloakTheme { DiagnosticsPostureDialog(preview(), false, {}, {},
            "This exact local posture report was already imported.") } }
        compose.onNodeWithText("This exact local posture report was already imported.").assertIsDisplayed()
        compose.onNodeWithText("Cancel").assertIsEnabled()
    }
}
