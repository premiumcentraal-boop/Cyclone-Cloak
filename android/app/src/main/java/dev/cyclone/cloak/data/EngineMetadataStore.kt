package dev.cyclone.cloak.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

/** Additive sidecar in app-private preferences. Existing profiles/ files and readers stay unchanged. */
class EngineMetadataStore(context: Context, private val validateMetadata: (JSONObject, JSONObject) -> Unit) {
    private val preferences = context.getSharedPreferences("device-profile-engine", Context.MODE_PRIVATE)

    fun read(profile: JSONObject): JSONObject? = runCatching {
        val record = JSONObject(preferences.getString(profile.getString("id"), null) ?: return null)
        if (record.getInt("format") != 1 || record.getString("profile_sha256") != digest(profile)) return null
        record.getJSONObject("metadata").also { validateMetadata(it, profile) }
    }.getOrNull()

    /** Stage metadata first. Hash matching hides it until the unchanged body has been saved.
     * A failed body write restores the previous sidecar; process interruption leaves only an invisible orphan.
     */
    @Synchronized fun save(profile: JSONObject, metadata: JSONObject, saveBody: (JSONObject) -> String): String {
        validateMetadata(metadata, profile)
        val id = profile.getString("id")
        val previous = preferences.getString(id, null)
        val record = JSONObject().put("format", 1).put("profile_sha256", digest(profile)).put("metadata", metadata)
        check(preferences.edit().putString(id, record.toString()).commit()) { "Could not save engine metadata." }
        try { return saveBody(profile) } catch (failure: Exception) {
            val editor = preferences.edit()
            if (previous == null) editor.remove(id) else editor.putString(id, previous)
            if (!editor.commit()) failure.addSuppressed(IllegalStateException("Could not restore engine metadata."))
            throw failure
        }
    }

    companion object {
        // JSONObject object order is insignificant; array order and every body value are significant.
        fun digest(profile: JSONObject): String {
            fun canonical(value: Any?): String = when (value) {
                is JSONObject -> value.keys().asSequence().toSet().sorted().joinToString(",", "{", "}") { JSONObject.quote(it) + ":" + canonical(value.opt(it)) }
                is JSONArray -> (0 until value.length()).joinToString(",", "[", "]") { canonical(value.opt(it)) }
                is String -> JSONObject.quote(value)
                is Number -> JSONObject.numberToString(value)
                null, JSONObject.NULL -> "null"
                else -> value.toString()
            }
            return MessageDigest.getInstance("SHA-256").digest(canonical(profile).toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
        }
    }
}
