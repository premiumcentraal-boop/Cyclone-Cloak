package dev.cyclone.cloak.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import dev.cyclone.cloak.data.*
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
@Config(sdk = [34], qualifiers = "w400dp-h900dp-mdpi")
class CompatibilityReportCardTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val now = Instant.parse("2026-10-13T12:00:00Z")
    private fun record(state: PostureState = PostureState.PASS) = PostureResult(state, now.toString(), "local-report",
        "Local checks passed. No platform verdict.", artifactDigest = "a".repeat(64), profileDigest = "b".repeat(64),
        reportDigest = "c".repeat(64)).recordFor("profile-1")
    private fun show(record: PostureRecord?, error: String? = null, load: () -> Unit = {},
        mark: (PostureRecord?) -> Unit = {}, clear: (PostureRecord, Boolean) -> Unit = { _, _ -> }) {
        compose.setContent { CloakTheme { CompatibilityReportCard(record, "profile-1", "b".repeat(64), "a".repeat(64),
            false, error, now, load, mark, clear) } }
    }
    @Test fun reportShowsShortIdAndExplicitMatches() {
        show(record())
        for (text in listOf("Report: ${"c".repeat(12)}", "Source: local-report", "Checked: $now",
            "Profile match: matches saved profile", "Artifact match: matches bundled artifact", "Freshness: within 7 days"))
            compose.onNodeWithText(text).assertIsDisplayed()
        compose.onNodeWithText("c".repeat(64), substring = true).assertDoesNotExist()
        compose.onNodeWithText("Clear after explicit local reset").assertIsNotEnabled()
    }
    @Test fun missingReportRemainsReadableAndExplainsDetails() {
        show(null)
        compose.onNodeWithText("Report:", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Freshness: not recorded").assertIsDisplayed()
        compose.onNodeWithText("Review report details").performClick()
        compose.onNodeWithText("No readable local report is recorded. Load a compatibility report to review evidence.").performScrollTo().assertIsDisplayed()
    }
    @Test fun everyStateIsVisibleInDetails() {
        val state = mutableStateOf(record())
        compose.setContent { CloakTheme { CompatibilityReportCard(state.value, "profile-1", "b".repeat(64), "a".repeat(64),
            false, null, now, {}, {}, { _, _ -> }) } }
        compose.onNodeWithText("Review report details").performClick()
        for (value in PostureState.entries) {
            compose.runOnIdle { state.value = record(value) }
            compose.onNodeWithText(PostureSummary.label(record(value))).assertIsDisplayed()
        }
    }
    @Test fun driftAndStaleStatusAreVisible() {
        show(record().copy(profileId = "another", artifactDigest = "d".repeat(64), checkedAt = now.minusSeconds(8 * 86400).toString()))
        compose.onNodeWithText("Profile match: another profile").assertIsDisplayed()
        compose.onNodeWithText("Artifact match: different bundled artifact").assertIsDisplayed()
        compose.onNodeWithText("Freshness: stale (older than 7 days)").assertIsDisplayed()
    }
    @Test fun markRequiresConfirmationAndCancelDoesNothing() {
        var marks = 0
        show(record(), mark = { assertEquals(record(), it); marks++ })
        compose.onNodeWithText("Mark needs rotation").performClick()
        assertEquals(0, marks)
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(0, marks)
        compose.onNodeWithText("Mark needs rotation").performClick()
        compose.onNodeWithText("Confirm needs rotation").performClick()
        assertEquals(1, marks)
    }
    @Test fun clearRequiresExplicitCompletedResetAcknowledgement() {
        var clears = 0
        show(record(PostureState.NEEDS_ROTATION), clear = { _, completed -> assertTrue(completed); clears++ })
        compose.onNodeWithText("Clear after explicit local reset").performClick()
        compose.onNodeWithText("Confirm completed reset").assertIsNotEnabled()
        assertEquals(0, clears)
        compose.onNode(isToggleable()).performClick()
        compose.onNodeWithText("Confirm completed reset").assertIsEnabled().performClick()
        assertEquals(1, clears)
    }
    @Test fun changedReportDuringConfirmationRequiresReviewAgain() {
        val state = mutableStateOf(record()); var marks = 0
        compose.setContent { CloakTheme { CompatibilityReportCard(state.value, "profile-1", "b".repeat(64), "a".repeat(64),
            false, null, now, {}, { marks++ }, { _, _ -> }) } }
        compose.onNodeWithText("Mark needs rotation").performClick()
        compose.runOnIdle { state.value = record().copy(reason = "New local evidence") }
        compose.onNodeWithText("The saved report changed. Cancel and review it again before continuing.").assertIsDisplayed()
        compose.onNodeWithText("Confirm needs rotation").assertIsNotEnabled()
        assertEquals(0, marks)
    }
    @Test fun errorHasAVisibleRetryAction() {
        var loads = 0
        show(record(), "Couldn't load the compatibility report. Check the file format and try again. No record was saved.", { loads++ })
        compose.onNodeWithText("Couldn't load the compatibility report.", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Load compatibility report").assertIsEnabled().performClick()
        assertEquals(1, loads)
    }
    @Test fun malformedFileLoadShowsSafeErrorWithoutSaving() {
        val app = org.robolectric.RuntimeEnvironment.getApplication()
        val profile = dev.cyclone.cloak.forge.CloakForge.forgeProfile("Report error", "ef".repeat(32), dev.cyclone.cloak.forge.TestPhones["pixel_7"])
        val id = CloakStore.save(app, profile)
        PostureStore(app).clear(id)
        val file = java.io.File(app.cacheDir, "invalid-compatibility-report.json").apply { writeText("not JSON: private user content") }
        val vm = CloakViewModel(app)
        compose.setContent {
            val state by vm.state.collectAsState()
            CloakTheme { CompatibilityReportCard(state.postures[id], id, EngineMetadataStore.digest(profile), null,
                state.busy != null, state.postureErrors[id], now,
                { vm.importPostureReport(android.net.Uri.fromFile(file), id) }, {}, { _, _ -> }) }
        }
        compose.onNodeWithText("Load compatibility report").performClick()
        compose.waitUntil(10_000) { vm.state.value.postureErrors[id] != null && vm.state.value.busy == null }
        compose.onNodeWithText("Couldn't load the compatibility report. Check the file format and try again. No record was saved.").assertIsDisplayed()
        compose.onNodeWithText("private user content", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Load compatibility report").assertIsEnabled()
        assertNull(PostureStore(app).read(id))
    }
    @Test fun compatibilityImportRequiresPreviewBeforeExplicitSave() {
        val app = org.robolectric.RuntimeEnvironment.getApplication()
        val profile = dev.cyclone.cloak.forge.CloakForge.forgeProfile("Report preview", "ed".repeat(32), dev.cyclone.cloak.forge.TestPhones["pixel_7"])
        val id = CloakStore.save(app, profile)
        PostureStore(app).clear(id)
        app.getSharedPreferences("compatibility-posture-provenance", 0).edit().remove(id).commit()
        val report = CompatibilityPostureReport("local-test", now.toString(), id, EngineMetadataStore.digest(profile), "a".repeat(64),
            listOf(PostureReportCheck("build.fields", PostureCheckStatus.WARNING, "Local check requires review.")))
        val file = java.io.File(app.cacheDir, "valid-compatibility-report.json").apply { writeText(report.canonicalJson().toString()) }
        val vm = CloakViewModel(app)
        compose.setContent {
            val state by vm.state.collectAsState()
            CloakTheme {
                CompatibilityReportCard(state.postures[id], id, EngineMetadataStore.digest(profile), null,
                    state.busy != null, state.postureErrors[id], now,
                    { vm.importPostureReport(android.net.Uri.fromFile(file), id) }, {}, { _, _ -> })
                state.diagnosticsReview?.let { DiagnosticsPostureDialog(it.preview, state.busy != null, vm::saveDiagnosticsPosture,
                    vm::dismissDiagnosticsPosture, state.postureErrors[id]) }
            }
        }
        compose.onNodeWithText("Load compatibility report").performClick()
        compose.waitUntil(10_000) { vm.state.value.diagnosticsReview != null && vm.state.value.busy == null }
        assertNull(PostureStore(app).read(id))
        compose.onNodeWithText("Preview local compatibility evidence").assertIsDisplayed()
        compose.onNodeWithText("Save local posture").performClick()
        compose.waitUntil(10_000) { PostureStore(app).read(id) != null && vm.state.value.diagnosticsReview == null }
        assertNotEquals(PostureState.PASS, PostureStore(app).read(id)!!.state)
    }
}

