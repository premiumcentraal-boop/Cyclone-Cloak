package dev.cyclone.cloak.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import dev.cyclone.cloak.forge.PhoneDraft
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
class PhoneEditorScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val valid = PhoneDraft.from(TestPhones["pixel_7"])

    @Test
    fun summaryTracksEditsAndUsesTheSavedLabelFallback() {
        var draft by mutableStateOf(valid.copy(label = ""))
        compose.setContent {
            CloakTheme { PhoneEditorScreen(PhoneEditor(draft, true), false, { draft = it }, {}, {}) }
        }
        compose.onNodeWithText("Profile summary").assertIsDisplayed()
        compose.onNode(hasText(valid.model) and hasAnyAncestor(hasTestTag("phone-summary"))).assertIsDisplayed()
        compose.onNode(hasText("Name in Cloak") and hasSetTextAction())
            .performScrollTo().performTextReplacement("Travel phone")
        compose.onNodeWithText("Profile summary").performScrollTo()
        compose.onNode(hasText("Travel phone") and hasAnyAncestor(hasTestTag("phone-summary"))).assertIsDisplayed()
        assertEquals("Travel phone", draft.toTemplate().label)
    }

    @Test
    fun blockingErrorAppearsAtItsFieldAndBothSaveActionsRecoverAfterCorrection() {
        var draft by mutableStateOf(valid)
        var saved = 0
        compose.setContent {
            CloakTheme { PhoneEditorScreen(PhoneEditor(draft, true), false, { draft = it }, { saved++ }, {}) }
        }
        val sdk = compose.onNode(hasText("SDK level") and hasSetTextAction())
        sdk.performScrollTo().performTextReplacement("bad")
        sdk.assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Error))
        val finding = draft.findings().first { it.field == "device.sdk_int" && it.isError }
        compose.onNodeWithText("Error: ${finding.message}", useUnmergedTree = true)
            .performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Save").assertIsNotEnabled()
        compose.onNodeWithText("Fix the marked fields to save").performScrollTo().assertIsNotEnabled()
        sdk.performScrollTo().performTextReplacement(valid.sdkInt)
        compose.onNodeWithText("Save").assertIsEnabled()
        compose.onNodeWithText("Save phone").performScrollTo().assertIsEnabled().performClick()
        assertEquals(1, saved)
    }

    @Test
    fun warningsRemainVisibleAndAllowSaving() {
        val draft = valid.copy(refreshRate = "300")
        compose.setContent {
            CloakTheme { PhoneEditorScreen(PhoneEditor(draft, false), false, {}, {}, {}) }
        }
        val warning = draft.findings().single { it.field == "display.refresh_rate_hz" }
        compose.onNodeWithText("Save").assertIsEnabled()
        compose.onNode(hasText("Refresh (Hz)") and hasSetTextAction()).performScrollTo()
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Error))
        compose.onNodeWithText("Check: ${warning.message}", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Save phone").performScrollTo().assertIsEnabled()
        compose.onNodeWithText("Identities already made from this phone keep their own copy; changes apply to new identities.")
            .performScrollTo().assertIsDisplayed()
    }

    @Test
    fun busyDisablesBothSaveActions() {
        compose.setContent {
            CloakTheme { PhoneEditorScreen(PhoneEditor(valid, true), true, {}, {}, {}) }
        }
        compose.onNodeWithText("Save").assertIsNotEnabled()
        compose.onNodeWithText("Please wait…").performScrollTo().assertIsNotEnabled()
    }
}
