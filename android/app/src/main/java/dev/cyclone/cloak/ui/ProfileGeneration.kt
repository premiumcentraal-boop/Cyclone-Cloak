package dev.cyclone.cloak.ui

import dev.cyclone.cloak.forge.CloakForge
import dev.cyclone.cloak.forge.PhoneTemplate
import dev.cyclone.cloak.forge.ProfileUniqueness
import dev.cyclone.cloak.forge.ProfileValidator
import org.json.JSONObject

/** Local orchestration only. The caller serializes work and supplies the existing atomic store. */
object ProfileGeneration {
    sealed interface Result {
        data object Running : Result
        data class Saved(val id: String, val name: String, val findings: List<ProfileValidator.Finding>) : Result
        data class NeedsReview(val profile: JSONObject, val findings: List<ProfileValidator.Finding>) : Result
        data class Rejected(val findings: List<ProfileValidator.Finding>) : Result
        data class Failed(val code: String, val message: String) : Result
    }

    fun nextName(phone: PhoneTemplate, existing: List<Pair<String, JSONObject>>): String {
        val base = "${phone.label.trim().ifBlank { phone.id }} profile".take(64)
        val names = existing.map { it.second.optString("name") }
        var candidate = base
        var suffix = 2
        while (names.any { it.equals(candidate, ignoreCase = true) }) {
            val ending = " ${suffix++}"
            candidate = base.take(64 - ending.length).trimEnd() + ending
        }
        return candidate
    }

    fun generate(
        phone: PhoneTemplate,
        existing: List<Pair<String, JSONObject>>,
        save: (JSONObject) -> String,
        seedFactory: () -> String = CloakForge::newSeed,
        name: String = nextName(phone, existing),
    ): Result {
        val profile = try {
            // The seed is allocated once, and copied into the same profile that is validated and saved.
            CloakForge.forgeProfile(name, seedFactory(), phone)
        } catch (_: Exception) {
            return Result.Failed("GENERATION_FAILED", "Could not generate the profile. Try again.")
        }
        val findings = ProfileValidator.validateProfileResult(profile).findings
        if (findings.any { it.isError }) return Result.Rejected(findings)
        val id = profile.getString("id")
        if (existing.any { (storedId, value) -> storedId == id || value.optString("id") == id }) {
            return Result.Failed("PROFILE_EXISTS", "This profile already exists. No existing identity was replaced.")
        }
        if (existing.any { it.second.optString("name").equals(name, ignoreCase = true) }) {
            return Result.Failed("NAME_EXISTS", "That profile name is already in use. No existing identity was replaced.")
        }
        val duplicates = ProfileUniqueness(existing).findings(profile)
        if (duplicates.isNotEmpty()) return Result.NeedsReview(profile, findings + duplicates)
        return try {
            save(profile)
            Result.Saved(id, name, findings)
        } catch (_: Exception) {
            Result.Failed("SAVE_FAILED", "Could not save the profile. Check local storage and try again.")
        }
    }
}
