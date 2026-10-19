package dev.cyclone.cloak.play

import dev.cyclone.cloak.forge.StrictJson
import org.json.JSONObject
import java.time.Instant

/** Strict local evidence envelope. No response tokens, account contents or arbitrary log text. */
object PlayReadinessReport {
    const val FORMAT = 1
    const val MAX_BYTES = 65536
    private val id = Regex("[A-Za-z0-9][A-Za-z0-9._:-]{0,95}")
    private val hash = Regex("[0-9a-f]{64}")
    private val time = Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}Z")
    private val packageName = Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+")
    val warningCodes = setOf("PERMISSION_LIMITATION", "PACKAGE_LIMITATION", "OPTIONAL_OBSERVATION", "USER_REPORTED_WARNING")
    val failureCodes = setOf("PACKAGE_BLOCKED", "ACCOUNT_BLOCKED", "SETTINGS_BLOCKED", "LOCAL_CHECK_FAILED")
    class InvalidReport : IllegalArgumentException("PR_MALFORMED_REPORT: Invalid or incomplete local readiness report.")
    /** Retains the exact received bytes for the evidence digest; JSON access always returns a copy. */
    class Parsed internal constructor(val text: String) { val json: JSONObject get() = JSONObject(text) }

    internal fun keys(j: JSONObject, required: Set<String>, optional: Set<String> = emptySet()) {
        val keys = j.keys().asSequence().toSet()
        require(keys.containsAll(required) && keys.all { it in required || it in optional })
    }
    private fun string(j: JSONObject, key: String): String { require(j.get(key) is String); return j.getString(key) }
    private fun choice(j: JSONObject, key: String, choices: Set<String>) { require(string(j, key) in choices) }
    internal fun timestamp(text: String): Instant {
        require(time.matches(text) && text.take(4).toInt() in 1..9999)
        val parsed = java.time.LocalDateTime.parse(text.dropLast(1))
        return parsed.toInstant(java.time.ZoneOffset.UTC)
    }
    private fun pkg(j: JSONObject, name: String?) {
        keys(j, setOf("package_name", "state"), setOf("version_name"))
        choice(j, "state", setOf("enabled", "disabled", "missing", "blocked", "permission_denied", "unknown"))
        if (name != null) require(string(j, "package_name") == name)
        else if (j.isNull("package_name")) require(j.getString("state") != "enabled")
        else require(string(j, "package_name").let { it.length <= 200 && packageName.matches(it) })
        if (j.has("version_name")) require(string(j, "version_name").let { it.isNotBlank() && it.length <= 128 && it.none(Char::isISOControl) })
    }
    fun parse(text: String): Parsed {
        try {
            require(text.toByteArray(Charsets.UTF_8).size <= MAX_BYTES)
            StrictJson(text).check()
            val j = JSONObject(text)
            keys(j, setOf("format", "profile_id", "profile_hash", "android_user_id", "artifact_hash", "timestamp",
                "play_services", "play_store", "play_protect", "web_provider", "google_account", "google_settings_access",
                "integrity_api", "warnings", "failures"), setOf("integrity_response"))
            require(j.get("format") == FORMAT)
            require(id.matches(string(j, "profile_id")))
            require(hash.matches(string(j, "profile_hash")) && hash.matches(string(j, "artifact_hash")))
            require(j.get("android_user_id") is Int && j.getInt("android_user_id") in 0..21474)
            val reportedAt = timestamp(string(j, "timestamp"))
            pkg(j.getJSONObject("play_services"), "com.google.android.gms")
            pkg(j.getJSONObject("play_store"), "com.android.vending")
            pkg(j.getJSONObject("web_provider"), null)
            choice(j, "play_protect", setOf("enabled", "disabled", "unavailable", "blocked", "permission_denied", "unknown"))
            choice(j, "google_account", setOf("associated", "none", "blocked", "permission_denied", "unknown"))
            choice(j, "google_settings_access", setOf("accessible", "blocked", "unavailable", "permission_denied", "unknown"))
            choice(j, "integrity_api", setOf("available", "unavailable", "blocked", "permission_denied", "unknown"))
            for ((key, codes) in listOf("warnings" to warningCodes, "failures" to failureCodes)) {
                val a = j.getJSONArray(key)
                require(a.length() <= codes.size)
                val values = (0 until a.length()).map { require(a.get(it) is String); a.getString(it) }
                require(values.toSet().size == values.size && values.all { it in codes })
            }
            if (j.has("integrity_response")) {
                val r = j.getJSONObject("integrity_response")
                keys(r, setOf("response_digest", "received_at", "provider_id"))
                require(j.getString("integrity_api") == "available")
                require(hash.matches(string(r, "response_digest")) && id.matches(string(r, "provider_id")))
                require(timestamp(string(r, "received_at")) <= reportedAt)
            }
            return Parsed(text)
        } catch (_: Exception) { throw InvalidReport() }
    }
}
