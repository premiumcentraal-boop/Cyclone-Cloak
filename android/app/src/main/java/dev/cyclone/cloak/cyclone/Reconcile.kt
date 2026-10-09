package dev.cyclone.cloak.cyclone

import dev.cyclone.cloak.data.*
import com.cyclone.connector.client.CycloneConnectorException
import org.json.JSONObject

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
