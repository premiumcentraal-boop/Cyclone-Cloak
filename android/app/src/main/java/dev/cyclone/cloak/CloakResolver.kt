package dev.cyclone.cloak

import android.content.Context
import com.cyclone.connector.client.ProfileBehaviorProvider
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Delivers bound profiles to the platform module: resolves a binding, writes the
 * resolved profile into the connector state directory, and keeps the module-facing
 * index current. Resolution results are cached so repeat launches stay well inside
 * the connector deadline.
 */
object CloakResolver {
    private val cache = ConcurrentHashMap<String, String>()

    fun entryKey(androidUserId: Int, packageName: String) = "$androidUserId/$packageName"

    fun cacheKey(profileId: String, androidUserId: Int, packageName: String) =
        "$profileId|$androidUserId|$packageName"

    /** Returns the bound cloak-profile id, or null when unbound or disabled. */
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
        val dir = ProfileBehaviorProvider.stateDirectory(context, profileId, androidUserId, packageName)
        val file = File(dir, CloakStateLayout.PROFILE_FILE)
        file.parentFile?.mkdirs()
        file.writeText(json)
        file.setReadable(true, false)
        dir.apply {
            setReadable(true, false)
            setExecutable(true, false)
        }
        cache[cacheId] = json
        rebuildIndex(context)
        return binding.cloakProfileId
    }

    fun invalidate(profileId: String, androidUserId: Int, packageName: String) {
        cache.remove(cacheKey(profileId, androidUserId, packageName))
    }

    fun clearState(context: Context, profileId: String, androidUserId: Int, packageName: String) {
        invalidate(profileId, androidUserId, packageName)
        runCatching {
            val dir = ProfileBehaviorProvider.stateDirectory(context, profileId, androidUserId, packageName)
            dir.listFiles()?.forEach { it.delete() }
            dir.delete()
        }
        rebuildIndex(context)
    }

    /** Rebuilds the module-facing index from the current enabled bindings. */
    fun rebuildIndex(context: Context) {
        runCatching {
            val root = File(context.noBackupFilesDir, CloakStateLayout.ROOT_DIR)
            root.mkdirs()
            val entries = JSONObject()
            for (binding in CloakBindingStore.all(context)) {
                if (!binding.enabled) continue
                entries.put(
                    entryKey(binding.androidUserId, binding.packageName),
                    JSONObject()
                        .put("profileId", binding.profileId)
                        .put("cloakProfileId", binding.cloakProfileId)
                        .put("key", CloakStateLayout.key(binding.profileId, binding.androidUserId, binding.packageName)),
                )
            }
            val index = File(root, CloakStateLayout.INDEX_FILE)
            index.writeText(JSONObject().put("schemaVersion", 1).put("entries", entries).toString())
            index.setReadable(true, false)
            context.noBackupFilesDir.apply {
                setReadable(true, false)
                setExecutable(true, false)
            }
root.apply {
                setReadable(true, false)
                setExecutable(true, false)
            }
        }
    }
}