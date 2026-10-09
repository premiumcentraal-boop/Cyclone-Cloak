package dev.cyclone.cloak.cyclone

import org.json.JSONArray
import org.json.JSONObject

/** Events since the cursor, de-duplicated by `seq` (delivered at least once), and what they ask Cloak to do. */
object CycloneEvents {
    data class Cursor(val since: Long, val lastSeq: Long)

    data class Batch(
        val cursor: Cursor,
        val events: List<JSONObject>,
        val reset: Boolean,
    ) {
        private fun ofType(type: String) = events.filter { it.optString("type") == type }
        /** The approval may have just arrived with a switch: say hello again. */
        val helloAgain: Boolean get() = reset || ofType("profile.switched").isNotEmpty()
        val reconcile: Boolean get() = reset || events.any { it.optString("type") in RECONCILE_TYPES }
        val removed: Set<String> get() = ofType("profile.removed").mapNotNull { it.opt("profileId") as? String }.toSet()
        val switchedTo: String? get() = ofType("profile.switched").lastOrNull()?.opt("profileId") as? String
    }

    private val RECONCILE_TYPES = setOf(
        "profile.created", "profile.updated", "profile.switched", "profile.trashed", "profile.restored", "profile.removed",
    )

    /** Pulls every waiting page (at most [maxPages]); unknown event types are kept but ask for nothing. */
    fun pull(api: CycloneApi, cursor: Cursor, maxPages: Int = 10): Batch {
        var since = cursor.since
        var lastSeq = cursor.lastSeq
        var reset = false
        val fresh = mutableListOf<JSONObject>()
        for (page in 0 until maxPages) {
            val answer = api.events(since)
            val next = (answer.opt("next") as? Number)?.toLong() ?: since
            // Cyclone's journal started over (its data was cleared): old sequence numbers mean nothing now.
            if (next < since) {
                lastSeq = 0
                reset = true
            }
            if (answer.optBoolean("reset", false)) reset = true
            val events = answer.optJSONArray("events") ?: JSONArray()
            for (i in 0 until events.length()) {
                val event = events.optJSONObject(i) ?: continue
                val seq = (event.opt("seq") as? Number)?.toLong() ?: continue
                if (seq <= lastSeq) continue
                lastSeq = seq
                fresh += event
            }
            since = next
            if (!answer.optBoolean("more", false)) break
        }
        return Batch(Cursor(since, lastSeq), fresh, reset)
    }
}
