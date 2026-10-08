package dev.cyclone.cloak

import org.json.JSONObject

/**
 * Compact, display-oriented identity summary written into the Cyclone config
 * for every bound app. Carries no hardware identifiers: IMEI, serial, MACs
 * and the rest stay inside the on-device profile file. Bump IDENTITY_VERSION
 * whenever fields change; readers must tolerate unknown and missing fields.
 */
object CloakIdentity {
    const val IDENTITY_VERSION = 1

    fun summary(cloakProfileId: String, profile: JSONObject?): JSONObject {
        val body = JSONObject()
            .put("identityVersion", IDENTITY_VERSION)
            .put("cloakProfileId", cloakProfileId)
            .put("boundAt", System.currentTimeMillis())
        val device = profile?.optJSONObject("device") ?: return body
        return body
            .put("name", profile.optString("name"))
            .put("manufacturer", device.optString("manufacturer"))
            .put("model", device.optString("model"))
            .put("androidRelease", device.optString("version_release"))
            .put("sdkInt", device.optInt("sdk_int", 0))
    }
}
