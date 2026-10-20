package dev.cyclone.cloak.play

import android.content.Context
import dev.cyclone.cloak.data.EngineMetadataStore
import dev.cyclone.cloak.forge.StrictJson
import org.json.JSONObject
import java.time.Instant

/** Additive binding sidecars in the existing assignment preferences. The format-1 library is untouched. */
class ServiceSurfaceStore(context: Context) {
    private val preferences = context.getSharedPreferences("google-surface-isolation", Context.MODE_PRIVATE)
    private val surfaces = GoogleSurfaceStore(context)
    companion object { private val lock = Any(); private const val MAX_BYTES = 262144 }
    data class Snapshot(val evidence: ServiceSurfaceEvidence?, val reviewRequested: Boolean, val readable: Boolean)
    class InvalidRecord : IllegalArgumentException("SSI_MALFORMED_RECORD")
    class StoreUnreadable : IllegalStateException("SSI_STORE_UNREADABLE")

    private fun key(profileId: String, packageName: String): String {
        require(GoogleSurfaceCodec.id.matches(profileId) && packageName.length <= 200 && GoogleSurfaceCodec.packageName.matches(packageName))
        return "binding:" + EngineMetadataStore.digest(JSONObject().put("profile", profileId).put("package", packageName))
    }

    internal fun encode(evidence: ServiceSurfaceEvidence, reviewRequested: Boolean): String {
        try {
            ServiceSurfaceIsolation.validate(evidence.binding)
            val o = evidence.observation
            val validation = GoogleSurfaceIsolation.evaluate(evidence.binding.assignment, evidence.binding.assignment.profileId,
                evidence.binding.assignment.androidUserId, evidence.binding.assignment.profileHash,
                listOf(evidence.binding.assignment), o)
            require(validation.checks.none { it.reason == "GS_MALFORMED_EVIDENCE" })
            require(o.profileId == evidence.binding.assignment.profileId && o.profileHash == evidence.binding.assignment.profileHash &&
                o.androidUserId == evidence.binding.assignment.androidUserId)
            PlayReadinessReport.timestamp(o.checkedAt.toString())
            return JSONObject().put("format", 1).put("assignment", JSONObject(GoogleSurfaceCodec.encode(evidence.binding.assignment)))
                .put("package", evidence.binding.packageName).put("artifact_hash", evidence.binding.artifactHash)
                .put("review_requested", reviewRequested).put("observation", JSONObject()
                    .put("source", o.source).put("checked_at", o.checkedAt.toString())
                    .put("account_mode", o.accountMode ?: JSONObject.NULL).put("account_slot", o.accountSlotId ?: JSONObject.NULL)
                    .put("backup_sync", o.backupSync ?: JSONObject.NULL).put("private_boundary", o.privateDataBoundary ?: JSONObject.NULL)
                    .put("packages", JSONObject(o.packageStates)).put("versions", JSONObject(o.packageVersions)))
                .toString().also { require(it.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) }
        } catch (_: Exception) { throw InvalidRecord() }
    }

    private fun parse(text: String): Snapshot {
        require(text.toByteArray(Charsets.UTF_8).size <= MAX_BYTES)
        StrictJson(text).check()
        val j = JSONObject(text)
        GoogleSurfaceCodec.keys(j, setOf("format", "assignment", "package", "artifact_hash", "review_requested", "observation"))
        require(j.get("format") == 1 && j.get("review_requested") is Boolean)
        require(j.get("package") is String && j.get("artifact_hash") is String)
        val b = ServiceSurfaceBinding(GoogleSurfaceCodec.parse(j.getJSONObject("assignment").toString()), j.getString("package"), j.getString("artifact_hash"))
        val o = j.getJSONObject("observation")
        GoogleSurfaceCodec.keys(o, setOf("source", "checked_at", "account_mode", "account_slot", "backup_sync", "private_boundary", "packages", "versions"))
        fun nullable(name: String): String? { require(o.isNull(name) || o.get(name) is String); return if (o.isNull(name)) null else o.getString(name) }
        fun map(name: String): Map<String, String> = o.getJSONObject(name).let { obj ->
            require(obj.length() <= 256)
            obj.keys().asSequence().associateWith { require(obj.get(it) is String); obj.getString(it) }
        }
        require(o.get("source") is String && o.get("checked_at") is String)
        require(o.isNull("private_boundary") || o.get("private_boundary") is Boolean)
        val evidence = ServiceSurfaceEvidence(b, GoogleSurfaceObservation(b.assignment.profileId,
            requireNotNull(b.assignment.androidUserId), b.assignment.profileHash, o.getString("source"), Instant.parse(o.getString("checked_at")),
            nullable("account_mode"), nullable("account_slot"), nullable("backup_sync"), map("packages"),
            if (o.isNull("private_boundary")) null else o.getBoolean("private_boundary"), map("versions")))
        encode(evidence, j.getBoolean("review_requested"))
        return Snapshot(evidence, j.getBoolean("review_requested"), true)
    }

    fun read(profileId: String, packageName: String): Snapshot = synchronized(lock) {
        val storageKey = key(profileId, packageName)
        try {
            val review = preferences.getBoolean(storageKey + ":review", false)
            val text = preferences.getString(storageKey, null) ?: return@synchronized Snapshot(null, review, true)
            parse(text).also { require(it.evidence?.binding?.let { b -> b.assignment.profileId == profileId && b.packageName == packageName } == true) }
                .let { it.copy(reviewRequested = it.reviewRequested || review) }
        } catch (_: Exception) { Snapshot(null, true, false) }
    }

    /** Explicit local review only; importing evidence cannot silently clear an earlier review request. */
    fun save(evidence: ServiceSurfaceEvidence, reviewConfirmed: Boolean = false) = synchronized(lock) {
        val b = evidence.binding
        val old = read(b.assignment.profileId, b.packageName)
        if (!old.readable) throw StoreUnreadable()
        val encoded = encode(evidence, old.reviewRequested && !reviewConfirmed)
        val storageKey = key(b.assignment.profileId, b.packageName)
        val edit = preferences.edit().putString(storageKey, encoded)
        if (reviewConfirmed) edit.remove(storageKey + ":review")
        check(edit.commit()) { "SSI_SAVE_FAILED" }
    }

    fun requestReview(profileId: String, packageName: String) = synchronized(lock) {
        val old = read(profileId, packageName)
        if (!old.readable) throw StoreUnreadable()
        // A review request without evidence also survives process restart.
        val evidence = old.evidence
        if (evidence == null) {
            check(preferences.edit().putBoolean(key(profileId, packageName) + ":review", true).commit()) { "SSI_SAVE_FAILED" }
        } else check(preferences.edit().putString(key(profileId, packageName), encode(evidence, true)).commit()) { "SSI_SAVE_FAILED" }
    }

    fun evaluate(binding: ServiceSurfaceBinding, context: PlayReadinessContext): ServiceIsolationResult = synchronized(lock) {
        val snapshot = read(binding.assignment.profileId, binding.packageName)
        val result = surfaces.evaluateService(binding, context, snapshot.evidence, snapshot.reviewRequested, snapshot.readable)
        if (snapshot.readable && !snapshot.reviewRequested && snapshot.evidence != null &&
            result.state in setOf(ServiceIsolationState.STALE, ServiceIsolationState.NEEDS_REVIEW)) {
            try { requestReview(binding.assignment.profileId, binding.packageName) }
            catch (_: Exception) { return@synchronized ServiceIsolationResult(ServiceIsolationState.UNKNOWN, listOf("SSI_STORE_UNREADABLE")) }
        }
        result
    }
}
