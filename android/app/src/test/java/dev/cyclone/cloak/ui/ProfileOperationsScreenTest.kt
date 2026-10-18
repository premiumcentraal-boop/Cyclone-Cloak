package dev.cyclone.cloak.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import dev.cyclone.cloak.cyclone.CycloneProfile
import dev.cyclone.cloak.data.CloakBinding
import dev.cyclone.cloak.diagnostics.*
import dev.cyclone.cloak.forge.*
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
class ProfileOperationsScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val profile = CycloneProfile("Cyclone_bbbbbbbbbbbbbbbb", "Work", "profile", "ready", 11, listOf("com.example.one"))
    private fun plan() = BatchIdentityPlanner().plan(BatchIdentityPlanner.Request(listOf("pixel_7"), 1, "ab".repeat(32)), TestPhones.all, emptyList())
    @Test fun creationRequiresRealTemplateAndValidCount() {
        var request = ""
        compose.setContent { CloakTheme { CompatibleDeviceScreen(BatchWorkshopState(), listOf(TestPhones["pixel_7"]), emptyList(), false,
            { ids, count, prefix -> request = "${ids.single()}:$count:$prefix" }, {}, { _, _ -> }, {}) } }
        compose.onNodeWithText("Generate for review").assertIsNotEnabled()
        compose.onNodeWithText("Choose a template").performClick()
        compose.onNodeWithText(TestPhones["pixel_7"].label).performClick()
        compose.onNodeWithText("Compatible profiles (1–20)").performTextReplacement("0")
        compose.onNodeWithText("Generate for review").assertIsNotEnabled()
        compose.onNodeWithText("Compatible profiles (1–20)").performTextReplacement("2")
        compose.onNodeWithText("Label prefix (optional)").performTextReplacement("Travel")
        compose.waitForIdle()
        val view = compose.activity.window.decorView
        val bitmap = android.graphics.Bitmap.createBitmap(view.width, view.height, android.graphics.Bitmap.Config.ARGB_8888)
        view.draw(android.graphics.Canvas(bitmap))
        val file = java.io.File("build/screenshots/new-compatible-device.png")
        file.parentFile!!.mkdirs()
        file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        compose.onNodeWithText("Generate for review").performClick()
        assertEquals("pixel_7:2:Travel", request)
    }
    @Test fun bindAllPreselectsEveryAppForReview() {
        val value = plan().drafts.single().profile!!
        val target = profile.copy(packages = listOf("com.example.one", "com.example.two"))
        val input = BindingInputs(listOf(target), listOf(value.getString("id") to value), emptyList(), true, null)
        val draft = ProfileOperations.draft(target, input, ProfileAction.BIND_ALL).copy(step = BindingStep.APP)
        var apps = emptySet<String>()
        compose.setContent { CloakTheme { BindingFlowScreen(draft, input, emptyList(), false, {}, { apps = it }, {}, {}, {}, {}, {}, {}, {}, {}, {}) } }
        compose.onNodeWithText("All apps (2)").assertExists()
        compose.onNodeWithText("2 selected. Choose one or more apps for this device profile.").assertExists()
        compose.onNodeWithText("com.example.two").performClick()
        assertEquals(setOf("com.example.one"), apps)
    }
    private fun blockedTarget(target: CycloneProfile, reason: String) {
        val value = plan().drafts.single().profile!!
        val input = BindingInputs(listOf(target), listOf(value.getString("id") to value), emptyList(), true, null)
        val initial = ProfileOperations.draft(target, input, ProfileAction.BIND_ALL).copy(step = BindingStep.REVIEW, identityId = value.getString("id"), acknowledged = true)
        val draft = initial.copy(reviewStamp = BindingFlow.stamp(initial, input))
        compose.setContent { CloakTheme { BindingFlowScreen(draft, input, emptyList(), false, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}) } }
        compose.onNodeWithText(reason, substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Confirm binding").performScrollTo().assertIsNotEnabled()
    }
    @Test fun missingUserExplainsSetupAndBlocksBinding() = blockedTarget(profile.copy(androidUserId = null), "This profile has no Android user assignment.")
    @Test fun missingAppsExplainsSelectionAndBlocksBinding() = blockedTarget(profile.copy(packages = null), "Choose at least one app or select All apps.")
    @Test fun creationReviewSavesOnlyApprovedSelectionThenOffersAssignment() {
        val plan = plan(); val state = mutableStateOf(BatchWorkshopState(plan)); var saved = 0; var assigned = 0
        compose.setContent { CloakTheme {
            CompatibleDeviceScreen(state.value, TestPhones.all, listOf(profile), false, { _, _, _ -> }, {
                saved++; state.value = state.value.copy(results = listOf(BatchIdentitySave.Item(plan.drafts.single().key, "SAVED", "Saved locally.")))
            }, { _, _ -> assigned++ }, {})
        } }
        compose.onNodeWithText("Quality and validation").assertIsDisplayed()
        compose.onNodeWithText("Save approved profiles").performScrollTo().assertIsNotEnabled()
        compose.onNode(isToggleable()).performScrollTo().performClick()
        compose.onNodeWithText("Save approved profiles").performScrollTo().performClick()
        assertEquals(1, saved)
        compose.onNodeWithText("Choose a saved device").performScrollTo().performClick()
        compose.onAllNodesWithText(plan.drafts.single().label).onLast().performClick()
        compose.onNodeWithText("Choose a Cyclone profile").performScrollTo().performClick()
        compose.onNodeWithText("Work").performClick()
        compose.onNodeWithText("Review assignment").performScrollTo().assertIsEnabled().performClick()
        assertEquals(1, assigned)
    }
    @Test fun invalidDraftBlocksApprovalAndProvidesRetry() {
        val plan = plan(); val invalid = plan.copy(drafts = plan.drafts.map { it.copy(profile = null,
            findings = listOf(ProfileValidator.Finding(ProfileValidator.ERROR, "INVALID", "Choose another real template.", "device"))) })
        var saves = 0; var retries = 0
        compose.setContent { CloakTheme { CompatibleDeviceScreen(BatchWorkshopState(invalid), TestPhones.all, emptyList(), false,
            { _, _, _ -> retries++ }, { saves++ }, { _, _ -> }, {}) } }
        compose.onNode(isToggleable()).assertIsNotEnabled()
        compose.onNodeWithText("Save approved profiles").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Retry generation").performScrollTo().performClick()
        assertEquals(0, saves); assertEquals(1, retries)
    }
    @Test fun replacementShowsBeforeAfterAndRequiresExplicitConfirmation() {
        val old = CloakForge.forgeProfile("Old phone", "cd".repeat(32), TestPhones["pixel_7"])
        val new = CloakForge.forgeProfile("New phone", "ab".repeat(32), TestPhones["pixel_7"])
        val input = BindingInputs(listOf(profile), listOf(old.getString("id") to old, new.getString("id") to new),
            listOf(CloakBinding(profile.id, 11, "com.example.one", old.getString("id"), 0, true, 100)), true, null)
        val initial = ProfileOperations.draft(profile, input, ProfileAction.REPLACE).copy(step = BindingStep.REVIEW, identityId = new.getString("id"))
        val state = mutableStateOf(initial.copy(reviewStamp = BindingFlow.stamp(initial, input))); var confirms = 0
        compose.setContent { CloakTheme { BindingFlowScreen(state.value, input, emptyList(), false, {}, {}, {}, {}, {}, {},
            { state.value = state.value.copy(acknowledged = it) }, { confirms++ }, {}, {}, {}) } }
        compose.onNodeWithText("Before: Old phone: com.example.one").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("After: New phone for 1 apps").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Confirm replacement").performScrollTo().assertIsNotEnabled()
        assertEquals(0, confirms)
        compose.onNode(isToggleable()).performScrollTo().performClick()
        compose.onNodeWithText("Confirm replacement").performScrollTo().assertIsEnabled().performClick()
        assertEquals(1, confirms)
    }
    @Test fun readinessActionsAndHistoryExportAreReachable() {
        var binds = 0; var replaces = 0; var history = 0
        compose.setContent { CloakTheme { ProfileReadinessCard(ProfileReadiness(listOf("Android user mapping" to "Missing"),
            "Finish setup in Cyclone, then refresh.", false), true, { binds++ }, { replaces++ }, { history++ }) } }
        compose.onNodeWithText("Bind all apps").performClick()
        compose.onNodeWithText("Replace device profile").performClick()
        compose.onNodeWithText("Run history").performClick()
        assertEquals(1, binds); assertEquals(1, replaces); assertEquals(1, history)
    }
    @Test fun failedOperationOffersPublicationRetryAndDiagnosticExport() {
        var retry = 0; var export = 0
        val run = BindingRun(operation = RunOperation.REPLACE, targets = listOf(RunTarget(profile.id, 11, listOf("com.example.one"), "new",
            previousDeviceProfiles = mapOf("com.example.one" to "old"))), outcome = RunOutcome.FAILED, reasons = listOf("PUBLISH_FAILED"))
        compose.setContent { CloakTheme { BindingRunCard(run, true, { retry++ }, { export++ }) } }
        compose.onNodeWithText("Review run details").performClick()
        compose.onNodeWithText("Reasons: PUBLISH_FAILED").assertExists()
        compose.onNodeWithText("Retry reconciliation").performClick()
        compose.onNodeWithText("Download debug bundle").performClick()
        assertEquals(1, retry); assertEquals(1, export)
    }
}
