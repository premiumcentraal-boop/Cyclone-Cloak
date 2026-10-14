package dev.cyclone.cloak.cyclone

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Display-only cache, never an authorization source or input to reconciliation. */
data class CachedRegistry(val snapshot: ProfilesSnapshot, val seenAt: Long)

object RegistryCacheCodec {
    fun encode(cache: CachedRegistry): String = JSONObject().put("current", cache.snapshot.current ?: JSONObject.NULL)
        .put("profiles", JSONArray(cache.snapshot.profiles.map { p ->
            JSONObject().put("id", p.id).put("label", p.label).put("kind", p.kind).put("state", p.state)
                .put("androidUserId", p.androidUserId ?: JSONObject.NULL)
                .put("packages", p.packages?.let { JSONArray(it) } ?: JSONObject.NULL)
        })).toString()

    fun restore(text: String?, seenAt: Long, now: Long = System.currentTimeMillis()): CachedRegistry? = runCatching {
        require(text != null && text.length <= 1_048_576 && seenAt > 0 && seenAt <= now + 60_000)
        val value = JSONObject(text)
        val profiles = value.getJSONArray("profiles")
        require(profiles.length() <= 1000)
        for (i in 0 until profiles.length()) {
            val p = profiles.getJSONObject(i)
            require(p.opt("id") is String && p.opt("label") is String && p.opt("kind") is String && p.opt("state") is String)
            require(p.isNull("androidUserId") || integer(p.opt("androidUserId"))?.let { it >= 0 } == true)
            require(p.isNull("packages") || p.opt("packages") is JSONArray)
        }
        CachedRegistry(ProfilesSnapshot.parse(value), seenAt)
    }.getOrNull()
}

internal class RegistryCache(context: Context) {
    private val prefs = context.getSharedPreferences("cloak", Context.MODE_PRIVATE)
    fun read(): CachedRegistry? = runCatching {
        RegistryCacheCodec.restore(prefs.getString("registrySnapshot", null), prefs.getLong("registrySeenAt", 0))
    }.getOrNull()
    fun save(cache: CachedRegistry) {
        val encoded = RegistryCacheCodec.encode(cache)
        require(encoded.length <= 1_048_576)
        check(prefs.edit().putString("registrySnapshot", encoded).putLong("registrySeenAt", cache.seenAt).commit())
    }
}
