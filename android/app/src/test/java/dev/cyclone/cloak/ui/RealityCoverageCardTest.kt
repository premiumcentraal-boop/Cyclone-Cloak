package dev.cyclone.cloak.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import dev.cyclone.cloak.forge.CloakForge
import dev.cyclone.cloak.forge.TestPhones
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h900dp-mdpi")
class RealityCoverageCardTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun coherenceProvenanceGapsAndEvidenceAreNeutralAndPrivate() {
        val p = CloakForge.forgeProfile("Reality", "ab".repeat(32), TestPhones["pixel_7"], "0.4")
        compose.setContent { CloakTheme { Column(Modifier.verticalScroll(rememberScrollState())) {
            RealityCoverageCard(p, mapOf("reality.drm.security_level" to "BLOCKED"))
        } } }
        compose.onNodeWithText("Reality coverage").performClick()
        compose.onNodeWithText("Local coherence: 100/100 · Schema 0.4").assertIsDisplayed()
        compose.onNodeWithText("Missing optional sections:", substring = true).assertExists()
        compose.onNodeWithText("drm: 1 blocked").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(p.getJSONObject("reality").getString("inventory_id"), substring = true).assertDoesNotExist()
        compose.onNodeWithText(p.getString("seed"), substring = true).assertDoesNotExist()
    }
    @Test fun legacyProfileShowsNoRecordedCoverageWithoutMigration() {
        val p = CloakForge.forgeProfile("Old", "ab".repeat(32), TestPhones["pixel_7"])
        compose.setContent { CloakTheme { RealityCoverageCard(p) } }
        compose.onNodeWithText("Reality coverage").performClick()
        compose.onNodeWithText("Local coherence: not recorded · Schema 0.3").assertIsDisplayed()
    }
}
