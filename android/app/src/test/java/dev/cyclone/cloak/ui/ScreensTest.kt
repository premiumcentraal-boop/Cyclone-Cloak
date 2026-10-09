package dev.cyclone.cloak.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
import dev.cyclone.cloak.cyclone.CycloneGate
import dev.cyclone.cloak.cyclone.Placement
import dev.cyclone.cloak.cyclone.ProfilesSnapshot
import dev.cyclone.cloak.cyclone.SyncReport
import dev.cyclone.cloak.data.CloakBinding
import dev.cyclone.cloak.forge.CloakForge
import dev.cyclone.cloak.forge.PhoneDraft
import dev.cyclone.cloak.forge.PropImport
import dev.cyclone.cloak.forge.TestPhones
import dev.cyclone.cloak.root.RootDoctorCode
import dev.cyclone.cloak.root.RootDoctorResult
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Renders every tab and the phone builder with a realistic state, on the JVM (Robolectric native graphics), and saves
 * a screenshot of each to build/screenshots. A screen that throws while composing fails here, not on a phone.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h1400dp-mdpi")
class ScreensTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val b = "Cyclone_bbbbbbbbbbbbbbbb"
    private val c = "Cyclone_cccccccccccccccc"

    private fun state(): CloakState {
        val work = CloakForge.forgeProfile("Work Pixel", "ab".repeat(32), TestPhones["pixel_7"])
        val travel = CloakForge.forgeProfile("Travel Galaxy", "cd".repeat(32), TestPhones["galaxy_s23"])
        val own = TestPhones["galaxy_s23"].copy(id = "galaxy_de", label = "Galaxy S23 Germany", builtIn = false)
        val snapshot = ProfilesSnapshot.parse(JSONObject().put("current", "owner").put("profiles", JSONArray()
            .put(JSONObject().put("id", "owner").put("label", "This phone").put("kind", "owner").put("state", "ready"))
            .put(JSONObject().put("id", b).put("label", "Work").put("kind", "profile").put("state", "ready").put("androidUserId", 11)
                .put("packages", JSONArray(listOf("com.example.bank", "com.example.chat"))))
            .put(JSONObject().put("id", c).put("label", "Travel").put("kind", "profile").put("state", "ready").put("androidUserId", 12)
                .put("packages", JSONArray(listOf("com.example.maps"))))))
        val gate = CycloneGate(true, 3, setOf("profiles.read", "profiles.apps.read", "profiles.config", "events.profiles",
            "device.root.read", "profiles.open.request", "profiles.startup"), emptySet())
        return CloakState(
            phones = TestPhones.all + own,
            identities = listOf(work.getString("id") to work, travel.getString("id") to travel),
            bindings = listOf(
                CloakBinding(b, 11, "com.example.bank", work.getString("id"), 0, true, 1, "ready"),
                CloakBinding(b, 11, "com.example.chat", work.getString("id"), 0, true, 1, "module update needs reboot"),
            ),
            report = SyncReport("Connected", "In Main · Cyclone minor 3", gate, snapshot, Placement.Main,
                rootFacts = mapOf(b to true, c to null)),
            providerState = "ready",
            rootDoctor = RootDoctorResult(RootDoctorCode.MODULE_REBOOT_REQUIRED, moduleVersion = "v0.9.0-alpha.1"),
        )
    }

    /** The app's background, as the Scaffold gives it in the app. */
    @Composable
    private fun Screen(content: @Composable () -> Unit) = CloakTheme {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) { content() }
    }

    private val vm by lazy { CloakViewModel(RuntimeEnvironment.getApplication()) }

    private fun shot(name: String) {
        compose.waitForIdle()
        // Draw the activity's views (Compose's captureToImage waits on a redraw Robolectric never signals).
        val view = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        val out = File("build/screenshots").apply { mkdirs() }
        File(out, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun phones() {
        compose.setContent { Screen { PhonesScreen(state(), vm) } }
        compose.onNodeWithText("Galaxy S23 Germany").assertIsDisplayed()
        shot("1-phones")
    }

    @Test
    fun identities() {
        compose.setContent { Screen { IdentitiesScreen(state(), vm) } }
        compose.onNodeWithText("Work Pixel").assertIsDisplayed()
        shot("2-identities")
    }

    @Test
    fun profiles() {
        compose.setContent { Screen { ProfilesScreen(state(), vm) } }
        compose.onNodeWithText("Rooted !").assertIsDisplayed()
        shot("3-profiles")
    }

    @Test
    fun health() {
        compose.setContent { Screen { HealthScreen(state(), vm) } }
        compose.onNodeWithText("Reboot required").assertIsDisplayed()
        shot("4-health")
    }

    @Test
    fun builderFromADump() {
        val dump = javaClass.classLoader!!.getResource("dumps.json")!!.readText()
            .let { JSONObject(it).getJSONArray("cases").getJSONObject(1).getString("text") }
        val editor = PhoneEditor(PhoneDraft.from(PropImport.draftPhone(dump, emptySet())), isNew = true,
            note = "Read from a dump. Fill in what it couldn't tell (marked below), then save.")
        compose.setContent { Screen { PhoneEditorScreen(editor, busy = false, onChange = {}, onSave = {}, onClose = {}) } }
        compose.onNodeWithText("Build a phone").assertIsDisplayed()
        shot("5-builder-from-dump")
    }
}
