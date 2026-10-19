package dev.cyclone.cloak.playevidence

import dev.cyclone.cloak.forge.StrictJson
import dev.cyclone.cloak.play.PlayReadinessReport
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.time.Instant

enum class EvidenceStatus { PASS, FAIL, OBSERVED, BLOCKED, MISSING, NOT_RUN }
data class EvidenceBinding(val requestId: String, val profileId: String, val profileHash: String, val artifactHash: String, val androidUserId: Int) {
    init {
        require(Regex("[A-Za-z0-9_-]{1,80}").matches(requestId)) { "INVALID_BINDING" }
        require(Regex("[A-Za-z0-9][A-Za-z0-9._:-]{0,95}").matches(profileId)) { "INVALID_BINDING" }
        require(listOf(profileHash, artifactHash).all { Regex("[0-9a-f]{64}").matches(it) }) { "INVALID_BINDING" }
        require(androidUserId in 0..21474) { "INVALID_BINDING" }
    }
}
data class EvidenceRow(val id: String, val status: EvidenceStatus, val reason: String,
    val metadata: JSONObject = JSONObject(), val durationMs: Long = 0) {
    fun json(): JSONObject = JSONObject().put("id", id).put("status", status.name).put("reason", reason)
        .put("metadata", JSONObject(metadata.toString())).put("duration_ms", durationMs)
}

object EvidenceReport {
    const val MAX_BYTES = 131072
    val packages = listOf("com.google.android.gms", "com.android.vending", "com.google.android.gsf", "com.google.android.webview", "com.android.webview", "com.android.chrome")
    val ids = packages.map { "package.$it" } + listOf("play_services.availability", "play_store.availability", "play_protect.state",
        "web_provider.state", "google_settings.access", "account.association", "process.android_user", "profile.binding", "integrity.api", "integrity.verdict")
    val reasons = setOf("PACKAGE_OBSERVED", "NOT_VISIBLE_OR_MISSING", "PACKAGE_DISABLED", "PERMISSION_DENIED", "API_ERROR",
        "SERVICE_AVAILABLE", "SERVICE_UNAVAILABLE", "NO_PUBLIC_OBSERVATION", "PROVIDER_OBSERVED", "NO_PROVIDER",
        "RESOLVABLE_NOT_ACCESS_PROOF", "SETTINGS_NOT_RESOLVABLE", "VISIBLE_ACCOUNT", "NO_CALLER_VISIBLE_ACCOUNT",
        "USER_MATCHED", "USER_MISMATCH", "PROFILE_HASH_MATCHED", "PROFILE_HASH_MISMATCH", "PROFILE_INPUT_MISSING",
        "USER_NOT_REQUESTED", "TOKEN_RECEIVED", "INTEGRITY_API_ERROR", "BACKEND_VERIFICATION_REQUIRED", "COLLECTION_FAILED")
    val metadataKeys = setOf("package_name", "version_name", "version_code", "enabled", "installer", "installer_reason",
        "api_code", "visible_account", "android_user_id", "actual_profile_hash", "response_digest", "received_at", "request_hash", "provider_id")
    val statusReasons = mapOf(
        EvidenceStatus.PASS to setOf("SERVICE_AVAILABLE", "USER_MATCHED", "PROFILE_HASH_MATCHED"),
        EvidenceStatus.FAIL to setOf("SERVICE_UNAVAILABLE", "USER_MISMATCH", "PROFILE_HASH_MISMATCH", "COLLECTION_FAILED"),
        EvidenceStatus.OBSERVED to setOf("PACKAGE_OBSERVED", "PROVIDER_OBSERVED", "RESOLVABLE_NOT_ACCESS_PROOF", "VISIBLE_ACCOUNT", "NO_CALLER_VISIBLE_ACCOUNT", "TOKEN_RECEIVED"),
        EvidenceStatus.BLOCKED to setOf("PERMISSION_DENIED", "API_ERROR", "PACKAGE_DISABLED", "INTEGRITY_API_ERROR"),
        EvidenceStatus.MISSING to setOf("NOT_VISIBLE_OR_MISSING", "NO_PROVIDER", "SETTINGS_NOT_RESOLVABLE", "PROFILE_INPUT_MISSING"),
        EvidenceStatus.NOT_RUN to setOf("NO_PUBLIC_OBSERVATION", "USER_NOT_REQUESTED", "BACKEND_VERIFICATION_REQUIRED")
    )
    fun allowedReasons(id: String): Set<String> = setOf("USER_NOT_REQUESTED", "PERMISSION_DENIED", "API_ERROR") + when {
        id.startsWith("package.") || id == "play_store.availability" -> setOf("PACKAGE_OBSERVED", "NOT_VISIBLE_OR_MISSING", "PACKAGE_DISABLED")
        id == "play_services.availability" -> setOf("SERVICE_AVAILABLE", "SERVICE_UNAVAILABLE")
        id == "play_protect.state" -> setOf("NO_PUBLIC_OBSERVATION")
        id == "web_provider.state" -> setOf("PROVIDER_OBSERVED", "NO_PROVIDER", "PACKAGE_DISABLED")
        id == "google_settings.access" -> setOf("RESOLVABLE_NOT_ACCESS_PROOF", "SETTINGS_NOT_RESOLVABLE")
        id == "account.association" -> setOf("VISIBLE_ACCOUNT", "NO_CALLER_VISIBLE_ACCOUNT")
        id == "process.android_user" -> setOf("USER_MATCHED", "USER_MISMATCH")
        id == "profile.binding" -> setOf("PROFILE_HASH_MATCHED", "PROFILE_HASH_MISMATCH", "PROFILE_INPUT_MISSING", "COLLECTION_FAILED")
        id == "integrity.api" -> setOf("TOKEN_RECEIVED", "INTEGRITY_API_ERROR")
        id == "integrity.verdict" -> setOf("BACKEND_VERIFICATION_REQUIRED")
        else -> emptySet()
    }
    fun digest(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private fun same(a: Any?, b: Any?): Boolean = when {
        a is JSONObject && b is JSONObject -> a.keys().asSequence().toSet() == b.keys().asSequence().toSet() &&
            a.keys().asSequence().all { same(a.get(it), b.get(it)) }
        a is JSONArray && b is JSONArray -> a.length() == b.length() && (0 until a.length()).all { same(a.get(it), b.get(it)) }
        a is Number && b is Number -> a.toString() == b.toString()
        else -> a == b
    }
    fun build(binding: EvidenceBinding, rows: List<EvidenceRow>, timestamp: Instant): JSONObject {
        val j = JSONObject().put("format", 1).put("request_id", binding.requestId).put("profile_id", binding.profileId)
            .put("profile_hash", binding.profileHash).put("artifact_hash", binding.artifactHash).put("android_user_id", binding.androidUserId)
            .put("timestamp", timestamp.toString()).put("rows", JSONArray(rows.map { it.json() }))
        val warnings = rows.filter { it.status in setOf(EvidenceStatus.BLOCKED, EvidenceStatus.MISSING, EvidenceStatus.NOT_RUN) }.map { it.reason }.distinct().sorted()
        j.put("warnings", JSONArray(warnings))
        j.put("readiness_report", readiness(binding, rows, timestamp))
        return parse(j.toString())
    }
    private fun readiness(b: EvidenceBinding, rows: List<EvidenceRow>, at: Instant): JSONObject {
        fun row(id: String) = rows.single { it.id == id }
        fun pkg(id: String, name: String?): JSONObject {
            val r = row(id)
            val state = when {
                r.status == EvidenceStatus.BLOCKED && r.reason == "PERMISSION_DENIED" -> "permission_denied"
                r.reason == "PACKAGE_DISABLED" -> "disabled"
                r.status == EvidenceStatus.OBSERVED && r.metadata.opt("enabled") == true -> "enabled"
                else -> "unknown"
            }
            return JSONObject().put("package_name", name ?: r.metadata.opt("package_name") ?: JSONObject.NULL).put("state", state)
        }
        // Package-not-found does not prove absence outside the caller's visibility boundary.
        val j = JSONObject().put("format", 1).put("profile_id", b.profileId).put("profile_hash", b.profileHash)
            .put("artifact_hash", b.artifactHash).put("android_user_id", b.androidUserId).put("timestamp", at.toString())
            .put("play_services", pkg("package.com.google.android.gms", "com.google.android.gms"))
            .put("play_store", pkg("package.com.android.vending", "com.android.vending"))
            .put("web_provider", pkg("web_provider.state", null)).put("play_protect", "unknown").put("google_settings_access", "unknown")
            .put("google_account", when (row("account.association").reason) { "VISIBLE_ACCOUNT" -> "associated"; "PERMISSION_DENIED" -> "permission_denied"; else -> "unknown" })
            .put("integrity_api", if (row("integrity.api").reason == "TOKEN_RECEIVED") "available" else "unknown")
            .put("warnings", JSONArray(buildList {
                if (rows.any { it.reason == "PERMISSION_DENIED" }) add("PERMISSION_LIMITATION")
                if (rows.any { it.status == EvidenceStatus.BLOCKED && it.reason != "PERMISSION_DENIED" }) add("PACKAGE_LIMITATION")
            }))
            .put("failures", JSONArray(if (rows.any { it.status == EvidenceStatus.FAIL }) listOf("LOCAL_CHECK_FAILED") else emptyList<String>()))
        val token = row("integrity.api")
        if (token.reason == "TOKEN_RECEIVED") j.put("integrity_response", JSONObject()
            .put("response_digest", token.metadata.getString("response_digest"))
            .put("received_at", token.metadata.getString("received_at")).put("provider_id", "public.play.integrity"))
        return PlayReadinessReport.parse(j.toString()).json
    }
    fun parse(text: String): JSONObject {
        try {
            require(text.toByteArray(Charsets.UTF_8).size <= MAX_BYTES)
            StrictJson(text).check()
            val j = JSONObject(text)
            require(j.keys().asSequence().toSet() == setOf("format", "request_id", "profile_id", "profile_hash", "artifact_hash", "android_user_id", "timestamp", "rows", "warnings", "readiness_report"))
            require(j.get("format") == 1 && j.get("android_user_id") is Int)
            for (key in listOf("request_id", "profile_id", "profile_hash", "artifact_hash", "timestamp")) require(j.get(key) is String)
            val binding = EvidenceBinding(j.getString("request_id"), j.getString("profile_id"), j.getString("profile_hash"), j.getString("artifact_hash"), j.getInt("android_user_id"))
            PlayReadinessReport.timestamp(j.getString("timestamp"))
            val a = j.getJSONArray("rows")
            require(a.length() == ids.size)
            val found = mutableSetOf<String>()
            for (i in 0 until a.length()) {
                val r = a.getJSONObject(i)
                require(r.keys().asSequence().toSet() == setOf("id", "status", "reason", "metadata", "duration_ms"))
                require(r.get("id") is String && r.get("status") is String && r.get("reason") is String)
                require(r.getString("id") in ids && found.add(r.getString("id")))
                val status = EvidenceStatus.valueOf(r.getString("status"))
                val reason = r.getString("reason")
                require(reason in statusReasons.getValue(status) && reason in allowedReasons(r.getString("id")))
                require(r.get("duration_ms") is Int || r.get("duration_ms") is Long)
                require(r.getLong("duration_ms") in 0..300000)
                val m = r.getJSONObject("metadata")
                val scopedKeys = when {
                    r.getString("id").startsWith("package.") || r.getString("id") in setOf("play_store.availability", "web_provider.state") ->
                        setOf("package_name", "version_name", "version_code", "enabled", "installer", "installer_reason")
                    r.getString("id") == "play_services.availability" -> setOf("api_code")
                    r.getString("id") == "account.association" -> setOf("visible_account")
                    r.getString("id") == "process.android_user" -> setOf("android_user_id")
                    r.getString("id") == "profile.binding" -> setOf("actual_profile_hash")
                    r.getString("id") == "integrity.api" -> setOf("api_code", "response_digest", "received_at", "request_hash", "provider_id")
                    else -> emptySet()
                }
                require(m.keys().asSequence().all { key -> key in metadataKeys && key in scopedKeys })
                for (key in m.keys()) {
                    val v = m.get(key)
                    when (key) {
                        "enabled" -> require(v is Boolean || v == JSONObject.NULL)
                        "visible_account" -> require(v is Boolean)
                        "api_code", "version_code", "android_user_id" -> {
                            require(v is Int || v is Long)
                            if (key == "version_code") require(m.getLong(key) >= 0)
                            if (key == "android_user_id") require(m.getLong(key) in 0..21474)
                        }
                        "actual_profile_hash", "response_digest", "request_hash" -> require(v is String && Regex("[0-9a-f]{64}").matches(v))
                        "received_at" -> { require(v is String); PlayReadinessReport.timestamp(v) }
                        "installer", "package_name" -> require(v == JSONObject.NULL || v is String && v.length <= 200 && Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+").matches(v))
                        "installer_reason" -> require(v in setOf("PACKAGE_OBSERVED", "PERMISSION_DENIED", "NOT_VISIBLE_OR_MISSING", "API_ERROR"))
                        "provider_id" -> require(v == "public.play.integrity")
                        else -> require(v is String && v.length <= 128 && v.none(Char::isISOControl))
                    }
                }
                when (reason) {
                    "PROFILE_HASH_MATCHED" -> require(m.getString("actual_profile_hash") == binding.profileHash)
                    "PROFILE_HASH_MISMATCH" -> require(m.getString("actual_profile_hash") != binding.profileHash)
                    "USER_MATCHED" -> require(m.getInt("android_user_id") == binding.androidUserId)
                    "USER_MISMATCH" -> require(m.has("android_user_id"))
                    "SERVICE_AVAILABLE" -> require(m.getInt("api_code") == 0)
                    "SERVICE_UNAVAILABLE" -> require(m.getInt("api_code") != 0)
                    "VISIBLE_ACCOUNT" -> require(m.get("visible_account") == true)
                    "NO_CALLER_VISIBLE_ACCOUNT" -> require(m.get("visible_account") == false)
                    "TOKEN_RECEIVED" -> require(setOf("response_digest", "received_at", "request_hash", "provider_id").all(m::has))
                    "PACKAGE_OBSERVED", "PROVIDER_OBSERVED", "PACKAGE_DISABLED" -> {
                        require(m.get("package_name") is String && m.has("version_code") && m.has("version_name"))
                        require(m.get("enabled") == (reason != "PACKAGE_DISABLED"))
                        val expected = if (r.getString("id").startsWith("package.")) r.getString("id").removePrefix("package.")
                            else if (r.getString("id") == "play_store.availability") "com.android.vending" else null
                        require(expected == null || m.getString("package_name") == expected)
                    }
                }
            }
            val w = j.getJSONArray("warnings")
            require(w.length() <= reasons.size)
            val warnings = (0 until w.length()).map { require(w.get(it) is String); w.getString(it) }
            require(warnings.distinct().size == warnings.size && warnings.all { it in reasons })
            val parsedRows = (0 until a.length()).map { i -> a.getJSONObject(i).let {
                EvidenceRow(it.getString("id"), EvidenceStatus.valueOf(it.getString("status")), it.getString("reason"), it.getJSONObject("metadata"), it.getLong("duration_ms"))
            } }
            require(warnings == parsedRows.filter { it.status in setOf(EvidenceStatus.BLOCKED, EvidenceStatus.MISSING, EvidenceStatus.NOT_RUN) }.map { it.reason }.distinct().sorted())
            val p = PlayReadinessReport.parse(j.getJSONObject("readiness_report").toString()).json
            require(listOf("profile_id", "profile_hash", "artifact_hash", "android_user_id", "timestamp").all { p.get(it) == j.get(it) })
            require(same(p, readiness(binding, parsedRows, Instant.parse(j.getString("timestamp")))))
            return j
        } catch (_: Exception) { throw IllegalArgumentException("INVALID_EVIDENCE_REPORT") }
    }
}
