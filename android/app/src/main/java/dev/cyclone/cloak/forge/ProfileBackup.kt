package dev.cyclone.cloak.forge

import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.time.Instant
import java.util.UUID

/** Portable profile-only envelope; existing profile/library storage and connector schemas do not change. */
object ProfileBackup {
    const val KIND = "cyclone-cloak-profile-backup"
    const val FORMAT = 1
    private val sections = mapOf(
        "device" to listOf("manufacturer", "brand", "model", "product", "device", "hardware", "fingerprint", "version_release", "sdk_int", "security_patch", "build_id", "version_incremental", "build_date_utc", "first_api_level", "bootloader", "baseband"),
        "display" to listOf("width", "height", "density", "refresh_rate_hz", "screen_size_class"),
        "telephony" to listOf("sim_slot_count", "carrier_name", "mcc", "mnc", "network_type"),
        "locale" to listOf("language", "country", "timezone"), "ua" to listOf("value"),
    )
    private fun project(profile: JSONObject, sensitive: Boolean): JSONObject {
        val out = JSONObject().put("schema_version", profile.opt("schema_version")).put("name", profile.opt("name"))
        sections.forEach { (section, keys) -> profile.optJSONObject(section)?.let { source ->
            val block = JSONObject(); keys.forEach { key -> if (source.has(key)) block.put(key, source.get(key)) }; out.put(section, block)
        } }
        if (sensitive) {
            out.put("id", profile.opt("id")).put("seed", profile.opt("seed"))
            val identifiers = JSONObject(); val source = profile.optJSONObject("identifiers")
            ProfileUniqueness.fields.forEach { key -> if (source?.has(key) == true) identifiers.put(key, source.get(key)) }
            out.put("identifiers", identifiers)
        }
        return out
    }
    data class Review(val profiles: List<JSONObject>, val findings: List<ProfileValidator.Finding>, val manifest: JSONObject?, val libraryStamp: String) {
        val canSave get() = findings.none { it.isError }
    }
    fun export(profiles: List<JSONObject>, appVersion: String, includeIdentity: Boolean = false,
               label: String? = null, now: Instant = Instant.now()): JSONObject {
        require(profiles.isNotEmpty() && profiles.size <= 1000) { "Backup must contain 1..1000 profiles." }
        profiles.forEach { require(ProfileValidator.errors(it).isEmpty()) { "An invalid profile cannot be exported." } }
        require(label == null || label.length <= 128) { "Backup label must be at most 128 characters." }
        val manifest = JSONObject().put("schema_version", ProfileValidator.SCHEMA_VERSION).put("app_version", appVersion)
            .put("created_at", now.toString()).put("profile_count", profiles.size)
        label?.trim()?.takeIf { it.isNotEmpty() }?.let { manifest.put("label", it) }
        return JSONObject().put("kind", KIND).put("schemaVersion", FORMAT).put("manifest", manifest)
            .put("includes_identity", includeIdentity).put("profiles", JSONArray(profiles.map { project(it, includeIdentity) }))
    }
    fun libraryStamp(existing: List<Pair<String, JSONObject>>): String = existing.sortedBy { it.first }
        .joinToString("\n") { it.first + ":" + it.second.toString() }
    fun parse(text: String, existing: List<Pair<String, JSONObject>>, seed: () -> String = CloakForge::newSeed,
              uuid: () -> String = { UUID.randomUUID().toString() }): Review {
        val tokenizer = JSONTokener(text)
        val root = JSONObject(tokenizer)
        require(tokenizer.nextClean() == '\u0000') { "BACKUP_JSON: trailing data is not allowed." }
        var manifest: JSONObject? = null
        val raw: List<JSONObject>
        var sensitive = true
        when (root.optString("kind")) {
            KIND -> {
                require(root.opt("schemaVersion") == FORMAT) { "BACKUP_VERSION: unsupported backup format." }
                manifest = root.getJSONObject("manifest")
                require(manifest.opt("schema_version") == ProfileValidator.SCHEMA_VERSION) { "SCHEMA_VERSION: backup schema mismatch." }
                Instant.parse(manifest.getString("created_at"))
                require(manifest.opt("app_version") is String && manifest.getString("app_version").isNotBlank()) { "MANIFEST: app version missing." }
                require(!manifest.has("label") || (manifest.opt("label") is String && manifest.getString("label").length <= 128)) { "MANIFEST: invalid label." }
                require(root.opt("includes_identity") is Boolean) { "MANIFEST: identity selection missing." }
                sensitive = root.getBoolean("includes_identity")
                val array = root.getJSONArray("profiles")
                require(manifest.opt("profile_count") == array.length()) { "MANIFEST: profile count mismatch." }
                raw = (0 until array.length()).map { array.getJSONObject(it) }
            }
            Imports.FLEET_KIND -> {
                require(root.opt("schemaVersion") == 1) { "BACKUP_VERSION: unsupported fleet format." }
                val array = root.getJSONArray("profiles")
                require(root.opt("count") == array.length()) { "MANIFEST: fleet count mismatch." }
                raw = (0 until array.length()).map { array.getJSONObject(it) }
            }
            "" -> raw = listOf(root)
            else -> error("BACKUP_KIND: unsupported backup kind.")
        }
        require(raw.isNotEmpty() && raw.size <= 1000) { "PROFILE_COUNT: import must contain 1..1000 profiles." }
        val occupied = existing.map { it.first }.toMutableSet()
        val names = existing.map { it.second.optString("name").lowercase() }.toMutableSet()
        val profiles = raw.map { source ->
            require(source.opt("schema_version") == ProfileValidator.SCHEMA_VERSION) { "SCHEMA_VERSION: profile schema mismatch." }
            val profile = project(source, sensitive)
            if (!sensitive) {
                val fresh = seed(); profile.put("seed", fresh).put("id", uuid())
                    .put("identifiers", JSONObject(CloakForge.deriveIdentifierBundle(fresh)))
            }
            val id = profile.optString("id")
            require(Regex("[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}").matches(id)) { "PROFILE_ID: invalid profile ID." }
            if (id in occupied) profile.put("id", uuid())
            require(occupied.add(profile.getString("id"))) { "PROFILE_ID: duplicate imported ID." }
            require(profile.opt("name") is String && profile.getString("name").isNotBlank() && profile.getString("name").length <= 64) { "NAME: profile name must be a nonempty string of at most 64 characters." }
            val base = profile.getString("name")
            var name = base; var suffix = 2
            while (name.lowercase() in names) { val ending = " (imported ${suffix++})"; name = base.take(64 - ending.length) + ending }
            names += name.lowercase(); profile.put("name", name)
            profile
        }
        val validation = profiles.flatMapIndexed { index, profile -> ProfileValidator.validateProfile(profile).map {
            it.copy(field = "profiles[$index].${it.field.orEmpty()}")
        } }
        val duplicates = profiles.flatMapIndexed { index, profile ->
            ProfileUniqueness(existing + profiles.map { it.getString("id") to it }).findings(profile).map { it.copy(field = "profiles[$index].${it.field}") }
        }
        return Review(profiles, ProfileValidator.summarize(validation + duplicates).findings, manifest, libraryStamp(existing))
    }
    fun commit(review: Review, existing: List<Pair<String, JSONObject>>, confirmed: Boolean, acceptWarnings: Boolean,
               saveBatch: (List<JSONObject>) -> Unit) {
        require(confirmed) { "Confirm the complete import first." }
        require(review.libraryStamp == libraryStamp(existing)) { "The library changed. Review the import again." }
        require(review.canSave && review.profiles.all { ProfileValidator.errors(it).isEmpty() }) { "Invalid profiles cannot be imported." }
        require(acceptWarnings || review.findings.none { it.severity == ProfileValidator.WARNING }) { "Review duplicate values and warnings before importing." }
        saveBatch(review.profiles)
    }
}
