package dev.cyclone.cloak.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import dev.cyclone.cloak.forge.BatchIdentityPlanner
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
class BatchIdentityWorkshopTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun templateSelectionRequiresPreviewAndNeverSavesAutomatically() {
        var previews = 0; var saves = 0
        compose.setContent { CloakTheme {
            BatchIdentityWorkshop(BatchWorkshopState(), listOf(TestPhones["pixel_7"]), false,
                { _, _, _ -> previews++ }, {}, {}, { saves++ }, {}, {}, {})
        } }
        compose.onNodeWithText("Preview drafts").assertIsNotEnabled()
        compose.onNode(isToggleable()).performClick()
        compose.onNodeWithText("Preview drafts").performClick()
        assertEquals(1, previews); assertEquals(0, saves)
    }

    @Test fun reviewHidesIdentifiersAndSaveRequiresSelectionAndConfirmation() {
        val plan = BatchIdentityPlanner().plan(BatchIdentityPlanner.Request(listOf("pixel_7"), 1, "ab".repeat(32)), TestPhones.all, emptyList())
        var saves = 0
        compose.setContent { CloakTheme {
            BatchIdentityWorkshop(BatchWorkshopState(plan), TestPhones.all, false,
                { _, _, _ -> }, {}, {}, { saves++ }, {}, {}, {})
        } }
        compose.onNodeWithText("Batch review").assertIsDisplayed()
        compose.onNodeWithText(plan.drafts.single().chosenSeed).assertDoesNotExist()
        compose.onNodeWithText("Save approved drafts").performScrollTo().assertIsNotEnabled()
        compose.onAllNodes(isToggleable())[0].performScrollTo().performClick()
        compose.onNodeWithText("Save approved drafts").performScrollTo().performClick()
        assertEquals(0, saves)
        compose.onNodeWithText("Save approved drafts?").assertIsDisplayed()
        compose.onNodeWithText("Confirm").performClick()
        assertEquals(1, saves)
    }

    @Test fun failedActionIsVisibleAndDoesNotTriggerSave() {
        var saves = 0
        compose.setContent { CloakTheme {
            BatchIdentityWorkshop(BatchWorkshopState(message = "Malformed batch request."), listOf(TestPhones["pixel_7"]), false,
                { _, _, _ -> }, {}, {}, { saves++ }, {}, {}, {})
        } }
        compose.onNodeWithText("Malformed batch request.").assertIsDisplayed()
        assertEquals(0, saves)
    }
}
