package dev.cyclone.cloak.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * One app in one Cyclone profile, bound to one cloak profile.
 *
 * A binding is identified by [profileId] + [packageName]: Cyclone profile ids never change, while the Android user
 * number can (a profile restored under a new user). [androidUserId] is the user number last seen for that profile and
 * is rewritten by reconcile when Cyclone reports a new one.
 *
 * [state] is Cloak's own detailed health ("ready", "pending", "module missing", …); CloakHealth.cycloneState (cyclone package) maps it
 * onto the four states Cyclone accepts.
 *
 * [origin] is [ORIGIN_LOCAL] for a binding this install made, or [ORIGIN_MAIN] for one Cloak in a Cyclone profile
 * mirrored from Cloak in Main (read from the published root state). Mirrored bindings are never published by this
 * install, and are replaced on every mirror.
 */
data class CloakBinding(
    val profileId: String,
    val androidUserId: Int,
    val packageName: String,
    val cloakProfileId: String,
    val revision: Int,
    val enabled: Boolean,
    val updatedAt: Long,
    val state: String = "unknown",
    val origin: String = ORIGIN_LOCAL,
    /** Identity summary JSON mirrored from Main (only for [ORIGIN_MAIN]; local bindings read their own profile file). */
    val mirroredSummary: String? = null,
) {
    fun sameAs(other: CloakBinding): Boolean = other.profileId == profileId && other.packageName == packageName

    companion object {
        const val ORIGIN_LOCAL = "local"
        const val ORIGIN_MAIN = "main"
    }
}

object CloakBindingStore {
    private const val SCHEMA_VERSION = 2
    private const val FILE_NAME = "cloak-bindings-v1.json"
    private val profileIdRegex = Regex("^Cyclone_[a-f0-9]{16}$")
    private val packageRegex = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+$")

    private val file: (Context) -> File = { context ->
        File(context.noBackupFilesDir, FILE_NAME)
    }

    fun key(profileId: String, androidUserId: Int, packageName: String): String =
        "$profileId\n$androidUserId\n$packageName"

    fun validate(profileId: String, androidUserId: Int, packageName: String) {
        require(profileIdRegex.matches(profileId)) { "profileId must be a registry profile" }
        require(androidUserId >= 0) { "androidUserId must be non-negative" }
        require(packageRegex.matches(packageName)) { "packageName must be a valid Android package" }
    }

    fun all(context: Context): List<CloakBinding> = synchronized(this) { read(context) }

    fun find(context: Context, profileId: String, androidUserId: Int, packageName: String): CloakBinding? =
        all(context).firstOrNull { it.profileId == profileId && it.androidUserId == androidUserId && it.packageName == packageName }

    fun upsert(context: Context, binding: CloakBinding) {
        validate(binding.profileId, binding.androidUserId, binding.packageName)
        update(context) { current -> upsertIn(current, binding) }
    }

    fun remove(context: Context, profileId: String, androidUserId: Int, packageName: String) {
        validate(profileId, androidUserId, packageName)
        update(context) { current -> current.filterNot { it.profileId == profileId && it.packageName == packageName } }
    }

    fun markState(context: Context, binding: CloakBinding, state: String) {
        update(context) { current ->
            current.map { if (binding.sameAs(it) && it.state != state) it.copy(state = state, updatedAt = System.currentTimeMillis()) else it }
        }
    }

    /** Atomic read-modify-write. Returns true when anything changed. */
    fun update(context: Context, change: (List<CloakBinding>) -> List<CloakBinding>): Boolean = synchronized(this) {
        val before = read(context)
        val after = change(before)
        if (after == before) return false
        write(context, after)
        true
    }

    /** Replaces the binding for the same profile + app (the user number may differ). Pure. */
    internal fun upsertIn(current: List<CloakBinding>, binding: CloakBinding): List<CloakBinding> =
        current.filterNot(binding::sameAs) + binding

    /** A profile restored under a new Android user number keeps its bindings. Pure. */
    internal fun moveUser(current: List<CloakBinding>, profileId: String, androidUserId: Int): List<CloakBinding> =
        current.map { if (it.profileId == profileId && it.androidUserId != androidUserId) it.copy(androidUserId = androidUserId) else it }

    /** Cyclone permanently deleted the profile: forget every binding of it. Pure. */
    internal fun forgetProfile(current: List<CloakBinding>, profileId: String): List<CloakBinding> =
        current.filterNot { it.profileId == profileId }

    /** Replaces every mirrored binding with [mirrored]; a local binding for the same app gives way to Main's. Pure. */
    internal fun replaceMirrored(current: List<CloakBinding>, mirrored: List<CloakBinding>): List<CloakBinding> {
        val kept = current.filter { it.origin == CloakBinding.ORIGIN_LOCAL && mirrored.none(it::sameAs) }
        return kept + mirrored
    }

    private fun read(context: Context): List<CloakBinding> {
        val target = file(context)
        if (!target.exists()) return emptyList()
        return runCatching { parse(target.readText()) }.getOrDefault(emptyList())
    }

    /** Older files were keyed by user number too; keep only the newest record per profile + app. Pure. */
    internal fun parse(text: String): List<CloakBinding> {
        val array = JSONObject(text).optJSONArray("bindings") ?: JSONArray()
        val parsed = (0 until array.length()).mapNotNull { array.optJSONObject(it)?.let(::fromJson) }
        return parsed.groupBy { it.profileId to it.packageName }.values.map { group -> group.maxBy { it.updatedAt } }
    }

    internal fun serialize(bindings: List<CloakBinding>): String = JSONObject()
        .put("schemaVersion", SCHEMA_VERSION)
        .put("bindings", JSONArray(bindings.map { it.toJson() }))
        .toString()

    private fun write(context: Context, bindings: List<CloakBinding>) {
        val dir = context.noBackupFilesDir
        dir.mkdirs()
        val temp = File.createTempFile("cloak-bindings", ".tmp", dir)
        temp.writeText(serialize(bindings))
        Files.move(
            temp.toPath(),
            file(context).toPath(),
            StandardCopyOption.REPLACE_EXISTING,
            StandardCopyOption.ATOMIC_MOVE,
        )
    }

    private fun fromJson(item: JSONObject): CloakBinding? = runCatching {
        CloakBinding(
            profileId = item.getString("profileId"),
            androidUserId = item.getInt("androidUserId"),
            packageName = item.getString("packageName"),
            cloakProfileId = item.getString("cloakProfileId"),
            revision = item.optInt("revision", 0),
            enabled = item.optBoolean("enabled", true),
            updatedAt = item.optLong("updatedAt", 0L),
            state = item.optString("state", "unknown"),
            origin = item.optString("origin", CloakBinding.ORIGIN_LOCAL)
                .takeIf { it == CloakBinding.ORIGIN_MAIN } ?: CloakBinding.ORIGIN_LOCAL,
            mirroredSummary = item.optJSONObject("mirroredSummary")?.toString(),
        )
    }.getOrNull()

    private fun CloakBinding.toJson(): JSONObject = JSONObject()
        .put("profileId", profileId)
        .put("androidUserId", androidUserId)
        .put("packageName", packageName)
        .put("cloakProfileId", cloakProfileId)
        .put("revision", revision)
        .put("enabled", enabled)
        .put("updatedAt", updatedAt)
        .put("state", state)
        .put("origin", origin)
        .also { json -> mirroredSummary?.let { json.put("mirroredSummary", JSONObject(it)) } }
}
