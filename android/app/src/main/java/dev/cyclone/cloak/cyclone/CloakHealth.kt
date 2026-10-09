package dev.cyclone.cloak.cyclone

import dev.cyclone.cloak.data.*
import org.json.JSONObject

/** Cloak's detailed local states, mapped onto the four Cyclone accepts (`config.status.v1`, the pill in Cyclone). */
object CloakHealth {
    const val READY = "ready"
    const val UNKNOWN = "unknown"
    const val DEGRADED = "degraded"
    const val FAILED = "failed"

    /** The cloak can't apply at all. */
    private val failed = setOf(
        "missing", "module missing", "module disabled", "module removal pending", "module update needed",
        "module package invalid", "module install failed", "Zygisk disabled", "unsupported architecture", "publish failed",
    )

    fun cycloneState(local: String): String = when (local) {
        "ready" -> READY
        "unknown", "pending" -> UNKNOWN
        in failed -> FAILED
        // Root not granted here, a pending reboot, an unreadable Zygisk setting: bound, but something is off.
        else -> DEGRADED
    }

    /** The worst state wins, as in Cyclone's pill. */
    fun worst(states: Collection<String>): String = when {
        states.isEmpty() -> UNKNOWN
        FAILED in states -> FAILED
        DEGRADED in states -> DEGRADED
        states.all { it == READY } -> READY
        else -> UNKNOWN
    }
}

enum class CloakPillTone { READY, ATTENTION, NEUTRAL }

data class CloakPill(val label: String, val tone: CloakPillTone, val reason: String? = null)

/** Cloak's own pill per Cyclone profile (handoff §4.2). */
object CloakPills {
    /** Null: nothing to act on (setting up, in Recently deleted, or Main). */
    fun forProfile(profile: CycloneProfile, bindings: List<CloakBinding>, rootProven: Boolean?): CloakPill? {
        if (profile.kind != "profile" || profile.state != "ready") return null
        val mine = bindings.filter { it.profileId == profile.id && it.enabled }
        if (mine.isEmpty()) return CloakPill("Native", CloakPillTone.NEUTRAL)
        val states = mine.map { CloakHealth.cycloneState(it.state) }
        val worst = CloakHealth.worst(states)
        val badLocal = mine.firstOrNull { CloakHealth.cycloneState(it.state) == worst && worst != CloakHealth.READY }?.state
        return when {
            worst == CloakHealth.FAILED || worst == CloakHealth.DEGRADED ->
                CloakPill("Rooted !", CloakPillTone.ATTENTION, badLocal)
            rootProven == false ->
                CloakPill("Rooted !", CloakPillTone.ATTENTION, "Cyclone's last root check in this profile failed")
            worst == CloakHealth.READY && rootProven == true -> CloakPill("Rooted ✓", CloakPillTone.READY)
            worst == CloakHealth.READY -> CloakPill("Rooted", CloakPillTone.READY, "Cyclone hasn't checked root in this profile yet")
            else -> CloakPill("Rooted", CloakPillTone.NEUTRAL, "Not checked yet; run Root Doctor")
        }
    }

    /** `root.status.v1` → profile id → rootProven. Unknown fields are ignored. */
    fun rootFacts(rootStatus: JSONObject?): Map<String, Boolean?> {
        val array = rootStatus?.optJSONArray("profiles") ?: return emptyMap()
        return (0 until array.length()).mapNotNull { i ->
            val o = array.optJSONObject(i) ?: return@mapNotNull null
            val id = o.opt("id") as? String ?: return@mapNotNull null
            id to (o.opt("rootProven") as? Boolean)
        }.toMap()
    }
}
