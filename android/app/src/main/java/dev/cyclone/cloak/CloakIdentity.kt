package dev.cyclone.cloak

import org.json.JSONObject

/**
 * Compact, display-oriented identity summary written into the Cyclone config
 * for every bound app. Carries no hardware identifiers: IMEI, serial, MACs
 * and the rest stay inside the on-device profile file. Bump IDENTITY_VERSION
 * whenever fields change; readers must tolerate unknown and missing fields.
 *
 * The summary is deterministic (no timestamps) so reconcile can compare it with
 * what Cyclone holds and skip writes that would change nothing. Field limits
 * match Cyclone's strict reader (CycloneCloakProfileBinding, identityVersion 1).
 */
object CloakIdentity {
    const val IDENTITY_VERSION = 1

    fun summary(cloakProfileId: String, profile: JSONObject?): JSONObject {
        val body = JSONObject()
            .put("cloakProfileId", cloakProfileId.take(160))
            .put("identityVersion", IDENTITY_VERSION)
        val device = profile?.optJSONObject("device") ?: return body
        putText(body, "name", profile.optString("name"), 80)
        putText(body, "manufacturer", device.optString("manufacturer"), 80)
        putText(body, "model", device.optString("model"), 80)
        putText(body, "androidRelease", device.optString("version_release"), 40)
        device.optInt("sdk_int", 0).takeIf { it in 1..1000 }?.let { body.put("sdkInt", it) }
        return body
    }

    private fun putText(body: JSONObject, key: String, value: String, limit: Int) {
        val clean = value.replace(Regex("[\\p{Cntrl}]+"), " ").replace(Regex("\\s+"), " ").trim().take(limit)
        if (clean.isNotEmpty()) body.put(key, clean)
    }
}
