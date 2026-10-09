package dev.cyclone.cloak

import com.cyclone.connector.client.CycloneConnector
import com.cyclone.connector.client.CycloneConnectorException
import org.json.JSONArray
import org.json.JSONObject

/*
 * Cloak's side of Cyclone's profiles (contract cyclone.connector/1, minor 3; Cyclone 5.0.0-alpha.122).
 * Everything here is pure or talks only to [CycloneApi], so it runs on the JVM against fakes.
 * The Android glue (connect, preferences, root, the wake receiver) is CycloneBridge.
 */

/** The slice of Cyclone's connector API Cloak uses. A refused call throws [CycloneConnectorException]. */
interface CycloneApi {
    fun hello(): JSONObject
    fun profiles(): JSONObject
    fun getConfig(profileId: String, androidUserId: Int, packageName: String): JSONObject
    fun setConfig(profileId: String, androidUserId: Int, packageName: String, value: JSONObject?): JSONObject
    fun configStatus(profileId: String, androidUserId: Int, packageName: String, state: String): JSONObject
    fun events(since: Long): JSONObject
    fun rootStatus(): JSONObject
    fun requestOpenProfile(profileId: String): JSONObject
}

class ConnectorApi(private val cyclone: CycloneConnector) : CycloneApi {
    override fun hello() = cyclone.hello()
    override fun profiles() = cyclone.profiles()
    override fun getConfig(profileId: String, androidUserId: Int, packageName: String) =
        cyclone.getConfig(profileId, androidUserId, packageName)
    override fun setConfig(profileId: String, androidUserId: Int, packageName: String, value: JSONObject?) =
        cyclone.setConfig(profileId, androidUserId, packageName, value)
    override fun configStatus(profileId: String, androidUserId: Int, packageName: String, state: String) =
        cyclone.configStatus(profileId, androidUserId, packageName, state)
    override fun events(since: Long) = cyclone.events(since)
    override fun rootStatus() = cyclone.rootStatus()
    override fun requestOpenProfile(profileId: String) = cyclone.requestOpenProfile(profileId)
}

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

/** Which bindings this install publishes into the shared root state, and for whom. */
data class PublishScope(val androidUserId: Int, val isMain: Boolean, val liveUsers: List<Int>?) {
    /** Main publishes its own bindings for every profile; a profile's Cloak only its own bindings for its own user. */
    fun publishes(binding: CloakBinding): Boolean =
        binding.origin == CloakBinding.ORIGIN_LOCAL && (isMain || binding.androidUserId == androidUserId)

    companion object {
        val MAIN_DEFAULT = PublishScope(0, true, null)
        fun forThisInstall(context: android.content.Context): PublishScope = CycloneBridge.publishScope(context)
    }
}

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

/** One tuple Cyclone should hold for Cloak. */
data class DesiredBinding(
    val profileId: String,
    val androidUserId: Int,
    val packageName: String,
    val value: JSONObject,
    val state: String,
) {
    val tupleKey: String get() = tupleKey(profileId, androidUserId, packageName)
}

fun tupleKey(profileId: String, androidUserId: Int, packageName: String) = "$profileId\n$androidUserId\n$packageName"

/** Why a binding isn't in Cyclone right now, keyed by `profileId\npackageName`. */
fun bindingKey(profileId: String, packageName: String) = "$profileId\n$packageName"

/** What to change locally and what Cyclone should hold, from the profile list and Cloak's own bindings. Pure. */
object ReconcilePlanner {
    data class Plan(
        val desired: List<DesiredBinding>,
        /** Profiles now under a new Android user number: id → new number. Bindings follow (handoff §3.5). */
        val moves: Map<String, Int>,
        /** Profiles permanently deleted from Cyclone (Main only): forget their bindings. */
        val forgotten: Set<String>,
        /** Bindings Cyclone would refuse right now, and why. */
        val issues: Map<String, String>,
        /** Profiles whose apps this install may check for stale Cloak bindings. */
        val sweep: List<CycloneProfile>,
    )

    const val NOT_LISTED = "app not in this profile's list in Cyclone; add it to the profile in Cyclone"

    fun plan(
        placement: Placement,
        snapshot: ProfilesSnapshot,
        bindings: List<CloakBinding>,
        summaryFor: (CloakBinding) -> JSONObject,
    ): Plan {
        val inScope = when (placement) {
            is Placement.Main -> bindings.filter { it.origin == CloakBinding.ORIGIN_LOCAL }
            is Placement.InProfile -> bindings.filter { it.profileId == placement.profile.id }
        }
        val moves = mutableMapOf<String, Int>()
        val forgotten = mutableSetOf<String>()
        val issues = mutableMapOf<String, String>()
        val desired = mutableListOf<DesiredBinding>()
        for (binding in inScope) {
            val profile = snapshot.byId(binding.profileId)
            if (profile == null) {
                if (placement.isMain) forgotten += binding.profileId
                continue
            }
            val user = profile.androidUserId
            if (user != null && user != binding.androidUserId) moves[profile.id] = user
            // Trashed or still setting up: keep the binding, Cyclone keeps its tuple (a restore brings it back).
            if (!binding.enabled || !profile.bindable || user == null) continue
            val packages = profile.packages ?: continue
            if (binding.packageName !in packages) {
                issues[bindingKey(binding.profileId, binding.packageName)] = NOT_LISTED
                continue
            }
            desired += DesiredBinding(
                profileId = profile.id,
                androidUserId = user,
                packageName = binding.packageName,
                value = summaryFor(binding),
                state = CloakHealth.cycloneState(binding.state),
            )
        }
        val sweep = when (placement) {
            is Placement.Main -> snapshot.registry.filter { it.bindable }
            is Placement.InProfile -> listOfNotNull(snapshot.byId(placement.profile.id)?.takeIf { it.bindable })
        }
        return Plan(desired, moves, forgotten, issues, sweep)
    }
}

/**
 * Makes Cyclone hold exactly [desired] for this install: reads each tuple, writes it only when it differs, reports
 * health, and clears tuples Cloak wrote earlier but no longer wants. Gentle: paced under Cyclone's 20 calls a second,
 * one retry on RATE_LIMITED or INTERNAL, never a loop.
 */
class BindingReconciler(
    private val api: CycloneApi,
    private val sleep: (Long) -> Unit = { Thread.sleep(it) },
    private val paceMs: Long = 70,
) {
    data class Result(
        /** Tuples Cyclone now holds for this install (the next run's "previously pushed"). */
        val pushed: Set<String>,
        val issues: Map<String, String>,
        val written: Int,
        val statuses: Int,
        val cleared: Int,
        /** Set when a whole-connector error stopped the run: NOT_APPROVED, SCOPE_NOT_GRANTED or UNKNOWN_METHOD. */
        val stoppedBy: String? = null,
    )

    private class Stop(val code: String) : Exception(code)

    private var calls = 0

    fun reconcile(desired: List<DesiredBinding>, previouslyPushed: Set<String>?, sweep: List<CycloneProfile>): Result {
        val pushed = mutableSetOf<String>()
        val issues = mutableMapOf<String, String>()
        var written = 0
        var statuses = 0
        var cleared = 0
        val wanted = desired.associateBy { it.tupleKey }
        try {
            for (want in desired) {
                val key = bindingKey(want.profileId, want.packageName)
                try {
                    val held = call { api.getConfig(want.profileId, want.androidUserId, want.packageName) }
                    val heldValue = held.optJSONObject("value")
                    if (heldValue == null || !sameJson(heldValue, want.value)) {
                        call { api.setConfig(want.profileId, want.androidUserId, want.packageName, want.value) }
                        written++
                    }
                    if (held.optString("state", CloakHealth.UNKNOWN) != want.state) {
                        call { api.configStatus(want.profileId, want.androidUserId, want.packageName, want.state) }
                        statuses++
                    }
                    pushed += want.tupleKey
                } catch (error: CycloneConnectorException) {
                    issues[key] = when (error.code) {
                        "BAD_REQUEST" -> ReconcilePlanner.NOT_LISTED
                        "NO_SUCH_PROFILE" -> "profile isn't ready in Cyclone"
                        else -> "Cyclone couldn't save it (${error.code}); it will be retried"
                    }
                    // Keep it in the pushed set: Cyclone may still hold an older write we must be able to clear.
                    if (previouslyPushed?.contains(want.tupleKey) == true) pushed += want.tupleKey
                }
            }
            // Tuples written earlier that Cloak no longer wants (unbound, disabled, moved to a new user number).
            for (old in previouslyPushed.orEmpty() - wanted.keys) {
                val (profileId, user, pkg) = splitTuple(old) ?: continue
                try {
                    call { api.setConfig(profileId, user, pkg, null) }
                    cleared++
                } catch (error: CycloneConnectorException) {
                    // NO_SUCH_PROFILE / BAD_REQUEST: Cyclone already dropped or moved it. Anything else: try next time.
                    if (error.code !in setOf("NO_SUCH_PROFILE", "BAD_REQUEST")) pushed += old
                }
            }
            // First run (or lost bookkeeping): look for Cloak bindings Cyclone holds that Cloak doesn't know.
            if (previouslyPushed == null) {
                for (profile in sweep) {
                    val user = profile.androidUserId ?: continue
                    for (pkg in profile.packages.orEmpty()) {
                        val tuple = tupleKey(profile.id, user, pkg)
                        if (tuple in wanted) continue
                        try {
                            val held = call { api.getConfig(profile.id, user, pkg) }
                            if (held.optJSONObject("value")?.opt("cloakProfileId") is String) {
                                call { api.setConfig(profile.id, user, pkg, null) }
                                cleared++
                            }
                        } catch (_: CycloneConnectorException) {
                        }
                    }
                }
            }
        } catch (stop: Stop) {
            return Result(pushed + previouslyPushed.orEmpty(), issues, written, statuses, cleared, stoppedBy = stop.code)
        }
        return Result(pushed, issues, written, statuses, cleared)
    }

    /** Paces every call, retries once on RATE_LIMITED or INTERNAL, and stops the run on whole-connector errors. */
    private fun <T> call(block: () -> T): T {
        if (calls++ > 0) sleep(paceMs)
        return try {
            block()
        } catch (error: CycloneConnectorException) {
            when (error.code) {
                "NOT_APPROVED", "SCOPE_NOT_GRANTED", "UNKNOWN_METHOD", "NOT_A_CONNECTOR" -> throw Stop(error.code)
                "RATE_LIMITED", "INTERNAL" -> {
                    sleep(if (error.code == "RATE_LIMITED") 1_100 else 500)
                    try {
                        block()
                    } catch (again: CycloneConnectorException) {
                        if (again.code in setOf("NOT_APPROVED", "SCOPE_NOT_GRANTED", "UNKNOWN_METHOD")) throw Stop(again.code)
                        throw again
                    }
                }
                else -> throw error
            }
        }
    }

    companion object {
        fun splitTuple(key: String): Triple<String, Int, String>? {
            val parts = key.split('\n')
            if (parts.size != 3) return null
            val user = parts[1].toIntOrNull() ?: return null
            return Triple(parts[0], user, parts[2])
        }
    }
}

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

/** `profiles.open.request.v1` (handoff §5): a question the owner answers on Cyclone's own screen. No retry loop. */
object OpenRequests {
    const val APPROVE_LINE = "Approve Cyclone Cloak in Cyclone → Settings → Connectors in this profile"

    data class Outcome(val message: String, val refreshProfiles: Boolean = false)

    /** Profiles Cloak may ask Cyclone to open from here: ready, not the one in front. Main is offered from a profile. */
    fun targets(snapshot: ProfilesSnapshot, placement: Placement): List<CycloneProfile> = snapshot.profiles.filter { p ->
        p.state == "ready" && p.id != snapshot.current &&
            when (placement) {
                is Placement.Main -> p.kind == "profile" && CycloneProfile.PROFILE_ID.matches(p.id)
                is Placement.InProfile -> p.id != placement.profile.id &&
                    (p.isOwner || (p.kind == "profile" && CycloneProfile.PROFILE_ID.matches(p.id)))
            }
    }

    fun ask(api: CycloneApi, profile: CycloneProfile): Outcome = try {
        val answer = api.requestOpenProfile(profile.id)
        if (answer.optBoolean("requested", false)) {
            Outcome("Cyclone is asking you to open ${profile.label}. Answer on Cyclone's screen.")
        } else {
            Outcome("Cyclone didn't show the question. Try again in a moment.")
        }
    } catch (error: CycloneConnectorException) {
        outcomeFor(error.code, profile.label)
    }

    fun outcomeFor(code: String, label: String): Outcome = when (code) {
        "NO_SUCH_PROFILE" -> Outcome("$label isn't ready in Cyclone. The list is refreshed.", refreshProfiles = true)
        "ALREADY_OPEN" -> Outcome("$label is already open.")
        "BUSY" -> Outcome("Cyclone is busy; try again in a moment.")
        "RATE_LIMITED" -> Outcome("Wait a few seconds before asking again.")
        "NOT_APPROVED" -> Outcome(APPROVE_LINE)
        "SCOPE_NOT_GRANTED" -> Outcome("Allow \"Ask you to open a profile\" for Cyclone Cloak in Cyclone → Settings → Connectors.")
        "UNKNOWN_METHOD" -> Outcome("Update Cyclone to 5.0.0-alpha.122 or newer to open profiles from Cloak.")
        else -> Outcome("Cyclone couldn't answer; try again later.")
    }
}

/**
 * Cloak in a Cyclone profile learns what Cloak in Main bound for it from the published root state: the index entries
 * for its own Android user and profile id. Pure.
 */
object CloakMirror {
    fun fromIndex(indexText: String, profileId: String, androidUserId: Int, mainUser: Int?): List<CloakBinding> {
        val entries = runCatching { JSONObject(indexText).optJSONObject("entries") }.getOrNull() ?: return emptyList()
        val prefix = "$androidUserId/"
        return entries.keys().asSequence().filter { it.startsWith(prefix) }.mapNotNull { key ->
            val entry = entries.optJSONObject(key) ?: return@mapNotNull null
            val packageName = key.removePrefix(prefix)
            if (entry.opt("profileId") != profileId) return@mapNotNull null
            val publisher = integer(entry.opt("publisher"))
            // Only Main's bindings are mirrored; this install's own are already local.
            if (publisher != null && publisher == androidUserId) return@mapNotNull null
            if (mainUser != null && publisher != null && publisher != mainUser) return@mapNotNull null
            val cloakProfileId = (entry.opt("cloakProfileId") as? String)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            CloakBinding(
                profileId = profileId,
                androidUserId = androidUserId,
                packageName = packageName,
                cloakProfileId = cloakProfileId,
                revision = 0,
                enabled = true,
                updatedAt = 0,
                state = "unknown",
                origin = CloakBinding.ORIGIN_MAIN,
                mirroredSummary = entry.optJSONObject("summary")?.toString(),
            )
        }.filter { runCatching { CloakBindingStore.validate(it.profileId, it.androidUserId, it.packageName) }.isSuccess }
            .sortedBy { it.packageName }
            .toList()
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

/** What the sync engine needs from this install: its bindings, its bookkeeping, and root. Android: CycloneBridge. */
interface CloakLocal {
    val myUser: Int
    fun bindings(): List<CloakBinding>
    /** Atomic read-modify-write of the bindings; true when anything changed. */
    fun updateBindings(change: (List<CloakBinding>) -> List<CloakBinding>): Boolean
    fun summaryFor(binding: CloakBinding): JSONObject
    var cursor: CycloneEvents.Cursor
    /** Tuples Cyclone holds for this install; null when never recorded (first run of this version, or data cleared). */
    var pushed: Set<String>?
    fun savePlacement(placement: Placement, liveUsers: List<Int>)
    /** Root may be used now: this install passed Root Doctor, or the owner is looking at Cloak. */
    fun rootAllowed(): Boolean
    /** Publishes this install's share of the root state and refreshes local binding health. */
    fun republish(): RootDoctorResult
    /** Read-only: the root check and the published index (Cloak in a profile mirrors Main's bindings from it). */
    fun readPublished(): Pair<RootDoctorResult, String?>
}

data class SyncReport(
    val headline: String,
    val message: String,
    val gate: CycloneGate? = null,
    val snapshot: ProfilesSnapshot? = null,
    val placement: Placement? = null,
    val rootFacts: Map<String, Boolean?> = emptyMap(),
    /** `profileId\npackageName` → why that binding isn't in Cyclone right now. */
    val issues: Map<String, String> = emptyMap(),
    val reconcile: BindingReconciler.Result? = null,
    val events: CycloneEvents.Batch? = null,
) {
    val approved: Boolean get() = gate?.approved == true

    companion object {
        fun failed(code: String, message: String?) = SyncReport(
            headline = when (code) {
                "CYCLONE_NOT_FOUND" -> "Cyclone not found"
                "CYCLONE_NOT_ANSWERING" -> "Cyclone not answering"
                "NOT_APPROVED" -> "Needs approval"
                else -> "Connector failed"
            },
            message = if (code == "NOT_APPROVED") OpenRequests.APPROVE_LINE else listOfNotNull(code, message).joinToString(": "),
        )
    }
}

/**
 * One sync with Cyclone (handoff §6): hello and gating, events, the profile list, bindings that follow their profile,
 * the mirror of Main's bindings (in a profile), reconcile and health, root facts. Safe to run again at any time.
 */
class CycloneSyncEngine(
    private val api: CycloneApi,
    private val local: CloakLocal,
    private val reconciler: BindingReconciler = BindingReconciler(api),
) {
    fun sync(): SyncReport {
        var gate = try {
            CycloneGate.of(api.hello())
        } catch (error: CycloneConnectorException) {
            return SyncReport.failed(error.code, error.message)
        }
        if (!gate.approved) return SyncReport("Needs approval", OpenRequests.APPROVE_LINE, gate)

        val batch = if (gate.events) {
            runCatching { CycloneEvents.pull(api, local.cursor) }.getOrNull()?.also { local.cursor = it.cursor }
        } else null
        // A switch may have just carried the approval or new scopes: ask again.
        if (batch?.helloAgain == true) gate = runCatching { CycloneGate.of(api.hello()) }.getOrDefault(gate)
        if (!gate.approved) return SyncReport("Needs approval", OpenRequests.APPROVE_LINE, gate, events = batch)
        if (!gate.profiles) {
            return SyncReport("Connected", "Allow \"See your profiles\" for Cyclone Cloak in Cyclone → Settings → Connectors.", gate, events = batch)
        }

        val snapshot = try {
            ProfilesSnapshot.parse(api.profiles())
        } catch (error: CycloneConnectorException) {
            return SyncReport.failed(error.code, error.message).copy(gate = gate, events = batch)
        }
        val placement = Placement.of(local.myUser, snapshot)
        local.savePlacement(placement, snapshot.registry.mapNotNull { it.androidUserId })
        val issues = linkedMapOf<String, String>()
        val notes = mutableListOf<String>()

        if (placement is Placement.InProfile && local.rootAllowed() && mirrorMain(placement)) {
            // Main now binds an app this install had bound itself: drop it from this install's share too.
            local.republish()
        }

        var plan = ReconcilePlanner.plan(placement, snapshot, local.bindings(), local::summaryFor)
        val forget = plan.forgotten + batch?.removed.orEmpty().filter { placement.isMain || it == (placement as? Placement.InProfile)?.profile?.id }
        if (plan.moves.isNotEmpty() || forget.isNotEmpty()) {
            val changed = local.updateBindings { current ->
                var next = current
                plan.moves.forEach { (id, user) -> next = CloakBindingStore.moveUser(next, id, user) }
                forget.forEach { next = CloakBindingStore.forgetProfile(next, it) }
                next
            }
            if (changed) {
                // The module looks bindings up by Android user number: publish again so they follow the profile.
                if (local.rootAllowed()) {
                    val result = local.republish()
                    if (!result.published) notes += "Moved bindings wait for Root Doctor: ${result.title}"
                } else {
                    notes += "Open Cyclone Cloak and run Root Doctor so moved or removed profiles apply."
                }
                plan = ReconcilePlanner.plan(placement, snapshot, local.bindings(), local::summaryFor)
            }
        }
        issues += plan.issues

        var reconcile: BindingReconciler.Result? = null
        if (gate.bindings) {
            reconcile = reconciler.reconcile(plan.desired, local.pushed, plan.sweep)
            local.pushed = reconcile.pushed
            issues += reconcile.issues
            when (reconcile.stoppedBy) {
                null -> Unit
                "NOT_APPROVED" -> return SyncReport("Needs approval", OpenRequests.APPROVE_LINE, gate, snapshot, placement, events = batch)
                else -> notes += "Cyclone stopped the binding update (${reconcile.stoppedBy})."
            }
        } else {
            val missing = listOf("profiles.read", "profiles.apps.read", "profiles.config").filter { it !in gate.granted }
            notes += if (gate.minor < 1) "Update Cyclone to load Cloak profiles onto Cyclone profiles."
            else "Approve ${missing.joinToString(", ")} for Cyclone Cloak in Cyclone → Settings → Connectors to load profiles."
        }

        val rootFacts = if (gate.rootStatus) {
            runCatching { CloakPills.rootFacts(api.rootStatus()) }.getOrDefault(emptyMap())
        } else emptyMap()

        val where = when (placement) {
            is Placement.Main -> "Main"
            is Placement.InProfile -> placement.profile.label
        }
        val message = (listOf("In $where · Cyclone minor ${gate.minor}") + gate.pending.takeIf { it.isNotEmpty() }
            ?.let { listOf("Waiting for approval: ${it.sorted().joinToString(", ")}") }.orEmpty() + notes).joinToString("\n")
        return SyncReport("Connected", message, gate, snapshot, placement, rootFacts, issues, reconcile, batch)
    }

    /**
     * Cloak in a profile takes over what Cloak in Main bound for this profile, so this profile's Cyclone shows it too.
     * Returns true when Main's binding displaced one this install had made (Main wins an app both bound).
     */
    private fun mirrorMain(placement: Placement.InProfile): Boolean {
        val (result, index) = local.readPublished()
        if (index != null) {
            val mirrored = CloakMirror.fromIndex(index, placement.profile.id, local.myUser, mainUser = null)
                .map { it.copy(state = CloakResolver.localState(result)) }
            val displaced = local.bindings().any { own ->
                own.origin == CloakBinding.ORIGIN_LOCAL && mirrored.any(own::sameAs)
            }
            local.updateBindings { CloakBindingStore.replaceMirrored(it, mirrored) }
            return displaced
        } else {
            // Couldn't read: keep the last mirror, and say why it may not apply.
            val state = CloakResolver.localState(result)
            local.updateBindings { current ->
                current.map { if (it.origin == CloakBinding.ORIGIN_MAIN && it.state != state) it.copy(state = state) else it }
            }
            return false
        }
    }
}
