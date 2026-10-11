package dev.cyclone.cloak.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h900dp-mdpi")
class CoverageMatrixScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun allAreasRenderWithDatesOnlyForPartialBaselineProof() {
        val coverage = CompatibilityCoverage.parse(javaClass.classLoader!!.getResource("compatibility-coverage.json")!!.readText())
        compose.setContent {
            CloakTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) { CoverageMatrix(coverage) }
            }
        }
        compose.onNodeWithText("Compatibility coverage").assertIsDisplayed()
        coverage.rows.forEach { compose.onNodeWithText(it.area).assertExists() }
        compose.onAllNodesWithText("Last verified (UTC): not verified").assertCountEquals(11)
        compose.onAllNodesWithText("Not implemented").assertCountEquals(2)
        compose.onNodeWithText("DRM identifier").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun unavailableDataNeverShowsVerificationClaims() {
        compose.setContent { CloakTheme { CoverageMatrix(null) } }
        compose.onNodeWithText("Coverage data unavailable. No verification claims can be shown.").assertIsDisplayed()
        compose.onNodeWithText("Partial device proof").assertDoesNotExist()
    }
}
