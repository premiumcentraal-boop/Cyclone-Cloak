package dev.cyclone.cloak

import android.content.Context
import org.json.JSONObject
import java.io.File

/** Local cloak-profile storage and light validation. */
object CloakStore {
    private val FINGERPRINT = Regex(
        "^[^/]+/[^/]+/[^:]+:[0-9.]+/[^/]+/[^:]+:[^:/]+/(release-keys|dev-keys)$"
    )

    fun validate(profile: JSONObject): String? {
        val device = profile.optJSONObject("device") ?: return "profile needs a device block"
        if (profile.optString("name").isBlank()) return "profile needs a name"
        val fingerprint = device.optString("fingerprint")
        if (!FINGERPRINT.matches(fingerprint)) return "device.fingerprint is not a valid AOSP fingerprint"
        if (device.optInt("sdk_int", 0) < 26) return "device.sdk_int must be 26 or higher"
        return null
    }

    fun save(context: Context, profile: JSONObject): String {
        val id = profile.optString("id").ifBlank { "profile" }
        val safe = id.replace(Regex("[^A-Za-z0-9_.-]"), "_")
        dir(context).mkdirs()
        File(dir(context), "$safe.json").writeText(profile.toString(2))
        return id
    }

    fun all(context: Context): List<Pair<String, JSONObject>> =
        dir(context).listFiles { file -> file.name.endsWith(".json") }
            ?.sortedBy { it.name }
            ?.map { it.name.removeSuffix(".json") to JSONObject(it.readText()) }
            ?: emptyList()

    /** Loads one stored cloak profile by id, or null when absent. */
    fun find(context: Context, id: String): JSONObject? =
        File(dir(context), "${id.replace(Regex("[^A-Za-z0-9_.-]"), "_")}.json")
            .takeIf { it.isFile }
            ?.let { runCatching { JSONObject(it.readText()) }.getOrNull() }
    private fun dir(context: Context) = File(context.filesDir, "profiles")
}
