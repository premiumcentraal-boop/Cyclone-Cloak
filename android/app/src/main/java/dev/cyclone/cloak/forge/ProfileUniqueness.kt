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
            (DeviceReality.at(profile, if (field.contains(".")) field else "identifiers.$field") as? String)
                ?.takeIf { it.isNotBlank() }?.lowercase(Locale.ROOT)
    }
    private val allFields = fields.map { "identifiers.$it" } + DeviceReality.uniquePaths
    private val entries = allFields.associateWith { field ->
        saved.mapNotNull { (id, profile) -> value(profile, field)?.let { it to id } }
            .groupBy({ it.first }, { it.second }).mapValues { it.value.distinct().sorted() }
    }
    val duplicates: List<Duplicate> = allFields.sorted().flatMap { field ->
        entries.getValue(field).values.filter { it.size > 1 }.map { Duplicate(field, it) }
            .sortedBy { it.profiles.joinToString() }
    }
    fun findings(profile: JSONObject, ownId: String = profile.optString("id")): List<ProfileValidator.Finding> =
        allFields.sorted().mapNotNull { field ->
            val owners = value(profile, field)?.let { entries.getValue(field)[it] }.orEmpty().filter { it != ownId }
            if (owners.isEmpty()) null else ProfileValidator.Finding(ProfileValidator.WARNING,
                "DUPLICATE_IDENTIFIER", "$field: this value already exists in ${owners.size} other saved profile(s). Regenerate it or explicitly keep the duplicate.", field)
        }

    /** Returns a draft copy; only explicitly selected identifiers change. No saved record is mutated. */
    fun regenerate(profile: JSONObject, field: String, seedFactory: () -> String = CloakForge::newSeed): JSONObject {
        val key = if (field.contains(".")) field else "identifiers.$field"
        require(key in allFields) { "This field does not support generation." }
        val draft = JSONObject(profile.toString())
        repeat(32) {
            val seed = seedFactory()
            val candidate = if (key.startsWith("identifiers.")) CloakForge.deriveIdentifierBundle(seed).getValue(key.substringAfter("."))
                else if (key == "reality.inventory_id") DeviceReality.token(seed, "inventory")
                else "Cloak-" + DeviceReality.token(seed, if (key.endsWith("wifi_name")) "wifi" else "bluetooth").take(12)
            if (entries.getValue(key)[candidate.lowercase(Locale.ROOT)].isNullOrEmpty() && candidate != value(profile, key)) {
                val parts = key.split('.'); var parent = draft
                parts.dropLast(1).forEach { parent = parent.getJSONObject(it) }
                parent.put(parts.last(), candidate)
                return draft
            }
        }
        error("Could not generate a unique value. Nothing was saved; try again.")
    }
}
