package dev.cyclone.cloak.diagnostics

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.ArrayDeque
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class Severity { INFO, WARNING, ERROR }
enum class Transition { STARTED, COMPLETED, DEGRADED, FAILED, CRASH }

/** Free-form payloads and exception messages are deliberately not accepted. */
data class DiagnosticEvent(
    val timestamp: Long, val severity: Severity, val source: String,
    val subsystem: String, val actionId: String, val thread: String, val process: String,
    val targetId: String?, val transition: Transition, val reason: String?,
    val errorSummary: String?, val durationMs: Long?,
) {
    fun json(): JSONObject = JSONObject().put("timestamp", timestamp).put("severity", severity.name)
        .put("source", source).put("subsystem", subsystem).put("action_id", actionId)
        .put("thread", thread).put("process", process).put("target_id", targetId ?: JSONObject.NULL)
        .put("transition", transition.name).put("message", when (transition) {
            Transition.STARTED -> "Action started"
            Transition.COMPLETED -> "Action completed"
            Transition.DEGRADED -> "Action needs attention"
            Transition.FAILED -> "Action failed"
            Transition.CRASH -> "Unhandled exception"
        }).put("reason", reason ?: JSONObject.NULL).put("error_summary", errorSummary ?: JSONObject.NULL)
        .put("duration_ms", durationMs ?: JSONObject.NULL)
}

object DiagnosticPrivacy {
    // Only opaque profile IDs, digest-like references and package names are useful targets.
    fun target(value: String?): String? = value?.takeIf {
        it.matches(Regex("[a-fA-F0-9-]{16,64}|[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+"))
    }
    fun code(value: String): String = value.takeIf { it.matches(Regex("[A-Z][A-Z0-9_]{0,63}")) } ?: "UNSPECIFIED"
    fun label(value: String): String = value.takeIf { it.matches(Regex("[a-zA-Z][a-zA-Z0-9_.-]{0,63}")) } ?: "redacted"
    fun version(value: String): String = value.takeIf {
        it.matches(Regex("v?[0-9]+\\.[0-9]+\\.[0-9]+(?:-[A-Za-z0-9.-]+)?"))
    } ?: "unknown"
    // getId is available on all supported Android APIs; threadId is not.
    @Suppress("DEPRECATION")
    fun threadId(): String = Thread.currentThread().id.toString()
}

/** Bounded, non-blocking producers; one daemon consumer owns persistence. Fail-open. */
class DiagnosticsRegistry(
    private val directory: File? = null,
    private val capacity: Int = 256,
    private val fileCap: Int = 256 * 1024,
    private val process: String = "unknown",
    private val sink: (DiagnosticEvent) -> Unit = {},
) : AutoCloseable {
    private val lock = Any()
    private val ring = ArrayDeque<DiagnosticEvent>()
    private val recent = MutableStateFlow<List<DiagnosticEvent>>(emptyList())
    val recentEvents: StateFlow<List<DiagnosticEvent>> = recent.asStateFlow()
    private val queue = ArrayBlockingQueue<() -> Unit>(capacity)
    private val counts = linkedMapOf<String, Long>()
    private val active = linkedMapOf<String, DiagnosticEvent>()
    val dropped = AtomicLong()
    val persistenceFailures = AtomicLong()
    @Volatile private var closed = false
    private val worker: Thread

    init {
        require(capacity > 0 && fileCap >= 2048)
        recover()
        recent.value = ring.toList()
        worker = Thread({
            while (!closed || queue.isNotEmpty()) {
                val item = queue.poll(100, TimeUnit.MILLISECONDS) ?: continue
                runCatching { item() }.onFailure { persistenceFailures.incrementAndGet() }
            }
        }, "cloak-diagnostics").apply { isDaemon = true; start() }
    }

    fun event(subsystem: String, actionId: String, transition: Transition,
              target: String? = null, reason: String? = null, error: Throwable? = null,
              durationMs: Long? = null, source: String = "app") {
        if (closed) return
        val event = DiagnosticEvent(System.currentTimeMillis(), when (transition) {
            Transition.FAILED, Transition.CRASH -> Severity.ERROR
            Transition.DEGRADED -> Severity.WARNING
            else -> Severity.INFO
        }, DiagnosticPrivacy.label(source), DiagnosticPrivacy.label(subsystem), DiagnosticPrivacy.label(actionId),
            DiagnosticPrivacy.threadId(), process.filter(Char::isDigit).take(20).ifBlank { "unknown" }, DiagnosticPrivacy.target(target), transition,
            reason?.let(DiagnosticPrivacy::code), error?.javaClass?.simpleName?.let(DiagnosticPrivacy::label),
            durationMs?.coerceAtLeast(0))
        if (!queue.offer { accept(event) }) dropped.incrementAndGet()
    }

    private fun accept(event: DiagnosticEvent) {
        synchronized(lock) {
            ring.addLast(event)
            if (ring.size > capacity) ring.removeFirst()
            recent.value = ring.toList()
            val key = "${event.subsystem}.${event.transition}"
            // Caller vocabulary is bounded even if an unexpected caller invents labels.
            if (key in counts || counts.size < capacity) counts[key] = (counts[key] ?: 0) + 1
            if (event.transition == Transition.STARTED) {
                if (active.size >= capacity) active.remove(active.keys.first())
                active[event.actionId] = event
            } else active.remove(event.actionId)
        }
        // Disk/logcat failures cannot hold the snapshot lock or suppress each other.
        runCatching { persist(event) }.onFailure { persistenceFailures.incrementAndGet() }
        runCatching { sink(event) }.onFailure { persistenceFailures.incrementAndGet() }
    }

    private fun persist(event: DiagnosticEvent) {
        val dir = directory ?: return
        dir.mkdirs()
        val file = File(dir, "recent.jsonl")
        val bytes = (event.json().toString() + "\n").toByteArray(Charsets.UTF_8)
        if (bytes.size > fileCap) return
        if (file.length() + bytes.size > fileCap) {
            val previous = File(dir, "previous.jsonl")
            if (previous.exists() && !previous.delete()) error("Diagnostics rotation unavailable")
            if (file.exists() && !file.renameTo(previous)) error("Diagnostics rotation unavailable")
        }
        file.appendBytes(bytes)
    }

    private fun recover() {
        val dir = directory ?: return
        val recovered = mutableListOf<DiagnosticEvent>()
        for (name in listOf("previous.jsonl", "recent.jsonl", "crash.jsonl")) {
            val file = File(dir, name)
            if (!file.exists() || file.length() > fileCap) continue
            runCatching { file.useLines { lines -> lines.forEach { line ->
                runCatching {
                    val j = JSONObject(line)
                    val e = DiagnosticEvent(j.getLong("timestamp"), Severity.valueOf(j.getString("severity")),
                        DiagnosticPrivacy.label(j.getString("source")), DiagnosticPrivacy.label(j.getString("subsystem")),
                        DiagnosticPrivacy.label(j.getString("action_id")), j.getString("thread").filter(Char::isDigit).take(20),
                        j.getString("process").filter(Char::isDigit).take(20), DiagnosticPrivacy.target(j.optString("target_id")),
                        Transition.valueOf(j.getString("transition")), j.optString("reason").takeIf { it != "null" }?.let(DiagnosticPrivacy::code),
                        j.optString("error_summary").takeIf { it != "null" }?.let(DiagnosticPrivacy::label),
                        if (j.isNull("duration_ms")) null else j.getLong("duration_ms").coerceAtLeast(0))
                    recovered.add(e)
                }
            } } }
        }
        recovered.distinct().sortedBy { it.timestamp }.takeLast(capacity).forEach(ring::addLast)
    }

    /** Export waits briefly for accepted events, never blocks a producer. */
    fun flush(timeoutMs: Long = 1000): Boolean {
        if (closed) return queue.isEmpty()
        val done = CountDownLatch(1)
        return queue.offer({ done.countDown() }, timeoutMs, TimeUnit.MILLISECONDS) && done.await(timeoutMs, TimeUnit.MILLISECONDS)
    }

    fun events(): List<DiagnosticEvent> = synchronized(lock) { ring.toList() }
    fun counters(): Map<String, Long> = synchronized(lock) { counts.toMap() }

    /** Synchronous, capped crash snapshot; never serializes exception contents/stack. */
    fun crash(error: Throwable) {
        event("exceptions", "unhandled", Transition.CRASH, error = error)
        flush(250)
        synchronized(lock) {
            runCatching {
                directory?.let { dir ->
                    dir.mkdirs()
                    val events = ring.toList().takeLast(capacity - 1) + DiagnosticEvent(System.currentTimeMillis(), Severity.ERROR,
                        "app", "exceptions", "unhandled", DiagnosticPrivacy.threadId(), process.filter(Char::isDigit).take(20).ifBlank { "unknown" }, null,
                        Transition.CRASH, "UNHANDLED_EXCEPTION", DiagnosticPrivacy.label(error.javaClass.simpleName), null)
                    val bytes = events.map { (it.json().toString() + "\n").toByteArray() }
                    var remaining = fileCap
                    val selected = bytes.asReversed().takeWhile { b -> (remaining - b.size).also { remaining = it } >= 0 }.asReversed()
                    File(dir, "crash.jsonl").outputStream().use { out -> selected.forEach(out::write); out.fd.sync() }
                }
            }.onFailure { persistenceFailures.incrementAndGet() }
        }
    }

    fun bundle(appVersion: String, moduleVersion: String?, api: Int, abi: String): JSONObject {
        val complete = flush()
        return synchronized(lock) {
            JSONObject().put("schema_version", 1).put("redacted", true).put("created_at", System.currentTimeMillis())
                .put("app_version", DiagnosticPrivacy.version(appVersion))
                .put("module_version", moduleVersion?.let(DiagnosticPrivacy::version) ?: JSONObject.NULL)
                .put("environment", JSONObject().put("api", api).put("abi", DiagnosticPrivacy.label(abi)))
                .put("events", JSONArray(ring.map { it.json() })).put("counters", JSONObject(counts.toMap()))
                .put("failures", JSONArray(ring.filter { it.severity == Severity.ERROR }.map { it.json() }))
                .put("current_actions", JSONArray(active.values.map { it.json() }))
                .put("dropped", dropped.get()).put("persistence_failures", persistenceFailures.get())
                .put("flush_complete", complete)
        }
    }

    fun zip(report: JSONObject): ByteArray = java.io.ByteArrayOutputStream().use { bytes ->
        ZipOutputStream(bytes).use { zip ->
            zip.putNextEntry(ZipEntry("diagnostics.json")); zip.write(report.toString(2).toByteArray()); zip.closeEntry()
        }
        bytes.toByteArray()
    }

    override fun close() { flush(); closed = true; worker.join(1500) }
}
