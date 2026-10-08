package dev.cyclone.cloak

import android.content.Context
import android.util.Log
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
        if (rebuildIndex(context)) {
            cache[cacheId] = json
        } else {
            cache.remove(cacheId)
        }
        return binding.cloakProfileId
    }

    fun invalidate(profileId: String, androidUserId: Int, packageName: String) {
        cache.remove(cacheKey(profileId, androidUserId, packageName))
    }

    fun clearState(context: Context, profileId: String, androidUserId: Int, packageName: String): Boolean {
        invalidate(profileId, androidUserId, packageName)
        runCatching {
            stagedFile(context, CloakStateLayout.key(profileId, androidUserId, packageName))
                .parentFile?.deleteRecursively()
        }
        return rebuildIndex(context)
    }

    /**
     * Rebuilds the staged tree from the current enabled bindings and publishes it
     * to the module's root-only state directory. Returns publish success.
     */
    fun rebuildIndex(context: Context): Boolean {
        val staging = stagingRoot(context)
        val staged = runCatching {
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
        }.onFailure { Log.e("CloakPublish", "Could not stage profile state", it) }.isSuccess
        val published = staged && publish(context, staging)
        for (binding in CloakBindingStore.all(context)) {
            val state = when {
                !binding.enabled && published -> "disabled"
                !published -> "publish failed"
                CloakStore.find(context, binding.cloakProfileId) == null -> "missing"
                else -> "ready"
            }
            if (binding.state != state) CloakBindingStore.markState(context, binding, state)
        }
        return published
    }

    /**
     * Publishes the staged tree via Magisk su: copy and validate a private temp
     * tree, replace the published state, then purge legacy state from older releases.
     */
    private fun publish(context: Context, staging: File): Boolean {
        val legacyDirs = CloakBindingStore.all(context)
            .map { it.androidUserId }.distinct()
            .map { "/data/user/$it/dev.cyclone.cloak/no_backup/${CloakStateLayout.ROOT_DIR}" }
        val root = "/data/adb/cyclone_cloak"
        val temp = "${CloakStateLayout.MODULE_STATE_DIR}.tmp"
        val backup = "${CloakStateLayout.MODULE_STATE_DIR}.previous"
        fun quote(value: String) = "'" + value.replace("'", "'\\''") + "'"
        val script = buildString {
            append("set -eu; umask 077; ")
            append("mkdir -p ${quote(root)}; ")
            append("rm -rf ${quote(temp)} ${quote(backup)}; ")
            append("cp -R ${quote(staging.absolutePath)} ${quote(temp)}; ")
            append("test -s ${quote("$temp/${CloakStateLayout.INDEX_FILE}")}; ")
            append("chmod -R go-rwx ${quote(temp)}; ")
            append("chmod 700 ${quote(root)} ${quote(temp)}; ")
            append("if [ -e ${quote(CloakStateLayout.MODULE_STATE_DIR)} ]; then mv ${quote(CloakStateLayout.MODULE_STATE_DIR)} ${quote(backup)}; fi; ")
            append("if mv ${quote(temp)} ${quote(CloakStateLayout.MODULE_STATE_DIR)}; then rm -rf ${quote(backup)} || true; ")
            append("else if [ -e ${quote(backup)} ]; then mv ${quote(backup)} ${quote(CloakStateLayout.MODULE_STATE_DIR)}; fi; exit 1; fi; ")
            for (dir in legacyDirs) append("rm -rf ${quote(dir)}; ")
            append("test -s ${quote("${CloakStateLayout.MODULE_STATE_DIR}/${CloakStateLayout.INDEX_FILE}")}")
        }
        return runCatching {
            // App processes may have a private mount namespace that hides /data/adb.
            val process = ProcessBuilder("su", "--mount-master", "-c", script)
                .redirectErrorStream(true)
                .start()
            val output = StringBuilder()
            val outputReader = Thread {
                runCatching {
                    process.inputStream.bufferedReader().use { reader ->
                        val buffer = CharArray(512)
                        while (true) {
                            val count = reader.read(buffer)
                            if (count < 0) break
                            synchronized(output) {
                                if (output.length < 2048) {
                                    output.append(buffer, 0, minOf(count, 2048 - output.length))
                                }
                            }
                        }
                    }
                }
            }.apply {
                isDaemon = true
                start()
            }
            val finished = process.waitFor(15, TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                process.waitFor(1, TimeUnit.SECONDS)
                outputReader.join(1000)
                Log.e("CloakPublish", "Root publish timed out")
                false
            } else {
                outputReader.join(1000)
                val details = synchronized(output) { output.toString().trim() }
                val exitCode = process.exitValue()
                Log.d("CloakPublish", "su finished=true exit=$exitCode output=$details")
                exitCode == 0
            }
        }.onFailure { Log.e("CloakPublish", "Could not start root publish", it) }.getOrDefault(false)
    }
}
