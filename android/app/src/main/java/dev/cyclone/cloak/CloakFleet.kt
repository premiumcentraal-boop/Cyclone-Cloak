package dev.cyclone.cloak

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Fleet operations: batch profile generation, bulk binding planning and
 * whole-fleet export/import. The planning functions are pure so the fleet
 * behavior is JVM-testable without a device or Robolectric.
 */
object CloakFleet {
    const val FLEET_PREFIX = "Vault"
    const val MAX_FLEET_SIZE = 99

    data class BulkBindPlan(
        val assignments: List<Pair<JSONObject, String>>,
        val skippedBound: Int,
        val unusedCloakProfiles: Int,
    )

    /** Deterministic fleet names: Vault 01, Vault 02, ... (idempotent on re-run). */
    fun fleetNames(count: Int): List<String> {
        require(count in 1..MAX_FLEET_SIZE) { "fleet size must be 1..$MAX_FLEET_SIZE" }
        return (1..count).map { "$FLEET_PREFIX %02d".format(it) }
    }

    /** Forges a fleet of full coherent profiles; identical names forge identically. */
    fun forgeFleet(template: String, count: Int): List<JSONObject> =
        fleetNames(count).map { name -> CloakForge.forgeProfile(name, CloakForge.fleetSeed(template, name), template) }

    /**
     * Plans a bulk bind: every ready Cyclone profile that has no bindings yet
     * gets the next unused cloak profile. Already-bound Cyclone profiles are
     * left untouched, and cloak profiles in use are never handed out twice.
     */
    fun planBulkBind(
        cloakProfileIds: List<String>,
        cycloneProfiles: List<JSONObject>,
        existingBindings: List<CloakBinding>,
    ): BulkBindPlan {
        val usedCloak = existingBindings.map { it.cloakProfileId }.toMutableSet()
        val boundPackageKeys = existingBindings
            .map { CloakBindingStore.key(it.profileId, it.androidUserId, it.packageName) }
            .toMutableSet()
        val assignments = mutableListOf<Pair<JSONObject, String>>()
        var skippedBound = 0
        for (profile in cycloneProfiles) {
            val id = profile.optString("id")
            val userId = profile.optInt("androidUserId", 0)
            val packages = profile.optJSONArray("packages")?.let { array ->
                (0 until array.length()).map { array.optString(it) }
            } ?: emptyList()
            if (packages.isEmpty()) continue
            val alreadyBound = packages.any { "$id\n$userId\n$it" in boundPackageKeys }
            if (alreadyBound) {
                skippedBound++
                continue
            }
            val cloakId = cloakProfileIds.firstOrNull { it !in usedCloak } ?: break
            usedCloak.add(cloakId)
            assignments.add(profile to cloakId)
        }
        val available = cloakProfileIds.count { it !in usedCloak }
        return BulkBindPlan(assignments, skippedBound, available)
    }

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
            CloakStore.validate(profile)?.let { reason ->
                throw IllegalArgumentException("profile '${profile.optString("name")}': $reason")
            }
        }
        return profiles
    }

    /** Saves every profile; returns the saved ids in order. */
    fun saveAll(context: Context, profiles: List<JSONObject>): List<String> =
        profiles.map { CloakStore.save(context, it) }
}
