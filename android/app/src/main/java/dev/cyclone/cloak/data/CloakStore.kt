package dev.cyclone.cloak.data

import android.content.Context
import org.json.JSONObject
import java.io.File

/** The device identities (cloak profiles) saved on this phone, one JSON file each. */
object CloakStore {
    private fun dir(context: Context) = JsonDirectory(File(context.filesDir, "profiles"))

    /** Saves [profile] under its `id` and returns the id. */
    fun save(context: Context, profile: JSONObject): String {
        val id = profile.optString("id").ifBlank { "profile" }
        dir(context).write(id, profile)
        return id
    }

    fun all(context: Context): List<Pair<String, JSONObject>> = dir(context).all()

    /** Loads one stored cloak profile by id, or null when absent or unreadable. */
    fun find(context: Context, id: String): JSONObject? = dir(context).read(id)

    fun delete(context: Context, id: String): Boolean = dir(context).delete(id)
}
