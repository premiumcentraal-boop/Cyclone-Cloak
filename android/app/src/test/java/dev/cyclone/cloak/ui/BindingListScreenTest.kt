package dev.cyclone.cloak.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import dev.cyclone.cloak.data.CloakBinding
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h900dp-mdpi")
class BindingListScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val id = "Cyclone_aaaaaaaaaaaaaaaa"
    private val binding = CloakBinding(id, 11, "com.example.chat", "identity", 0, false, 1, "module update needs reboot")

    @Test
    fun filtersSelectStableProfileIdAndClearBothInputs() {
        var profile by mutableStateOf<String?>(null)
        var query by mutableStateOf("")
        compose.setContent {
            CloakTheme {
                BindingFilters(profile, listOf(id), { "Work" }, { profile = it }, query, { query = it }, {
                    profile = null
                    query = ""
                })
            }
        }
        compose.onNodeWithText("All Cyclone profiles").performClick()
        compose.onNodeWithText(id).performClick()
        assertEquals(id, profile)
        compose.onNodeWithText("Cyclone profile: Work").assertIsDisplayed()
        compose.onNodeWithText("App package filter").performTextReplacement("com.example")
        assertEquals("com.example", query)
        compose.onNodeWithText("Clear filters").performClick()
        assertEquals(null, profile)
        assertEquals("", query)
        compose.onNodeWithText("All Cyclone profiles").assertIsDisplayed()
    }

    @Test
    fun disabledBindingShowsHealthAndWarningAndPreservesActionArguments() {
        var toggled: CloakBinding? = null
        var removed: CloakBinding? = null
        compose.setContent {
            CloakTheme {
                CloakBindingCard(binding, "Work", "app not listed", true, "Work Pixel",
                    { toggled = it }, { removed = it })
            }
        }
        compose.onNodeWithText("Disabled").assertIsDisplayed()
        compose.onNodeWithText(binding.state).assertIsDisplayed()
        compose.onNodeWithText("Cloak: Work Pixel").assertIsDisplayed()
        compose.onNodeWithText("Cyclone: app not listed").assertIsDisplayed()
        compose.onNodeWithText("Cyclone profile no longer present: $id").assertIsDisplayed()
        compose.onNodeWithText("On").performClick()
        compose.onNodeWithText("Remove").performClick()
        assertSame(binding, toggled)
        assertSame(binding, removed)
    }

    @Test
    fun mirroredBindingKeepsManagementActionsHidden() {
        compose.setContent {
            CloakTheme {
                CloakBindingCard(binding.copy(enabled = true, origin = CloakBinding.ORIGIN_MAIN), "Work", null,
                    onToggle = { error("Mirrored binding cannot be toggled") },
                    onRemove = { error("Mirrored binding cannot be removed") })
            }
        }
        compose.onNodeWithText("Enabled").assertIsDisplayed()
        compose.onNodeWithText("managed by Cloak in Main").assertIsDisplayed()
        compose.onNodeWithText("Off").assertDoesNotExist()
        compose.onNodeWithText("Remove").assertDoesNotExist()
        compose.onNodeWithText("Cyclone profile no longer present: $id").assertDoesNotExist()
    }
}
