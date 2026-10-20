package dev.cyclone.cloak.forge

import org.json.JSONArray
import org.json.JSONObject

/** Pure facade: generation never writes, binds or publishes anything. */
object IdentityEngine {
    data class GenerationRequest(
        val name: String,
        val seed: String,
        val template: PhoneTemplate,
        val schema_version: String = "0.3",
        val instance_realism: Boolean = false,
        val realism_tuple: String? = null,
    )

    fun generate(request: GenerationRequest): JSONObject {
        require(request.schema_version in setOf("0.2", "0.3", "0.4")) { "schema_version must be 0.2, 0.3 or 0.4" }
        require(request.name.isNotBlank()) { "name must be a non-empty string" }
        require(Regex("[0-9a-f]{64}").matches(request.seed)) { "seed must be 64 lowercase hex characters" }
        require(request.instance_realism || request.realism_tuple == null) { "REALISM_REQUEST_INVALID" }
        require(!request.instance_realism || request.schema_version == "0.4") { "REALISM_REQUIRES_SCHEMA_04" }
        val selection = if (request.instance_realism) InstanceRealism.plan("phones." + request.template.id, request.seed, request.realism_tuple) else null
        val baseline = CloakForge.forgeProfile(request.name, selection?.seed ?: request.seed, request.template, request.schema_version)
            .put("schema_version", request.schema_version)
        val profile = if (selection == null) baseline else InstanceRealism.apply(baseline, selection)
        return JSONObject().put("profile", profile).put("posture", derive_posture(profile))
            .put("findings", findingsJson(validate(profile)))
            .put("uniqueness", findingsJson(check_uniqueness(profile, emptyList())))
            .put("metadata", JSONObject().put("engine_version", "1")
                .put("schema_version", request.schema_version).put("template", request.template.id).also { metadata ->
                    if (selection != null) metadata.put("instance_realism", JSONObject(profile.getJSONObject("meta").getJSONObject("instance_realism").toString()))
                        .put("realism_request_seed", request.seed)
                })
    }

    fun validate(profile: JSONObject): List<ProfileValidator.Finding> = ProfileValidator.validateProfile(profile)
    fun derive_posture(profile: JSONObject): JSONObject = CloakForge.buildPif(profile)
    fun check_uniqueness(profile: JSONObject, library: List<JSONObject>): List<ProfileValidator.Finding> =
        ProfileUniqueness(library.map { it.optString("id") to it }).findings(profile)

    private fun findingsJson(findings: List<ProfileValidator.Finding>): JSONArray = JSONArray().also { array ->
        findings.forEach { array.put(JSONObject().put("severity", it.severity).put("code", it.code)
            .put("message", it.message).put("field", it.field ?: JSONObject.NULL)) }
    }

    // Mirrors coverage.py. Shared engine vectors pin the entire field ledger.
    private val applied = setOf(
        "device.manufacturer", "device.brand", "device.model", "device.product", "device.device", "device.hardware",
        "device.fingerprint", "device.version_release", "device.sdk_int", "device.security_patch", "device.build_id",
        "device.version_incremental", "device.build_date_utc", "device.first_api_level", "device.bootloader", "device.baseband",
        "telephony.carrier_name", "telephony.mcc", "telephony.mnc", "telephony.network_type", "telephony.sim_slot_count",
        "display.density", "identifiers.android_id", "identifiers.advertising_id", "identifiers.app_set_id", "identifiers.mac",
        "identifiers.bt_mac", "identifiers.imei_primary", "identifiers.imei_secondary", "identifiers.sim_serial",
        "identifiers.gsf_id", "identifiers.widevine_id", "identifiers.serial", "locale.language", "locale.country",
        "locale.timezone", "ua.value", "camera.cameras",
    )
    private val internal = setOf("schema_version", "id", "name", "seed", "integrity.verdict", "integrity.checked_at",
        "integrity.keybox_tag", "health.state", "health.last_seen", "meta.source", "meta.forge_version", "meta.template")
    private val testStates = setOf("VERIFIED", "PASS", "FAIL", "BLOCKED", "OBSERVED", "MATCH_ONLY", "UNMAPPED", "INCOMPLETE", "not verified")

    fun score_coverage(profile: JSONObject, device_report: Map<String, String>): JSONObject {
        fun leaves(value: JSONObject, prefix: String = ""): List<String> = value.keys().asSequence().flatMap { key ->
            val path = if (prefix.isEmpty()) key else "$prefix.$key"
            val child = value.optJSONObject(key)
            (if (child == null) listOf(path) else leaves(child, path)).asSequence()
        }.toList()
        val rows = JSONArray()
        val counts = linkedMapOf("APPLIED" to 0, "PENDING" to 0, "INTERNAL" to 0)
        for (field in leaves(profile).sorted()) {
            val implementation = when (field) { in applied -> "APPLIED"; in internal -> "INTERNAL"; else -> "PENDING" }
            val status = device_report[field] ?: "not verified"
            require(status in testStates) { "Unsupported test status for $field" }
            rows.put(JSONObject().put("field", field).put("implementation", implementation).put("test_status", status))
            counts[implementation] = counts.getValue(implementation) + 1
        }
        return JSONObject().put("rows", rows).put("summary", JSONObject(counts as Map<*, *>))
    }
}
