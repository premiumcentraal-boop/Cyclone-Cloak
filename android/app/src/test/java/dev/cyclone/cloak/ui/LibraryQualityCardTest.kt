package dev.cyclone.cloak.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Modifier
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import dev.cyclone.cloak.forge.CloakForge
import dev.cyclone.cloak.forge.TestPhones
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
class LibraryQualityCardTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val now = Instant.parse("2026-10-13T12:00:00Z")
    private fun state(): CloakState {
        val profile = CloakForge.forgeProfile("Work phone", "ab".repeat(32), TestPhones["pixel_7"])
        return CloakState(identities = listOf(profile.getString("id") to profile), identityInventoryKnown = true, unreadableIdentityIds = setOf("private-unreadable"))
    }
    @Test fun summaryRowsAndCountsAreVisibleWithoutIdentifiers() {
        val state = state()
        compose.setContent { CloakTheme { Column(Modifier.verticalScroll(rememberScrollState())) {
            LibraryQualityCard(LibraryQuality.assess(state, now), emptyMap(), false, {})
        } } }
        for (text in listOf("Library quality", "Readable saved profiles: 1", "Unreadable saved profiles: 1", "Duplicate identifier groups: 0",
            "Profiles with validation errors: 0", "Profiles with validation warnings: 0", "Missing local compatibility records: 1",
            "Stale or attention-state local records: 0", "Profiles bound to apps: 0", "Profiles with no bindings: 1"))
            compose.onNodeWithText(text).performScrollTo().assertIsDisplayed()
        val profile = state.identities.single().second
        compose.onNodeWithText(profile.getJSONObject("identifiers").getString("android_id"), substring = true).assertDoesNotExist()
        compose.onNodeWithText("private-unreadable", substring = true).assertDoesNotExist()
    }
    @Test fun affectedProfileNavigationUsesExistingIdentityId() {
        val state = state(); var reviewed: String? = null
        compose.setContent { CloakTheme { Column(Modifier.verticalScroll(rememberScrollState())) {
            LibraryQualityCard(LibraryQuality.assess(state, now), mapOf(state.identities.single().first to "Work phone"), false, { reviewed = it })
        } } }
        compose.onAllNodesWithText("Show profiles (1)")[0].performScrollTo().performClick()
        compose.onNodeWithText("Review Work phone").performScrollTo().performClick()
        assertEquals(state.identities.single().first, reviewed)
    }
    @Test fun expandedNavigationIsDisabledWhileBusy() {
        val state = state(); val busy = mutableStateOf(false); var reviewed = false
        compose.setContent { CloakTheme { Column(Modifier.verticalScroll(rememberScrollState())) {
            LibraryQualityCard(LibraryQuality.assess(state, now), mapOf(state.identities.single().first to "Work phone"), busy.value, { reviewed = true })
        } } }
        compose.onAllNodesWithText("Show profiles (1)")[0].performScrollTo().performClick()
        compose.runOnIdle { busy.value = true }
        compose.onNodeWithText("Review Work phone").performScrollTo().assertIsNotEnabled()
        assertFalse(reviewed)
    }
    @Test fun emptyLibraryIsNeutralAndUnknownInventoryIsExplicit() {
        compose.setContent { CloakTheme { Column(Modifier.verticalScroll(rememberScrollState())) {
            LibraryQualityCard(LibraryQuality.assess(CloakState(), now), emptyMap(), false, {})
        } } }
        compose.onNodeWithText("No readable saved profiles to assess.").assertIsDisplayed()
        compose.onNodeWithText("Saved-profile inventory is not yet known.").assertIsDisplayed()
        compose.onNodeWithText("Unreadable saved profiles:", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Profiles with no bindings: 0").performScrollTo().assertIsDisplayed()
        compose.onAllNodes(hasText("Review", substring = true)).assertCountEquals(0)
    }
}
