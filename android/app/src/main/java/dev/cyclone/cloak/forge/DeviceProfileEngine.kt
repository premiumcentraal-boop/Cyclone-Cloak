package dev.cyclone.cloak.forge

import org.json.JSONObject
import java.security.MessageDigest

/** App boundary. All operations are local and pure; persistence belongs to the caller. */
interface DeviceProfileEngine {
    fun generate(request: IdentityEngine.GenerationRequest): JSONObject
    fun generate_unique(request: IdentityEngine.GenerationRequest, existingProfiles: List<JSONObject>): JSONObject
    fun validate(profile: JSONObject): List<ProfileValidator.Finding>
    fun check_uniqueness(profile: JSONObject, existingProfiles: List<JSONObject>): List<ProfileValidator.Finding>
    fun derive_posture(profile: JSONObject): JSONObject
    fun score_coverage(profile: JSONObject, deviceReport: Map<String, String>): JSONObject
}

class DefaultDeviceProfileEngine : DeviceProfileEngine {
    class InvalidProfile(val findings: List<ProfileValidator.Finding>) : IllegalArgumentException("Generated profile has coherence errors.")
    override fun generate(request: IdentityEngine.GenerationRequest) = IdentityEngine.generate(request)
    override fun validate(profile: JSONObject) = IdentityEngine.validate(profile)
    override fun check_uniqueness(profile: JSONObject, existingProfiles: List<JSONObject>) = IdentityEngine.check_uniqueness(profile, existingProfiles)
    override fun derive_posture(profile: JSONObject) = IdentityEngine.derive_posture(profile)
    override fun score_coverage(profile: JSONObject, deviceReport: Map<String, String>) = IdentityEngine.score_coverage(profile, deviceReport)

    override fun generate_unique(request: IdentityEngine.GenerationRequest, existingProfiles: List<JSONObject>): JSONObject {
        // New records must not exclude a saved record merely because its ID matches the candidate.
        val index = ProfileUniqueness(existingProfiles.mapIndexed { i, profile -> "saved-$i" to profile })
        for (attempt in 1..128) {
            val seed = if (attempt == 1) request.seed else retrySeed(request.seed, attempt)
            val bundle = generate(request.copy(seed = seed))
            val profile = bundle.getJSONObject("profile")
            val findings = validate(profile)
            if (findings.any { it.isError }) throw InvalidProfile(findings)
            if (existingProfiles.any { it.optString("id") == profile.optString("id") } ||
                index.findings(profile, ownId = "new").isNotEmpty() ||
                request.instance_realism && InstanceRealism.conflicts(profile, existingProfiles)) continue
            bundle.getJSONObject("metadata").put("chosen_seed", profile.getString("seed")).put("attempt_count", attempt)
                .put("source_confidence", request.template.corpus.optString("source_confidence")
                    .ifBlank { request.template.confidence ?: "needs verification" })
            return bundle
        }
        error("Could not generate a unique profile after 128 attempts. Nothing was saved.")
    }

    companion object {
        fun retrySeed(seed: String, attempt: Int): String {
            require(Regex("[0-9a-f]{64}").matches(seed) && attempt in 2..128)
            return MessageDigest.getInstance("SHA-256").digest("cloak-engine-unique-v1:$seed:$attempt".toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
        }
    }
}
