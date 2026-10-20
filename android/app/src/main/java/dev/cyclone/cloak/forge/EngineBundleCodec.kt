package dev.cyclone.cloak.forge

import org.json.JSONObject
import org.json.JSONTokener

/** Imported findings/posture are untrusted. Validation is always recomputed from the original body. */
object EngineBundleCodec {
    data class Bundle(val profile: JSONObject, val metadata: JSONObject, val findings: List<ProfileValidator.Finding>, val extras: JSONObject = JSONObject())
    private val keys = setOf("profile", "metadata", "posture", "findings", "uniqueness")
    private val metadataKeys = setOf("engine_version", "schema_version", "template", "source_confidence", "chosen_seed", "attempt_count", "instance_realism", "realism_request_seed")

    fun decode(text: String): Bundle {
        require(text.length <= 2_000_000) { "Engine bundle is too large." }
        StrictJson(text).check()
        // Bound nesting before the recursive JSON reader (including malicious unknown fields).
        var depth = 0; var quoted = false; var escaped = false
        for (char in text) {
            if (quoted) { if (escaped) escaped = false else if (char == '\\') escaped = true else if (char == '"') quoted = false }
            else when (char) { '"' -> quoted = true; '{', '[' -> { depth++; require(depth <= 32) { "Engine bundle is too deeply nested." } }; '}', ']' -> depth-- }
        }
        val parser = JSONTokener(text)
        val root = parser.nextValue() as? JSONObject ?: throw IllegalArgumentException("Engine bundle must be an object.")
        require(parser.nextClean() == '\u0000' && root.keys().asSequence().toSet().all { it in keys }) { "Malformed engine bundle." }
        val profile = root.optJSONObject("profile") ?: throw IllegalArgumentException("Engine bundle has no profile body.")
        val metadata = root.optJSONObject("metadata") ?: throw IllegalArgumentException("Engine bundle has no metadata.")
        validateMetadata(metadata, profile)
        root.opt("posture")?.let { require(it is JSONObject && it.keys().asSequence().toSet().all { key -> it.opt(key) is String }) { "Malformed posture." } }
        for (key in listOf("findings", "uniqueness")) if (root.has(key)) {
            val array = root.optJSONArray(key) ?: throw IllegalArgumentException("Malformed $key.")
            require(array.length() <= 1000) { "Too many findings." }
            for (i in 0 until array.length()) {
                val finding = array.optJSONObject(i) ?: throw IllegalArgumentException("Malformed finding.")
                require(finding.keys().asSequence().toSet() == setOf("severity", "code", "message", "field") &&
                    finding.opt("severity") is String && finding.opt("code") is String && finding.opt("message") is String &&
                    (finding.isNull("field") || finding.opt("field") is String)) { "Malformed finding." }
                ProfileValidator.Finding(finding.getString("severity"), finding.getString("code"), finding.getString("message"))
            }
        }
        val findings = IdentityEngine.validate(profile)
        require(findings.none { it.isError }) { findings.filter { it.isError }.joinToString("\n") { "${it.code}: ${it.message}" } }
        val extras = JSONObject()
        listOf("posture", "findings", "uniqueness").filter(root::has).forEach { extras.put(it, root.get(it)) }
        return Bundle(JSONObject(profile.toString()), JSONObject(metadata.toString()), findings, extras)
    }

    fun validateMetadata(metadata: JSONObject, profile: JSONObject) {
        require(metadata.keys().asSequence().toSet().all { it in metadataKeys }) { "Unsupported engine metadata field." }
        require(metadata.opt("engine_version") == "1") { "Unsupported engine version." }
        require(metadata.opt("schema_version") in setOf("0.2", "0.3", "0.4") && metadata.opt("schema_version") == profile.opt("schema_version")) { "Engine/profile schema versions disagree." }
        require(metadata.opt("template") is String && metadata.getString("template").length in 1..128) { "Missing template ID." }
        val profileTemplate = profile.optJSONObject("meta")?.optString("template").orEmpty()
        require(profileTemplate.isEmpty() || metadata.getString("template") == profileTemplate) { "Engine/profile template IDs disagree." }
        require(metadata.has("instance_realism") == metadata.has("realism_request_seed")) { "Incomplete instance realism metadata." }
        if (metadata.has("instance_realism")) {
            val provenance = metadata.getJSONObject("instance_realism")
            require(InstanceRealism.same(provenance, profile.optJSONObject("meta")?.optJSONObject("instance_realism"))) { "Instance provenance disagrees with profile." }
            val requestSeed = metadata.opt("realism_request_seed")
            require(requestSeed is String && requestSeed.matches(Regex("[0-9a-f]{64}"))) { "Invalid instance request seed." }
            val selected = InstanceRealism.plan("phones." + metadata.getString("template"), requestSeed, provenance.getString("tuple_id"))
            require(selected.seed == profile.optString("seed") && InstanceRealism.validate(profile).isEmpty()) { "Instance derivation binding failed." }
        }
        if (metadata.has("source_confidence")) require(metadata.opt("source_confidence") in setOf("public reference", "approximated", "needs verification")) { "Invalid source confidence." }
        require(metadata.has("chosen_seed") == metadata.has("attempt_count")) { "Incomplete generation metadata." }
        if (metadata.has("chosen_seed")) {
            require(metadata.opt("chosen_seed") is String && Regex("[0-9a-f]{64}").matches(metadata.getString("chosen_seed")) && metadata.opt("chosen_seed") == profile.opt("seed")) { "Chosen seed disagrees with profile." }
            val attempts = metadata.opt("attempt_count")
            require(attempts is Int && attempts in 1..128) { "Invalid attempt count." }
        }
    }

    fun encode(bundle: Bundle): String {
        val root = JSONObject(bundle.extras.toString()).put("profile", bundle.profile).put("metadata", bundle.metadata)
        decode(root.toString())
        return root.toString()
    }

}
