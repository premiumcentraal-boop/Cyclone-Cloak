package dev.cyclone.cloak.play

import android.content.Context
import dev.cyclone.cloak.data.EngineMetadataStore
import dev.cyclone.cloak.data.JsonDirectory
import dev.cyclone.cloak.forge.StrictJson
import dev.cyclone.cloak.playevidence.EvidenceReport
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.time.Instant

/** Exact saved-file bytes bind reports; assignment records keep their existing canonical hash. */
data class PlayImportTarget(val profileId: String, val identityId: String, val androidUserId: Int,
    val profileText: String, val artifactHash: String) {
    val profile: JSONObject get() = JSONObject(profileText)
    fun context(now: Instant) = PlayReadinessContext(profileId, EvidenceReport.digest(profileText.toByteArray(Charsets.UTF_8)), androidUserId, artifactHash, now)
}

data class PlayImportPreview(val target: PlayImportTarget, val envelope: String, val projection: String,
    val result: PlayReadinessResult, val stamp: String, val sharedProfiles: List<String>, val sharingConfirmed: Boolean,
    val assignment: GoogleSurfaceRecord?, val assignmentReview: String?, val rowNotes: List<String>)

data class PlayReadinessCardState(val identityId: String? = null, val identityLabel: String? = null,
    val verdict: String = "UNKNOWN", val timestamp: String? = null, val source: String = "not recorded",
    val details: Map<String, String> = emptyMap(), val reasons: List<String> = emptyList(),
    val sharedProfiles: List<String> = emptyList(), val sharingConfirmed: Boolean = false,
    val profileHash: String? = null, val artifactHash: String? = null, val contextAvailable: Boolean = false)

object PlayEvidenceImport {
    class Rejected(val reasons: List<String>) : IllegalArgumentException("PR_IMPORT_REJECTED")
    fun projection(text: String, context: PlayReadinessContext): Pair<String, PlayReadinessResult> {
        val envelope = try { EvidenceReport.parse(text) } catch (_: IllegalArgumentException) {
            throw Rejected(listOf("The diagnostics report is malformed or incomplete. Choose the original report.json."))
        }
        val rows = envelope.getJSONArray("rows")
        for (i in 0 until rows.length()) {
            val row = rows.getJSONObject(i)
            if (row.getString("id") in setOf("profile.binding", "process.android_user") && row.getString("status") != "PASS")
                throw Rejected(listOf("The capture did not confirm its profile and Android user binding."))
        }
        val report = envelope.getJSONObject("readiness_report").toString()
        val result = LocalPlayReadinessProvider().evaluate(context, report)
        if (!result.accepted) throw Rejected(result.findings.map { it.message })
        return report to result
    }

    fun assignment(target: PlayImportTarget): GoogleSurfaceRecord? {
        val surface = target.profile.optJSONObject("play_surface") ?: return null
        val packages = linkedSetOf<String>()
        val expected = surface.getJSONObject("services")
        for (field in listOf("play_services", "play_store", "web_provider")) {
            expected.optJSONObject(field)?.optString("package_name")?.takeIf { it.isNotBlank() }?.let(packages::add)
        }
        val scoped = surface.getJSONObject("account").getJSONArray("allowed_packages")
        for (i in 0 until scoped.length()) packages += scoped.getString(i)
        return GoogleSurfaceRecord.fromProfile(target.profileId, target.androidUserId, target.profile,
            packages.map { PackageRule(it, PackageRole.SURFACE, PackageRequirement.OPTIONAL) })
    }

    fun display(result: PlayReadinessResult, report: String?): PlayReadinessCardState {
        val json = report?.let { runCatching { PlayReadinessReport.parse(it).json }.getOrNull() }
        val fields = linkedMapOf("Play Services" to "play_services", "Play Store" to "play_store", "Web provider" to "web_provider",
            "Account association" to "google_account", "Play Protect" to "play_protect", "Google settings access" to "google_settings_access")
        return PlayReadinessCardState(verdict = if (result.review == PlayReviewState.NEEDS_REVIEW) "NEEDS REVIEW" else result.state.name,
            timestamp = json?.getString("timestamp"), source = if (json == null) "not recorded" else "Imported local report · local.play.report",
            details = fields.mapValues { (_, path) -> json?.get(path)?.let { if (it is JSONObject) "${it.optString("package_name", "not recorded")} · ${it.getString("state")}" else it.toString() } ?: "not recorded" },
            reasons = result.findings.map { "${it.field}: ${it.message} (${it.code})" })
    }
}

/** Only the existing Google assignment/readiness sidecars are written. No profile/binding writers. */
class PlayEvidenceRepository(private val app: Context, private val clock: () -> Instant = { Instant.now() },
    private val artifact: () -> String? = { PlayEvidenceArtifacts.current(app) }) {
    private val readiness = PlayReadinessStore(app)
    private val surfaces = GoogleSurfaceStore(app)
    class ReviewChanged(val preview: PlayImportPreview) : IllegalStateException("PR_REVIEW_CHANGED")
    class PartialSave : IllegalStateException("PR_ASSIGNMENT_SAVED_READINESS_FAILED")
    private fun assignment(target: PlayImportTarget): GoogleSurfaceRecord? {
        val proposed = PlayEvidenceImport.assignment(target) ?: return null
        val previous = surfaces.read(target.profileId)
        return if (previous?.profileHash == proposed.profileHash && previous.androidUserId == proposed.androidUserId)
            proposed.copy(packages = previous.packages) else proposed
    }
    fun target(profileId: String, user: Int?, identityId: String?): PlayImportTarget {
        if (user == null || identityId == null) throw PlayEvidenceImport.Rejected(listOf("Choose a saved device profile and a Cyclone profile with an Android user assignment."))
        val directory = File(app.filesDir, "profiles")
        // Match existing JsonDirectory file naming, without invoking any profile writer/recovery.
        val file = File(directory, JsonDirectory(directory).fileName(identityId))
        val bytes = file.inputStream().use { it.readBounded(262144) }
        val text = Charsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
            .decode(java.nio.ByteBuffer.wrap(bytes)).toString()
        StrictJson(text).check()
        require(JSONObject(text).optString("schema_version") in setOf("0.2", "0.3", "0.4"))
        return PlayImportTarget(profileId, identityId, user, text, artifact() ?: throw PlayEvidenceImport.Rejected(listOf("The current app and bundled module artifact digest is unavailable. No report can be accepted.")))
    }
    fun preview(text: String, target: PlayImportTarget): PlayImportPreview {
        val (projection, result) = PlayEvidenceImport.projection(text, target.context(clock()))
        val record = assignment(target)
        val review = record?.let(surfaces::review)
        val confirmed = record?.let(surfaces::sharingConfirmed) ?: false
        val envelope = EvidenceReport.parse(text)
        val notes = envelope.getJSONArray("rows").let { a -> (0 until a.length()).map { a.getJSONObject(it) }
            .filter { it.getString("status") in setOf("FAIL", "BLOCKED", "MISSING", "NOT_RUN") }
            .map { "${it.getString("id")}: ${it.getString("status")} · ${it.getString("reason")}" } }
        val stamp = EvidenceReport.digest(listOf(target.profileId, target.identityId, target.context(clock()).profileHash,
            target.androidUserId.toString(), target.artifactHash, EvidenceReport.digest(text.toByteArray(Charsets.UTF_8)), review?.digest ?: "no-assignment", confirmed.toString()).joinToString("\n").toByteArray())
        return PlayImportPreview(target, text, projection, result, stamp, review?.sharedProfileIds.orEmpty(), confirmed, record, review?.digest, notes)
    }
    fun save(review: PlayImportPreview, current: PlayImportTarget, confirmedShared: Boolean): PlayReadinessResult {
        val latest = preview(review.envelope, current)
        if (latest.stamp != review.stamp) throw ReviewChanged(latest)
        if (latest.sharedProfiles.isNotEmpty() && !latest.sharingConfirmed && !confirmedShared) throw GoogleSurfaceStore.ReviewRequired()
        readiness.checkReadable(current.profileId)
        latest.assignment?.let { surfaces.save(it, latest.assignmentReview, confirmedShared) }
        // The explicit save is also the explicit review of the currently matching evidence.
        return try { readiness.save(current.context(clock()), latest.projection, reviewConfirmed = true) }
        catch (error: Exception) {
            if (latest.assignment != null) throw PartialSave()
            throw error
        }
    }
    fun card(profileId: String, user: Int?, identityId: String?): PlayReadinessCardState = try {
        val target = target(profileId, user, identityId)
        val result = readiness.read(target.context(clock()))
        val proposed = assignment(target)
        val previous = surfaces.read(profileId)
        val record = proposed ?: previous
        val assignmentChanged = previous != null && (previous.profileHash != EngineMetadataStore.digest(target.profile) || previous.androidUserId != user)
        val review = record?.let(surfaces::review)
        val confirmed = record?.let(surfaces::sharingConfirmed) ?: false
        val conflict = !confirmed && !review?.sharedProfileIds.isNullOrEmpty()
        PlayEvidenceImport.display(result, readiness.evidence(profileId)).copy(identityId = identityId,
            identityLabel = target.profile.optString("name", identityId ?: "Device profile"),
            verdict = if (conflict || assignmentChanged) "NEEDS REVIEW" else if (result.review == PlayReviewState.NEEDS_REVIEW) "NEEDS REVIEW" else result.state.name,
            reasons = PlayEvidenceImport.display(result, readiness.evidence(profileId)).reasons +
                if (assignmentChanged) listOf("Stored account assignment belongs to a different device profile or Android user. Review the assignment; no account changes were made.") else emptyList(),
            sharedProfiles = review?.sharedProfileIds.orEmpty(), sharingConfirmed = confirmed,
            profileHash = target.context(clock()).profileHash, artifactHash = target.artifactHash, contextAvailable = true)
    } catch (_: Exception) {
        val assigned = runCatching { surfaces.read(profileId) }.getOrNull()
        val shared = assigned?.let { runCatching { surfaces.review(it).sharedProfileIds }.getOrNull() }.orEmpty()
        val consent = assigned?.let { runCatching { surfaces.sharingConfirmed(it) }.getOrNull() } ?: false
        val saved = runCatching { readiness.evidence(profileId)?.let(PlayReadinessReport::parse)?.json }.getOrNull()
        PlayReadinessCardState(identityId = identityId, verdict = if (saved != null || shared.isNotEmpty() && !consent) "NEEDS REVIEW" else "UNKNOWN",
            timestamp = saved?.getString("timestamp"), source = if (saved == null) "not recorded" else "Imported local report · current binding unavailable",
            sharedProfiles = shared, sharingConfirmed = consent, reasons = listOf(if (user == null) "This Cyclone profile has no Android user assignment."
            else if (identityId == null) "Choose one saved device profile for this local evidence review. No bindings will change."
            else "The selected profile, artifact or private readiness store is unavailable. Review it before importing."))
    }
}

/** App APK + exact bundled module ZIP; no claim about an independently installed runtime module. */
object PlayEvidenceArtifacts {
    fun combine(appHash: String, moduleHash: String): String {
        require(listOf(appHash, moduleHash).all { Regex("[0-9a-f]{64}").matches(it) })
        return EvidenceReport.digest("play-artifacts/1\napp_sha256=$appHash\nmodule_zip_sha256=$moduleHash\n".toByteArray(Charsets.UTF_8))
    }
    fun current(context: Context): String? = runCatching {
        fun hash(input: InputStream) = input.use { source ->
            val digest = java.security.MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(8192)
            while (true) { val n = source.read(buffer); if (n < 0) break; digest.update(buffer, 0, n) }
            digest.digest().joinToString("") { "%02x".format(it) }
        }
        combine(hash(File(context.applicationInfo.sourceDir).inputStream()), hash(context.assets.open("cyclone-cloak-module.zip")))
    }.getOrNull()
}

internal fun InputStream.readBounded(limit: Int): ByteArray {
    val output = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(4096)
    while (output.size() <= limit) {
        val count = read(buffer, 0, minOf(buffer.size, limit + 1 - output.size()))
        if (count < 0) break
        output.write(buffer, 0, count)
    }
    require(output.size() <= limit) { "PR_REPORT_TOO_LARGE" }
    return output.toByteArray()
}
