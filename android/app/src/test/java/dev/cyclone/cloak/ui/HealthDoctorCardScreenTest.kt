package dev.cyclone.cloak.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import dev.cyclone.cloak.cyclone.CycloneGate
import dev.cyclone.cloak.root.RootDoctorCode
import dev.cyclone.cloak.root.RootDoctorResult
import org.json.JSONObject
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
class HealthDoctorCardScreenTest {
    @Test fun largeBindingInventoryPagesDetailsButCopiesEveryCheck() {
        val model = HealthDiagnostics.assess(facts.copy(bindings = (1..327).map { n ->
            facts.bindings.single().copy(displayContext = "Binding $n")
        }))
        var copied = ""
        compose.setContent { CloakTheme { Column(Modifier.verticalScroll(rememberScrollState())) {
            HealthDoctorCard(model) { copied = it }
        } } }
        compose.onNodeWithText("Binding 327").assertDoesNotExist()
        compose.onNodeWithText("Copy diagnostics").performClick()
        assertEquals(model.checks.size, JSONObject(copied).getJSONArray("checks").length())
        compose.onNodeWithText("Next checks").performScrollTo().performClick()
        compose.onNodeWithText("Checks 21–40 of ${model.checks.size}").assertIsDisplayed()
        compose.onNodeWithText("Previous checks").performClick()
        compose.onNodeWithText("Checks 1–20 of ${model.checks.size}").assertIsDisplayed()
    }
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val facts = DoctorFacts(RootDoctorResult(RootDoctorCode.READY, "v0.10.0-alpha.1"), "0.10.0-alpha.1",
        "Connected", false, CycloneGate(true, 3, setOf("profiles.read", "profiles.apps.read", "profiles.config", "profiles.startup"), emptySet()),
        "ready", listOf(BindingDoctorFacts("PRIVATE-LABEL / PRIVATE-PACKAGE", true, "ready", true, true, true, true, true, false)))

    @Test fun copyButtonProducesTheAllowlistedReportRatherThanDisplayContext() {
        var copied = ""
        compose.setContent {
            CloakTheme { Column(Modifier.verticalScroll(rememberScrollState())) {
                HealthDoctorCard(HealthDiagnostics.assess(facts)) { copied = it }
            } }
        }
        compose.onAllNodesWithText("Ready").assertCountEquals(1)
        compose.onNodeWithText("Copy diagnostics").performClick()
        assertEquals("ready", JSONObject(copied).getString("verdict"))
        assertFalse(copied.contains("PRIVATE-LABEL"))
        assertFalse(copied.contains("PRIVATE-PACKAGE"))
        compose.onAllNodesWithText("PRIVATE-LABEL / PRIVATE-PACKAGE").onFirst().performScrollTo().assertIsDisplayed()
    }

    @Test fun queuedModuleShowsOneDegradedVerdictAndUnconfirmedInstalledVersion() {
        val model = HealthDiagnostics.assess(facts.copy(root = RootDoctorResult(RootDoctorCode.MODULE_REBOOT_REQUIRED, "v0.10.0-alpha.1")))
        compose.setContent { CloakTheme { Column(Modifier.verticalScroll(rememberScrollState())) { HealthDoctorCard(model) {} } } }
        compose.onAllNodesWithText("Degraded").assertCountEquals(1)
        compose.onAllNodesWithText("Warning").assertCountEquals(3)
        compose.onNodeWithText("App: 0.10.0-alpha.1\nInstalled module: not confirmed").performScrollTo().assertIsDisplayed()
    }

    @Test fun failedBindingShowsItsReasonAndSingleFailedVerdict() {
        val model = HealthDiagnostics.assess(facts.copy(bindings = listOf(facts.bindings.single().copy(health = "module missing"))))
        compose.setContent { CloakTheme { Column(Modifier.verticalScroll(rememberScrollState())) { HealthDoctorCard(model) {} } } }
        compose.onAllNodesWithText("Failed").assertCountEquals(1)
        compose.onNodeWithText(DoctorReason.MODULE_MISSING.message).performScrollTo().assertIsDisplayed()
    }
}
