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
        val assignments: List<Pair<CycloneProfile, String>>,
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
        cycloneProfiles: List<CycloneProfile>,
        existingBindings: List<CloakBinding>,
    ): BulkBindPlan {
        val usedCloak = existingBindings.map { it.cloakProfileId }.toMutableSet()
        val boundProfiles = existingBindings.map { it.profileId }.toSet()
        val assignments = mutableListOf<Pair<CycloneProfile, String>>()
        var skippedBound = 0
        for (profile in cycloneProfiles) {
            // Never Main, never a profile without a known Android user number.
            if (!profile.bindable) continue
            val packages = profile.packages.orEmpty()
            if (packages.isEmpty()) continue
            if (profile.id in boundProfiles) {
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
