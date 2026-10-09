package dev.cyclone.cloak

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import java.util.concurrent.atomic.AtomicBoolean
import androidx.compose.runtime.remember
import org.json.JSONObject
import java.io.File
import kotlin.concurrent.thread

class MainActivity : ComponentActivity() {
    private val model = CloakUiModel()
    private val creatingIdentity = AtomicBoolean(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CloakTheme {
                val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                    if (uri != null) importProfile(uri)
                }
                val importFleetLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                    if (uri != null) importFleet(uri)
                }
                val exportFleetLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
                    if (uri != null) exportFleet(uri)
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
                    onCreateIdentity = { name, template -> createIdentity(name, template) },
                    onForgeFleet = { count -> forgeFleet(count) },
                    onFleetBind = { fleetBind() },
                    onOpenInCyclone = { profile -> openInCyclone(profile) },
                    onExportFleet = { exportFleetLauncher.launch("cyclone-cloak-fleet.json") },
                    onImportFleet = { importFleetLauncher.launch(arrayOf("application/json")) },
                )
            }
        }
        reload()
    }

    override fun onResume() {
        super.onResume()
        // The owner may have just approved Cloak, or switched profiles: look again.
        if (model.loadedOnce.value) reload()
    }

    private fun reload() {
        model.status.value = "Checking Cyclone"
        thread {
            val appContext = applicationContext
            var providerState = "not registered"
            val report = CycloneBridge.sync(appContext, foreground = true) { cyclone, report ->
                // The startup provider lives as long as this process; register it again on every connect (SPEC §11).
                providerState = when {
                    report.gate?.approved != true -> "not registered"
                    report.gate.minor < 1 -> "Cyclone too old"
                    !report.gate.startup -> "not approved"
                    else -> runCatching { cyclone.registerProfileProvider(CloakStartupProvider(appContext)) }
                        .fold({ "ready" }, { it.message ?: "failed" })
                }
            }
            showReport(report, providerState)
        }
    }

    /** Shows one sync: approval, Cloak's own pill per profile, what can be opened, and why a binding isn't in Cyclone. */
    private fun showReport(report: SyncReport, providerState: String) {
        val appContext = applicationContext
        val cloakProfiles = CloakStore.all(appContext)
        val bindingList = CloakBindingStore.all(appContext)
        val snapshot = report.snapshot
        val placement = report.placement
        val registry = snapshot?.registry.orEmpty()
        val pills = registry.mapNotNull { profile ->
            CloakPills.forProfile(profile, bindingList, report.rootFacts[profile.id])?.let { profile.id to it }
        }.toMap()
        val openTargets = if (report.gate?.openRequests == true && snapshot != null && placement != null) {
            OpenRequests.targets(snapshot, placement)
        } else emptyList()
        runOnUiThread {
            model.loadedOnce.value = true
            model.profiles.clear()
            model.profiles.addAll(registry)
            model.cloakProfiles.clear()
            model.cloakProfiles.addAll(cloakProfiles)
            model.bindings.clear()
            model.bindings.addAll(bindingList)
            model.pills.clear()
            model.pills.putAll(pills)
            model.issues.clear()
            model.issues.putAll(report.issues)
            model.openTargets.clear()
            model.openTargets.addAll(openTargets.map { it.id })
            model.ownProfileId.value = (placement as? Placement.InProfile)?.profile?.id
            model.mainTarget.value = openTargets.firstOrNull { it.isOwner }
            model.canBind.value = report.gate?.bindings == true
            if (model.selectedCloakProfile.value == null && cloakProfiles.isNotEmpty()) {
                model.selectedCloakProfile.value = cloakProfiles.first().first
            }
            model.selectedCycloneProfile.value = model.selectedCycloneProfile.value
                ?.let { selected -> registry.firstOrNull { it.id == selected.id } }
            model.status.value = report.headline
            model.message.value = report.message
            model.providerState.value = providerState
        }
    }

    private fun openInCyclone(profile: CycloneProfile) {
        if (model.opening.value) return
        model.opening.value = true
        thread {
            val outcome = CycloneBridge.requestOpen(applicationContext, profile)
            runOnUiThread {
                model.opening.value = false
                toast(outcome.message)
                if (outcome.refreshProfiles) reload()
            }
        }
    }

    /** Profiles this Cloak may bind: Main binds every ready profile, a profile's Cloak only its own. Never Main itself. */
    private fun bindableHere(profile: CycloneProfile): Boolean =
        profile.bindable && (model.ownProfileId.value == null || model.ownProfileId.value == profile.id)

    /** Loads Cloak profile [cloakId] onto every app of [profile]. Returns how many apps were bound. */
    private fun bindLocally(profile: CycloneProfile, cloakId: String): Int {
        val userId = profile.androidUserId ?: return 0
        // Inside a profile, apps Cloak in Main already binds stay Main's (Main wins an app both bind).
        val managedByMain = CloakBindingStore.all(applicationContext)
            .filter { it.profileId == profile.id && it.origin == CloakBinding.ORIGIN_MAIN }
            .map { it.packageName }.toSet()
        val packages = profile.packages.orEmpty().filter { pkg ->
            pkg !in managedByMain && runCatching { CloakBindingStore.validate(profile.id, userId, pkg) }.isSuccess
        }
        val now = System.currentTimeMillis()
        CloakBindingStore.update(applicationContext) { current ->
            packages.fold(current) { list, pkg ->
                CloakBindingStore.upsertIn(
                    list,
                    CloakBinding(
                        profileId = profile.id,
                        androidUserId = userId,
                        packageName = pkg,
                        cloakProfileId = cloakId,
                        revision = 0,
                        enabled = true,
                        updatedAt = now,
                        state = "pending",
                    ),
                )
            }
        }
        return packages.size
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

    private fun createIdentity(name: String, template: String) {
        val cleanName = name.trim()
        if (cleanName.isBlank()) {
            toast("Enter a name for this device identity.")
            return
        }
        if (!creatingIdentity.compareAndSet(false, true)) return
        model.creatingIdentity.value = true
        thread {
            try {
                val existing = CloakStore.all(applicationContext)
                require(existing.none { it.second.optString("name").equals(cleanName, ignoreCase = true) }) {
                    "An identity with this name already exists. Choose another name."
                }
                val profile = CloakForge.forgeNewProfile(cleanName, template)
                CloakStore.validate(profile)?.let { reason -> throw IllegalArgumentException(reason) }
                val id = CloakStore.save(applicationContext, profile)
                runOnUiThread {
                    model.newIdentityName.value = ""
                    model.selectedCloakProfile.value = id
                    reload()
                    toast("Created $cleanName")
                }
            } catch (error: Exception) {
                runOnUiThread { toast("Could not create device: ${error.message}") }
            } finally {
                creatingIdentity.set(false)
                runOnUiThread { model.creatingIdentity.value = false }
            }
        }
    }

    private fun forgeFleet(count: Int) {
        val template = model.fleetTemplate.value
        thread {
            try {
                val profiles = CloakFleet.forgeFleet(template, count)
                val ids = CloakFleet.saveAll(applicationContext, profiles)
                runOnUiThread {
                    model.selectedCloakProfile.value = ids.first()
                    reload()
                    toast("Forged ${ids.size} identities")
                }
            } catch (error: Exception) {
                runOnUiThread { toast("Fleet forge failed: ${error.message}") }
            }
        }
    }

    private fun fleetBind() {
        val cloakIds = model.cloakProfiles.map { it.first }
        val readyProfiles = model.profiles.filter(::bindableHere)
        val plan = CloakFleet.planBulkBind(cloakIds, readyProfiles, model.bindings.toList())
        if (plan.assignments.isEmpty()) {
            val used = model.cloakProfiles.size - plan.unusedCloakProfiles
            toast(
                when {
                    !model.canBind.value -> "Approve Cyclone Cloak's profile settings in Cyclone → Settings → Connectors first"
                    readyProfiles.isEmpty() -> "No ready Cyclone profiles to bind"
                    plan.unusedCloakProfiles == 0 && used > 0 -> "No unused identities left; forge more first"
                    else -> "Nothing to bind"
                },
            )
            return
        }
        thread {
            try {
                val appContext = applicationContext
                val boundCount = plan.assignments.sumOf { (profile, cloakId) -> bindLocally(profile, cloakId) }
                val publishResult = CloakResolver.rebuildIndexDetailed(appContext)
                runOnUiThread {
                    model.rootDoctor.value = publishResult
                    val note = if (publishResult.published) "" else " ${publishResult.title}: ${publishResult.message}"
                    toast("Bound $boundCount apps across ${plan.assignments.size} profiles.$note")
                    reload()
                }
            } catch (error: Exception) {
                runOnUiThread { toast("Fleet bind failed: ${error.message}") }
            }
        }
    }

    private fun exportFleet(uri: Uri) {
        thread {
            try {
                val profiles = CloakStore.all(applicationContext)
                if (profiles.isEmpty()) {
                    runOnUiThread { toast("No identities to export") }
                    return@thread
                }
                val body = CloakFleet.exportFleet(profiles)
                contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(body.toString(2).toByteArray(Charsets.UTF_8))
                } ?: throw IllegalStateException("cannot open output")
                runOnUiThread { toast("Exported ${profiles.size} identities") }
            } catch (error: Exception) {
                runOnUiThread { toast("Export failed: ${error.message}") }
            }
        }
    }

    private fun importFleet(uri: Uri) {
        thread {
            try {
                val text = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    ?: throw IllegalArgumentException("empty file")
                val profiles = CloakFleet.parseFleet(text)
                val ids = CloakFleet.saveAll(applicationContext, profiles)
                runOnUiThread {
                    model.selectedCloakProfile.value = ids.firstOrNull()
                    reload()
                    toast("Imported ${ids.size} identities")
                }
            } catch (error: Exception) {
                runOnUiThread { toast("Fleet import failed: ${error.message}") }
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
        if (!model.canBind.value) {
            toast("Approve Cyclone Cloak's profile settings in Cyclone → Settings → Connectors first")
            return
        }
        if (!bindableHere(profile)) {
            toast(
                if (profile.bindable) "Bind ${profile.label} from Cyclone Cloak in Main, or from Cloak inside ${profile.label}."
                else "${profile.label} isn't ready in Cyclone yet.",
            )
            return
        }
        thread {
            try {
                val appContext = applicationContext
                val count = bindLocally(profile, cloakId)
                if (count == 0) {
                    runOnUiThread { toast("Cyclone shared no apps for this profile.") }
                    return@thread
                }
                val publishResult = CloakResolver.rebuildIndexDetailed(appContext)
                runOnUiThread {
                    model.rootDoctor.value = publishResult
                    toast(
                        if (publishResult.published) "Bound $count apps."
                        else "Bound $count apps. ${publishResult.title}: ${publishResult.message}",
                    )
                    reload()
                }
            } catch (error: Exception) {
                runOnUiThread { toast("Bind failed: ${error.message}") }
            }
        }
    }

    private fun toggleBinding(binding: CloakBinding) {
        if (binding.origin == CloakBinding.ORIGIN_MAIN) {
            toast("This binding is managed by Cyclone Cloak in Main.")
            return
        }
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
        if (binding.origin == CloakBinding.ORIGIN_MAIN) {
            toast("This binding is managed by Cyclone Cloak in Main.")
            return
        }
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
            val result = CloakResolver.rebuildIndexDetailed(applicationContext, repairModule = true)
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
