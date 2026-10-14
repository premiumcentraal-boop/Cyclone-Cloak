package dev.cyclone.cloak.ui

import dev.cyclone.cloak.diagnostics.*

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import dev.cyclone.cloak.data.CloakBinding
import dev.cyclone.cloak.cyclone.CloakHealth
import dev.cyclone.cloak.cyclone.SyncReport
import dev.cyclone.cloak.root.RootDoctorResult
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object BindingRunBundle {
    fun window(runs: List<BindingRun>, since: Long, until: Long): List<BindingRun> =
        runs.filter { it.timestamp <= until && it.end >= since }.sortedBy { it.timestamp }

    /** Export an allowlisted projection, never raw binding JSON or mirrored identity summaries. */
    fun build(runs: List<BindingRun>, recent: List<DiagnosticEvent>, bindings: List<CloakBinding>, root: RootDoctorResult,
              handshake: SyncReport?, appVersion: String, since: Long, until: Long, capturedAt: Long,
              journalFailures: Int = 0): Map<String, JSONObject> {
        require(since <= until)
        val selected = window(runs, since, until)
        val events = (selected.flatMap { it.events }.map(RunPrivacy::event) + recent.map { RunPrivacy.event(it.json()) })
            .filter { it.getLong("timestamp") in since..until }.distinctBy { it.toString() }.sortedBy { it.getLong("timestamp") }
        val module = JSONObject().put("code", root.code.name)
            .put("version", root.moduleVersion?.let(DiagnosticPrivacy::version) ?: JSONObject.NULL)
            .put("publication", root.publicationReason?.name ?: JSONObject.NULL).put("stale_lock", root.staleLockDetected)
        val gate = handshake?.gate
        return linkedMapOf(
            "manifest.json" to JSONObject().put("schema_version", 1).put("redacted", true).put("captured_at", capturedAt)
                .put("window_start", since).put("window_end", until).put("run_count", selected.size)
                .put("app_version", DiagnosticPrivacy.version(appVersion))
                .put("module_version", root.moduleVersion?.let(DiagnosticPrivacy::version) ?: JSONObject.NULL)
                .put("journal_failures", journalFailures)
                .put("evidence_limits", JSONArray(listOf("Bounded local retention; missing events are not proof of success.",
                    "Snapshots describe export time, not necessarily run time.", "Local evidence only; no platform verdict."))),
            "runs.json" to JSONObject().put("runs", JSONArray(selected.map { it.json() })),
            "diagnostics.json" to JSONObject().put("window_start", since).put("window_end", until)
                .put("events", JSONArray(events)).put("retained_run_events_dropped", selected.sumOf { it.eventsDropped }),
            "bindings-index.json" to JSONObject().put("captured_at", capturedAt).put("snapshot_type", "current_local_bindings_projection")
                .put("bindings", JSONArray(bindings.map { b -> JSONObject().put("profile_id", RunPrivacy.reference(b.profileId))
                    .put("android_user", b.androidUserId).put("package", DiagnosticPrivacy.target(b.packageName) ?: JSONObject.NULL)
                    .put("device_profile_id", RunPrivacy.reference(b.cloakProfileId)).put("enabled", b.enabled)
                    .put("revision", b.revision).put("updated_at", b.updatedAt).put("state", CloakHealth.cycloneState(b.state)) })),
            "module-state.json" to JSONObject().put("captured_at", capturedAt).put("scope", "last_observed_root_doctor_state")
                .put("sha256", RunPrivacy.sha256(module.toString())).put("state", module)
                .put("run_observations", JSONArray(selected.map { run -> JSONObject().put("run_id", run.id)
                    .put("code", run.moduleCode?.let(DiagnosticPrivacy::code) ?: JSONObject.NULL)
                    .put("module_version", run.moduleVersion?.let(DiagnosticPrivacy::version) ?: JSONObject.NULL) }))
                .put("installed_binary_hash", JSONObject.NULL),
            "connector-handshake.json" to JSONObject().put("captured_at", capturedAt)
                .put("observed", handshake != null).put("client_contract", "cyclone.connector/1")
                .put("host_minor", gate?.minor ?: JSONObject.NULL).put("approved", gate?.approved ?: JSONObject.NULL)
                .put("run_observations", JSONArray(selected.map { run -> JSONObject().put("run_id", run.id)
                    .put("host_minor", run.connectorMinor ?: JSONObject.NULL).put("approved", run.connectorApproved ?: JSONObject.NULL) }))
                .put("failure_code", handshake?.failureCode?.let(DiagnosticPrivacy::code) ?: JSONObject.NULL)
        )
    }
    fun zip(files: Map<String, JSONObject>): ByteArray = ByteArrayOutputStream().use { out ->
        ZipOutputStream(out).use { zip -> files.forEach { (name, json) ->
            require(name.matches(Regex("[a-z-]+\\.json")))
            zip.putNextEntry(ZipEntry(name)); zip.write(json.toString(2).toByteArray(Charsets.UTF_8)); zip.closeEntry()
        } }; out.toByteArray()
    }
}

object RunBundleShare {
    /** Only this narrow cache subdirectory is exposed; grants apply to the chosen ZIP URI. */
    fun prepare(context: Context, bytes: ByteArray, now: Long = System.currentTimeMillis()): Intent {
        val file = write(context, bytes, now)
        return intent(FileProvider.getUriForFile(context, "${context.packageName}.runbundles", file))
    }
    internal fun write(context: Context, bytes: ByteArray, now: Long = System.currentTimeMillis()): File {
        val dir = File(context.cacheDir, "run-bundles").apply { check(isDirectory || mkdirs()) }
        val temporary = File.createTempFile("cloak-runs-$now-", ".tmp", dir)
        val file = File(dir, temporary.name.removeSuffix(".tmp") + ".zip")
        try {
            temporary.outputStream().use { it.write(bytes); it.fd.sync() }
            java.nio.file.Files.move(temporary.toPath(), file.toPath(), java.nio.file.StandardCopyOption.ATOMIC_MOVE)
        } catch (e: Exception) { temporary.delete(); throw e }
        dir.listFiles().orEmpty().filter { it != file && it.isFile && it.name.startsWith("cloak-runs-") && it.extension == "zip" }
            .sortedByDescending { it.lastModified() }.drop(4).forEach { it.delete() }
        return file
    }
    internal fun intent(uri: Uri): Intent {
        require(uri.scheme == "content")
        return Intent(Intent.ACTION_SEND).setType("application/zip").putExtra(Intent.EXTRA_STREAM, uri).apply {
            clipData = ClipData.newRawUri("Cloak binding diagnostics", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
