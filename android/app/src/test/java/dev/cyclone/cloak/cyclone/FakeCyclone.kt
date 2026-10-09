package dev.cyclone.cloak.cyclone

import dev.cyclone.cloak.data.*
import dev.cyclone.cloak.forge.*
import dev.cyclone.cloak.root.*
import com.cyclone.connector.client.CycloneConnectorException
import org.json.JSONArray
import org.json.JSONObject

/**
 * A stateful Cyclone for JVM tests. Config calls are checked the way Cyclone 5.0.0-alpha.122 checks them
 * (ProfileConfigKey.parse): a ready profile, the profile's own integer user number, a package in its list, never Main.
 * Stored envelopes have Cyclone's shape: `{profileId, androidUserId, packageName, value, state}`.
 */
class FakeCyclone(
    var granted: Set<String> = ALL_SCOPES,
    var minor: Int = 3,
    var approved: Boolean = true,
) : CycloneApi {
    data class Profile(val id: String, val label: String, val user: Int?, val packages: List<String>, val state: String = "ready")

    val profiles = mutableListOf<Profile>()
    var current: String? = "owner"
    /** storageKey-equivalent tuple → stored envelope. */
    val config = linkedMapOf<String, JSONObject>()
    val journal = mutableListOf<JSONObject>()
    var journalBase = 0L
    val calls = mutableListOf<String>()
    /** Errors to throw on upcoming calls of a method, in order. */
    val failNext = mutableMapOf<String, ArrayDeque<String>>()
    var rootProven: Map<String, Boolean?> = emptyMap()
    var openAnswer: () -> JSONObject = { JSONObject().put("version", 1).put("requested", true) }

    private fun check(method: String, scope: String?) {
        calls += method
        failNext[method]?.removeFirstOrNull()?.let { throw CycloneConnectorException(it, "scripted") }
        if (method != "hello" && !approved) throw CycloneConnectorException("NOT_APPROVED", "approve")
        if (scope != null && scope !in granted) throw CycloneConnectorException("SCOPE_NOT_GRANTED", scope)
    }

    override fun hello(): JSONObject {
        check("hello", null)
        return JSONObject().put("contract", "cyclone.connector/1").put("minor", minor).put("connectorId", "cyclone-cloak")
            .put("approved", approved).put("granted", JSONArray(if (approved) granted.sorted() else emptyList()))
            .put("pending", JSONArray()).put("profileSchema", 2)
    }

    override fun profiles(): JSONObject {
        check("profiles", "profiles.read")
        val list = JSONArray().put(JSONObject().put("id", "owner").put("label", "This phone").put("kind", "owner").put("state", "ready"))
        for (p in profiles) {
            val o = JSONObject().put("id", p.id).put("label", p.label).put("kind", "profile").put("state", p.state)
                .put("androidUserId", p.user ?: JSONObject.NULL).put("appCount", p.packages.size)
            if ("profiles.apps.read" in granted) o.put("packages", JSONArray(p.packages))
            list.put(o)
        }
        return JSONObject().put("schemaVersion", 2).put("current", current ?: JSONObject.NULL).put("profiles", list)
    }

    private fun tuple(profileId: String, user: Int, pkg: String): String {
        val profile = profiles.singleOrNull { it.id == profileId && it.user == user && it.state == "ready" }
            ?: throw CycloneConnectorException("NO_SUCH_PROFILE", "no ready profile")
        if (pkg !in profile.packages) throw CycloneConnectorException("BAD_REQUEST", "package not in profile")
        return tupleKey(profileId, user, pkg)
    }

    private fun answer(key: String, profileId: String, user: Int, pkg: String): JSONObject {
        val stored = config[key]
        return JSONObject().put("version", 1).put("profileId", profileId).put("androidUserId", user).put("packageName", pkg)
            .put("storageKey", key.hashCode().toString()).put("value", stored?.opt("value") ?: JSONObject.NULL)
            .put("state", stored?.optString("state", "unknown") ?: "unknown")
    }

    private fun envelope(key: String, profileId: String, user: Int, pkg: String) = config.getOrPut(key) {
        JSONObject().put("profileId", profileId).put("androidUserId", user).put("packageName", pkg)
            .put("value", JSONObject.NULL).put("state", "unknown")
    }

    override fun getConfig(profileId: String, androidUserId: Int, packageName: String): JSONObject {
        check("config.get.v1", "profiles.config")
        val key = tuple(profileId, androidUserId, packageName)
        return answer(key, profileId, androidUserId, packageName)
    }

    override fun setConfig(profileId: String, androidUserId: Int, packageName: String, value: JSONObject?): JSONObject {
        check("config.set.v1", "profiles.config")
        val key = tuple(profileId, androidUserId, packageName)
        if (value != null && value.toString().toByteArray().size > 4096) throw CycloneConnectorException("BAD_REQUEST", "too big")
        envelope(key, profileId, androidUserId, packageName).put("value", value?.let { JSONObject(it.toString()) } ?: JSONObject.NULL)
        return answer(key, profileId, androidUserId, packageName)
    }

    override fun configStatus(profileId: String, androidUserId: Int, packageName: String, state: String): JSONObject {
        check("config.status.v1", "profiles.config")
        val key = tuple(profileId, androidUserId, packageName)
        require(state in setOf("unknown", "ready", "degraded", "failed")) { "bad state $state" }
        envelope(key, profileId, androidUserId, packageName).put("state", state)
        return answer(key, profileId, androidUserId, packageName)
    }

    fun emit(type: String, profileId: String) {
        journal += JSONObject().put("seq", journalBase + journal.size + 1).put("type", type).put("profileId", profileId).put("at", 1)
    }

    override fun events(since: Long): JSONObject {
        check("events", "events.profiles")
        val page = journal.filter { it.getLong("seq") > since }.take(2)
        val next = page.lastOrNull()?.getLong("seq") ?: maxOf(since.coerceAtMost(journalBase + journal.size), 0)
        val more = journal.any { it.getLong("seq") > next }
        return JSONObject().put("events", JSONArray(page)).put("next", next).put("reset", false).put("more", more)
    }

    override fun rootStatus(): JSONObject {
        check("root.status.v1", "device.root.read")
        if (minor < 2) throw CycloneConnectorException("UNKNOWN_METHOD", "old")
        val list = JSONArray()
        rootProven.forEach { (id, proven) -> list.put(JSONObject().put("id", id).put("rootProven", proven ?: JSONObject.NULL)) }
        return JSONObject().put("version", 1).put("rootManager", "magisk").put("profileRoom", JSONObject.NULL).put("profiles", list)
    }

    override fun requestOpenProfile(profileId: String): JSONObject {
        check("profiles.open.request.v1", "profiles.open.request")
        return openAnswer()
    }

    /** Bound apps as Cyclone's strict reader sees them (CycloneCloakProfileBinding.readBindings). */
    fun readBindings(): List<Pair<String, String>> = profiles.filter { it.state == "ready" && it.user != null }.flatMap { p ->
        p.packages.mapNotNull { pkg ->
            val stored = config[tupleKey(p.id, p.user!!, pkg)] ?: return@mapNotNull null
            CycloneReader.cloakProfileId(stored, p.id, p.user, pkg)?.let { "${p.id}/$pkg" to it }
        }
    }

    fun stateOf(profileId: String, user: Int, pkg: String): String? = config[tupleKey(profileId, user, pkg)]?.optString("state")

    companion object {
        val ALL_SCOPES = setOf(
            "profiles.read", "profiles.apps.read", "profiles.config", "events.profiles", "device.root.read",
            "profiles.open.request", "profiles.startup", "selector.contribute",
        )
    }
}

/** A port of Cyclone 5.0.0-alpha.122's strict reader (CycloneCloakProfileBinding.envelope + identitySnapshot). */
object CycloneReader {
    private fun jsonInt(value: Any?): Int? = (value as? Number)?.toDouble()?.takeIf {
        it.isFinite() && it % 1.0 == 0.0
    }?.toInt()

    private fun clean(value: Any?, limit: Int): String? = (value as? String)?.trim()
        ?.replace(Regex("[\\p{Cntrl}]+"), " ")?.replace(Regex("\\s+"), " ")?.take(limit)?.takeIf { it.isNotBlank() }

    fun value(envelope: JSONObject, profileId: String, user: Int, pkg: String): JSONObject? {
        if (envelope.opt("profileId") != profileId || jsonInt(envelope.opt("androidUserId")) != user ||
            envelope.opt("androidUserId") !is Number || envelope.opt("packageName") != pkg
        ) return null
        return envelope.optJSONObject("value")
    }

    fun cloakProfileId(envelope: JSONObject, profileId: String, user: Int, pkg: String): String? =
        value(envelope, profileId, user, pkg)?.let { clean(it.opt("cloakProfileId"), 160) }

    /** The version-1 summary Cyclone and Glass show, or null when bound without a readable summary. */
    fun summary(envelope: JSONObject, profileId: String, user: Int, pkg: String): Map<String, Any?>? {
        val value = value(envelope, profileId, user, pkg) ?: return null
        if (clean(value.opt("cloakProfileId"), 160) == null) return null
        if (jsonInt(value.opt("identityVersion")) != 1) return null
        return mapOf(
            "name" to clean(value.opt("name"), 80),
            "manufacturer" to clean(value.opt("manufacturer"), 80),
            "model" to clean(value.opt("model"), 80),
            "androidRelease" to clean(value.opt("androidRelease"), 40),
            "sdkInt" to jsonInt(value.opt("sdkInt"))?.takeIf { it in 1..1000 },
        )
    }
}

/** An in-memory [CloakLocal]. */
class FakeLocal(override val myUser: Int, bindings: List<CloakBinding> = emptyList()) : CloakLocal {
    var store: List<CloakBinding> = bindings
    var root = true
    var republished = 0
    var publishResult = RootDoctorResult(RootDoctorCode.READY)
    var published: Pair<RootDoctorResult, String?> = RootDoctorResult(RootDoctorCode.READY) to null
    var placement: Placement? = null
    var liveUsers: List<Int> = emptyList()
    val profilesById = mutableMapOf<String, JSONObject>()

    override fun bindings() = store
    override fun updateBindings(change: (List<CloakBinding>) -> List<CloakBinding>): Boolean {
        val next = change(store)
        val changed = next != store
        store = next
        return changed
    }
    override fun summaryFor(binding: CloakBinding): JSONObject =
        binding.mirroredSummary?.let(::JSONObject) ?: CloakIdentity.summary(binding.cloakProfileId, profilesById[binding.cloakProfileId])
    override var cursor = CycloneEvents.Cursor(0, 0)
    override var pushed: Set<String>? = null
    override fun savePlacement(placement: Placement, liveUsers: List<Int>) {
        this.placement = placement
        this.liveUsers = liveUsers
    }
    override fun rootAllowed() = root
    override fun republish(): RootDoctorResult {
        republished++
        store = store.map { if (it.origin == CloakBinding.ORIGIN_LOCAL) it.copy(state = CloakResolver.localState(publishResult)) else it }
        return publishResult
    }
    override fun readPublished() = published
}

fun binding(
    profileId: String,
    user: Int,
    pkg: String,
    cloak: String = "pixel-8-work",
    state: String = "ready",
    enabled: Boolean = true,
    origin: String = CloakBinding.ORIGIN_LOCAL,
) = CloakBinding(profileId, user, pkg, cloak, 0, enabled, 1, state, origin)
