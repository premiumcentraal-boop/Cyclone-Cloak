package dev.cyclone.cloak.cyclone

import org.json.JSONArray
import org.json.JSONObject

/** What `hello` allows. Features are enabled by approval, granted scopes and Cyclone's minor, never assumed. */
data class CycloneGate(
    val approved: Boolean,
    val minor: Int,
    val granted: Set<String>,
    val pending: Set<String>,
) {
    /** Loading Cloak profiles onto Cyclone profiles (§3): the profile list, its apps, and the config store. */
    val bindings: Boolean get() = approved && minor >= 1 &&
        "profiles.read" in granted && "profiles.apps.read" in granted && "profiles.config" in granted
    val profiles: Boolean get() = approved && "profiles.read" in granted
    val events: Boolean get() = approved && "events.profiles" in granted
    val rootStatus: Boolean get() = approved && minor >= 2 && "device.root.read" in granted
    val openRequests: Boolean get() = approved && minor >= 3 && "profiles.open.request" in granted
    val startup: Boolean get() = approved && minor >= 1 && "profiles.startup" in granted

    companion object {
        fun of(hello: JSONObject): CycloneGate = CycloneGate(
            approved = hello.optBoolean("approved", false),
            minor = (hello.opt("minor") as? Number)?.toInt() ?: 0,
            granted = strings(hello.optJSONArray("granted")).toSet(),
            pending = strings(hello.optJSONArray("pending")).toSet(),
        )
    }
}

/** One entry of `profiles`. [androidUserId] and [packages] are null when Cyclone doesn't give them. */
data class CycloneProfile(
    val id: String,
    val label: String,
    val kind: String,
    val state: String,
    val androidUserId: Int?,
    val packages: List<String>?,
) {
    val isOwner: Boolean get() = kind == "owner" && id == OWNER
    /** A Cyclone profile Cloak may bind apps in: never Main, only ready, with a known Android user number. */
    val bindable: Boolean get() = kind == "profile" && state == "ready" && androidUserId != null && PROFILE_ID.matches(id)

    companion object {
        const val OWNER = "owner"
        val PROFILE_ID = Regex("^Cyclone_[a-f0-9]{16}$")
    }
}

data class ProfilesSnapshot(val current: String?, val profiles: List<CycloneProfile>) {
    fun byId(id: String): CycloneProfile? = profiles.firstOrNull { it.id == id }
    val registry: List<CycloneProfile> get() = profiles.filter { it.kind == "profile" && CycloneProfile.PROFILE_ID.matches(it.id) }

    companion object {
        fun parse(result: JSONObject): ProfilesSnapshot {
            val array = result.optJSONArray("profiles") ?: JSONArray()
            val profiles = (0 until array.length()).mapNotNull { i ->
                val o = array.optJSONObject(i) ?: return@mapNotNull null
                val id = o.opt("id") as? String ?: return@mapNotNull null
                CycloneProfile(
                    id = id,
                    label = (o.opt("label") as? String)?.takeIf { it.isNotBlank() } ?: id,
                    kind = o.opt("kind") as? String ?: "profile",
                    state = o.opt("state") as? String ?: "ready",
                    // Strictly an integer: a missing or null user number never becomes user 0 (Main).
                    androidUserId = integer(o.opt("androidUserId"))?.takeIf { it >= 0 },
                    packages = o.optJSONArray("packages")?.let(::strings),
                )
            }
            return ProfilesSnapshot(result.opt("current") as? String, profiles)
        }
    }
}

/** Which Cloak this is. Cloak in Main is the authority for every profile; Cloak in a profile looks after that profile. */
sealed class Placement {
    object Main : Placement()
    data class InProfile(val profile: CycloneProfile) : Placement()

    val isMain: Boolean get() = this is Main

    companion object {
        /** Each Cyclone profile is its own Android user; an install whose user is no profile's is Main's. */
        fun of(myUser: Int, snapshot: ProfilesSnapshot): Placement =
            snapshot.registry.firstOrNull { it.androidUserId == myUser }?.let(::InProfile) ?: Main
    }
}

/** JSON objects equal by value (key order and number representation don't matter). */
fun sameJson(a: Any?, b: Any?): Boolean = when {
    a is JSONObject && b is JSONObject ->
        a.length() == b.length() && a.keys().asSequence().all { b.has(it) && sameJson(a.opt(it), b.opt(it)) }
    a is JSONArray && b is JSONArray -> a.length() == b.length() && (0 until a.length()).all { sameJson(a.opt(it), b.opt(it)) }
    a is Number && b is Number -> a.toDouble() == b.toDouble()
    else -> a == b
}

internal fun strings(array: JSONArray?): List<String> =
    if (array == null) emptyList() else (0 until array.length()).mapNotNull { array.opt(it) as? String }

internal fun integer(value: Any?): Int? = (value as? Number)?.toDouble()?.takeIf {
    it.isFinite() && it % 1.0 == 0.0 && it in Int.MIN_VALUE.toDouble()..Int.MAX_VALUE.toDouble()
}?.toInt()
