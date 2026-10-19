package dev.cyclone.cloak.forge

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDateTime

/** Pure local descriptions and evidence binding. No Google, account, package or storage APIs. */
object PlaySurfaceContract {
    const val FORMAT = 1
    const val MAX_BYTES = 131072
    private val rules get() = JSONObject(PlaySurfaceRules.json)
    val accountModes: List<String> get() = strings(rules.getJSONObject("properties").getJSONObject("account").getJSONObject("properties").getJSONObject("mode").getJSONArray("enum"))
    val readinessStates: List<String> get() = strings(recordRule().getJSONObject("properties").getJSONObject("state").getJSONArray("enum"))
    val reasonCodes: List<String> get() = strings(recordRule().getJSONObject("properties").getJSONObject("reason_codes").getJSONObject("items").getJSONArray("enum"))
    private fun recordRule() = rules.getJSONObject("\$defs").getJSONObject("readiness")
    private fun strings(a: JSONArray) = (0 until a.length()).map { a.getString(it) }

    data class Issue(val field: String, val code: String) {
        val message: String get() = when (code) {
            "PS_TYPE" -> "Expected a value of the declared type."
            "PS_FIELD" -> "Only declared fields are allowed; credentials are not supported."
            "PS_REQUIRED" -> "A required contract field is missing."
            "PS_VALUE" -> "Value is outside the allowed format, range or vocabulary."
            "PS_ACCOUNT" -> "Account mode and slot assignment disagree."
            "PS_RECORD" -> "Readiness state and evidence requirements disagree."
            "PS_NO_EVIDENCE" -> "Complete local evidence is not available."
            "PS_PROFILE_MISMATCH" -> "Evidence belongs to another profile or profile body."
            "PS_ARTIFACT_MISMATCH" -> "Evidence belongs to a different artifact."
            "PS_EVIDENCE_MISMATCH" -> "Evidence digest does not match the supplied report."
            "PS_ACCOUNT_SLOT_CONFLICT" -> "Active profiles reuse an account slot without shared consent on every assignment."
            "PS_PACKAGE_CONFLICT" -> "Package expectations disagree with the supplied local observation."
            "PS_EXPECTATION_CONFLICT" -> "Service expectations disagree with the supplied local observation."
            "PS_EXPECTATION_UNKNOWN" -> "An expectation or its observation is unknown."
            "PS_NOT_PRESENT" -> "No Play surface expectations are recorded."
            "PS_REVIEW_REQUIRED" -> "Local account setup needs review."
            "PS_LOCAL_CHECKS_PASSED" -> "Supplied local checks passed; no platform verdict is implied."
            else -> "Invalid local contract."
        }
    }
    class ContractException(val issues: List<Issue>) : IllegalArgumentException(issues.joinToString("; ") { "${it.field}: ${it.code}" })
    private val order = compareBy<Issue>({ it.field }, { it.code })
    private fun sorted(issues: List<Issue>) = issues.distinct().sortedWith(order)
    private fun check(value: Any?, rule: JSONObject, path: String, issues: MutableList<Issue>) {
        fun add(code: String) { issues += Issue(path, code) }
        if (rule.has("anyOf")) {
            if (value == null || value == JSONObject.NULL) return
            check(value, rule.getJSONArray("anyOf").getJSONObject(0), path, issues)
            return
        }
        val kind = rule.getString("type")
        val valid = when (kind) {
            "object" -> value is JSONObject
            "array" -> value is JSONArray
            "string" -> value is String
            "integer" -> value is Int || value is Long
            "boolean" -> value is Boolean
            "null" -> value == null || value == JSONObject.NULL
            else -> false
        }
        if (!valid) { add("PS_TYPE"); return }
        if ((rule.has("const") && rule.opt("const") != value) ||
            (rule.has("enum") && (0 until rule.getJSONArray("enum").length()).none { rule.getJSONArray("enum").get(it) == value })) add("PS_VALUE")
        when (value) {
            is JSONObject -> {
                val props = rule.getJSONObject("properties")
                if (value.keys().asSequence().any { !props.has(it) }) add("PS_FIELD")
                strings(rule.getJSONArray("required")).filterNot(value::has).forEach { issues += Issue("$path.$it", "PS_REQUIRED") }
                props.keys().asSequence().toList().sorted().filter(value::has).forEach { check(value.opt(it), props.getJSONObject(it), "$path.$it", issues) }
            }
            is JSONArray -> {
                val max = rule.getInt("maxItems")
                if (value.length() > max || (rule.optBoolean("uniqueItems") &&
                        (0 until value.length()).map { it -> value.opt(it).toString() }.distinct().size != value.length())) add("PS_VALUE")
                for (i in 0 until minOf(max, value.length())) check(value.opt(i), rule.getJSONObject("items"), "$path[$i]", issues)
            }
            is String -> {
                if (value.codePointCount(0, value.length) !in rule.optInt("minLength", 0)..rule.optInt("maxLength", 100000)) add("PS_VALUE")
                if (rule.has("pattern") && !Regex(rule.getString("pattern")).matches(value)) add("PS_VALUE")
                if (rule.optString("format") == "date-time") try {
                    require(value.take(4).toInt() in 1..9999 && value.endsWith("Z"))
                    LocalDateTime.parse(value.dropLast(1))
                } catch (_: Exception) { add("PS_VALUE") }
            }
        }
    }
    fun validateSurface(value: Any?): List<Issue> {
        val issues = mutableListOf<Issue>()
        check(value, rules, "play_surface", issues)
        if (issues.isEmpty()) {
            val a = (value as JSONObject).getJSONObject("account")
            if ((a.getString("mode") in setOf("dedicated", "managed") && a.isNull("slot_id")) ||
                (a.getString("mode") == "none" && (!a.isNull("slot_id") || a.getBoolean("shared")))) issues += Issue("play_surface.account.slot_id", "PS_ACCOUNT")
        }
        return sorted(issues)
    }
    fun validateProfileSection(profile: JSONObject): List<Issue> = if (profile.has("play_surface")) validateSurface(profile.opt("play_surface")) else emptyList()
    fun validateReadiness(value: Any?): List<Issue> {
        val issues = mutableListOf<Issue>()
        check(value, recordRule(), "readiness", issues)
        if (issues.isEmpty()) {
            val record = value as JSONObject
            val ready = record.getString("state") == "READY"
            val complete = !record.isNull("evidence_digest") && !record.isNull("artifact_hash")
            val reasons = strings(record.getJSONArray("reason_codes"))
            if ((record.getString("state") != "UNKNOWN" && !complete) ||
                (ready && reasons != listOf("PS_LOCAL_CHECKS_PASSED")) ||
                (!ready && (reasons.isEmpty() || "PS_LOCAL_CHECKS_PASSED" in reasons))) issues += Issue("readiness", "PS_RECORD")
        }
        return sorted(issues)
    }
    private fun parse(text: String, validator: (Any?) -> List<Issue>): JSONObject {
        val value = try {
            require(text.toByteArray(Charsets.UTF_8).size <= MAX_BYTES)
            StrictJson(text).check()
            JSONObject(text)
        } catch (_: Exception) { throw ContractException(listOf(Issue("contract", "PS_VALUE"))) }
        val issues = validator(value)
        if (issues.isNotEmpty()) throw ContractException(issues)
        return value
    }
    fun parseSurface(text: String): JSONObject = parse(text, ::validateSurface)
    fun parseReadiness(text: String): JSONObject = parse(text, ::validateReadiness)
    fun encodeSurface(value: JSONObject): String = parseSurface(value.toString()).toString()
    fun encodeReadiness(value: JSONObject): String = parseReadiness(value.toString()).toString()

    data class Assignment(val profileId: String, val active: Boolean, val profile: JSONObject)
    fun accountSlotConflicts(assignments: List<Assignment>): List<Issue> {
        if (assignments.size > 4096 || assignments.map { it.profileId }.distinct().size != assignments.size ||
            assignments.any { !Regex("[A-Za-z0-9][A-Za-z0-9._:-]{0,95}").matches(it.profileId) }) throw ContractException(listOf(Issue("assignments", "PS_VALUE")))
        val groups = mutableMapOf<String, MutableList<Pair<Int, Boolean>>>()
        assignments.forEachIndexed { i, assignment ->
            val issues = validateProfileSection(assignment.profile)
            if (issues.isNotEmpty()) throw ContractException(issues)
            val a = assignment.profile.optJSONObject("play_surface")?.getJSONObject("account")
            if (assignment.active && a != null && !a.isNull("slot_id")) groups.getOrPut(a.getString("slot_id")) { mutableListOf() }.add(i to a.getBoolean("shared"))
        }
        return sorted(groups.values.filter { it.size > 1 && it.any { member -> !member.second } }
            .flatten().map { Issue("assignments[${it.first}].play_surface.account.slot_id", "PS_ACCOUNT_SLOT_CONFLICT") })
    }
    fun serviceConflicts(expected: JSONObject, observed: JSONObject?): List<Issue> {
        val issues = validateSurface(expected)
        if (issues.isNotEmpty()) throw ContractException(issues)
        if (observed == null) return listOf(Issue("play_surface.services", "PS_NO_EVIDENCE"))
        val malformed = mutableListOf<Issue>()
        check(observed, rules.getJSONObject("properties").getJSONObject("services"), "observed.services", malformed)
        if (malformed.isNotEmpty()) throw ContractException(sorted(malformed))
        val result = mutableListOf<Issue>()
        val services = expected.getJSONObject("services")
        services.keys().asSequence().forEach { key ->
            val wanted = services.get(key); val actual = observed.get(key); val path = "play_surface.services.$key"
            if (wanted is JSONObject) {
                val got = actual as JSONObject
                val unknown = wanted.getString("state") == "unknown" || got.getString("state") == "unknown"
                val declared = listOf("package_name", "version_name").filter(wanted::has)
                if ((!unknown && wanted.getString("state") != got.getString("state")) || declared.any { got.has(it) && wanted.get(it) != got.get(it) }) result += Issue(path, "PS_PACKAGE_CONFLICT")
                else if (unknown || declared.any { !got.has(it) }) result += Issue(path, "PS_EXPECTATION_UNKNOWN")
            } else if (wanted == "unknown" || actual == "unknown") result += Issue(path, "PS_EXPECTATION_UNKNOWN")
            else if (wanted != actual) result += Issue(path, "PS_EXPECTATION_CONFLICT")
        }
        return sorted(result)
    }
    /** Returns original evidence bindings, downgrading on mismatch; never relabels another report. */
    fun bindReadiness(record: JSONObject, profileId: String, profileHash: String, artifactHash: String?, evidenceDigest: String?): JSONObject {
        val result = parseReadiness(record.toString())
        val reasons = mutableListOf<String>()
        if (result.getString("profile_id") != profileId || result.getString("profile_hash") != profileHash) reasons += "PS_PROFILE_MISMATCH"
        if ((if (result.isNull("artifact_hash")) null else result.getString("artifact_hash")) != artifactHash) reasons += "PS_ARTIFACT_MISMATCH"
        if ((if (result.isNull("evidence_digest")) null else result.getString("evidence_digest")) != evidenceDigest) reasons += "PS_EVIDENCE_MISMATCH"
        if (evidenceDigest == null || artifactHash == null || result.isNull("evidence_digest") || result.isNull("artifact_hash")) reasons += "PS_NO_EVIDENCE"
        if (reasons.isNotEmpty()) result.put("state", "UNKNOWN").put("reason_codes", JSONArray(reasons.distinct().sorted()))
        return result
    }
}
