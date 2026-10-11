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
        compose.onNodeWithText("PRIVATE-LABEL / PRIVATE-PACKAGE").performScrollTo().assertIsDisplayed()
    }

    @Test fun queuedModuleShowsOneDegradedVerdictAndUnconfirmedInstalledVersion() {
        val model = HealthDiagnostics.assess(facts.copy(root = RootDoctorResult(RootDoctorCode.MODULE_REBOOT_REQUIRED, "v0.10.0-alpha.1")))
        compose.setContent { CloakTheme { Column(Modifier.verticalScroll(rememberScrollState())) { HealthDoctorCard(model) {} } } }
        compose.onAllNodesWithText("Degraded").assertCountEquals(1)
        compose.onAllNodesWithText("Warn").assertCountEquals(2)
        compose.onNodeWithText("App: 0.10.0-alpha.1\nInstalled module: not confirmed").performScrollTo().assertIsDisplayed()
    }

    @Test fun failedBindingShowsItsReasonAndSingleFailedVerdict() {
        val model = HealthDiagnostics.assess(facts.copy(bindings = listOf(facts.bindings.single().copy(health = "module missing"))))
        compose.setContent { CloakTheme { Column(Modifier.verticalScroll(rememberScrollState())) { HealthDoctorCard(model) {} } } }
        compose.onAllNodesWithText("Failed").assertCountEquals(1)
        compose.onNodeWithText(DoctorReason.MODULE_MISSING.message).performScrollTo().assertIsDisplayed()
    }
}
