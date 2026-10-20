package dev.cyclone.cloak.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.cyclone.cloak.play.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w500dp-h1400dp-mdpi")
class PlayReadinessCardTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private fun card() = PlayReadinessCardState(identityId = "device-a", identityLabel = "Local phone", timestamp = "2026-10-19T12:00:00Z",
        source = "Imported local report", details = linkedMapOf("Play Services" to "enabled", "Play Store" to "enabled", "Web provider" to "enabled", "Account association" to "unknown"), contextAvailable = true)
    @Test fun serviceIsolationStatesAndReasonsAreVisible() {
        val state = mutableStateOf(card().copy(details = emptyMap()))
        compose.setContent { CloakTheme { PlayReadinessCard(state.value, emptyList(), true, null, { it }, {}, {}) } }
        for (value in ServiceIsolationState.entries) {
            compose.runOnIdle { state.value = state.value.copy(serviceIsolation = mapOf("com.android.vending" to
                ServiceIsolationResult(value, listOf(if (value == ServiceIsolationState.STALE) "SSI_STALE" else "SSI_NO_EVIDENCE")))) }
            compose.onNodeWithText("com.android.vending: ${value.name}").assertIsDisplayed()
        }
        compose.onNodeWithText("Service isolation: local evidence only").assertIsDisplayed()
    }
    @Test fun integrityStatesAndReviewReasonsAreVisibleWithoutCertification() {
        val state = mutableStateOf(card().copy(details = emptyMap()))
        compose.setContent { CloakTheme { PlayReadinessCard(state.value, emptyList(), true, null, { it }, {}, {}) } }
        for (value in IntegrityState.entries) {
            compose.runOnIdle { state.value = state.value.copy(integrity = IntegrityResult(value, if (value == IntegrityState.NEEDS_REVIEW) "IV_STALE" else "IV_TOKEN_ONLY")) }
            compose.onNodeWithText("Play Integrity: ${value.name.replace('_', ' ')}").assertIsDisplayed()
        }
        compose.onNodeWithText("The verdict or request receipt is outside the freshness window. (IV_STALE)").assertIsDisplayed()
        compose.onNodeWithText("Evidence-bound local readiness. Authenticated verdicts are separate evidence, not platform certification.").assertIsDisplayed()
    }
    @Test fun authenticatedImportRequiresCurrentProfileContext() {
        compose.setContent { CloakTheme { PlayReadinessCard(card().copy(contextAvailable = false, details = emptyMap()), emptyList(), true,
            "The backend signature is invalid or its provider key is not configured. (IV_AUTHENTICATION)", { it }, {}, {}, {}) } }
        compose.onNodeWithText("Import authenticated verdict").assertIsNotEnabled()
        compose.onNodeWithText("The backend signature is invalid or its provider key is not configured. (IV_AUTHENTICATION)").assertIsDisplayed()
    }
    private fun review(): PlayImportPreview {
        val envelope = JSONObject(javaClass.classLoader!!.getResource("play_evidence_report.json")!!.readText())
        val projection = envelope.getJSONObject("readiness_report").toString()
        val context = PlayReadinessContext("synthetic-profile", "a".repeat(64), 17, "b".repeat(64), Instant.parse("2026-10-19T12:00:00Z"))
        return PlayImportPreview(PlayImportTarget(context.profileId, "device-a", 17, "{}", context.artifactHash), envelope.toString(), projection,
            LocalPlayReadinessProvider().evaluate(context, projection), "stamp", emptyList(), false, null, null, emptyList())
    }
    @Test fun allFiveStatesAreVisibleWithProvenance() {
        val state = mutableStateOf(card().copy(serviceIsolation = mapOf("com.android.vending" to
            ServiceIsolationResult(ServiceIsolationState.ISOLATED, emptyList()))))
        compose.setContent { CloakTheme { PlayReadinessCard(state.value, emptyList(), true, null, { it }, {}, {}) } }
        for (verdict in listOf("READY", "DEGRADED", "BLOCKED", "UNKNOWN", "NEEDS REVIEW")) {
            compose.runOnIdle { state.value = state.value.copy(verdict = verdict) }
            compose.onNodeWithText("Play/Google local readiness · $verdict").assertIsDisplayed()
        }
        compose.onNodeWithText("Checked: 2026-10-19T12:00:00Z").assertIsDisplayed()
        compose.onNodeWithText("Source: Imported local report").assertIsDisplayed()
        compose.onNodeWithText("Account association: unknown").assertIsDisplayed()
    }
    @Test fun oldReadyLabelCannotHideAServiceConflict() {
        compose.setContent { CloakTheme { PlayReadinessCard(card().copy(verdict = "READY", details = emptyMap(),
            serviceIsolation = mapOf("com.android.vending" to ServiceIsolationResult(ServiceIsolationState.CONFLICT, listOf("GS_SHARED_REVIEW")))),
            emptyList(), true, null, { it }, {}, {}) } }
        compose.onNodeWithText("Play/Google local readiness · NEEDS REVIEW").assertIsDisplayed()
        compose.onNodeWithText("com.android.vending: CONFLICT").assertIsDisplayed()
    }
    @Test fun missingContextBlocksImportAndExplainsNextStep() {
        compose.setContent { CloakTheme { PlayReadinessCard(PlayReadinessCardState(reasons = listOf("Choose a saved device profile.")), emptyList(), true, null, { it }, {}, {}) } }
        compose.onNodeWithText("Import diagnostics report").assertIsNotEnabled()
        compose.onNodeWithText("Choose a saved device profile.").assertIsDisplayed()
    }
    @Test fun validContextImportsOnlyOnTap() {
        var imported = 0
        compose.setContent { CloakTheme { PlayReadinessCard(card(), emptyList(), true, null, { it }, {}, { imported++ }) } }
        assertEquals(0, imported)
        compose.onNodeWithText("Import diagnostics report").assertIsEnabled().performClick()
        assertEquals(1, imported)
    }
    @Test fun selectionIsExplicitAndDoesNotInvokeImport() {
        var selected = ""; var imported = 0
        compose.setContent { CloakTheme { PlayReadinessCard(card(), listOf("device-b" to "Other phone"), true, null, { it }, { selected = it }, { imported++ }) } }
        compose.onNodeWithText("Choose evidence device profile").performClick()
        compose.onNodeWithText("Other phone").performClick()
        assertEquals("device-b", selected); assertEquals(0, imported)
    }
    @Test fun conflictsAndImportErrorsAreActionable() {
        compose.setContent { CloakTheme { PlayReadinessCard(card().copy(verdict = "NEEDS REVIEW", sharedProfiles = listOf("first", "second")), emptyList(), true,
            "The report belongs to a different Android user.", { "Profile $it" }, {}, {}) } }
        compose.onNodeWithText("Account-slot conflict: shared use needs confirmation").assertIsDisplayed()
        compose.onNodeWithText("Profile first, Profile second").assertIsDisplayed()
        compose.onNodeWithText("The report belongs to a different Android user.").assertIsDisplayed()
    }
    @Test fun sharedUseRequiresSecondExplicitConfirmation() {
        val calls = mutableListOf<Boolean>()
        compose.setContent { CloakTheme { PlayEvidenceReviewDialog(review().copy(sharedProfiles = listOf("first", "second")), true, null, { it }, { calls += it }, {}) } }
        compose.onNodeWithText("Save local evidence").performClick()
        assertTrue(calls.isEmpty())
        compose.onNodeWithText("Confirm shared account-slot metadata").assertIsDisplayed()
        compose.onNodeWithText("Confirm shared use and save").performClick()
        assertEquals(listOf(true), calls)
    }
    @Test fun cancelAndBackCannotSaveSharedUse() {
        var saved = 0; var cancelled = 0
        compose.setContent { CloakTheme { PlayEvidenceReviewDialog(review().copy(sharedProfiles = listOf("first", "second")), true, null, { it }, { saved++ }, { cancelled++ }) } }
        compose.onNodeWithText("Save local evidence").performClick()
        compose.onNodeWithText("Back to report").performClick()
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(0, saved); assertEquals(1, cancelled)
    }
    @Test fun savingUnknownEvidenceDoesNotClaimReady() {
        var saved: Boolean? = null
        compose.setContent { CloakTheme { PlayEvidenceReviewDialog(review(), true, null, { it }, { saved = it }, {}) } }
        compose.onNodeWithText("Preview: UNKNOWN").assertIsDisplayed()
        compose.onNodeWithText("Save local evidence").performClick()
        assertEquals(false, saved)
    }
}
