package dev.cyclone.cloak

import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.cyclone.connector.client.CycloneConnector
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.concurrent.thread

/**
 * Cyclone Cloak 0.1 companion: imports cloak profiles, binds them to Cyclone profiles over the
 * `cyclone.connector/1` contract, and writes the per-app config the Zygisk module applies.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var status: TextView
    private lateinit var list: ListView
    private val labels = mutableListOf<String>()
    private val profiles = mutableListOf<JSONObject>()
    private var selected = -1

    private val importProfile =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) importProfile(uri)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        status = TextView(this).apply { textSize = 13f; setPadding(32, 48, 32, 8) }
        list = ListView(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_list_item_1, labels)
            setOnItemClickListener { _, _, position, _ -> selected = position; refresh() }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0
            ).apply { weight = 1f }
        }

        fun button(label: String, onClick: () -> Unit) =
            Button(this).apply { text = label; setOnClickListener { onClick() } }

        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
            addView(status)
            addView(button("Check Cyclone + root") { checkStatus() })
            addView(button("Import cloak profile (.json)") {
                importProfile.launch(arrayOf("application/json"))
            })
            addView(list)
            addView(button("Apply selected profile to a Cyclone profile") { pickCycloneProfile() })
        }
        setContentView(column)
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val all = CloakStore.all(this)
        profiles.clear()
        profiles.addAll(all.map { it.second })
        labels.clear()
        labels.addAll(all.mapIndexed { index, (id, json) ->
            (if (index == selected) "* " else "") + json.optString("name", id)
        })
        (list.adapter as ArrayAdapter<*>).notifyDataSetChanged()
    }

    private fun importProfile(uri: Uri) {
        thread {
            try {
                val text = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    ?: throw IllegalArgumentException("empty file")
                val profile = JSONObject(text)
                CloakStore.validate(profile)?.let { reason -> throw IllegalArgumentException(reason) }
                val id = CloakStore.save(this, profile)
                runOnUiThread {
                    selected = -1
                    refresh()
                    toast("Imported $id")
                }
            } catch (error: Exception) {
                runOnUiThread { toast("Import failed: ${error.message}") }
            }
        }
    }

    private fun checkStatus() {
        thread {
            val lines = mutableListOf<String>()
            lines += "Cyclone installed: ${CycloneConnector.isCycloneInstalled(this)}"
            try {
                CycloneConnector.connect(this).use { cyclone ->
                    val hello = cyclone.hello()
                    lines += "Connector approved: ${hello.optBoolean("approved")}"
                    val granted = hello.optJSONArray("granted") ?: JSONArray()
                    lines += "Granted: " +
                        (0 until granted.length()).joinToString(", ") { granted.optString(it) }
                }
            } catch (error: Exception) {
                lines += "Connector: ${error.message}"
            }
            lines += "Root: ${CloakStore.runSu("id").replace('\n', ' ')}"
            val text = lines.joinToString("\n")
            runOnUiThread { status.text = text }
        }
    }

    private fun pickCycloneProfile() {
        if (selected < 0 || selected >= profiles.size) {
            toast("Select a cloak profile first")
            return
        }
        val cloak = profiles[selected]
        thread {
            try {
                val result = CycloneConnector.connect(this).use { it.profiles() }
                val array = result.optJSONArray("profiles") ?: JSONArray()
                val options = (0 until array.length())
                    .mapNotNull { array.optJSONObject(it) }
                    .filter { it.optString("kind") == "profile" && it.optString("state") == "ready" }
                runOnUiThread {
                    val names = options.map { it.optString("label", it.optString("id")) }.toTypedArray()
                    AlertDialog.Builder(this)
                        .setTitle("Apply to which Cyclone profile?")
                        .setItems(names) { _, which -> applyTo(cloak, options[which]) }
                        .setNegativeButton(android.R.string.cancel, null)
                        .show()
                }
            } catch (error: Exception) {
                runOnUiThread { toast("Cyclone: ${error.message}") }
            }
        }
    }

    private fun applyTo(cloak: JSONObject, cycloneProfile: JSONObject) {
        thread {
            try {
                val packages = cycloneProfile.optJSONArray("packages")
                    ?.let { array -> (0 until array.length()).map { array.optString(it) } }
                    ?: emptyList()
                if (packages.isEmpty()) {
                    runOnUiThread {
                        toast("Cyclone shared no apps for this profile (check the profiles.apps.read scope)")
                    }
                    return@thread
                }
                val userKey = cycloneProfile.optInt("androidUserId", 0).toString()
                val bindings = CloakStore.readConfig()?.optJSONObject("bindings") ?: JSONObject()
                for (pkg in packages) bindings.put("$userKey:$pkg", cloak)
                val config = JSONObject().put("version", 1).put("bindings", bindings)
                val staged = File(cacheDir, "cloak_config.json")
                staged.writeText(config.toString())
                val result = CloakStore.runSu(
                    "mkdir -p /data/adb/cyclone_cloak && cp ${staged.absolutePath} " +
                        "/data/adb/cyclone_cloak/config.json"
                )
                try {
                    CycloneConnector.connect(this).use { cyclone ->
                        cyclone.setExt(
                            cycloneProfile.getString("id"),
                            JSONObject()
                                .put("cloakId", cloak.optString("id"))
                                .put("fingerprint",
                                    cloak.optJSONObject("device")?.optString("fingerprint", "").orEmpty())
                        )
                    }
                } catch (_: Exception) {
                }
                runOnUiThread { toast("Applied. $result") }
            } catch (error: Exception) {
                runOnUiThread { toast("Apply failed: ${error.message}") }
            }
        }
    }

    private fun toast(message: String) =
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
}

