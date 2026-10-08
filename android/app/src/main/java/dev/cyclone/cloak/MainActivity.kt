package dev.cyclone.cloak

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.remember
import com.cyclone.connector.client.CycloneConnector
import com.cyclone.connector.client.CycloneConnectorException
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.concurrent.thread

class MainActivity : ComponentActivity() {
    private val model = CloakUiModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CloakTheme {
                val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                    if (uri != null) importProfile(uri)
                }
                CloakUi(
                    model = model,
                    onImport = { importLauncher.launch(arrayOf("application/json")) },
                    onReload = { reload() },
                    onApply = { applySelected() },
                    onSelectCloak = { id -> model.selectedCloakProfile.value = id },
                    onSelectProfile = { profile -> model.selectedCycloneProfile.value = profile },
                    onToggleBinding = { binding -> toggleBinding(binding) },
                    onRemoveBinding = { binding -> removeBinding(binding) },
                    onRootDoctor = { runRootDoctor() },
                    onOpenMagisk = { openMagisk() },
                    onGetModule = { openModuleDownload() },
                )
            }
        }
        reload()
    }

    private fun reload() {
        model.status.value = "Checking Cyclone"
        thread {
            val appContext = applicationContext
            try {
                CycloneConnector.connect(appContext).use { cyclone ->
                    val hello = cyclone.hello()
                    val profilesResult = cyclone.profiles()
                    val providerResult = runCatching {
                        cyclone.registerProfileProvider(CloakStartupProvider(appContext))
                    }
                    val profileList = profilesResult.optJSONArray("profiles")?.let { array ->
                        (0 until array.length()).mapNotNull { array.optJSONObject(it) }
                            .filter { it.optString("kind") == "profile" && it.optString("state") == "ready" }
                    } ?: emptyList()
                    val cloakProfiles = CloakStore.all(appContext)
                    val bindingList = CloakBindingStore.all(appContext)
                    val granted = hello.optJSONArray("granted")?.let { array ->
                        (0 until array.length()).map { array.optString(it) }
                    } ?: emptyList()
                    runOnUiThread {
                        model.profiles.clear()
                        model.profiles.addAll(profileList)
                        model.cloakProfiles.clear()
                        model.cloakProfiles.addAll(cloakProfiles)
                        model.bindings.clear()
                        model.bindings.addAll(bindingList)
                        if (model.selectedCloakProfile.value == null && cloakProfiles.isNotEmpty()) {
                            model.selectedCloakProfile.value = cloakProfiles.first().first
                        }
                        model.status.value = if (hello.optBoolean("approved")) "Connected" else "Needs approval"
                        model.message.value = "Scopes: " + granted.joinToString(", ")
                        model.providerState.value = when {
                            hello.optInt("minor", 0) < 1 -> "Cyclone too old"
                            providerResult.isSuccess -> "ready"
                            else -> providerResult.exceptionOrNull()?.message ?: "failed"
                        }
                    }
                }
            } catch (error: CycloneConnectorException) {
                runOnUiThread {
                    model.status.value = "Connector failed"
                    model.message.value = "${error.code}: ${error.message}"
                    model.providerState.value = "not registered"
                }
            } catch (error: Exception) {
                runOnUiThread {
                    model.status.value = "Connector failed"
                    model.message.value = error.message ?: "unknown error"
                    model.providerState.value = "not registered"
                }
            }
        }
    }

    private fun importProfile(uri: Uri) {
        thread {
            try {
                val text = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    ?: throw IllegalArgumentException("empty file")
                val profile = JSONObject(text)
                CloakStore.validate(profile)?.let { reason -> throw IllegalArgumentException(reason) }
                val id = CloakStore.save(applicationContext, profile)
                runOnUiThread {
                    model.selectedCloakProfile.value = id
                    reload()
                    toast("Imported $id")
                }
            } catch (error: Exception) {
                runOnUiThread { toast("Import failed: ${error.message}") }
            }
        }
    }

    private fun applySelected() {
        val cloakId = model.selectedCloakProfile.value
        val profile = model.selectedCycloneProfile.value
        if (cloakId == null) {
            toast("Select a cloak identity first")
            return
        }
        if (profile == null) {
            toast("Select a Cyclone profile first")
            return
        }
        thread {
            try {
                var publishResult = RootDoctorResult(RootDoctorCode.PUBLISH_FAILED)
                val appContext = applicationContext
                val profileId = profile.getString("id")
                val userId = profile.optInt("androidUserId", 0)
                val packages = profile.optJSONArray("packages")?.let { array ->
                    (0 until array.length()).map { array.optString(it) }
                } ?: emptyList()
                if (packages.isEmpty()) {
                    runOnUiThread { toast("Cyclone shared no apps for this profile.") }
                    return@thread
                }
                CycloneConnector.connect(appContext).use { cyclone ->
                    for (pkg in packages) {
                        val binding = CloakBinding(
                            profileId = profileId,
                            androidUserId = userId,
                            packageName = pkg,
                            cloakProfileId = cloakId,
                            revision = 0,
                            enabled = true,
                            updatedAt = System.currentTimeMillis(),
                            state = "pending",
                        )
                        CloakBindingStore.upsert(appContext, binding)
                        cyclone.setConfig(
                            profileId,
                            userId,
                            pkg,
                            JSONObject().put("cloakProfileId", cloakId),
                        )
                    }
                    publishResult = CloakResolver.rebuildIndexDetailed(appContext)
                }
                runOnUiThread {
                    model.rootDoctor.value = publishResult
                    toast(
                        if (publishResult.published) "Bound ${packages.size} apps."
                        else "Bound ${packages.size} apps. ${publishResult.title}: ${publishResult.message}",
                    )
                    reload()
                }
            } catch (error: Exception) {
                runOnUiThread { toast("Bind failed: ${error.message}") }
            }
        }
    }


    private fun toggleBinding(binding: CloakBinding) {
        thread {
            val updated = binding.copy(enabled = !binding.enabled, updatedAt = System.currentTimeMillis())
            CloakBindingStore.upsert(applicationContext, updated)
            val publishResult = if (!updated.enabled) {
                CloakResolver.clearStateDetailed(applicationContext)
            } else {
                CloakResolver.rebuildIndexDetailed(applicationContext)
            }
            runOnUiThread {
                model.rootDoctor.value = publishResult
                if (!publishResult.published) toast("${publishResult.title}: ${publishResult.message}")
                reload()
            }
        }
    }

    private fun removeBinding(binding: CloakBinding) {
        thread {
            CloakBindingStore.remove(applicationContext, binding.profileId, binding.androidUserId, binding.packageName)
            val publishResult = CloakResolver.clearStateDetailed(applicationContext)
            runOnUiThread {
                model.rootDoctor.value = publishResult
                if (!publishResult.published) toast("Binding removed. ${publishResult.title}: ${publishResult.message}")
                reload()
            }
        }
    }

    private fun runRootDoctor() {
        model.rootDoctor.value = RootDoctorResult(RootDoctorCode.CHECKING)
        thread {
            val result = CloakResolver.rebuildIndexDetailed(applicationContext)
            runOnUiThread {
                model.rootDoctor.value = result
                reload()
            }
        }
    }

    private fun openMagisk() {
        val intent = packageManager.getLaunchIntentForPackage("com.topjohnwu.magisk")
        if (intent == null) {
            toast("Magisk app not found. Install Magisk, then check root setup again.")
            return
        }
        startActivity(intent)
    }

    private fun openModuleDownload() {
        @Suppress("DEPRECATION")
        val version = packageManager.getPackageInfo(packageName, 0).versionName.orEmpty()
        val asset = "https://github.com/premiumcentraal-boop/Cyclone-Cloak/releases/download/v$version/cyclone-cloak-$version.zip"
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(asset)))
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()
}
