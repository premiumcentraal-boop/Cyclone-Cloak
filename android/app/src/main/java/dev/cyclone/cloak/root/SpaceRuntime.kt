package dev.cyclone.cloak.root

import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.os.Build
import android.os.UserManager
import dev.cyclone.cloak.data.CloakBindingStore
import dev.cyclone.cloak.data.CloakSpace
import dev.cyclone.cloak.data.CloakSpaceStore
import dev.cyclone.cloak.data.CloakStore
import dev.cyclone.cloak.data.InstallPlacement
import dev.cyclone.cloak.data.PhoneStore
import dev.cyclone.cloak.forge.CloakForge
import dev.cyclone.cloak.forge.PhoneCatalog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** An app Cloak can put in a space: its label and package, from Main's launcher list. */
data class SpaceApp(val label: String, val packageName: String)

/** What the spaces card shows: whether an action is running, and the last message. */
data class SpaceStatus(val busy: Boolean = false, val message: String = "")

/**
 * Cloak's profile maker and switcher. Runs only when the owner asks, only from Main, and only on Android 13 and up.
 * Each action opens one [PrivilegedSession]. The same session serves the publish that follows a delete. Failures
 * become the status message, so nothing escapes to the UI thread.
 */
object SpaceRuntime {
    private const val MIN_SDK = 33
    private val lock = Any()
    private val _status = MutableStateFlow(SpaceStatus())
    val status: StateFlow<SpaceStatus> = _status.asStateFlow()

    /** Whether this install can manage spaces: Android 13 and up, from Main. */
    fun available(): Boolean = Build.VERSION.SDK_INT >= MIN_SDK && InstallPlacement.myUser() == SpacePlan.MAIN_USER

    fun spaces(context: Context): List<CloakSpace> = CloakSpaceStore.all(context)

    /** The space Cloak last opened, or null when the owner is in Main. */
    fun current(context: Context): String? = prefs(context).getString(KEY_CURRENT, null)

    fun launchableApps(context: Context): List<SpaceApp> = context.packageManager.queryIntentActivities(
        Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), PackageManager.MATCH_ALL)
        .filter { it.activityInfo.packageName != context.packageName }
        .map { SpaceApp(it.loadLabel(context.packageManager).toString(), it.activityInfo.packageName) }
        .distinctBy { it.packageName }
        .sortedBy { it.label.lowercase() }

    /** Starts a new space with [packages]. Returns its id, or null when it could not start. */
    fun create(context: Context, label: String, packages: List<String>): String? {
        synchronized(lock) {
            if (!claim()) return null
            val space = try {
                val now = System.currentTimeMillis()
                CloakSpace(
                    id = CloakSpace.newId(),
                    label = label.trim(),
                    androidUserId = 0,
                    packages = packages,
                    identityId = null,
                    state = CloakSpace.STATE_CREATING,
                    createdAt = now,
                    updatedAt = now,
                ).also { CloakSpaceStore.save(context, it) }
            } catch (error: Exception) {
                _status.value = SpaceStatus(message = "Cloak couldn’t save the new space. Try again.")
                return null
            }
            runCreate(context, space)
            return space.id
        }
    }

    /** Continues a space whose creation stopped partway. Its saved user, identity and apps are reused, not duplicated. */
    fun resume(context: Context, id: String) {
        synchronized(lock) {
            if (!claim()) return
            val space = load(context, id)?.takeIf { it.state == CloakSpace.STATE_CREATING } ?: return
            runCreate(context, space)
        }
    }

    /** Starts [id] and opens [packageName] in it, then records the space as current. */
    fun open(context: Context, id: String, packageName: String) {
        synchronized(lock) {
            if (!claim()) return
            val space = load(context, id)?.takeIf { it.ready && packageName in it.packages } ?: return
            _status.value = SpaceStatus(busy = true, message = "Opening ${space.label}…")
            _status.value = try {
                withRoot { shell -> flows(context, shell).open(space, packageName) { target -> launch(context, target) } }
                prefs(context).edit().putString(KEY_CURRENT, id).apply()
                SpaceStatus()
            } catch (error: Exception) {
                SpaceStatus(message = failure(error))
            }
        }
    }

    /** Brings Cloak back to the front in Main and forgets the current space. */
    fun backToMain(context: Context) {
        prefs(context).edit().remove(KEY_CURRENT).apply()
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?: throw SpaceException("Cloak can’t open its own screen on this phone.")
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** Removes [id]: its bindings first, then the published state, then the Android user. */
    fun delete(context: Context, id: String) {
        synchronized(lock) {
            if (!claim()) return
            val space = load(context, id) ?: return
            _status.value = SpaceStatus(busy = true, message = "Deleting ${space.label}…")
            _status.value = try {
                withRoot { shell ->
                    flows(context, shell).delete(
                        space,
                        unbind = { CloakBindingStore.update(context) { bindings -> bindings.filterNot { it.profileId == id } } },
                        republish = { CloakResolver.rebuildIndexDetailed(context).published },
                        forgetIdentity = { identity -> forgetIdentityIfUnused(context, identity) },
                    )
                }
                if (prefs(context).getString(KEY_CURRENT, null) == id) prefs(context).edit().remove(KEY_CURRENT).apply()
                SpaceStatus(message = "Deleted ${space.label}.")
            } catch (error: Exception) {
                SpaceStatus(message = failure(error))
            }
        }
    }

    /** Claims the runner for one action. False when another action is running or this install can't manage spaces. */
    private fun claim(): Boolean {
        if (_status.value.busy) return false
        if (!available()) {
            _status.value = SpaceStatus(message = "Spaces need Android 13 or newer, and are managed from Main.")
            return false
        }
        return true
    }

    private fun load(context: Context, id: String): CloakSpace? = try {
        CloakSpaceStore.find(context, id)
    } catch (error: Exception) {
        _status.value = SpaceStatus(message = "Cloak can’t read its saved spaces, so nothing was changed.")
        null
    }

    private fun runCreate(context: Context, space: CloakSpace) {
        _status.value = SpaceStatus(busy = true, message = "Setting up ${space.label}…")
        _status.value = try {
            withRoot { shell -> flows(context, shell).create(space) { seedIdentity(context, space.label) } }
            SpaceStatus(message = "${space.label} is ready.")
        } catch (error: Exception) {
            SpaceStatus(message = failure(error))
        }
    }

    private fun flows(context: Context, shell: SpaceShell) = SpaceFlows(
        shell = shell,
        save = { CloakSpaceStore.save(context, it) },
        forget = { CloakSpaceStore.remove(context, it) },
    )

    private fun seedIdentity(context: Context, label: String): String {
        val template = PhoneCatalog.parse(PhoneStore.catalogText(context)).firstOrNull { it.builtIn }
            ?: throw SpaceException("Cloak has no built-in phone template to seed this space.")
        return CloakStore.save(context, CloakForge.forgeNewProfile(label, template))
    }

    private fun forgetIdentityIfUnused(context: Context, identity: String) {
        if (CloakBindingStore.all(context).none { it.cloakProfileId == identity }) CloakStore.delete(context, identity)
    }

    /** Launches [target]'s app in its space. Refuses unless exactly one profile on this phone matches. */
    private fun launch(context: Context, target: SpaceTarget) {
        val launcher = context.getSystemService(LauncherApps::class.java)
        val matches = context.getSystemService(UserManager::class.java).userProfiles.mapNotNull { handle ->
            launcher.getActivityList(target.packageName, handle).firstOrNull()?.let { handle to it }
        }.filter { (_, activity) -> activity.applicationInfo.uid / ANDROID_UIDS_PER_USER == target.androidUserId }
        val (handle, activity) = matches.singleOrNull()
            ?: throw SpaceException("${target.packageName} isn’t in this space. Add it, then try again.")
        launcher.startMainActivity(activity.componentName, handle, null, null)
    }

    private fun <T> withRoot(block: (SpaceShell) -> T): T {
        PrivilegedSession(budgetMs = FLOW_BUDGET_MS).use { session ->
            val preflight = session.preflight()
            if (preflight.code != RootDoctorCode.READY) throw SpaceException(rootMessage(preflight.code))
            // Publishing inside a delete reuses this session instead of asking Magisk a second time.
            return RootSessions.use(session) { block(SpaceShell { args -> runCommand(session, args) }) }
        }
    }

    private fun runCommand(session: PrivilegedSession, args: List<String>): String {
        val result = session.executeWithBudget(SpacePlan.shell(args), FLOW_BUDGET_MS, OUTPUT_MAX)
        if (result.timedOut) throw SpaceException("Android is taking too long. Your progress is saved; try again.")
        if (result.exitCode != 0) throw SpaceException("Android couldn’t complete this step. Check that extra profiles are allowed and try again.")
        return result.output
    }

    private fun rootMessage(code: RootDoctorCode): String = when (code) {
        RootDoctorCode.ROOT_TIMEOUT -> "Magisk approval timed out. Approve Cyclone Cloak, then try again."
        else -> "Allow Cyclone Cloak in your root manager, then try again."
    }

    private fun failure(error: Exception): String =
        (error as? SpaceException)?.message ?: "Cloak couldn’t finish that. Try again."

    private fun prefs(context: Context) = context.getSharedPreferences("cloak_spaces", Context.MODE_PRIVATE)

    private const val KEY_CURRENT = "current"
    private const val FLOW_BUDGET_MS = 300_000L
    private const val OUTPUT_MAX = 256_000
    private const val ANDROID_UIDS_PER_USER = 100_000
}
