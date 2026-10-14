package dev.cyclone.cloak.ui

import android.app.Application
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import dev.cyclone.cloak.cyclone.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ConnectionStatusChipTest {
    @get:Rule val compose = createComposeRule()

    @Test fun updateReasonIsOneTapAwayAndRetryWorks() {
        var retries = 0
        val state = ConnectionStatus(ConnectionPhase.NEEDS_UPDATE, 1, "CLOAK_NEEDS_UPDATE", "Update Cyclone Cloak.")
        compose.setContent { CloakTheme { ConnectionStatusChip(state) { retries++ } } }
        compose.onNodeWithText("Needs update").performClick()
        compose.onNodeWithText("Update Cyclone Cloak.", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Retry").performClick()
        assertEquals(1, retries)
    }

    @Test fun cachedRegistryIsVisibleWhileReconnecting() {
        val state = ConnectionStatus(attempt = 2, cached = CachedRegistry(ProfilesSnapshot(null, emptyList()), 1000))
        compose.setContent { CloakTheme { ConnectionStatusChip(state) {} } }
        compose.onNodeWithText("Reconnecting (attempt 2)").assertIsDisplayed()
        compose.onNodeWithText("Last seen", substring = true).assertIsDisplayed()
    }

    @Test fun missingAppExplainsInstallationAndProfile() {
        val state = ConnectionStatus(ConnectionPhase.NOT_INSTALLED, detail = "Install Cyclone in this Android profile.")
        compose.setContent { CloakTheme { ConnectionStatusChip(state) {} } }
        compose.onNodeWithText("Not installed").performClick()
        compose.onNodeWithText("Install Cyclone in this Android profile.", substring = true).assertIsDisplayed()
    }
}
