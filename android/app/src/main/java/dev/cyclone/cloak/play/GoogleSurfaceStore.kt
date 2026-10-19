package dev.cyclone.cloak.play

import android.content.Context
import dev.cyclone.cloak.data.EngineMetadataStore
import dev.cyclone.cloak.forge.StrictJson
import org.json.JSONArray
import org.json.JSONObject

/** One private, atomically committed sidecar. No profile, binding or publication writers. */
class GoogleSurfaceStore(context: Context) {
    private val preferences = context.getSharedPreferences("google-surface-isolation", Context.MODE_PRIVATE)
    companion object {
        private val lock = Any()
        private const val MAX_LIBRARY_BYTES = 2097152
        private const val MAX_RECORDS = 512
    }
    data class Snapshot(val records: List<GoogleSurfaceRecord>, val readable: Boolean)
    data class AssignmentReview(val digest: String, val sharedProfileIds: List<String>)
    class ReviewRequired : IllegalStateException("GS_SHARED_REVIEW: Confirm the current shared account assignment before saving.")
    class InventoryUnreadable : IllegalStateException("GS_INVENTORY_UNREADABLE: Local assignments cannot be read safely.")
    private data class Library(val records: List<GoogleSurfaceRecord>, val confirmations: Set<String>)

    private fun load(): Library {
        try {
            val text = preferences.getString("library", null) ?: return Library(emptyList(), emptySet())
            require(text.toByteArray(Charsets.UTF_8).size <= MAX_LIBRARY_BYTES)
            StrictJson(text).check()
            val j = JSONObject(text)
            GoogleSurfaceCodec.keys(j, setOf("format", "records", "confirmations"))
            require(j.get("format") == 1)
            val a = j.getJSONArray("records")
            require(a.length() <= MAX_RECORDS)
            val records = (0 until a.length()).map { GoogleSurfaceCodec.parse(a.getJSONObject(it).toString()) }
            require(records.map { it.profileId }.distinct().size == records.size)
            val c = j.getJSONArray("confirmations")
            require(c.length() <= MAX_RECORDS)
            val confirmations = (0 until c.length()).map { i ->
                require(c.get(i) is String)
                c.getString(i).also { require(GoogleSurfaceCodec.digest.matches(it)) }
            }
            require(confirmations.distinct().size == confirmations.size)
            return Library(records, confirmations.toSet())
        } catch (_: Exception) { throw InventoryUnreadable() }
    }
    private fun write(library: Library) {
        require(library.records.size <= MAX_RECORDS) { "GS_LIBRARY_LIMIT" }
        val a = JSONArray(); library.records.sortedBy { it.profileId }.forEach { a.put(JSONObject(GoogleSurfaceCodec.encode(it))) }
        // Drop obsolete confirmations without touching any saved profile or account.
        val relevant = library.records.mapNotNull { groupDigest(it, library.records) }.toSet()
        val c = JSONArray(); library.confirmations.intersect(relevant).sorted().forEach(c::put)
        val text = JSONObject().put("format", 1).put("records", a).put("confirmations", c).toString()
        require(text.toByteArray(Charsets.UTF_8).size <= MAX_LIBRARY_BYTES) { "GS_LIBRARY_LIMIT" }
        check(preferences.edit().putString("library", text).commit()) { "GS_SAVE_FAILED: Could not save local Google surface assignments." }
    }
    private fun groupDigest(record: GoogleSurfaceRecord, records: List<GoogleSurfaceRecord>): String? {
        val slot = record.slotId ?: return null
        if (!record.active) return null
        val group = records.filter { it.active && it.slotId == slot }.sortedBy { it.profileId }
        if (group.size < 2) return null
        val a = JSONArray(); group.forEach { a.put(JSONObject(GoogleSurfaceCodec.encode(it))) }
        return EngineMetadataStore.digest(JSONObject().put("format", 1).put("assignments", a))
    }
    fun snapshot(): Snapshot = synchronized(lock) {
        try { Snapshot(load().records, true) } catch (_: InventoryUnreadable) { Snapshot(emptyList(), false) }
    }
    fun read(profileId: String): GoogleSurfaceRecord? = snapshot().records.firstOrNull { it.profileId == profileId }
    /** Consent applies only to the exact prospective active group, never an imported shared flag. */
    fun sharingConfirmed(record: GoogleSurfaceRecord): Boolean = synchronized(lock) {
        GoogleSurfaceCodec.validate(record)
        val library = load()
        val next = library.records.filterNot { it.profileId == record.profileId } + record
        groupDigest(record, next)?.let { it in library.confirmations } ?: false
    }
    fun review(record: GoogleSurfaceRecord): AssignmentReview = synchronized(lock) {
        GoogleSurfaceCodec.validate(record)
        val next = load().records.filterNot { it.profileId == record.profileId } + record
        AssignmentReview(EngineMetadataStore.digest(JSONObject().put("proposal", JSONObject(GoogleSurfaceCodec.encode(record)))
            .put("inventory", JSONArray().also { a -> next.sortedBy { it.profileId }.forEach { a.put(JSONObject(GoogleSurfaceCodec.encode(it))) } })),
            next.filter { record.active && it.active && record.slotId != null && it.slotId == record.slotId }.map { it.profileId }.sorted()
                .takeIf { it.size > 1 } ?: emptyList())
    }
    /** Caller presents review(), then invokes this only after an explicit user confirmation. */
    fun save(record: GoogleSurfaceRecord, reviewedDigest: String? = null, confirmShared: Boolean = false) = synchronized(lock) {
        val review = review(record)
        val library = load()
        val next = library.records.filterNot { it.profileId == record.profileId } + record
        val group = groupDigest(record, next)
        val confirmations = library.confirmations.toMutableSet()
        if (group != null && group !in confirmations) {
            if (!confirmShared || reviewedDigest != review.digest) throw ReviewRequired()
            confirmations += group
        }
        write(Library(next, confirmations))
    }
    fun clear(profileId: String) = synchronized(lock) {
        val library = load()
        write(library.copy(records = library.records.filterNot { it.profileId == profileId }))
    }
    fun evaluate(profileId: String, androidUserId: Int?, profileHash: String, observation: GoogleSurfaceObservation? = null): IsolationReview = synchronized(lock) {
        val library = try { load() } catch (_: InventoryUnreadable) {
            return@synchronized GoogleSurfaceIsolation.evaluate(null, profileId, androidUserId, profileHash, emptyList(), inventoryReadable = false)
        }
        val record = library.records.firstOrNull { it.profileId == profileId }
        GoogleSurfaceIsolation.evaluate(record, profileId, androidUserId, profileHash, library.records, observation,
            record?.let { groupDigest(it, library.records) in library.confirmations } ?: false)
    }
}
