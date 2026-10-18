package dev.cyclone.cloak.forge

import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

private val uniquenessCodes = setOf("DUPLICATE_IDENTIFIER", "PROFILE_EXISTS", "NAME_EXISTS", "DUPLICATE_SEED")

/** Pure local planning. No Android context, file writes, connector or binding capability. */
class BatchIdentityPlanner(private val engine: DeviceProfileEngine = DefaultDeviceProfileEngine()) {
    data class Request(val templateIds: List<String>, val perTemplate: Int, val seed: String, val labelPrefix: String = "", val schemaVersion: String = "0.4")
    data class Draft(val key: String, val templateId: String, val templateName: String, val index: Int,
        val revision: Int, val requestedSeed: String, val chosenSeed: String, val attempts: Int,
        val label: String, val templateDigest: String, val profile: JSONObject?, val metadata: JSONObject?,
        val findings: List<ProfileValidator.Finding>) {
        val valid: Boolean get() = profile != null && findings.none { it.isError && it.code !in uniquenessCodes }
        val unique: Boolean get() = profile != null && findings.none { it.code in uniquenessCodes }
        val approved: Boolean get() = valid && unique
    }
    data class Plan(val request: Request, val drafts: List<Draft>) {
        val summary: JSONObject get() = JSONObject().put("total", drafts.size)
            .put("validation_pass", drafts.count { it.valid }).put("validation_fail", drafts.count { !it.valid })
            .put("unique", drafts.count { it.unique }).put("conflicting", drafts.count { it.profile != null && !it.unique })
            .put("uniqueness_not_checked", drafts.count { it.profile == null })
            .put("warnings", drafts.sumOf { d -> d.findings.count { it.severity == ProfileValidator.WARNING } })
            .put("templates", JSONObject(drafts.groupingBy { it.templateId }.eachCount()))
            .put("engine_version", "1").put("schema_version", request.schemaVersion)
    }

    fun plan(request: Request, templates: List<PhoneTemplate>, existing: List<JSONObject>): Plan {
        val selected = validate(request, templates)
        val drafts = mutableListOf<Draft>()
        for (phone in selected) for (index in 0 until request.perTemplate) {
            drafts += generate(request, phone, index, 0, existing + drafts.mapNotNull { it.profile })
        }
        require(drafts.map { it.chosenSeed }.distinct().size == drafts.size) { "DUPLICATE_SEED: generated drafts reused a seed." }
        return review(Plan(request, drafts), existing)
    }

    fun regenerate(plan: Plan, selected: Set<String>, templates: List<PhoneTemplate>, existing: List<JSONObject>): Plan {
        val phones = validate(plan.request, templates).associateBy { it.id }
        require(selected.all { key -> plan.drafts.any { it.key == key } }) { "Unknown selected draft." }
        val retained = plan.drafts.filter { it.key !in selected }.mapNotNull { it.profile }
        val generated = mutableListOf<JSONObject>()
        val drafts = plan.drafts.map { draft ->
            if (draft.key !in selected) draft else generate(plan.request, phones.getValue(draft.templateId),
                draft.index, draft.revision + 1, existing + retained + generated).also { it.profile?.let(generated::add) }
        }
        require(drafts.map { it.chosenSeed }.distinct().size == drafts.size) { "DUPLICATE_SEED: regenerated drafts reused a seed." }
        return review(plan.copy(drafts = drafts), existing)
    }

    fun review(plan: Plan, existing: List<JSONObject>): Plan = plan.copy(drafts = plan.drafts.map { draft ->
        val profile = draft.profile ?: return@map draft
        val others = existing + plan.drafts.filter { it.key != draft.key }.mapNotNull { it.profile }
        val findings = engine.validate(profile).toMutableList()
        findings += ProfileUniqueness(others.mapIndexed { i, p -> "other-$i" to p }).findings(profile, "new")
        if (others.any { it.optString("id") == profile.optString("id") }) findings += problem("PROFILE_EXISTS", "This profile ID already exists.")
        if (others.any { it.optString("name").equals(draft.label, true) }) findings += problem("NAME_EXISTS", "This label already exists. Use a different label prefix.")
        if (others.any { it.optString("seed") == draft.chosenSeed }) findings += problem("DUPLICATE_SEED", "This seed already exists. Regenerate the draft.")
        draft.copy(findings = ProfileValidator.summarize(findings).findings)
    })

    private fun generate(request: Request, phone: PhoneTemplate, index: Int, revision: Int, existing: List<JSONObject>): Draft {
        val requested = deriveSeed(request.seed, phone.id, index, revision)
        val label = label(request, phone, index)
        val base = Draft("${phone.id}:$index", phone.id, phone.label, index, revision, requested, requested, 1,
            label, templateDigest(phone), null, null, emptyList())
        return try {
            val bundle = engine.generate_unique(IdentityEngine.GenerationRequest(label, requested, phone, request.schemaVersion), existing)
            val profile = bundle.getJSONObject("profile")
            val metadata = bundle.getJSONObject("metadata")
            EngineBundleCodec.validateMetadata(metadata, profile)
            base.copy(profile = profile, metadata = metadata, chosenSeed = profile.getString("seed"),
                attempts = metadata.getInt("attempt_count"), findings = engine.validate(profile))
        } catch (invalid: DefaultDeviceProfileEngine.InvalidProfile) {
            base.copy(findings = invalid.findings)
        } catch (_: Exception) {
            base.copy(findings = listOf(problem("GENERATION_FAILED", "Could not generate a coherent unique draft. Remove it or regenerate.")))
        }
    }

    private fun validate(request: Request, templates: List<PhoneTemplate>): List<PhoneTemplate> {
        require(request.schemaVersion in setOf("0.2", "0.3", "0.4")) { "Unsupported profile schema." }
        require(request.templateIds.isNotEmpty() && request.templateIds.distinct().size == request.templateIds.size) { "Select distinct base templates." }
        require(request.perTemplate in 1..20 && request.templateIds.size <= 100 / request.perTemplate) { "Choose 1–20 drafts per template, up to 100 total." }
        require(SEED.matches(request.seed)) { "Malformed batch seed." }
        require(request.labelPrefix == request.labelPrefix.trim() && Regex("[\\p{L}\\p{N} _.\\-]{0,32}").matches(request.labelPrefix)) { "Label prefix: up to 32 letters, digits, spaces, dots, underscores or hyphens." }
        require(templates.map { it.id }.distinct().size == templates.size) { "Ambiguous template IDs." }
        return request.templateIds.sorted().map { id -> templates.firstOrNull { it.id == id } ?: error("Missing template: $id") }
    }

    /** Reproducible local plan only. Seeds are private; no bodies or identifier values are exported. */
    fun encode(plan: Plan): String {
        val request = plan.request
        return JSONObject().put("planner_version", VERSION).put("engine_version", "1")
            .put("schema_version", request.schemaVersion)
            .put("request", JSONObject().put("template_ids", JSONArray(request.templateIds.sorted()))
                .put("per_template", request.perTemplate).put("seed", request.seed).put("label_prefix", request.labelPrefix))
            .put("drafts", JSONArray().also { array -> plan.drafts.forEach { d -> array.put(JSONObject()
                .put("template_id", d.templateId).put("index", d.index).put("revision", d.revision)
                .put("seed", d.chosenSeed).put("attempt_count", d.attempts).put("label", d.label)
                .put("template_sha256", d.templateDigest)) } })
            .put("validation_summary", plan.summary).toString(2)
    }

    fun decode(text: String, templates: List<PhoneTemplate>, existing: List<JSONObject>): Plan {
        require(text.length <= 200_000) { "Batch plan is too large." }
        StrictJson(text).check()
        val root = JSONObject(text)
        require(root.optJSONObject("validation_summary") != null) { "Malformed validation summary." }
        require(root.keys().asSequence().toSet() == setOf("planner_version", "engine_version", "schema_version", "request", "drafts", "validation_summary")) { "Unsupported batch plan fields." }
        require(root.opt("planner_version") == VERSION && root.opt("engine_version") == "1" && root.opt("schema_version") in setOf("0.2", "0.3", "0.4")) { "Unsupported planner, engine or schema version." }
        val r = root.getJSONObject("request")
        require(r.keys().asSequence().toSet() == setOf("template_ids", "per_template", "seed", "label_prefix") && r.opt("per_template") is Int && r.opt("seed") is String && r.opt("label_prefix") is String) { "Malformed batch request." }
        val ids = r.getJSONArray("template_ids")
        val request = Request((0 until ids.length()).map { require(ids.get(it) is String); ids.getString(it) },
            r.getInt("per_template"), r.getString("seed"), r.getString("label_prefix"), root.getString("schema_version"))
        val phones = validate(request, templates).associateBy { it.id }
        val rows = root.getJSONArray("drafts")
        require(rows.length() in 1..request.perTemplate * phones.size) { "Invalid draft count." }
        val drafts = (0 until rows.length()).map { i ->
            val d = rows.getJSONObject(i)
            require(d.keys().asSequence().toSet() == setOf("template_id", "index", "revision", "seed", "attempt_count", "label", "template_sha256")) { "Malformed draft fields." }
            require(listOf("index", "revision", "attempt_count").all { d.opt(it) is Int }) { "Draft counts must be integers." }
            val phone = phones[d.getString("template_id")] ?: error("Missing template.")
            val index = d.getInt("index"); val revision = d.getInt("revision"); val attempts = d.getInt("attempt_count")
            require(index in 0 until request.perTemplate && revision in 0..1_000_000 && attempts in 1..128) { "Invalid derivation metadata." }
            require(d.opt("label") == label(request, phone, index) && d.opt("template_sha256") == templateDigest(phone)) { "Label or template changed. Review a new plan." }
            val requested = deriveSeed(request.seed, phone.id, index, revision)
            val chosen = if (attempts == 1) requested else DefaultDeviceProfileEngine.retrySeed(requested, attempts)
            require(d.opt("seed") == chosen) { "Seed does not match its derivation metadata." }
            val bundle = engine.generate(IdentityEngine.GenerationRequest(d.getString("label"), chosen, phone, request.schemaVersion))
            val metadata = bundle.getJSONObject("metadata").put("chosen_seed", chosen).put("attempt_count", attempts)
            val profile = bundle.getJSONObject("profile")
            EngineBundleCodec.validateMetadata(metadata, profile)
            Draft("${phone.id}:$index", phone.id, phone.label, index, revision, requested, chosen, attempts,
                d.getString("label"), templateDigest(phone), profile, metadata, engine.validate(profile))
        }
        require(drafts.map { it.key }.distinct().size == drafts.size && drafts.map { it.chosenSeed }.distinct().size == drafts.size) { "Duplicate draft or seed." }
        // Imported summaries are descriptive, never trusted to approve a draft.
        return review(Plan(request, drafts), existing)
    }

    companion object {
        const val VERSION = "1"
        private val SEED = Regex("[0-9a-f]{64}")
        fun deriveSeed(seed: String, templateId: String, index: Int, revision: Int): String =
            MessageDigest.getInstance("SHA-256").digest("cloak-batch-v1:$seed:$templateId:$index:$revision".toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
        private fun templateDigest(phone: PhoneTemplate): String {
            fun canonical(value: Any?): String = when (value) {
                is JSONObject -> value.keys().asSequence().toSet().sorted().joinToString(",", "{", "}") { JSONObject.quote(it) + ":" + canonical(value.opt(it)) }
                is JSONArray -> (0 until value.length()).joinToString(",", "[", "]") { canonical(value.opt(it)) }
                is String -> JSONObject.quote(value)
                is Number -> JSONObject.numberToString(value)
                null, JSONObject.NULL -> "null"
                else -> value.toString()
            }
            return MessageDigest.getInstance("SHA-256").digest(canonical(phone.toJson().put("camera", phone.camera)).toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
        }
        private fun label(request: Request, phone: PhoneTemplate, index: Int) =
            listOf(request.labelPrefix, phone.label).filter { it.isNotBlank() }.joinToString(" ").take(56) + " ${index + 1}"
        private fun problem(code: String, message: String) = ProfileValidator.Finding(ProfileValidator.ERROR, code, message)
    }
}

/** Explicit per-item saves; earlier successes survive later failures. No binding callbacks exist. */
object BatchIdentitySave {
    data class Item(val key: String, val status: String, val reason: String)
    fun save(plan: BatchIdentityPlanner.Plan, approved: Set<String>, existing: () -> List<JSONObject>,
        persist: (JSONObject, JSONObject) -> Unit): List<Item> {
        val saved = mutableListOf<JSONObject>()
        return plan.drafts.map { draft ->
            if (draft.key !in approved) Item(draft.key, "SKIPPED", "Not approved.")
            else try {
                val library = existing() + saved
                val checked = BatchIdentityPlanner().review(plan.copy(drafts = listOf(draft)), library).drafts.single()
                when {
                    !checked.approved -> Item(draft.key, "SKIPPED", checked.findings.joinToString("; ") { "${it.code}: ${it.message}" })
                    else -> {
                        val profile = requireNotNull(checked.profile)
                        persist(JSONObject(profile.toString()), JSONObject(requireNotNull(checked.metadata).toString()))
                        saved += profile
                        Item(draft.key, "SAVED", "Saved locally. No binding created.")
                    }
                }
            } catch (_: Exception) { Item(draft.key, "FAILED", "SAVE_FAILED: local write failed. Earlier saved items are preserved; retry this item.") }
        }
    }
}
