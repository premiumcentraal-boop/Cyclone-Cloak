package dev.cyclone.cloak.play

import dev.cyclone.cloak.forge.PlaySurfaceContract
import dev.cyclone.cloak.forge.StrictJson
import org.json.JSONArray
import org.json.JSONObject

/** Strict, bounded sidecar serialization. Errors never echo keys or supplied values. */
object GoogleSurfaceCodec {
    const val FORMAT = 1
    const val MAX_BYTES = 196608
    internal val id = Regex("[A-Za-z0-9][A-Za-z0-9._:-]{0,95}")
    internal val digest = Regex("[0-9a-f]{64}")
    internal val packageName = Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+")
    class InvalidRecord : IllegalArgumentException("GS_MALFORMED_RECORD: Invalid local Google surface record.")

    fun validate(record: GoogleSurfaceRecord) {
        try {
            require(id.matches(record.profileId) && digest.matches(record.profileHash))
            require(record.androidUserId == null || record.androidUserId in 0..21474)
            PlaySurfaceContract.parseSurface(record.surfaceJson)
            require(record.packages.size <= 256 && record.packages.map { it.packageName }.distinct().size == record.packages.size)
            require(record.packages.all { it.packageName.length <= 200 && packageName.matches(it.packageName) })
        } catch (_: Exception) { throw InvalidRecord() }
    }

    internal fun keys(value: JSONObject, expected: Set<String>) {
        require(value.keys().asSequence().toSet() == expected)
    }
    fun encode(record: GoogleSurfaceRecord): String {
        validate(record)
        val rules = JSONArray()
        record.packages.sortedBy { it.packageName }.forEach { rules.put(JSONObject()
            .put("package", it.packageName).put("role", it.role.name).put("requirement", it.requirement.name)) }
        return JSONObject().put("format", FORMAT).put("cyclone_profile_id", record.profileId)
            .put("android_user_id", record.androidUserId ?: JSONObject.NULL).put("profile_hash", record.profileHash)
            .put("play_surface", record.surface).put("packages", rules).put("active", record.active).toString()
            .also { if (it.toByteArray(Charsets.UTF_8).size > MAX_BYTES) throw InvalidRecord() }
    }
    fun parse(text: String): GoogleSurfaceRecord {
        try {
            require(text.toByteArray(Charsets.UTF_8).size <= MAX_BYTES)
            StrictJson(text).check()
            val j = JSONObject(text)
            keys(j, setOf("format", "cyclone_profile_id", "android_user_id", "profile_hash", "play_surface", "packages", "active"))
            require(j.get("format") == FORMAT && j.get("active") is Boolean)
            require(j.get("cyclone_profile_id") is String && j.get("profile_hash") is String)
            require(j.isNull("android_user_id") || j.get("android_user_id") is Int)
            val a = j.getJSONArray("packages")
            require(a.length() <= 256)
            val rules = (0 until a.length()).map { i ->
                val r = a.getJSONObject(i)
                keys(r, setOf("package", "role", "requirement"))
                require(r.get("package") is String && r.get("role") is String && r.get("requirement") is String)
                PackageRule(r.getString("package"), PackageRole.valueOf(r.getString("role")), PackageRequirement.valueOf(r.getString("requirement")))
            }
            return GoogleSurfaceRecord(j.getString("cyclone_profile_id"), if (j.isNull("android_user_id")) null else j.getInt("android_user_id"),
                j.getString("profile_hash"), PlaySurfaceContract.encodeSurface(j.getJSONObject("play_surface")), rules, j.getBoolean("active"))
                .also(::validate)
        } catch (_: Exception) { throw InvalidRecord() }
    }
}
