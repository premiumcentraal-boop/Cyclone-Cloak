package dev.cyclone.cloak.data

import org.json.JSONObject
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * A folder of JSON objects, one file per id. Writes are atomic (temp file, then rename), so a crash never leaves a
 * half-written file, and an unreadable file is skipped instead of hiding every other entry.
 */
class JsonDirectory(private val dir: File) {
    fun fileName(id: String): String = id.replace(Regex("[^A-Za-z0-9_.-]"), "_") + ".json"

    fun write(id: String, value: JSONObject) {
        dir.mkdirs()
        val temp = File.createTempFile(".write", ".tmp", dir)
        try {
            temp.writeText(value.toString(2))
            Files.move(temp.toPath(), File(dir, fileName(id)).toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } finally {
            temp.delete()
        }
    }

    fun read(id: String): JSONObject? = File(dir, fileName(id)).takeIf { it.isFile }
        ?.let { runCatching { JSONObject(it.readText()) }.getOrNull() }

    /** Every readable entry, keyed by file name without `.json`, in name order. */
    fun all(): List<Pair<String, JSONObject>> =
        dir.listFiles { file -> file.isFile && file.name.endsWith(".json") && !file.name.startsWith(".") }
            ?.sortedBy { it.name }
            ?.mapNotNull { file -> runCatching { file.name.removeSuffix(".json") to JSONObject(file.readText()) }.getOrNull() }
            ?: emptyList()

    fun delete(id: String): Boolean = File(dir, fileName(id)).delete()
}
