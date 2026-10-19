package dev.cyclone.cloak.play

import android.content.Context
import dev.cyclone.cloak.forge.PlaySurfaceContract
import dev.cyclone.cloak.forge.StrictJson
import org.json.JSONObject

/** Private report evidence only. Readiness is recomputed against the caller's current binding/time. */
class PlayReadinessStore(context: Context) {
    private val preferences = context.getSharedPreferences("play-readiness-evaluation", Context.MODE_PRIVATE)
    private val provider = LocalPlayReadinessProvider()
    companion object { private val lock = Any() }
    class RejectedReport(val result: PlayReadinessResult) : IllegalArgumentException("PR_REPORT_REJECTED: Report did not pass local evidence binding.")
    class UnreadableStore : IllegalStateException("PR_STORE_UNREADABLE: Stored readiness evidence cannot be read safely.")
    private data class Stored(val report: String?, val reviewRequested: Boolean, val readinessJson: String? = null)
    private fun checkId(id: String) { require(Regex("[A-Za-z0-9][A-Za-z0-9._:-]{0,95}").matches(id)) { "PR_INVALID_PROFILE_ID" } }
    private fun load(id: String): Stored {
        checkId(id)
        try {
            val text = preferences.getString(id, null) ?: return Stored(null, false)
            require(text.toByteArray(Charsets.UTF_8).size <= PlayReadinessReport.MAX_BYTES * 2)
            StrictJson(text).check()
            val j = JSONObject(text)
            PlayReadinessReport.keys(j, setOf("format", "profile_id", "report", "review_requested", "readiness"))
            require(j.get("format") == 1 && j.get("profile_id") is String && j.getString("profile_id") == id)
            require(j.get("review_requested") is Boolean)
            val report = if (j.isNull("report")) null else {
                require(j.get("report") is String)
                PlayReadinessReport.parse(j.getString("report")).also { require(it.json.getString("profile_id") == id) }.text
            }
            val record = if (j.isNull("readiness")) null else PlaySurfaceContract.parseReadiness(j.getJSONObject("readiness").toString())
            require(record == null || record.getString("profile_id") == id)
            require(report == null || record != null)
            if (report != null && record != null) {
                val evidence = PlayReadinessReport.parse(report).json
                require(listOf("profile_hash", "artifact_hash").all { record.getString(it) == evidence.getString(it) })
                require(record.getString("report_timestamp") == evidence.getString("timestamp"))
                val digest = java.security.MessageDigest.getInstance("SHA-256").digest(report.toByteArray(Charsets.UTF_8))
                    .joinToString("") { "%02x".format(it) }
                require(record.getString("evidence_digest") == digest)
            }
            return Stored(report, j.getBoolean("review_requested"), record?.toString())
        } catch (_: Exception) { throw UnreadableStore() }
    }
    private fun write(id: String, stored: Stored) {
        val text = JSONObject().put("format", 1).put("profile_id", id)
            .put("report", stored.report ?: JSONObject.NULL).put("review_requested", stored.reviewRequested)
            .put("readiness", stored.readinessJson?.let(::JSONObject) ?: JSONObject.NULL).toString()
        require(text.toByteArray(Charsets.UTF_8).size <= PlayReadinessReport.MAX_BYTES * 2) { "PR_STORE_LIMIT" }
        check(preferences.edit().putString(id, text).commit()) { "PR_SAVE_FAILED: Could not save local readiness evidence." }
    }
    fun read(context: PlayReadinessContext): PlayReadinessResult = synchronized(lock) {
        val stored = try { load(context.profileId) } catch (_: UnreadableStore) {
            return@synchronized provider.evaluate(context, null, true).let { it.copy(
                findings = listOf(PlayReadinessFinding("storage", "PR_STORE_UNREADABLE"))) }
        }
        val result = provider.evaluate(context, stored.report, stored.reviewRequested)
        if (result.review == PlayReviewState.NEEDS_REVIEW && !stored.reviewRequested)
            write(context.profileId, stored.copy(reviewRequested = true, readinessJson = result.contractRecordJson))
        result
    }
    /** Rejected imports never replace older valid evidence. Explicit review remains sticky by default. */
    fun save(context: PlayReadinessContext, report: String, reviewConfirmed: Boolean = false): PlayReadinessResult = synchronized(lock) {
        val result = provider.evaluate(context, report)
        if (!result.accepted) throw RejectedReport(result)
        val old = load(context.profileId)
        val review = old.reviewRequested && !reviewConfirmed
        val evaluated = provider.evaluate(context, report, review)
        val stored = Stored(PlayReadinessReport.parse(report).text, review, evaluated.contractRecordJson)
        write(context.profileId, stored)
        provider.evaluate(context, stored.report, stored.reviewRequested)
    }
    fun requestReview(profileId: String) = synchronized(lock) {
        val old = load(profileId)
        val unknown = old.readinessJson?.let { text -> PlaySurfaceContract.parseReadiness(text)
            .put("state", "UNKNOWN").put("reason_codes", org.json.JSONArray(listOf("PS_REVIEW_REQUIRED"))).toString() }
        write(profileId, old.copy(reviewRequested = true, readinessJson = unknown))
    }
    fun clear(profileId: String) = synchronized(lock) {
        checkId(profileId)
        check(preferences.edit().remove(profileId).commit()) { "PR_SAVE_FAILED: Could not clear local readiness evidence." }
    }
}
