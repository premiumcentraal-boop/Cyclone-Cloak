package dev.cyclone.cloak.forge

import org.json.JSONArray
import org.json.JSONObject

/** Pure read-only adapter. Supply catalog/cohort-separation-v1.json from bundled assets. */
class CohortSeparation(policyJson: String) {
    private val policy = JSONObject(policyJson)
    private fun strings(a: JSONArray) = (0 until a.length()).map(a::getString)
    private val categories = policy.getJSONObject("categories").let { c ->
        c.keys().asSequence().toList().sorted().associateWith { strings(c.getJSONArray(it)) }
    }
    private val paths = categories.values.flatten().distinct().sorted()
    private val identifiers = categories.getValue("stable_identifiers")
    init { require(policy.getInt("version") == 1 && categories.size == 7 && paths.isNotEmpty()) { "COHORT_POLICY_INVALID" } }

    fun analyze(input: List<JSONObject>): JSONObject {
        val valid = try {
            input.size <= 200 && input.all { p ->
                p.opt("id") is String && p.getString("id").matches(Regex("[A-Za-z0-9._:-]{1,96}")) &&
                    p.opt("name") is String && p.getString("name").let { it.codePointCount(0, it.length) in 1..160 } &&
                    p.getString("name").none { it.code < 32 || it.code == 127 } &&
                    ProfileValidator.validateProfile(p).none { it.isError }
            } && input.map { it.getString("id") }.distinct().size == input.size
        } catch (_: Exception) { false }
        require(valid) { "COHORT_INPUT_INVALID" }
        val profiles = input.sortedBy { it.getString("id") }
        val values = profiles.associate { p -> p.getString("id") to paths.associateWith { value(p, it) } }
        val pairs = mutableListOf<JSONObject>()
        val duplicates = mutableListOf<JSONObject>()
        for (i in profiles.indices) for (j in i + 1 until profiles.size) {
            val a = profiles[i].getString("id"); val b = profiles[j].getString("id")
            val av = values.getValue(a); val bv = values.getValue(b)
            val scores = JSONObject()
            var distance = 0; var confidence = 0
            for ((category, fields) in categories) {
                val known = fields.filter { av[it] != null && bv[it] != null }
                val d = known.count { av[it] != bv[it] } * 100 / fields.size
                val c = known.size * 100 / fields.size
                scores.put(category, JSONObject().put("distance", d).put("confidence", c))
                distance += d; confidence += c
            }
            val members = listOf(a, b)
            pairs += JSONObject().put("profiles", JSONArray(members)).put("categories", scores)
                .put("distance", distance / categories.size).put("confidence", confidence / categories.size)
            for (path in identifiers) {
                val x = av[path] ?: continue; val y = bv[path] ?: continue
                val nx = x.replace(Regex("[^a-z0-9]"), ""); val ny = y.replace(Regex("[^a-z0-9]"), "")
                if (x == y || nx.length == ny.length && nx.length >= 6 && nx.zip(ny).count { it.first != it.second } == 1) {
                    duplicates += JSONObject().put("profiles", JSONArray(members)).put("field", path)
                        .put("kind", if (x == y) "duplicate" else "near_duplicate")
                }
            }
        }
        val shared = mutableListOf<JSONObject>()
        val combinations = policy.getJSONArray("combinations")
        val groups = paths.map { listOf(it) } + (0 until combinations.length()).map { strings(combinations.getJSONArray(it)) }
        for (fields in groups) {
            val members = linkedMapOf<List<String>, MutableList<String>>()
            for (p in profiles) {
                val id = p.getString("id"); val signature = fields.map { values.getValue(id)[it] }
                if (signature.all { it != null }) members.getOrPut(signature.filterNotNull()) { mutableListOf() }.add(id)
            }
            members.values.sortedBy { it.joinToString("\u0000") }.filter { it.size > 1 }.forEach {
                shared += JSONObject().put("fields", JSONArray(fields)).put("profiles", JSONArray(it))
            }
        }
        val weakest = paths.map { path ->
            val different = pairs.count { pair ->
                val ids = pair.getJSONArray("profiles")
                val a = values.getValue(ids.getString(0))[path]; val b = values.getValue(ids.getString(1))[path]
                a != null && b != null && a != b
            }
            JSONObject().put("field", path).put("distance", different * 100 / maxOf(1, pairs.size))
                .put("known_profiles", profiles.count { values.getValue(it.getString("id"))[path] != null })
        }.sortedWith(compareBy({ it.getInt("distance") }, { it.getString("field") }))
        val missing = mutableListOf<JSONObject>()
        for (p in profiles) for (section in DeviceReality.sections.sorted()) {
            val availability = DeviceReality.at(p, "reality.$section.availability")
            val facts = DeviceReality.at(p, "reality.$section.values") as? JSONObject
            if (availability != "known" || facts == null || facts.length() == 0) {
                missing += JSONObject().put("profile", p.getString("id")).put("section", section)
                    .put("state", if (availability in listOf("unknown", "unavailable")) availability else "missing")
            }
        }
        val recommendations = weakest.filter { profiles.isNotEmpty() && it.getInt("distance") < 50 }.map {
            val path = it.getString("field")
            JSONObject().put("field", path).put("action", if (path in identifiers) "regenerate_derived_identifier"
                else "review_template_or_complete_known_facts")
        }
        val score = if (pairs.isEmpty()) null else pairs.sumOf { it.getInt("distance") } / pairs.size
        val confidence = if (pairs.isEmpty()) 0 else pairs.sumOf { it.getInt("confidence") } / pairs.size
        val warnings = mutableListOf<String>()
        if (pairs.isEmpty()) warnings += "INSUFFICIENT_COHORT"
        if (confidence < 80) warnings += "INCOMPLETE_FACTS"
        if (duplicates.isNotEmpty()) warnings += "IDENTIFIER_REUSE_OR_SIMILARITY"
        if (score != null && (score < 60 || pairs.any { it.getInt("distance") < 30 })) warnings += "HOMOGENEOUS_COHORT_NOT_HIGH_TRUST_EVIDENCE"
        return JSONObject().put("format", 1).put("policy", 1)
            .put("profiles", JSONArray(profiles.map { JSONObject().put("id", it.getString("id")).put("label", it.getString("name")) }))
            .put("pairs", JSONArray(pairs)).put("shared_groups", JSONArray(shared)).put("identifier_findings", JSONArray(duplicates))
            .put("weakest_fields", JSONArray(weakest)).put("missing_reality", JSONArray(missing)).put("recommendations", JSONArray(recommendations))
            .put("independence_score", score ?: JSONObject.NULL).put("confidence", confidence).put("warnings", JSONArray(warnings))
    }

    private fun value(p: JSONObject, path: String): String? {
        if (path.startsWith("reality.") && path.contains(".values") &&
            DeviceReality.at(p, path.split('.').take(2).joinToString(".") + ".availability") != "known") return null
        var v = DeviceReality.at(p, path) ?: return null
        if (v == JSONObject.NULL || v == "" || v == "unknown" || v == "unavailable" || v is JSONObject && v.length() == 0 || v is JSONArray && v.length() == 0) return null
        if (path in identifiers && v is String) v = v.lowercase(java.util.Locale.ROOT)
        return canonical(v)
    }
    private fun canonical(v: Any?): String = when (v) {
        is JSONObject -> v.keys().asSequence().toList().sorted().joinToString(",", "{", "}") { JSONObject.quote(it) + ":" + canonical(v.opt(it)) }
        is JSONArray -> (0 until v.length()).joinToString(",", "[", "]") { canonical(v.opt(it)) }
        is String -> JSONObject.quote(v)
        is Number -> if (v.toDouble() == 0.0) "0" else JSONObject.numberToString(v)
        null, JSONObject.NULL -> "null"
        else -> v.toString()
    }
}
