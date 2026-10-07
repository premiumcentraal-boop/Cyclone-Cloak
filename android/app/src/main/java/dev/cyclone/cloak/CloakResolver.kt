package dev.cyclone.cloak

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Delivers bound profiles to the platform module: builds a staging tree from the
 * current bindings, then publishes it via su to the module's root-only state
 * directory (docs/STATE_LAYOUT.md v2). Resolution results are cached so repeat
 * launches stay well inside the connector deadline; the su write itself only
 * happens on binding changes and first resolves.
 */
object CloakResolver {
    private val cache = ConcurrentHashMap<String, String>()

    fun entryKey(androidUserId: Int, packageName: String) = "$androidUserId/$packageName"

    fun cacheKey(profileId: String, androidUserId: Int, packageName: String) =
        "$profileId|$androidUserId|$packageName"

    private fun stagingRoot(context: Context) = File(context.cacheDir, CloakStateLayout.STAGING_DIR)

    private fun stagedFile(context: Context, key: String) =
        File(File(stagingRoot(context), key), CloakStateLayout.PROFILE_FILE)

    /** Returns the bound cloak-profile id, or null when unbound or missing. */
    fun resolve(context: Context, profileId: String, androidUserId: Int, packageName: String): String? {
        val binding = CloakBindingStore.find(context, profileId, androidUserId, packageName) ?: return null
        if (!binding.enabled) return null
        val cacheId = cacheKey(profileId, androidUserId, packageName)
        cache[cacheId]?.let { return binding.cloakProfileId }
        val profile = CloakStore.find(context, binding.cloakProfileId)
        if (profile == null) {
            CloakBindingStore.markState(context, binding, "missing")
            return null
        }
        val json = profile.toString()
        stagedFile(context, CloakStateLayout.key(profileId, androidUserId, packageName)).let { file ->
            file.parentFile?.mkdirs()
            file.writeText(json)
        }
        if (!rebuildIndex(context)) {
            // The module may still serve the previous published tree; the UI shows
            // the degraded state so the user knows this binding needs attention.
            CloakBindingStore.markState(context, binding, "degraded")
        }
        cache[cacheId] = json
        return binding.cloakProfileId
    }

    fun invalidate(profileId: String, androidUserId: Int, packageName: String) {
        cache.remove(cacheKey(profileId, androidUserId, packageName))
    }

    fun clearState(context: Context, profileId: String, androidUserId: Int, packageName: String) {
        invalidate(profileId, androidUserId, packageName)
        runCatching {
            stagedFile(context, CloakStateLayout.key(profileId, androidUserId, packageName))
                .parentFile?.deleteRecursively()
        }
        rebuildIndex(context)
    }

    /**
     * Rebuilds the staged tree from the current enabled bindings and publishes it
     * to the module's root-only state directory. Returns publish success.
     */
    fun rebuildIndex(context: Context): Boolean {
        val staging = stagingRoot(context)
        runCatching {
            staging.deleteRecursively()
            staging.mkdirs()
            val entries = JSONObject()
            for (binding in CloakBindingStore.all(context)) {
                if (!binding.enabled) continue
                val profile = CloakStore.find(context, binding.cloakProfileId) ?: continue
                val key = CloakStateLayout.key(binding.profileId, binding.androidUserId, binding.packageName)
                val file = File(File(staging, key), CloakStateLayout.PROFILE_FILE)
                file.parentFile?.mkdirs()
                file.writeText(profile.toString())
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
        }.onFailure { return false }
        return publish(context, staging)
    }

    /**
     * Publishes the staged tree via su: copy into a temp dir, flip permissions to
     * root-only, atomically swap into place, and purge legacy world-readable state
     * left by older releases.
     */
    private fun publish(context: Context, staging: File): Boolean {
        val legacyDirs = CloakBindingStore.all(context)
            .map { it.androidUserId }.distinct()
            .map { "/data/user/$it/dev.cyclone.cloak/no_backup/${CloakStateLayout.ROOT_DIR}" }
        val script = buildString {
            append("mkdir -p '/data/adb/cyclone_cloak'; ")
            append("rm -rf '/data/adb/cyclone_cloak/state-v1.tmp'; ")
            append("cp -r '${staging.absolutePath}' '/data/adb/cyclone_cloak/state-v1.tmp'; ")
            append("chmod 700 '/data/adb/cyclone_cloak' '/data/adb/cyclone_cloak/state-v1.tmp'; ")
            append("rm -rf '${CloakStateLayout.MODULE_STATE_DIR}'; ")
            append("mv '/data/adb/cyclone_cloak/state-v1.tmp' '${CloakStateLayout.MODULE_STATE_DIR}'; ")
            for (dir in legacyDirs) append("rm -rf '$dir'; ")
        }
        return runCatching {
            val process = ProcessBuilder("su", "-c", script)
                .redirectErrorStream(true)
                .start()
            val finished = process.waitFor(15, TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                false
            } else {
                process.exitValue() == 0
            }
        }.getOrDefault(false)
    }
}