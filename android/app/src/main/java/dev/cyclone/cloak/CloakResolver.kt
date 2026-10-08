package dev.cyclone.cloak

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.File

/**
 * Delivers bound profiles to the platform module: builds a staging tree from the
 * current bindings, then publishes it via su to the module's root-only state
 * directory (docs/STATE_LAYOUT.md v2). The Cyclone startup callback only checks
 * the already-published binding so it stays within Cyclone's short deadline.
 */
object CloakResolver {
    fun entryKey(androidUserId: Int, packageName: String) = "$androidUserId/$packageName"

    private fun stagingRoot(context: Context) = File(context.cacheDir, CloakStateLayout.STAGING_DIR)

    /** Returns the bound cloak-profile id, or null when unbound or missing. */
    fun resolve(context: Context, profileId: String, androidUserId: Int, packageName: String): String? {
        val binding = CloakBindingStore.find(context, profileId, androidUserId, packageName) ?: return null
        if (!binding.enabled) return null
        if (!CloakRootDoctor.verifiedForCurrentApp(context) || binding.state != "ready") return null
        val profile = CloakStore.find(context, binding.cloakProfileId)
        if (profile == null) {
            CloakBindingStore.markState(context, binding, "missing")
            return null
        }
        return binding.cloakProfileId
    }

    fun clearStateDetailed(context: Context): RootDoctorResult {
        return rebuildIndexDetailed(context)
    }

    /**
     * Rebuilds the staged tree from the current enabled bindings and publishes it
     * to the module's root-only state directory. Returns publish success.
     */
    fun rebuildIndex(context: Context): Boolean = rebuildIndexDetailed(context).published

    fun rebuildIndexDetailed(context: Context, repairModule: Boolean = false): RootDoctorResult {
        val staging = stagingRoot(context)
        val staged = runCatching {
            staging.deleteRecursively()
            staging.mkdirs()
            val entries = JSONObject()
            for (binding in CloakBindingStore.all(context)) {
                if (!binding.enabled) continue
                val profile = CloakStore.find(context, binding.cloakProfileId) ?: continue
                val key = CloakStateLayout.key(binding.profileId, binding.androidUserId, binding.packageName)
                val bindingDir = File(staging, key)
                bindingDir.mkdirs()
                File(bindingDir, CloakStateLayout.PROFILE_FILE).writeText(profile.toString())
                File(bindingDir, CloakStateLayout.PIF_FILE)
                    .writeText(CloakPif.fromProfile(profile).toString())
                entries.put(
                    entryKey(binding.androidUserId, binding.packageName),
                    JSONObject()
                        .put("profileId", binding.profileId)
                        .put("cloakProfileId", binding.cloakProfileId)
                        .put("key", key),
                )
            }
            File(staging, CloakStateLayout.INDEX_FILE)
                .writeText(JSONObject().put("schemaVersion", 2).put("entries", entries).toString())
        }.onFailure { Log.e("CloakPublish", "Could not stage profile state", it) }.isSuccess
        val result = if (staged) {
            CloakRootDoctor.run(context, staging, repairModule)
        } else {
            RootDoctorResult(RootDoctorCode.PUBLISH_FAILED)
        }
        for (binding in CloakBindingStore.all(context)) {
            val state = when {
                !binding.enabled && result.published -> "disabled"
                CloakStore.find(context, binding.cloakProfileId) == null -> "missing"
                result.published -> "ready"
                else -> when (result.code) {
                    RootDoctorCode.ROOT_REQUIRED -> "root approval needed"
                    RootDoctorCode.ROOT_TIMEOUT -> "Magisk approval timed out"
                    RootDoctorCode.ROOT_UNAVAILABLE -> "Magisk unavailable"
                    RootDoctorCode.MODULE_MISSING -> "module missing"
                    RootDoctorCode.MODULE_DISABLED -> "module disabled"
                    RootDoctorCode.MODULE_PENDING_REMOVAL -> "module removal pending"
                    RootDoctorCode.MODULE_REBOOT_REQUIRED -> "module update needs reboot"
                    RootDoctorCode.MODULE_OUTDATED -> "module update needed"
                    RootDoctorCode.MODULE_BUNDLE_INVALID -> "module package invalid"
                    RootDoctorCode.MODULE_INSTALL_FAILED -> "module install failed"
                    RootDoctorCode.ZYGISK_DISABLED -> "Zygisk disabled"
                    RootDoctorCode.ZYGISK_STATUS_UNKNOWN -> "Zygisk status unknown"
                    RootDoctorCode.ABI_UNSUPPORTED -> "unsupported architecture"
                    else -> "publish failed"
                }
            }
            if (binding.state != state) CloakBindingStore.markState(context, binding, state)
        }
        return result
    }
}
