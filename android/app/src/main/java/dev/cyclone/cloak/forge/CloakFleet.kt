package dev.cyclone.cloak.forge

import org.json.JSONArray
import org.json.JSONObject

/**
 * Fleet operations: batch profile generation and whole-fleet export/import. Pure, so it is JVM-testable.
 * Planning a bulk bind of a fleet onto Cyclone profiles is BulkBind (cyclone package).
 */
object CloakFleet {
    const val FLEET_PREFIX = "Vault"
    const val MAX_FLEET_SIZE = 99

    /** Deterministic fleet names: Vault 01, Vault 02, ... (idempotent on re-run). */
    fun fleetNames(count: Int): List<String> {
        require(count in 1..MAX_FLEET_SIZE) { "fleet size must be 1..$MAX_FLEET_SIZE" }
        return (1..count).map { "$FLEET_PREFIX %02d".format(it) }
    }

    /** Forges a fleet of full coherent profiles; identical names forge identically. */
    fun forgeFleet(phone: PhoneTemplate, count: Int): List<JSONObject> =
        fleetNames(count).map { name -> CloakForge.forgeProfile(name, CloakForge.fleetSeed(phone.id, name), phone) }

    /** Whole-fleet export body: one JSON file, schema-tagged, versioned. */
    fun exportFleet(profiles: List<Pair<String, JSONObject>>): JSONObject =
        JSONObject()
            .put("schemaVersion", 1)
            .put("kind", "cyclone-cloak-fleet")
            .put("count", profiles.size)
            .put("profiles", JSONArray(profiles.map { it.second }))

    /**
     * Parses and validates an exported fleet file. Throws with a precise
     * reason when any profile is unusable; nothing is saved on failure.
     */
    fun parseFleet(text: String): List<JSONObject> {
        val root = JSONObject(text)
        if (root.optString("kind") != "cyclone-cloak-fleet") {
            throw IllegalArgumentException("not a Cyclone Cloak fleet file")
        }
        val array = root.optJSONArray("profiles") ?: throw IllegalArgumentException("fleet file has no profiles")
        val profiles = (0 until array.length()).mapNotNull { array.optJSONObject(it) }
        for (profile in profiles) {
            ProfileValidator.errors(profile).firstOrNull()?.let { reason ->
                throw IllegalArgumentException("profile '${profile.optString("name")}': $reason")
            }
        }
        return profiles
    }

}
