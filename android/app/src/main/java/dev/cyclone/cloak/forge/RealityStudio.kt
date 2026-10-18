package dev.cyclone.cloak.forge

import org.json.JSONObject
import org.json.JSONTokener

/** Local, copy-on-edit proposals. No binding, publication, storage or Android capability. */
object RealityStudio {
    data class Change(val path: String, val before: String, val after: String)
    data class Preview(val originalHash: String, val body: String, val changes: List<Change>,
        val findings: List<ProfileValidator.Finding>) {
        val valid get() = findings.none { it.isError }
    }
    fun fields(section: String): List<String> = valueRules(section).keys().asSequence().toList().sorted()
    fun fieldRule(path: String): JSONObject {
        require(allowed(path)) { "Unsupported Reality field." }
        return valueRules(path.split('.')[1]).getJSONObject(path.substringAfterLast('.'))
    }
    private fun valueRules(section: String): JSONObject = DeviceReality.rules.getJSONObject("properties")
        .getJSONObject(section).getJSONObject("properties").getJSONObject("values").getJSONObject("properties")
    fun path(section: String, field: String) = "reality.$section.values.$field"
    fun sensitive(path: String) = path in DeviceReality.uniquePaths || path.endsWith("input_method")
    fun display(value: Any?, path: String, reveal: Boolean = false): String =
        if (value == null || value == JSONObject.NULL) "not recorded"
        else if (sensitive(path) && !reveal) "hidden" else if (value is String) JSONObject.quote(value) else value.toString()
    fun compatible(profile: JSONObject, phone: PhoneTemplate): Boolean =
        listOf("manufacturer", "model", "device", "hardware", "sdk_int", "fingerprint").all {
            DeviceReality.at(profile, "device.$it") == phone.device.opt(it)
        }

    class Session(profile: JSONObject) {
        private val original = JSONObject(profile.toString())
        private var draft = JSONObject(profile.toString())
        private val locks = mutableSetOf<String>()
        val body get() = JSONObject(draft.toString())
        val locked get() = locks.toSet()
        fun lock(path: String, locked: Boolean) {
            require(allowed(path)) { "Unsupported Reality field." }
            if (locked) locks += path else locks -= path
        }
        fun edit(path: String, json: String): Preview {
            require(allowed(path) && path !in locks) { "This field is locked or not editable." }
            require(json.length <= 32000) { "Field input is too large." }
            StrictJson(json).check()
            val next = body
            require(next.optString("schema_version") == "0.4") { "Editing requires schema 0.4; older profiles remain unchanged." }
            val section = path.split('.')[1]; val field = path.substringAfterLast('.')
            val reality = next.optJSONObject("reality") ?: JSONObject().also { next.put("reality", it) }
            val envelope = reality.optJSONObject(section) ?: unknown().also { reality.put(section, it) }
            require(envelope.optString("availability") != "unavailable") { "This fact is unavailable. Repair cannot fabricate it." }
            val value = JSONTokener(json).nextValue()
            val values = envelope.getJSONObject("values")
            if (value == JSONObject.NULL) values.remove(field) else values.put(field, value)
            val edited = userFields(envelope) + field
            envelope.put("availability", if (values.length() == 0) "unknown" else "known")
                .put("source", "needs verification").put("reason", "User-set fields: ${edited.sorted().joinToString(",")}. Local descriptions; not device evidence.")
            return accept(next)
        }
        /** Reuses catalog and seed; fixed hardware is retained, never randomized. */
        fun regenerate(section: String, phone: PhoneTemplate): Preview = populate(setOf(section), phone, false)
        fun repair(phone: PhoneTemplate): Preview = populate(DeviceReality.sections.toSet(), phone, true)
        private fun populate(sections: Set<String>, phone: PhoneTemplate, missingOnly: Boolean): Preview {
            require(sections.all { it in DeviceReality.sections }) { "Unknown Reality section." }
            require(compatible(draft, phone)) { "TEMPLATE_MISMATCH: model, firmware, API and hardware must match the saved identity." }
            require(draft.optString("schema_version") == "0.4") { "Repair requires schema 0.4; no automatic migration." }
            val generated = DeviceReality.generate(phone.reality, draft.getString("seed"), DeviceReality.at(draft, "ua.value") as? String ?: "")
            val next = body
            val reality = next.optJSONObject("reality") ?: JSONObject().also { next.put("reality", it) }
            for (section in sections.sorted()) {
                val current = reality.optJSONObject(section)
                if (current?.optString("availability") == "unavailable") continue
                val source = generated.optJSONObject(section) ?: continue
                if (source.optString("availability") != "known") continue
                val values = current?.optJSONObject("values")?.let { JSONObject(it.toString()) } ?: JSONObject()
                val supplied = source.getJSONObject("values")
                val touched = supplied.keys().asSequence().filter { field ->
                    path(section, field) !in locks && (!missingOnly || !values.has(field))
                }.filter { !same(values.opt(it), supplied.opt(it)) }.toList()
                if (touched.isEmpty()) continue
                touched.forEach { values.put(it, supplied.get(it)) }
                // Mixed provenance stays conservative. User-set paths retain their explicit marker.
                val user = current?.let(::userFields).orEmpty() - touched.toSet()
                val envelope = JSONObject(source.toString()).put("values", values)
                if (current != null && values.keys().asSequence().any { it !in touched }) {
                    envelope.put("source", "needs verification").put("reason", if (user.isEmpty())
                        "Mixed retained and template-derived facts; review each value."
                    else "User-set fields: ${user.sorted().joinToString(",")}. Mixed retained and template-derived facts.")
                }
                reality.put(section, envelope)
            }
            return accept(next)
        }
        private fun accept(next: JSONObject): Preview {
            val result = preview(next)
            if (result.valid) draft = JSONObject(next.toString())
            return result
        }
        fun preview() = preview(body)
        private fun preview(next: JSONObject) = Preview(RealityJson.digest(original), next.toString(),
            diff(original, next), ProfileValidator.validateProfile(next))
    }
    fun userFields(envelope: JSONObject): Set<String> = envelope.optString("reason").takeIf { it.startsWith("User-set fields: ") }
        ?.substringAfter("User-set fields: ")?.substringBefore('.')?.split(',')?.toSet().orEmpty()
    private fun unknown() = JSONObject().put("availability", "unknown").put("source", "needs verification")
        .put("reason", "Not recorded.").put("values", JSONObject())
    private fun allowed(path: String): Boolean = DeviceReality.sections.any { s -> fields(s).any { path == path(s, it) } }
    private fun same(a: Any?, b: Any?) = RealityJson.digest(JSONObject().put("value", a ?: JSONObject.NULL)) ==
        RealityJson.digest(JSONObject().put("value", b ?: JSONObject.NULL))
    private fun diff(before: JSONObject, after: JSONObject): List<Change> {
        val changes = mutableListOf<Change>()
        fun walk(a: Any?, b: Any?, path: String) {
            if (a is JSONObject || b is JSONObject) {
                val left = a as? JSONObject; val right = b as? JSONObject
                (left?.keys()?.asSequence()?.toSet().orEmpty() + right?.keys()?.asSequence()?.toSet().orEmpty()).sorted()
                    .forEach { walk(left?.opt(it), right?.opt(it), if (path.isEmpty()) it else "$path.$it") }
            } else if (!same(a, b)) changes += Change(path, display(a, path), display(b, path))
        }
        walk(before, after, "")
        return changes
    }
    /** Re-check at explicit save time; optimistic concurrency prevents overwriting another edit. */
    fun save(preview: Preview, current: JSONObject, library: List<Pair<String, JSONObject>>, confirmed: Boolean,
        persist: (JSONObject) -> Unit) {
        require(confirmed) { "Confirm the before/after preview before saving." }
        require(preview.originalHash == RealityJson.digest(current)) { "STALE_DRAFT: identity changed; reopen Reality Studio." }
        val next = JSONObject(preview.body)
        require(next.optString("schema_version") == "0.4") { "Reality Studio requires schema 0.4." }
        require(next.getString("id") == current.getString("id")) { "Identity cannot change." }
        require(same(DeviceReality.at(next, "reality.inventory_id"), DeviceReality.at(current, "reality.inventory_id"))) { "Inventory identity cannot change in Reality Studio." }
        val allowed = JSONObject(current.toString()).also { p -> next.optJSONObject("reality")?.let { p.put("reality", it) } }
        require(RealityJson.digest(allowed) == RealityJson.digest(next)) { "Only Reality descriptions may change." }
        val findings = ProfileValidator.validateProfile(next) + ProfileUniqueness(library).findings(next)
        require(findings.none { it.isError || it.code == "DUPLICATE_IDENTIFIER" }) {
            findings.filter { it.isError || it.code == "DUPLICATE_IDENTIFIER" }.joinToString("; ") { "${it.field}: ${it.code}: ${it.message}" }
        }
        persist(next)
    }
}

/** Canonical JSON hash; matches the existing hash-bound sidecar without depending on storage. */
internal object RealityJson {
    fun digest(value: JSONObject): String {
        fun canonical(item: Any?): String = when (item) {
            is JSONObject -> item.keys().asSequence().toSet().sorted().joinToString(",", "{", "}") { JSONObject.quote(it) + ":" + canonical(item.opt(it)) }
            is org.json.JSONArray -> (0 until item.length()).joinToString(",", "[", "]") { canonical(item.opt(it)) }
            is String -> JSONObject.quote(item)
            is Number -> JSONObject.numberToString(item)
            null, JSONObject.NULL -> "null"
            else -> item.toString()
        }
        return java.security.MessageDigest.getInstance("SHA-256").digest(canonical(value).toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
}
