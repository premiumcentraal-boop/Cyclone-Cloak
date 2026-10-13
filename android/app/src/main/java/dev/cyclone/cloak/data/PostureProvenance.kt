package dev.cyclone.cloak.data

import android.content.Context
import org.json.JSONArray
import java.time.Instant

/** Pure acceptance rules for replacing one local evidence record with another. */
object PostureProvenance {
    fun canReplace(previous: PostureRecord?, candidate: PostureRecord): Boolean {
        previous?.let { require(it.profileId == candidate.profileId) { "Posture records use different profiles." } }
        if (previous == null) return true
        if (previous.reportDigest != null && previous.reportDigest == candidate.reportDigest) return false
        val previousSequence = previous.sequence
        val candidateSequence = candidate.sequence
        return when {
            previousSequence != null && candidateSequence != null -> candidateSequence > previousSequence
            candidateSequence != null -> true
            previousSequence != null -> false
            previous.checkedAt != null && candidate.checkedAt != null ->
                Instant.parse(candidate.checkedAt).isAfter(Instant.parse(previous.checkedAt))
            else -> candidate.checkedAt != null
        }
    }

    fun shortId(digest: String?): String? = digest?.take(12)
}

/** Local replay history. A digest cannot be silently imported twice for one profile. */
class PostureProvenanceStore(context: Context) {
    private val preferences = context.getSharedPreferences("compatibility-posture-provenance", Context.MODE_PRIVATE)

    companion object {
        private val lock = Any()
        private const val maxReports = 64
    }

    fun consumed(profileId: String, reportDigest: String): Boolean = synchronized(lock) {
        require(profileId.isNotBlank() && profileId.length <= 128) { "Invalid posture profile ID." }
        require(Regex("[0-9a-f]{64}").matches(reportDigest)) { "Report digest must be lowercase SHA-256." }
        digests(profileId).contains(reportDigest)
    }

    fun remember(profileId: String, reportDigest: String) = synchronized(lock) {
        require(profileId.isNotBlank() && profileId.length <= 128) { "Invalid posture profile ID." }
        require(Regex("[0-9a-f]{64}").matches(reportDigest)) { "Report digest must be lowercase SHA-256." }
        val existing = digests(profileId)
        if (!existing.contains(reportDigest)) {
            val next = JSONArray()
            val overflow = maxOf(existing.size + 1 - maxReports, 0)
            for (index in overflow until existing.size) next.put(existing[index])
            next.put(reportDigest)
            check(preferences.edit().putString(profileId, next.toString()).commit()) { "Could not save posture provenance." }
        }
    }

    fun clear(profileId: String) = synchronized(lock) {
        check(preferences.edit().remove(profileId).commit()) { "Could not clear posture provenance." }
    }

    private fun digests(profileId: String): List<String> = runCatching {
        val raw = preferences.getString(profileId, null) ?: return@runCatching emptyList()
        val array = JSONArray(raw)
        (0 until array.length()).mapNotNull { index ->
            (array.opt(index) as? String)?.takeIf { Regex("[0-9a-f]{64}").matches(it) }
        }
    }.getOrDefault(emptyList())
}
