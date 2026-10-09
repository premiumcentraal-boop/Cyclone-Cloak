package dev.cyclone.cloak.cyclone

import dev.cyclone.cloak.data.*
import dev.cyclone.cloak.root.*
import com.cyclone.connector.client.CycloneConnectorException
import org.json.JSONObject

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
