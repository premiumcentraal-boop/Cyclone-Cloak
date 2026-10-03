package dev.cyclone.cloak

import android.content.Context
import org.json.JSONObject
import java.io.File

/** Local cloak-profile storage, light validation, and the su bridge to the module config. */
object CloakStore {

    // Same AOSP fingerprint grammar the forge validator enforces; a quick second gate at import time.
    private val FINGERPRINT = Regex(
        "^[^/]+/[^/]+/[^:]+:[0-9.]+/[^/]+/[^:]+:[^:/]+/(release-keys|dev-keys)$"
    )

    /** null when the profile is usable; otherwise a short reason. */
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

    /** Runs one shell command as root; returns exit code plus stdout/stderr. */
    fun runSu(command: String): String {
        val process = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
        val out = process.inputStream.bufferedReader().readText()
        val err = process.errorStream.bufferedReader().readText()
        val code = process.waitFor()
        return buildString {
            append("exit=").append(code)
            if (out.isNotBlank()) append('\n').append(out)
            if (err.isNotBlank()) append('\n').append(err)
        }
    }

    /** Reads the module config through root, or null when it is absent or unreadable. */
    fun readConfig(): JSONObject? {
        val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "cat /data/adb/cyclone_cloak/config.json"))
        val out = process.inputStream.bufferedReader().readText()
        process.errorStream.close()
        return if (process.waitFor() == 0) runCatching { JSONObject(out) }.getOrNull() else null
    }

    private fun dir(context: Context) = File(context.filesDir, "profiles")
}
