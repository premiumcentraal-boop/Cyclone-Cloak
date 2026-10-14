package dev.cyclone.cloak.root

import dev.cyclone.cloak.diagnostics.LogBus
import dev.cyclone.cloak.diagnostics.Transition
import dev.cyclone.cloak.data.*
import dev.cyclone.cloak.forge.*
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

    // Every invocation owns its staging snapshot, including callers in another app process.
    private fun stagingRoot(context: Context) = java.nio.file.Files.createTempDirectory(
        context.cacheDir.toPath(), "${CloakStateLayout.STAGING_DIR}-",
    ).toFile()

    /** Returns the bound cloak-profile id, or null when unbound or missing. */
    fun resolve(context: Context, profileId: String, androidUserId: Int, packageName: String): String? {
        val binding = CloakBindingStore.find(context, profileId, androidUserId, packageName) ?: return null
        if (!binding.enabled) return null
        // Main's binding, mirrored here: its identity file lives in Cloak in Main and in the published state, and the
        // mirror's own root check set its state.
        if (binding.origin == CloakBinding.ORIGIN_MAIN) return binding.cloakProfileId.takeIf { binding.state == "ready" }
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

    fun rebuildIndexDetailed(
        context: Context,
        repairModule: Boolean = false,
        scope: PublishScope = PublishScope.forThisInstall(context),
    ): RootDoctorResult = LogBus.trace("publication", "publish") {
        rebuildIndexObserved(context, repairModule, scope).also { result ->
            LogBus.event("publication", "result", if (result.published) Transition.COMPLETED else Transition.DEGRADED,
                reason = result.publicationReason?.name ?: result.code.name)
            if (result.staleLockDetected) LogBus.event("publication", "lock", Transition.DEGRADED, reason = "STALE_LOCK")
        }
    }

    private fun rebuildIndexObserved(context: Context, repairModule: Boolean, scope: PublishScope): RootDoctorResult {
        val staging = runCatching { LogBus.trace("publication", "staging-create") { stagingRoot(context) } }.getOrElse {
            LogBus.event("publication", "stage", Transition.FAILED, reason = "STAGING_FAILED", error = it)
            Log.e("CloakPublish", "Could not create staging snapshot", it)
            return RootDoctorResult(RootDoctorCode.PUBLISH_FAILED, publicationReason = PublicationReason.FAILED)
        }
        val staged = runCatching {
            check(staging.isDirectory) { "Staging directory unavailable" }
            val entries = JSONObject()
            for (binding in CloakBindingStore.all(context)) {
                if (!binding.enabled || !scope.publishes(binding)) continue
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
                        .put("key", key)
                        // Read by Cloak in the profile (to mirror Main's bindings), never by the module.
                        .put("publisher", scope.androidUserId)
                        .put("summary", CloakIdentity.summary(binding.cloakProfileId, profile)),
                )
            }
            // This install's share of the shared index; CloakRootDoctor assembles every share into index.json.
            File(staging, CloakStateLayout.INDEX_PART_FILE).writeText(indexPart(entries))
        }.onFailure {
            LogBus.event("publication", "stage", Transition.FAILED, reason = "STAGING_FAILED", error = it)
            Log.e("CloakPublish", "Could not stage profile state", it)
        }.isSuccess
        val result = if (staged) {
            CloakRootDoctor.run(context, staging, repairModule, scope)
        } else {
            RootDoctorResult(RootDoctorCode.PUBLISH_FAILED, publicationReason = PublicationReason.FAILED)
        }
        staging.deleteRecursively()
        for (binding in CloakBindingStore.all(context)) {
            // Mirrored bindings are Main's to publish; their health comes from the mirror check.
            if (!scope.publishes(binding)) continue
            val state = when {
                !binding.enabled && result.published -> "disabled"
                CloakStore.find(context, binding.cloakProfileId) == null -> "missing"
                result.published -> "ready"
                else -> localState(result)
            }
            if (binding.state != state) CloakBindingStore.markState(context, binding, state)
        }
        return result
    }

    /** The members of [entries] without the surrounding braces, so shares can be joined with commas. Pure. */
    internal fun indexPart(entries: JSONObject): String = entries.toString().removePrefix("{").removeSuffix("}")

    /** Cloak's local state for a Root Doctor result that didn't publish. */
    fun localState(result: RootDoctorResult): String = if (result.published) "ready" else when (result.code) {
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
