package dev.cyclone.cloak.forge

import org.json.JSONObject

/** The minimum a cloak profile needs before it is saved. */
object ProfileCheck {
    private val FINGERPRINT = Regex(
        "^[^/]+/[^/]+/[^:]+:[0-9.]+/[^/]+/[^:]+:[^:/]+/(release-keys|dev-keys)$"
    )

    /** The first problem with [profile], or null when it can be saved. */
    fun firstProblem(profile: JSONObject): String? {
        val device = profile.optJSONObject("device") ?: return "profile needs a device block"
        if (profile.optString("name").isBlank()) return "profile needs a name"
        val fingerprint = device.optString("fingerprint")
        if (!FINGERPRINT.matches(fingerprint)) return "device.fingerprint is not a valid AOSP fingerprint"
        if (device.optInt("sdk_int", 0) < 26) return "device.sdk_int must be 26 or higher"
        return null
    }
}
