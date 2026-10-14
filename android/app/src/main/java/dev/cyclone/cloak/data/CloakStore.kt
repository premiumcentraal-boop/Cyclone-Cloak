package dev.cyclone.cloak.data

import dev.cyclone.cloak.diagnostics.LogBus
import dev.cyclone.cloak.diagnostics.Transition
import android.content.Context
import org.json.JSONObject
import java.io.File

/** The device identities (cloak profiles) saved on this phone, one JSON file each. */
object CloakStore {
    private fun dir(context: Context) = JsonDirectory(File(context.filesDir, "profiles"))

    /** Saves [profile] under its `id` and returns the id. */
    fun save(context: Context, profile: JSONObject): String = LogBus.trace("profiles", "save", profile.optString("id")) {
        val id = profile.optString("id").ifBlank { "profile" }
        dir(context).write(id, profile)
        id
    }

    /** Add a completely reviewed batch without replacing existing identities. */
    fun importBatch(context: Context, profiles: List<JSONObject>) =
        LogBus.trace("profiles", "import") { dir(context).writeBatch(profiles.map { it.getString("id") to it }) }

    fun all(context: Context): List<Pair<String, JSONObject>> = LogBus.trace("profiles", "load") { dir(context).all() }

    /** Loads one stored cloak profile by id, or null when absent or unreadable. */
    fun find(context: Context, id: String): JSONObject? = LogBus.trace("profiles", "read", id) { dir(context).read(id).also { if (it == null) LogBus.event("profiles", "read", Transition.DEGRADED, id, "MISSING_OR_UNREADABLE") } }

    fun delete(context: Context, id: String): Boolean = dir(context).delete(id)
}
