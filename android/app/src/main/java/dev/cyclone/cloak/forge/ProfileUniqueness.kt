package dev.cyclone.cloak.forge

import org.json.JSONObject
import java.util.Locale

/** Derived, local-only index. Never persisted or trusted across library reloads. */
class ProfileUniqueness(saved: List<Pair<String, JSONObject>>) {
    data class Duplicate(val field: String, val profiles: List<String>)
    companion object {
        val fields = listOf("android_id", "advertising_id", "app_set_id", "serial", "widevine_id",
            "mac", "bt_mac", "imei_primary", "imei_secondary", "sim_serial", "gsf_id")
        private fun value(profile: JSONObject, field: String): String? =
            (profile.optJSONObject("identifiers")?.opt(field) as? String)
                ?.takeIf { it.isNotBlank() }?.lowercase(Locale.ROOT)
    }
    private val entries = fields.associateWith { field ->
        saved.mapNotNull { (id, profile) -> value(profile, field)?.let { it to id } }
            .groupBy({ it.first }, { it.second }).mapValues { it.value.distinct().sorted() }
    }
    val duplicates: List<Duplicate> = fields.sorted().flatMap { field ->
        entries.getValue(field).values.filter { it.size > 1 }.map { Duplicate("identifiers.$field", it) }
            .sortedBy { it.profiles.joinToString() }
    }
    fun findings(profile: JSONObject, ownId: String = profile.optString("id")): List<ProfileValidator.Finding> =
        fields.sorted().mapNotNull { field ->
            val owners = value(profile, field)?.let { entries.getValue(field)[it] }.orEmpty().filter { it != ownId }
            if (owners.isEmpty()) null else ProfileValidator.Finding(ProfileValidator.WARNING,
                "DUPLICATE_IDENTIFIER", "identifiers.$field: this value already exists in ${owners.size} other saved profile(s). Regenerate it or explicitly keep the duplicate.", "identifiers.$field")
        }

    /** Returns a draft copy; only explicitly selected identifiers change. No saved record is mutated. */
    fun regenerate(profile: JSONObject, field: String, seedFactory: () -> String = CloakForge::newSeed): JSONObject {
        val key = field.removePrefix("identifiers.")
        require(key in fields) { "This field does not support generation." }
        val draft = JSONObject(profile.toString())
        repeat(32) {
            val candidate = CloakForge.deriveIdentifierBundle(seedFactory()).getValue(key)
            if (entries.getValue(key)[candidate.lowercase(Locale.ROOT)].isNullOrEmpty() && candidate != value(profile, key)) {
                draft.getJSONObject("identifiers").put(key, candidate)
                return draft
            }
        }
        error("Could not generate a unique value. Nothing was saved; try again.")
    }
}
