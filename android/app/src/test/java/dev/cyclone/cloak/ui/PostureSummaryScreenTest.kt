package dev.cyclone.cloak.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import dev.cyclone.cloak.data.PostureRecord
import dev.cyclone.cloak.data.PostureResult
import dev.cyclone.cloak.data.PostureState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class PostureSummaryScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun rendersMissingAndEveryRecordedStateWithTheExistingTheme() {
        val record = mutableStateOf<PostureRecord?>(null)
        compose.setContent { CloakTheme { PostureSummaryLine(record.value) } }
        compose.onNodeWithText("Local compatibility posture: not recorded").assertIsDisplayed()
        for (state in PostureState.entries) {
            val next = PostureResult(state).recordFor("profile-1")
            compose.runOnIdle { record.value = next }
            compose.onNodeWithText(PostureSummary.label(next)).assertIsDisplayed()
        }
        compose.runOnIdle { record.value = null }
        compose.onNodeWithText("Local compatibility posture: not recorded").assertIsDisplayed()
    }
}
