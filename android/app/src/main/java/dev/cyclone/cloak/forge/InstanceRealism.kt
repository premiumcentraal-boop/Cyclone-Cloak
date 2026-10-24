package dev.cyclone.cloak.forge

import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.Locale

/** Pure approved-tuple resolver. No context, storage, host inspection or runtime APIs. */
object InstanceRealism {
    internal fun same(a: JSONObject, b: JSONObject?) = b != null && RealityJson.digest(a) == RealityJson.digest(b)
    private val rules get() = JSONObject(InstanceRealismRules.json)
    data class Selection(val sourceId: String, val tupleId: String, val seed: String)
    private fun hash(text: String) = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
    private fun list(a: JSONArray) = (0 until a.length()).map { a.getJSONObject(it) }
    private fun selected(sourceId: String, tupleId: String): Triple<JSONObject, JSONObject, JSONObject> {
        val data = rules
        val source = data.getJSONObject("sources").optJSONObject(sourceId)
        require(source != null) { "REALISM_TEMPLATE_UNSUPPORTED" }
        val selected = list(source.getJSONArray("tuples")).firstOrNull { it.getString("id") == tupleId }
        require(selected != null) { "REALISM_TUPLE_UNSUPPORTED" }
        return Triple(data, source, selected)
    }
    fun plan(sourceId: String, seed: String, tupleId: String? = null): Selection {
        require(seed.matches(Regex("[0-9a-f]{64}"))) { "REALISM_SEED_INVALID" }
        val source = rules.getJSONObject("sources").optJSONObject(sourceId)
        require(source != null) { "REALISM_TEMPLATE_UNSUPPORTED" }
        val options = list(source.getJSONArray("tuples")).sortedBy { it.getString("id") }
        val chosen = if (tupleId == null) options[(hash("cloak-realism-select-v1:$sourceId:$seed").take(8).toLong(16) % options.size).toInt()]
            else options.firstOrNull { it.getString("id") == tupleId }
        require(chosen != null) { "REALISM_TUPLE_UNSUPPORTED" }
        val id = chosen.getString("id")
        return Selection(sourceId, id, hash("cloak-realism-seed-v1:$sourceId:$seed:$id"))
    }
    private fun anchor(profile: JSONObject): String {
        val facts = JSONObject()
        for (key in listOf("device", "display", "camera", "telephony", "locale", "network", "reality")) {
            if (profile.has(key)) facts.put(key, JSONObject(profile.getJSONObject(key).toString()))
        }
        facts.optJSONObject("reality")?.let { reality ->
            reality.remove("inventory_id")
            // Wholly derived from device and webview facts, so it must not invalidate the anchor.
            reality.remove("packages")
            for ((section, fields) in listOf("radio" to listOf("wifi_name", "bluetooth_name"), "webview" to listOf("user_agent"))) {
                fields.forEach { reality.optJSONObject(section)?.optJSONObject("values")?.remove(it) }
            }
        }
        return RealityJson.digest(facts)
    }
    private fun setPath(profile: JSONObject, path: String, value: Any?) {
        val parts = path.split('.')
        var parent = profile
        for (key in parts.dropLast(1)) {
            if (!parent.has(key)) {
                if (value == null || value == JSONObject.NULL) return
                parent.put(key, JSONObject())
            }
            parent = parent.getJSONObject(key)
        }
        if (value == null || value == JSONObject.NULL) parent.remove(parts.last())
        else parent.put(parts.last(), when (value) {
            is JSONObject -> JSONObject(value.toString())
            is JSONArray -> JSONArray(value.toString())
            else -> value
        })
    }
    private fun record(profile: JSONObject, sourceId: String, tupleId: String): JSONObject {
        val (data, source, selected) = selected(sourceId, tupleId)
        val derived = listOf("id", "seed", "ua.value", "reality.webview.values.user_agent", "reality.inventory_id",
            "reality.radio.values.wifi_name", "reality.radio.values.bluetooth_name") +
            profile.getJSONObject("identifiers").keys().asSequence().map { "identifiers.$it" }.toList()
        val provenance = JSONObject()
        derived.filter { DeviceReality.at(profile, it) != null }.forEach { path ->
            provenance.put(path, JSONObject().put("source", "deterministic-derivation-v1").put("confidence", "derived").put("availability", "known"))
        }
        val changes = selected.getJSONObject("changes")
        changes.keys().forEach { path ->
            val fact = changes.getJSONObject(path)
            provenance.put(path, JSONObject().also { row ->
                listOf("source", "confidence", "availability").forEach { row.put(it, fact.get(it)) }
            })
        }
        return JSONObject().put("format", 1).put("catalog_digest", RealityJson.digest(data)).put("source_id", sourceId)
            .put("tuple_id", tupleId).put("provenance", provenance).put("axes", JSONObject(source.getJSONObject("axes").toString()))
    }
    fun apply(profile: JSONObject, selection: Selection): JSONObject {
        val (data, source, selected) = selected(selection.sourceId, selection.tupleId)
        require(profile.optString("schema_version") == "0.4" && anchor(profile) == source.getString("anchor")) { "REALISM_BASELINE_MISMATCH" }
        require(profile.optString("seed") == selection.seed) { "REALISM_SEED_MISMATCH" }
        val result = JSONObject(profile.toString())
        val allowed = data.getJSONArray("allowed_paths").let { a -> (0 until a.length()).map(a::getString) }
        val changes = selected.getJSONObject("changes")
        changes.keys().forEach { path ->
            require(path in allowed && source.getJSONObject("originals").has(path)) { "REALISM_PATH_UNSUPPORTED" }
            setPath(result, path, changes.getJSONObject(path).get("value"))
        }
        val device = result.getJSONObject("device")
        var agent = result.getJSONObject("ua").getString("value").replace(Regex("Linux; Android [^;]+; [^)]*")) {
            "Linux; Android ${device.getString("version_release")}; ${device.getString("model")}"
        }
        agent = DeviceReality.userAgent(result.getJSONObject("reality"), agent)
        result.getJSONObject("ua").put("value", agent)
        if (DeviceReality.at(result, "reality.webview.availability") == "known")
            result.getJSONObject("reality").getJSONObject("webview").getJSONObject("values").put("user_agent", agent)
        result.getJSONObject("meta").put("instance_realism", record(result, selection.sourceId, selection.tupleId))
        require(ProfileValidator.validateProfile(result).none { it.isError }) { "REALISM_COHERENCE_ERROR" }
        return result
    }
    fun validate(profile: JSONObject): List<ProfileValidator.Finding> {
        val meta = profile.optJSONObject("meta") ?: return emptyList()
        if (!meta.has("instance_realism")) return emptyList()
        return try {
            val supplied = meta.getJSONObject("instance_realism")
            val sourceId = supplied.getString("source_id"); val tupleId = supplied.getString("tuple_id")
            val (data, source, selected) = selected(sourceId, tupleId)
            require(profile.getString("schema_version") == "0.4" && meta.getString("template") == source.getString("template_id"))
            require(same(supplied, record(profile, sourceId, tupleId)))
            val restored = JSONObject(profile.toString())
            val allowed = data.getJSONArray("allowed_paths").let { a -> (0 until a.length()).map(a::getString) }
            val changes = selected.getJSONObject("changes")
            changes.keys().forEach { path ->
                require(path in allowed)
                val actual = DeviceReality.at(profile, path)
                val expected = changes.getJSONObject(path).get("value")
                require(same(JSONObject().put("v", actual ?: JSONObject.NULL), JSONObject().put("v", expected)))
                setPath(restored, path, source.getJSONObject("originals").opt(path))
            }
            require(anchor(restored) == source.getString("anchor"))
            val seed = profile.getString("seed")
            val expectedIds = CloakForge.deriveIdentifierBundle(seed).toMutableMap()
            if (sourceId.startsWith("factory.")) {
                val prefix = source.getString("iccid_prefix")
                val suffix = java.math.BigInteger(hash("[\"$seed\",\"iccid\"]"), 16)
                    .mod(java.math.BigInteger.TEN.pow(18 - prefix.length)).toString().padStart(18 - prefix.length, '0')
                val digits = prefix + suffix
                expectedIds["sim_serial"] = digits + CloakForge.luhnCheckDigit(digits)
                if (profile.getJSONObject("telephony").getInt("sim_slot_count") == 1) expectedIds.remove("imei_secondary")
            }
            require(same(profile.getJSONObject("identifiers"), JSONObject(expectedIds as Map<*, *>)))
            require(DeviceReality.at(profile, "reality.inventory_id") == DeviceReality.token(seed, "inventory"))
            if (DeviceReality.at(profile, "reality.radio.availability") == "known") {
                for ((key, field) in listOf("wifi_name" to "wifi", "bluetooth_name" to "bluetooth"))
                    require(DeviceReality.at(profile, "reality.radio.values.$key") == "Cloak-" + DeviceReality.token(seed, field).take(12))
            }
            val expectedUa = DeviceReality.userAgent(profile.getJSONObject("reality"), CloakForge.buildUa(profile.getJSONObject("device"),
                profile.getJSONObject("display").getString("screen_size_class"), seed))
            require(DeviceReality.at(profile, "ua.value") == expectedUa)
            if (DeviceReality.at(profile, "reality.webview.availability") == "known")
                require(DeviceReality.at(profile, "reality.webview.values.user_agent") == expectedUa)
            val ids = profile.getJSONObject("identifiers").let { ids -> ids.keys().asSequence().map { normalize(ids.getString(it)) }.toList() }
            require(ids.distinct().size == ids.size)
            emptyList()
        } catch (_: Exception) {
            listOf(ProfileValidator.Finding(ProfileValidator.ERROR, "INSTANCE_REALISM_INVALID",
                "meta.instance_realism: approved tuple, provenance or baseline binding failed.", "meta.instance_realism"))
        }
    }
    private fun normalize(value: String) = value.lowercase(Locale.ROOT).replace(Regex("[:-]"), "")
    fun conflicts(profile: JSONObject, library: List<JSONObject>): Boolean {
        val ids = profile.getJSONObject("identifiers").let { obj -> obj.keys().asSequence().map { normalize(obj.getString(it)) }.toSet() }
        return library.any { other -> other.optString("id") == profile.optString("id") ||
            other.optJSONObject("identifiers")?.let { obj -> obj.keys().asSequence().any { normalize(obj.optString(it)) in ids } } == true } ||
            ProfileUniqueness(library.mapIndexed { i, p -> "saved-$i" to p }).findings(profile, ownId = "new").isNotEmpty()
    }
}
