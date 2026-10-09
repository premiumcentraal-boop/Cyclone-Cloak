package dev.cyclone.cloak.forge

import org.json.JSONObject

/**
 * Builds the per-binding pif.json artifact from a bound device profile,
 * mirroring forge/cloak_forge/pif.py. The module does not read this file; it
 * is the Play Integrity posture artifact for Tricky Store-style consumers.
 */
object CloakPif {
    private val keys = listOf(
        "manufacturer" to "MANUFACTURER",
        "model" to "MODEL",
        "brand" to "BRAND",
        "product" to "PRODUCT",
        "device" to "DEVICE",
        "fingerprint" to "FINGERPRINT",
        "security_patch" to "SECURITY_PATCH",
        "first_api_level" to "FIRST_API_LEVEL",
        "build_id" to "BUILD_ID",
        "version_incremental" to "INCREMENTAL",
        "bootloader" to "BOOTLOADER",
        "baseband" to "BASEBAND",
        "version_release" to "VERSION_RELEASE",
        "sdk_int" to "SDK_INT",
    )

    fun fromProfile(profile: JSONObject): JSONObject {
        val device = profile.optJSONObject("device") ?: JSONObject()
        val body = JSONObject()
        for ((field, key) in keys) {
            val value = when (val raw = device.opt(field)) {
                null -> null
                is String -> raw
                else -> raw.toString()
            } ?: continue
            body.put(key, value)
        }
        return body
    }
}
