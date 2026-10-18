package dev.cyclone.cloak.forge

import org.json.JSONObject

/** Pure, value-free findings. Reality never overrides the authoritative base profile. */
object RealityRuntimeLink {
    enum class State { MATCHED, MISMATCHED, NOT_PRESENT, BASE_MISSING }
    data class Link(val fieldPath: String, val basePath: String, val realityPresent: Boolean, val state: State) {
        val parityPassed: Boolean? get() = when (state) { State.MATCHED -> true; State.MISMATCHED -> false; else -> null }
    }
    fun evaluate(profile: JSONObject): List<Link> {
        val fields = JSONObject(RealityRuntimeTable.json).getJSONArray("fields")
        return (0 until fields.length()).map { fields.getJSONObject(it) }.filter { !it.isNull("linked_base_path") }.map { row ->
            val path = row.getString("field_path"); val basePath = row.getString("linked_base_path")
            val section = DeviceReality.at(profile, path.split('.').take(2).joinToString(".")) as? JSONObject
            val reality = DeviceReality.at(profile, path); val base = DeviceReality.at(profile, basePath)
            val present = section?.optString("availability") == "known" && reality != null
            val same = if (reality is Number && base is Number) reality.toDouble() == base.toDouble() else reality == base
            Link(path, basePath, present, when { !present -> State.NOT_PRESENT; base == null -> State.BASE_MISSING; same -> State.MATCHED; else -> State.MISMATCHED })
        }
    }
}
