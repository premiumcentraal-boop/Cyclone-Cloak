package dev.cyclone.cloak.data

import org.json.JSONObject
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Existing per-profile JSON format; temporary directory swaps make additive batch imports recoverable. */
class JsonDirectory(private val dir: File, private val move: (File, File) -> Unit = { source, target ->
    Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); Unit
}) {
    companion object { private val lock = Any() }
    private val parent get() = requireNotNull(dir.absoluteFile.parentFile)
    private val previous get() = File(parent, ".${dir.name}.previous")
    private fun recover() {
        if (!dir.exists() && previous.exists()) move(previous, dir)
        if (dir.exists() && previous.exists()) previous.deleteRecursively()
    }
    fun fileName(id: String): String = id.replace(Regex("[^A-Za-z0-9_.-]"), "_") + ".json"
    fun write(id: String, value: JSONObject) = synchronized(lock) {
        recover(); check(dir.mkdirs() || dir.isDirectory)
        val temp = File.createTempFile(".write", ".tmp", dir)
        try {
            temp.writeText(value.toString(2), Charsets.UTF_8)
            move(temp, File(dir, fileName(id)))
        } finally { temp.delete() }
    }
    /** No existing entry is replaced. Readers through this class cannot observe a partial batch. */
    fun writeBatch(values: List<Pair<String, JSONObject>>) = synchronized(lock) {
        recover()
        require(values.map { fileName(it.first) }.distinct().size == values.size) { "Duplicate import file names." }
        require(values.none { File(dir, fileName(it.first)).exists() }) { "An import target already exists. Review again." }
        check(parent.mkdirs() || parent.isDirectory)
        val stage = Files.createTempDirectory(parent.toPath(), ".${dir.name}.stage-").toFile()
        var retired = false
        try {
            if (dir.exists()) check(dir.copyRecursively(stage, overwrite = true)) { "Could not stage the existing library." }
            val staged = JsonDirectory(stage)
            values.forEach { (id, value) -> staged.write(id, value) }
            if (dir.exists()) { move(dir, previous); retired = true }
            try { move(stage, dir) } catch (failure: Exception) {
                if (retired) try { move(previous, dir) } catch (rollback: Exception) { failure.addSuppressed(rollback) }
                throw failure
            }
            // Commit is complete. Cleanup failure must not report an import failure after a successful swap.
            previous.deleteRecursively()
        } finally { stage.deleteRecursively() }
    }
    fun read(id: String): JSONObject? = synchronized(lock) {
        recover()
        File(dir, fileName(id)).takeIf { it.isFile }?.let { runCatching { JSONObject(it.readText(Charsets.UTF_8)) }.getOrNull() }
    }
    fun all(): List<Pair<String, JSONObject>> = synchronized(lock) {
        recover()
        dir.listFiles { file -> file.isFile && file.name.endsWith(".json") && !file.name.startsWith(".") }
            ?.sortedBy { it.name }?.mapNotNull { file -> runCatching {
                file.name.removeSuffix(".json") to JSONObject(file.readText(Charsets.UTF_8))
            }.getOrNull() } ?: emptyList()
    }
    fun delete(id: String): Boolean = synchronized(lock) { recover(); File(dir, fileName(id)).delete() }
}
