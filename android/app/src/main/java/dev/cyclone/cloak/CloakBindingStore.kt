package dev.cyclone.cloak

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

data class CloakBinding(
    val profileId: String,
    val androidUserId: Int,
    val packageName: String,
    val cloakProfileId: String,
    val revision: Int,
    val enabled: Boolean,
    val updatedAt: Long,
    val state: String = "unknown",
)

object CloakBindingStore {
    private const val SCHEMA_VERSION = 1
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

    fun all(context: Context): List<CloakBinding> = synchronized(this) {
        val target = file(context)
        if (!target.exists()) return emptyList()
        runCatching {
            val root = JSONObject(target.readText())
            val array = root.optJSONArray("bindings") ?: JSONArray()
            (0 until array.length()).mapNotNull { array.optJSONObject(it)?.let(::fromJson) }
        }.getOrDefault(emptyList())
    }

    fun find(context: Context, profileId: String, androidUserId: Int, packageName: String): CloakBinding? =
        all(context).firstOrNull { it.profileId == profileId && it.androidUserId == androidUserId && it.packageName == packageName }

    fun upsert(context: Context, binding: CloakBinding) {
        validate(binding.profileId, binding.androidUserId, binding.packageName)
        val current = all(context).toMutableList()
        current.removeAll { it.profileId == binding.profileId && it.androidUserId == binding.androidUserId && it.packageName == binding.packageName }
        current.add(binding)
        write(context, current)
    }

    fun markState(context: Context, binding: CloakBinding, state: String) {
        val updated = binding.copy(state = state, updatedAt = System.currentTimeMillis())
        upsert(context, updated)
    }

    private fun write(context: Context, bindings: List<CloakBinding>) {
        val root = JSONObject()
            .put("schemaVersion", SCHEMA_VERSION)
            .put("bindings", JSONArray(bindings.map { it.toJson() }))
        val dir = context.noBackupFilesDir
        dir.mkdirs()
        val temp = File.createTempFile("cloak-bindings", ".tmp", dir)
        temp.writeText(root.toString())
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
}
