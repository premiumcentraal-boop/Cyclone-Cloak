package dev.cyclone.cloak.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import android.graphics.Bitmap
import android.graphics.Canvas
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h900dp-mdpi")
class CalmGlassScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val faces = listOf(
        CloakProfileFace("owner", "Main", "3 apps", false, true),
        CloakProfileFace("work", "Profile B", "In use", true, true),
        CloakProfileFace("test", "Travel", "Setting up", false, false),
        CloakProfileFace("other", "Personal", "2 apps", false, true),
    )

    @Test fun profileFacesOpenTheCorrectProfileWithoutMutatingTheInventory() {
        val selected = mutableListOf<String>()
        compose.setContent { CloakTheme { CloakProfileGallery(faces, null, selected::add) } }
        compose.onNodeWithText("Profile B").performClick()
        compose.onNodeWithText("Personal").performClick()
        assertEquals(listOf("work", "other"), selected)
        assertEquals(4, faces.size)
    }

    @Test fun currentAndSelectedAreSeparateAndNotReadyProfilesRemainInspectable() {
        var opened: String? = null
        compose.setContent { CloakTheme { CloakProfileGallery(faces, "test") { opened = it } } }
        compose.onNodeWithText("Travel").assertIsSelected().performClick()
        compose.onNodeWithText("Profile B").assertIsNotSelected()
        compose.onNodeWithText("In use").assertIsDisplayed()
        assertEquals("test", opened)
    }

    @Test fun emptyGalleryDoesNotInventAProfileOrAnAction() {
        compose.setContent { CloakTheme { CloakProfileGallery(emptyList(), null) { error("Unexpected selection") } } }
        compose.onAllNodes(hasClickAction()).assertCountEquals(0)
    }

    @Test fun initialsSkipSharedPrefixAndHandleBlankOrUnicodeNames() {
        assertEquals("C", profileInitial("Profile C"))
        assertEquals("W", profileInitial("  Work  "))
        assertEquals("?", profileInitial("  "))
        assertEquals("🌍", profileInitial("🌍 Travel"))
    }

    @Test fun detailsAreHiddenUntilRequestedAndAllActionsRemainAvailable() {
        var binds = 0
        var replacements = 0
        var history = 0
        compose.setContent { CloakTheme {
            ProfileReadinessCard(ProfileReadiness(listOf("Android user mapping" to "17"), "Choose a device profile", false),
                true, { binds++ }, { replacements++ }, { history++ })
        } }
        compose.onNodeWithText("17").assertDoesNotExist()
        compose.onNodeWithText("Readiness details").performClick()
        compose.onNodeWithText("17").assertIsDisplayed()
        compose.onNodeWithText("Bind all apps").performClick()
        compose.onNodeWithText("Replace device profile").performClick()
        compose.onNodeWithText("Run history").performClick()
        assertEquals(listOf(1, 1, 1), listOf(binds, replacements, history))
    }

    @Test fun disabledReadinessCannotStartBindingOrReplacement() {
        compose.setContent { CloakTheme {
            ProfileReadinessCard(ProfileReadiness(emptyList(), "Reconnect", false), false,
                { error("Disabled bind") }, { error("Disabled replacement") }, {})
        } }
        compose.onNodeWithText("Bind all apps").assertIsNotEnabled()
        compose.onNodeWithText("Replace device profile").assertHasNoClickAction()
    }

    @Test fun galleryAndLongLabelsFitAtCompactWidth() {
        compose.setContent { CloakTheme {
            Column(Modifier.width(320.dp).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CloakHeroCard("Profiles", "Choose a space. Review its apps and device profile.")
                CloakProfileGallery(faces + CloakProfileFace("long", "A very long profile name", "Needs review", false, false), null) {}
                CloakNotice("Local evidence only. No platform verdict is claimed.")
            }
        } }
        compose.onNodeWithText("A very long profile name").assertIsDisplayed().performClick()
        val output = File("build/reports/calm-glass/profiles-compact.png")
        output.parentFile?.mkdirs()
        compose.runOnIdle {
            val view = compose.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }
}
