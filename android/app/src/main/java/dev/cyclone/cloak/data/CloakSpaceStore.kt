package dev.cyclone.cloak.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

/**
 * An Android user Cloak created for itself. Its [id] is also the user's name in `pm list users`, so a retry can find a
 * user whose journal was never written (the same name-first recovery Cyclone uses for its profiles).
 */
data class CloakSpace(
    val id: String,
    val label: String,
    val androidUserId: Int,
    val packages: List<String>,
    val identityId: String?,
    val state: String,
    val createdAt: Long,
    val updatedAt: Long,
) {
    init {
        require(ID_REGEX.matches(id)) { "space id must be a Cloak space" }
        require(androidUserId >= 0) { "androidUserId must be non-negative" }
        require(label.isNotBlank() && label.length <= LABEL_MAX) { "space label is required" }
        require(packages.size <= PACKAGES_MAX) { "too many apps in one space" }
    }

    val ready: Boolean get() = state == STATE_READY

    companion object {
        const val STATE_CREATING = "creating"
        const val STATE_READY = "ready"
        const val STATE_DELETING = "deleting"
        const val LABEL_MAX = 40
        const val PACKAGES_MAX = 50
        val ID_REGEX = Regex("^Cloak_[a-f0-9]{16}$")

        fun isSpaceId(id: String): Boolean = ID_REGEX.matches(id)

        fun newId(): String = "Cloak_" + UUID.randomUUID().toString().replace("-", "").take(16)
    }
}

/**
 * The spaces saved on this phone, in one JSON file. A file that cannot be read stops every write instead of being
 * replaced by an empty list, because a lost record could leave a managed user that nothing refers to.
 */
object CloakSpaceStore {
    private const val SCHEMA_VERSION = 1
    private const val FILE_NAME = "cloak-spaces-v1.json"

    private fun file(context: Context) = File(context.noBackupFilesDir, FILE_NAME)

    fun all(context: Context): List<CloakSpace> = synchronized(this) { read(context) }

    fun find(context: Context, id: String): CloakSpace? = all(context).firstOrNull { it.id == id }

    /** Atomic read-modify-write. Returns the list as saved. */
    fun update(context: Context, change: (List<CloakSpace>) -> List<CloakSpace>): List<CloakSpace> = synchronized(this) {
        val after = change(read(context))
        write(context, after)
        after
    }

    fun save(context: Context, space: CloakSpace) {
        update(context) { upsertIn(it, space) }
    }

    fun remove(context: Context, id: String) {
        update(context) { removeIn(it, id) }
    }

    internal fun upsertIn(current: List<CloakSpace>, space: CloakSpace): List<CloakSpace> =
        current.filterNot { it.id == space.id } + space

    internal fun removeIn(current: List<CloakSpace>, id: String): List<CloakSpace> = current.filterNot { it.id == id }

    internal fun parse(text: String): List<CloakSpace> {
        val array = JSONObject(text).optJSONArray("spaces") ?: JSONArray()
        return (0 until array.length()).map { fromJson(array.getJSONObject(it)) }.distinctBy { it.id }
    }

    internal fun serialize(spaces: List<CloakSpace>): String = JSONObject()
        .put("schemaVersion", SCHEMA_VERSION)
        .put("spaces", JSONArray(spaces.map { it.toJson() }))
        .toString()

    private fun read(context: Context): List<CloakSpace> {
        val target = file(context)
        if (!target.exists()) return emptyList()
        return parse(target.readText())
    }

    private fun write(context: Context, spaces: List<CloakSpace>) {
        val dir = context.noBackupFilesDir
        dir.mkdirs()
        val temp = File.createTempFile("cloak-spaces", ".tmp", dir)
        temp.writeText(serialize(spaces))
        Files.move(
            temp.toPath(),
            file(context).toPath(),
            StandardCopyOption.REPLACE_EXISTING,
            StandardCopyOption.ATOMIC_MOVE,
        )
    }

    private fun fromJson(item: JSONObject): CloakSpace = CloakSpace(
        id = item.getString("id"),
        label = item.getString("label"),
        androidUserId = item.getInt("androidUserId"),
        packages = item.getJSONArray("packages").let { array -> (0 until array.length()).map { array.getString(it) } },
        identityId = if (item.isNull("identityId")) null else item.getString("identityId"),
        state = item.getString("state"),
        createdAt = item.getLong("createdAt"),
        updatedAt = item.getLong("updatedAt"),
    )

    private fun CloakSpace.toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("label", label)
        .put("androidUserId", androidUserId)
        .put("packages", JSONArray(packages))
        .put("identityId", identityId ?: JSONObject.NULL)
        .put("state", state)
        .put("createdAt", createdAt)
        .put("updatedAt", updatedAt)
}
