package dev.cyclone.cloak.diagnostics

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class RunOperation { BIND, RETRY_RECONCILIATION, ENABLE, DISABLE, REMOVE, REPLACE }
enum class RunOutcome(val label: String) { READY("ready"), FAILED("failed"), NEEDS_ATTENTION("needs-attention") }

object RunPrivacy {
    fun reference(value: String): String = value.takeIf {
        it.matches(Regex("Cyclone_[a-f0-9]{16}|[a-fA-F0-9-]{16,64}|ref-[a-f0-9]{16}"))
    } ?: "ref-" + sha256(value).take(16)
    fun sha256(value: String): String = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
    fun event(value: JSONObject): JSONObject = JSONObject()
        .put("timestamp", value.getLong("timestamp"))
        .put("severity", Severity.valueOf(value.getString("severity")).name)
        .put("source", DiagnosticPrivacy.label(value.optString("source")))
        .put("subsystem", DiagnosticPrivacy.label(value.optString("subsystem")))
        .put("action_id", DiagnosticPrivacy.label(value.optString("action_id")))
        .put("thread", value.optString("thread").filter(Char::isDigit).take(20))
        .put("process", value.optString("process").filter(Char::isDigit).take(20))
        .put("target_id", DiagnosticPrivacy.target(value.optString("target_id")) ?: JSONObject.NULL)
        .put("transition", Transition.valueOf(value.getString("transition")).name)
        .put("reason", if (value.isNull("reason")) JSONObject.NULL else DiagnosticPrivacy.code(value.getString("reason")))
        .put("error_summary", if (value.isNull("error_summary")) JSONObject.NULL else DiagnosticPrivacy.label(value.getString("error_summary")))
        .put("duration_ms", if (value.isNull("duration_ms")) JSONObject.NULL else value.getLong("duration_ms").coerceAtLeast(0))
}

data class RunTarget(val profileId: String, val androidUser: Int?, val packages: List<String>, val deviceProfileId: String,
                     val expectedPresent: Boolean = true, val expectedEnabled: Boolean? = null,
                     val previousDeviceProfiles: Map<String, String> = emptyMap()) {
    fun json(): JSONObject = JSONObject().put("profile_id", RunPrivacy.reference(profileId))
        .put("android_user", androidUser ?: JSONObject.NULL)
        .put("packages", JSONArray(packages.distinct().sorted().mapNotNull(DiagnosticPrivacy::target)))
        .put("device_profile_id", RunPrivacy.reference(deviceProfileId)).put("expected_present", expectedPresent)
        .put("expected_enabled", expectedEnabled ?: JSONObject.NULL)
        .put("previous_device_profiles", JSONObject().also { j -> previousDeviceProfiles.forEach { (pkg, id) ->
            DiagnosticPrivacy.target(pkg)?.let { j.put(it, RunPrivacy.reference(id)) }
        } })
    companion object {
        fun parse(j: JSONObject) = RunTarget(j.getString("profile_id"), if (j.isNull("android_user")) null else j.getInt("android_user"),
            j.getJSONArray("packages").let { a -> (0 until a.length()).map(a::getString) }, j.getString("device_profile_id"),
            j.optBoolean("expected_present", true), if (j.isNull("expected_enabled")) null else j.getBoolean("expected_enabled"),
            j.optJSONObject("previous_device_profiles")?.let { old -> old.keys().asSequence().mapNotNull { pkg ->
                DiagnosticPrivacy.target(pkg)?.let { it to RunPrivacy.reference(old.getString(pkg)) }
            }.toMap() }.orEmpty())
    }
}

class RunEventCapture {
    val events = mutableListOf<JSONObject>()
    var dropped = 0
        private set
    fun accept(event: DiagnosticEvent) {
        if (events.size < 256) events.add(RunPrivacy.event(event.json())) else dropped++
    }
}

/** Counts and fixed codes only; never reconciliation's free-text issue values. */
data class RunReconciliation(val written: Int, val statuses: Int, val cleared: Int, val issues: Int, val stoppedBy: String?) {
    fun json(): JSONObject = JSONObject().put("written", written.coerceAtLeast(0)).put("statuses", statuses.coerceAtLeast(0))
        .put("cleared", cleared.coerceAtLeast(0)).put("issues", issues.coerceAtLeast(0))
        .put("stopped_by", stoppedBy?.let(DiagnosticPrivacy::code) ?: JSONObject.NULL)
    companion object {
        fun parse(j: JSONObject) = RunReconciliation(j.getInt("written"), j.getInt("statuses"), j.getInt("cleared"),
            j.getInt("issues"), if (j.isNull("stopped_by")) null else DiagnosticPrivacy.code(j.getString("stopped_by")))
    }
}

data class BindingRun(
    val id: String = UUID.randomUUID().toString(), val timestamp: Long = System.currentTimeMillis(),
    val operation: RunOperation, val targets: List<RunTarget>, val outcome: RunOutcome = RunOutcome.NEEDS_ATTENTION,
    val reasons: List<String> = emptyList(), val durationMs: Long = 0,
    val events: List<JSONObject> = emptyList(), val eventsDropped: Int = 0,
    val appVersion: String? = null, val moduleVersion: String? = null, val moduleCode: String? = null,
    val connectorMinor: Int? = null, val connectorApproved: Boolean? = null,
    val active: Boolean = false, val retryOf: String? = null, val reconciliation: RunReconciliation? = null,
) {
    val end: Long get() = timestamp + durationMs
    fun json(): JSONObject = JSONObject().put("schema_version", 1).put("run_id", id)
        .put("timestamp", timestamp).put("operation", operation.name).put("targets", JSONArray(targets.map { it.json() }))
        .put("outcome", outcome.label).put("reason_codes", JSONArray(reasons.distinct().map(DiagnosticPrivacy::code)))
        .put("duration_ms", durationMs).put("events_dropped", eventsDropped)
        .put("active", active).put("end_timestamp", if (active) JSONObject.NULL else end)
        .put("first_failure_reason", reasons.firstOrNull()?.let(DiagnosticPrivacy::code) ?: JSONObject.NULL)
        .put("retry_of", retryOf?.takeIf { runCatching { UUID.fromString(it) }.isSuccess } ?: JSONObject.NULL)
        .put("reconciliation", reconciliation?.json() ?: JSONObject.NULL)
        .put("app_version", appVersion?.let(DiagnosticPrivacy::version) ?: JSONObject.NULL)
        .put("module_version", moduleVersion?.let(DiagnosticPrivacy::version) ?: JSONObject.NULL)
        .put("module_code", moduleCode?.let(DiagnosticPrivacy::code) ?: JSONObject.NULL)
        .put("connector_minor", connectorMinor ?: JSONObject.NULL).put("connector_approved", connectorApproved ?: JSONObject.NULL)
        .put("publication_phases", JSONArray(events.filter { it.optString("subsystem") == "publication" }.map {
            JSONObject().put("phase", DiagnosticPrivacy.label(it.optString("action_id")))
                .put("state", Transition.valueOf(it.getString("transition")).name).put("timestamp", it.getLong("timestamp"))
        })).put("events", JSONArray(events.map(RunPrivacy::event)))
    companion object {
        fun parse(j: JSONObject): BindingRun {
            require(j.getInt("schema_version") == 1)
            val id = j.getString("run_id"); UUID.fromString(id)
            val start = j.getLong("timestamp"); val duration = j.getLong("duration_ms")
            require(start >= 0 && duration >= 0 && duration <= Long.MAX_VALUE - start)
            val targets = j.getJSONArray("targets"); val reasons = j.getJSONArray("reason_codes"); val events = j.getJSONArray("events")
            require(targets.length() <= 1000 && events.length() <= 256)
            return BindingRun(id, start, RunOperation.valueOf(j.getString("operation")),
                (0 until targets.length()).map { RunTarget.parse(targets.getJSONObject(it)) },
                RunOutcome.entries.first { it.label == j.getString("outcome") },
                (0 until reasons.length()).map { DiagnosticPrivacy.code(reasons.getString(it)) }, duration,
                (0 until events.length()).map { RunPrivacy.event(events.getJSONObject(it)) }, j.optInt("events_dropped").coerceAtLeast(0),
                j.optString("app_version").takeIf { !j.isNull("app_version") }?.let(DiagnosticPrivacy::version),
                j.optString("module_version").takeIf { !j.isNull("module_version") }?.let(DiagnosticPrivacy::version),
                j.optString("module_code").takeIf { !j.isNull("module_code") }?.let(DiagnosticPrivacy::code),
                if (j.isNull("connector_minor")) null else j.getInt("connector_minor"),
                if (j.isNull("connector_approved")) null else j.getBoolean("connector_approved"),
                j.optBoolean("active", false), j.optString("retry_of").takeIf { !j.isNull("retry_of") && runCatching { UUID.fromString(it) }.isSuccess },
                j.optJSONObject("reconciliation")?.let(RunReconciliation::parse))
        }
    }
}

/** Four append-only segments, at most 50 records/2 MiB each, newest 200 retained. */
class BindingRunJournal(private val directory: File, private val segmentRuns: Int = 50, private val fileCap: Int = 2 * 1024 * 1024,
                        private val now: () -> Long = System::currentTimeMillis) {
    private val mutable = MutableStateFlow<List<BindingRun>>(emptyList())
    val runs = mutable.asStateFlow()
    val failures = MutableStateFlow(0)
    init {
        require(segmentRuns in 1..50 && fileCap >= 2048)
        mutable.value = read()
        runCatching {
            val pending = File(directory, "pending.json")
            val backup = File(directory, "pending.json.bak")
            if (backup.isFile) {
                require(backup.length() <= fileCap)
                java.nio.file.Files.move(backup.toPath(), pending.toPath(), java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING)
            }
            if (pending.isFile && pending.length() <= fileCap) {
                val text = pending.inputStream().use { input ->
                    require(input.channel.size() <= fileCap); input.readBytes().toString(Charsets.UTF_8)
                }
                val run = BindingRun.parse(JSONObject(text))
                if (mutable.value.none { it.id == run.id }) append(run.copy(active = false, reasons = (run.reasons + "INTERRUPTED").distinct(),
                    durationMs = (now() - run.timestamp).coerceAtLeast(0)))
                else pending.delete() // append removes the checkpoint only after a durable journal write.
            }
        }.onFailure { failures.value++ }
    }
    @Synchronized fun begin(run: BindingRun) = safely {
        directory.mkdirs()
        val pending = File(directory, "pending.json")
        val staged = File(directory, "pending.json.new")
        val active = run.copy(active = true)
        val bytes = active.json().toString().toByteArray(Charsets.UTF_8)
        require(bytes.size <= fileCap)
        try {
            staged.outputStream().use { it.write(bytes); it.fd.sync() }
            // A checked atomic replacement: failure retains the last complete checkpoint.
            java.nio.file.Files.move(staged.toPath(), pending.toPath(), java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        } catch (e: Exception) { staged.delete(); throw e }
        mutable.value = (mutable.value.filterNot { it.id == run.id } + active).sortedBy { it.timestamp }.takeLast(200)
    }
    @Synchronized fun append(run: BindingRun) = safely {
        directory.mkdirs()
        val bytes = (run.copy(active = false).json().toString() + "\n").toByteArray(Charsets.UTF_8)
        require(bytes.size <= fileCap)
        val current = File(directory, "runs.jsonl")
        val count = if (current.isFile && current.length() <= fileCap) current.useLines { it.count() } else 0
        if (count >= segmentRuns || current.length() + bytes.size + 1 > fileCap) {
            val oldest = File(directory, "runs.3.jsonl")
            check(!oldest.exists() || oldest.delete())
            for (i in 2 downTo 0) {
                val from = File(directory, if (i == 0) "runs.jsonl" else "runs.$i.jsonl")
                if (from.exists()) check(from.renameTo(File(directory, "runs.${i + 1}.jsonl")))
            }
        }
        val needsSeparator = current.isFile && current.length() > 0 && java.io.RandomAccessFile(current, "r").use {
            it.seek(it.length() - 1); it.read() != 10
        }
        java.io.FileOutputStream(current, true).use { if (needsSeparator) it.write(10); it.write(bytes); it.fd.sync() }
        mutable.value = read()
        File(directory, "pending.json").delete()
    }
    private fun read(): List<BindingRun> = (listOf(3, 2, 1, 0)).flatMap { n ->
        val file = File(directory, if (n == 0) "runs.jsonl" else "runs.$n.jsonl")
        if (!file.isFile || file.length() > fileCap) emptyList() else runCatching {
            file.useLines { lines -> lines.take(segmentRuns).mapNotNull { line ->
                runCatching { BindingRun.parse(JSONObject(line)) }.getOrNull()
            }.toList() }
        }.getOrDefault(emptyList())
    }.distinctBy { it.id }.sortedBy { it.timestamp }.takeLast(200)
    private fun safely(block: () -> Unit) { runCatching(block).onFailure { failures.value++ } }
}

object BindingRuns {
    @Volatile private var instance: BindingRunJournal? = null
    @Synchronized fun journal(context: Context): BindingRunJournal = instance ?: BindingRunJournal(
        File(context.applicationContext.noBackupFilesDir, "binding-runs")).also { instance = it }
}
