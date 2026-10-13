package dev.cyclone.cloak.data

import android.content.Context
import org.json.JSONObject

/** Separate app-private records. No access to profile files, engine metadata or publication. */
class PostureStore(context: Context) {
    private val preferences = context.getSharedPreferences("compatibility-posture", Context.MODE_PRIVATE)
    companion object { private val lock = Any() }

    fun read(profileId: String): PostureRecord? = synchronized(lock) {
        runCatching {
            val text = preferences.getString(profileId, null) ?: return@synchronized null
            require(text.length <= 4096) { "Posture record is too large." }
            PostureRecord.fromJson(JSONObject(text)).takeIf { it.profileId == profileId }
        }.getOrNull()
    }

    fun update(record: PostureRecord) = synchronized(lock) {
        check(preferences.edit().putString(record.profileId, record.toJson().toString()).commit()) { "Could not save local posture." }
    }

    fun clear(profileId: String) = synchronized(lock) {
        check(preferences.edit().remove(profileId).commit()) { "Could not clear local posture." }
    }
}
