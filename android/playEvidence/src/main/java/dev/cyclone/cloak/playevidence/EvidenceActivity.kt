package dev.cyclone.cloak.playevidence

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.util.AtomicFile
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import dev.cyclone.cloak.forge.StrictJson
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.util.UUID
import java.util.concurrent.Executors

/** No collection on launch. Both local collection and the optional API require dedicated taps. */
class EvidenceActivity : Activity() {
    private val worker = Executors.newSingleThreadExecutor()
    private val lock = Any()
    private var generation = 0L
    private lateinit var profileId: EditText
    private lateinit var artifact: EditText
    private lateinit var project: EditText
    private lateinit var status: TextView
    private lateinit var collect: Button
    private lateinit var integrity: Button
    private var pendingExport: Pair<Long, ByteArray>? = null
    private var selectedHash: String? = null
    private var expectedHash: String? = null
    private var requestId = UUID.randomUUID().toString()
    private var expectedUser = android.os.Process.myUid() / 100000
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(28, 28, 28, 28) }
        fun text(value: Int) = TextView(this).apply { setText(value) }.also(layout::addView)
        fun edit(hint: Int) = EditText(this).apply { setHint(hint); isSingleLine = true }.also(layout::addView)
        text(R.string.title)
        text(R.string.intro)
        profileId = edit(R.string.profile_id_hint)
        artifact = edit(R.string.artifact_hint)
        Button(this).apply {
            setText(R.string.choose_profile)
            setOnClickListener {
                @Suppress("DEPRECATION")
                startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).setType("application/json").addCategory(Intent.CATEGORY_OPENABLE), 10)
            }
        }.also(layout::addView)
        collect = Button(this).apply { setText(R.string.collect_local); setOnClickListener { begin(false) } }.also(layout::addView)
        project = edit(R.string.project_hint)
        integrity = Button(this).apply {
            setText(R.string.collect_integrity)
            setOnClickListener {
                AlertDialog.Builder(this@EvidenceActivity).setTitle(R.string.confirm_title)
                    .setMessage(R.string.confirm_message)
                    .setNegativeButton(R.string.cancel, null).setPositiveButton(R.string.request_once) { _, _ ->
                        AlertDialog.Builder(this@EvidenceActivity).setTitle(R.string.retain_title)
                            .setMessage(R.string.retain_message)
                            .setNegativeButton(R.string.no_retention) { _, _ -> begin(true, false) }
                            .setPositiveButton(R.string.request_retain) { _, _ -> begin(true, true) }.show()
                    }.show()
            }
        }.also(layout::addView)
        Button(this).apply {
            setText(R.string.export_verification)
            setOnClickListener { prepareExport() }
        }.also(layout::addView)
        status = text(R.string.setup_hint)
        setContentView(ScrollView(this).apply { addView(layout) })
        acceptIntent(intent)
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        synchronized(lock) { generation++ }
        setIntent(intent)
        acceptIntent(intent)
    }
    private fun acceptIntent(input: Intent) {
        pendingExport = null
        selectedHash = null
        expectedHash = null
        collect.isEnabled = true; integrity.isEnabled = true
        try {
            val requested = input.getStringExtra("request_id") ?: UUID.randomUUID().toString()
            require(Regex("[A-Za-z0-9_-]{1,80}").matches(requested))
            requestId = requested
            expectedUser = input.getIntExtra("android_user_id", android.os.Process.myUid() / 100000)
            input.getStringExtra("profile_id")?.let { profileId.setText(it) }
            input.getStringExtra("artifact_hash")?.let { artifact.setText(it) }
            expectedHash = input.getStringExtra("profile_hash")
            val file = File(filesDir, "inputs/$requested.json")
            if (file.exists()) selectedHash = file.inputStream().use { readProfile(readBounded(it)) }
            status.text = if (selectedHash == null) getString(R.string.no_checks) else getString(R.string.selected_ready, selectedHash)
        } catch (_: Exception) { status.setText(R.string.invalid_binding) }
    }
    private fun readProfile(bytes: ByteArray): String {
        require(bytes.size in 1..262144)
        val text = bytes.toString(Charsets.UTF_8)
        StrictJson(text).check()
        require(JSONObject(text).optString("schema_version") in setOf("0.2", "0.3", "0.4"))
        return EvidenceReport.digest(bytes)
    }
    private fun readBounded(input: java.io.InputStream): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(4096)
        while (out.size() <= 262144) {
            val count = input.read(buffer, 0, minOf(buffer.size, 262145 - out.size()))
            if (count < 0) break
            out.write(buffer, 0, count)
        }
        return out.toByteArray()
    }
    @Deprecated("Android activity result compatibility callback")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 11) {
            val pending = pendingExport
            pendingExport = null
            if (resultCode == RESULT_OK && pending != null && pending.first == synchronized(lock) { generation }) {
                try {
                    val validated = VerificationBundle.parse(pending.second.toString(Charsets.UTF_8), expected = currentBinding()).export()
                    contentResolver.openOutputStream(data!!.data!!, "wt")!!.use { it.write(validated) }
                    AtomicFile(File(filesDir, "integrity-verification.json")).delete()
                    status.setText(R.string.export_done)
                } catch (_: Exception) { status.setText(R.string.export_failed) }
            }
            return
        }
        if (requestCode == 10 && resultCode == RESULT_OK) try {
            selectedHash = contentResolver.openInputStream(data!!.data!!)!!.use { readProfile(readBounded(it)) }
            status.text = getString(R.string.selected_hash, selectedHash)
        } catch (_: Exception) { selectedHash = null; status.setText(R.string.invalid_profile) }
    }
    private fun currentBinding(): EvidenceBinding = EvidenceBinding(requestId, profileId.text.toString(),
        expectedHash ?: requireNotNull(selectedHash), artifact.text.toString(), expectedUser).also {
            require(it.profileHash == selectedHash && it.androidUserId == android.os.Process.myUid() / 100000)
        }
    private fun prepareExport() {
        try {
            val file = AtomicFile(File(filesDir, "integrity-verification.json"))
            val bytes = file.openRead().use { readBounded(it) }
            val validated = VerificationBundle.parse(bytes.toString(Charsets.UTF_8), expected = currentBinding()).export()
            AlertDialog.Builder(this).setTitle(R.string.export_title)
                .setMessage(R.string.export_message)
                .setNegativeButton(R.string.cancel, null).setPositiveButton(R.string.choose_file) { _, _ ->
                    pendingExport = synchronized(lock) { generation to validated }
                    @Suppress("DEPRECATION")
                    startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/json")
                        .addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE, "integrity-verification.json"), 11)
                }.show()
        } catch (_: Exception) { status.setText(R.string.no_retained_token) }
    }
    private fun begin(withIntegrity: Boolean, retainToken: Boolean = false) {
        val binding: EvidenceBinding
        val actual = selectedHash
        val cloud = if (withIntegrity) project.text.toString().toLongOrNull() else null
        try {
            require(actual != null)
            binding = EvidenceBinding(requestId, profileId.text.toString(), expectedHash ?: actual,
                artifact.text.toString(), expectedUser)
            require(!withIntegrity || actual == binding.profileHash && expectedUser == android.os.Process.myUid() / 100000)
            require(!withIntegrity || cloud != null && cloud > 0)
        } catch (_: Exception) { status.setText(R.string.invalid_request); return }
        val token = synchronized(lock) {
            pendingExport = null
            AtomicFile(File(filesDir, "integrity-verification.json")).delete()
            ++generation
        }
        collect.isEnabled = false; integrity.isEnabled = false
        status.setText(R.string.collecting)
        worker.execute {
            var retained: VerificationBundle? = null
            val report = try {
                val api = if (withIntegrity) IntegrityEvidence.request(this, cloud!!, binding,
                    if (retainToken) { raw, at -> retained = VerificationBundle.retain(raw, binding, at, true) } else null)
                    else EvidenceRow("integrity.api", EvidenceStatus.NOT_RUN, "USER_NOT_REQUESTED")
                SurfaceCollector.collect(binding, actual, AndroidSurfaceReader(this), api)
            } catch (_: Exception) {
                EvidenceReport.build(binding, EvidenceReport.ids.map { id ->
                    if (id == "profile.binding") EvidenceRow(id, EvidenceStatus.FAIL, "COLLECTION_FAILED")
                    else EvidenceRow(id, EvidenceStatus.NOT_RUN, "USER_NOT_REQUESTED")
                }, Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS))
            }
            try {
                synchronized(lock) {
                    if (token != generation) return@execute
                    retained?.let { bundle ->
                        val privateFile = AtomicFile(File(filesDir, "integrity-verification.json"))
                        val output = privateFile.startWrite()
                        try { output.write(bundle.export()); privateFile.finishWrite(output) }
                        catch (error: Exception) { privateFile.failWrite(output); throw error }
                    }
                    val directory = File(filesDir, "reports").apply { mkdirs() }
                    val target = AtomicFile(File(directory, "${binding.requestId}.json"))
                    val stream = target.startWrite()
                    try { stream.write(report.toString().toByteArray(Charsets.UTF_8)); target.finishWrite(stream) }
                    catch (error: Exception) { target.failWrite(stream); throw error }
                    directory.listFiles()?.filter { it.name.endsWith(".json") }?.sortedByDescending { it.lastModified() }?.drop(20)?.forEach { it.delete() }
                }
                runOnUiThread {
                    synchronized(lock) {
                        if (token != generation) return@runOnUiThread
                        val rows = report.getJSONArray("rows")
                        val counts = (0 until rows.length()).map { rows.getJSONObject(it).getString("status") }.groupingBy { it }.eachCount()
                        status.text = getString(R.string.report_saved, binding.requestId, counts.toString())
                        collect.isEnabled = true; integrity.isEnabled = true
                    }
                }
            } catch (_: Exception) {
                runOnUiThread { if (synchronized(lock) { token == generation }) {
                    status.setText(R.string.write_failed); collect.isEnabled = true; integrity.isEnabled = true
                } }
            }
        }
    }
    override fun onDestroy() {
        synchronized(lock) { generation++ }
        worker.shutdownNow()
        super.onDestroy()
    }
}
