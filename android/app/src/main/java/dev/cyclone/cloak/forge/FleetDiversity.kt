package dev.cyclone.cloak.forge

import org.json.JSONObject

/** Similarity of recorded local descriptions, never a platform or runtime verdict. */
object FleetDiversity {
    private val traits = linkedMapOf(
        "device.model" to "compute", "device.fingerprint" to "compute",
        "camera.cameras" to "cameras", "display" to "panel",
        "reality.memory.values.storage_gib" to "memory", "reality.compute.values.soc" to "compute")
    data class Cluster(val paths: List<String>, val labels: List<String>, val sections: List<String>)
    data class Summary(val readable: Int, val score: Int?, val clusters: List<Cluster>, val missingFacts: Int) {
        val recommendations get() = clusters.map { cluster ->
            "${cluster.labels.size} identities share ${cluster.paths.joinToString()}. Review section-level regeneration for ${cluster.sections.joinToString()} where derived values are available, or create new drafts from distinct catalog templates; fixed hardware cannot be diversified by reseeding."
        }.distinct()
    }
    private fun key(profile: JSONObject, paths: List<String>): String? {
        val values = JSONObject()
        for (path in paths) {
            val value = DeviceReality.at(profile, path) ?: return null
            values.put(path, value)
        }
        return RealityJson.digest(values)
    }
    fun clusterKey(profile: JSONObject): String = RealityJson.digest(JSONObject().also { signature ->
        traits.keys.forEach { path -> signature.put(path, DeviceReality.at(profile, path) ?: JSONObject.NULL) }
    })
    fun analyze(profiles: List<JSONObject>): Summary {
        val paths = traits.keys.toList()
        val groups = (paths.map { listOf(it) } + listOf(listOf("device.model", "device.fingerprint", "display", "camera.cameras")))
        val clusters = groups.flatMap { fields -> profiles.mapNotNull { p -> key(p, fields)?.let { it to p } }
            .groupBy({ it.first }, { it.second }).values.filter { it.size > 1 }.map { members ->
                Cluster(fields, members.map { it.optString("name").ifBlank { "Unnamed identity" } }.sorted(),
                    fields.map { traits.getValue(it) }.distinct())
            } }.sortedWith(compareBy({ it.paths.joinToString() }, { it.labels.joinToString() }))
        fun pairs(n: Long) = n * (n - 1) / 2
        var comparable = 0L; var distinct = 0L
        for (path in paths) {
            val counts = profiles.mapNotNull { key(it, listOf(path)) }.groupingBy { it }.eachCount()
            val knownPairs = pairs(counts.values.sum().toLong())
            comparable += knownPairs
            distinct += knownPairs - counts.values.sumOf { pairs(it.toLong()) }
        }
        return Summary(profiles.size, if (comparable == 0L) null else (distinct * 100 / (pairs(profiles.size.toLong()) * paths.size)).toInt(), clusters,
            profiles.sumOf { p -> paths.count { DeviceReality.at(p, it) == null } })
    }
    /** Deterministic fair rounds: exhaust distinct reality clusters before repeating one. */
    fun order(phones: List<PhoneTemplate>): List<PhoneTemplate> {
        val grouped = phones.sortedBy { it.id }.groupBy { phone ->
            clusterKey(phone.toJson().put("camera", phone.camera))
        }.values.map { it.toMutableList() }.toMutableList()
        val ordered = mutableListOf<PhoneTemplate>()
        while (grouped.any { it.isNotEmpty() }) grouped.filter { it.isNotEmpty() }.forEach { ordered += it.removeAt(0) }
        return ordered
    }
}
