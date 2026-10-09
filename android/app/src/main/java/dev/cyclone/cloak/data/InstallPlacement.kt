package dev.cyclone.cloak.data

import android.content.Context
import android.os.Process
import org.json.JSONArray

/**
 * Where this Cloak install runs, as Cyclone last told it: Main, or one Cyclone profile (each profile is its own
 * Android user with its own Cloak). Written by the Cyclone sync, read by root publishing.
 */
object InstallPlacement {
    private const val PREFS = "cloak"
    private const val KEY_PLACEMENT = "placement"
    private const val KEY_LIVE_USERS = "liveUsers"
    const val MAIN = "main"

    fun myUser(): Int = Process.myUid() / 100_000

    /** [MAIN], this install's Cyclone profile id, or null before the first sync. */
    fun placement(context: Context): String? = prefs(context).getString(KEY_PLACEMENT, null)

    /** Android users of every Cyclone profile, as Main's Cyclone listed them; null before the first sync. */
    fun liveUsers(context: Context): List<Int>? = prefs(context).getString(KEY_LIVE_USERS, null)?.let { text ->
        runCatching { JSONArray(text).let { a -> (0 until a.length()).map { a.getInt(it) } } }.getOrNull()
    }

    fun save(context: Context, placement: String, liveUsers: List<Int>) {
        prefs(context).edit()
            .putString(KEY_PLACEMENT, placement)
            .putString(KEY_LIVE_USERS, JSONArray(liveUsers.distinct().sorted()).toString())
            .apply()
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
