package dev.cyclone.cloak

import android.content.Context
import android.os.Process
import com.cyclone.connector.client.CycloneConnector
import com.cyclone.connector.client.CycloneConnectorException
import org.json.JSONArray
import org.json.JSONObject

/**
 * Cloak's connection to Cyclone in this Android user (handoff §6.1). Every call runs off the main thread; syncs are
 * serialized so the wake receiver and the app never reconcile at the same time.
 *
 * Each Cyclone profile runs its own Cyclone and its own Cloak. Cloak only ever talks to the Cyclone in its own
 * Android user, so Cloak in Main keeps every profile's bindings, and Cloak in a profile keeps that profile's.
 */
object CycloneBridge {
    private const val PREFS = "cloak"
    private const val KEY_SINCE = "since"
    private const val KEY_LAST_SEQ = "lastSeq"
    private const val KEY_PUSHED = "cyclonePushed"
    private const val KEY_PLACEMENT = "placement"
    private const val KEY_LIVE_USERS = "liveUsers"
    private const val PLACEMENT_MAIN = "main"

    private val lock = Any()

    fun myUser(): Int = Process.myUid() / 100_000

    /**
     * Which share of the root state this install publishes. Until Cyclone has told us where we are, user 0 is Main
     * and any other user publishes only its own apps (never anyone else's).
     */
    fun publishScope(context: Context): PublishScope {
        val prefs = prefs(context)
        val me = myUser()
        val placement = prefs.getString(KEY_PLACEMENT, null)
        val isMain = placement?.let { it == PLACEMENT_MAIN } ?: (me == 0)
        val live = prefs.getString(KEY_LIVE_USERS, null)?.let { text ->
            runCatching { JSONArray(text).let { a -> (0 until a.length()).map { a.getInt(it) } } }.getOrNull()
        }
        return PublishScope(me, isMain, if (isMain) live else null)
    }

    /** Connects, syncs and closes. [foreground]: the owner is looking at Cloak, so a root prompt is acceptable. */
    fun sync(context: Context, foreground: Boolean, withConnection: (CycloneConnector, SyncReport) -> Unit = { _, _ -> }): SyncReport =
        synchronized(lock) {
            val app = context.applicationContext
            try {
                CycloneConnector.connect(app).use { cyclone ->
                    val report = CycloneSyncEngine(ConnectorApi(cyclone), AndroidLocal(app, foreground)).sync()
                    withConnection(cyclone, report)
                    report
                }
            } catch (error: CycloneConnectorException) {
                SyncReport.failed(error.code, error.message)
            } catch (error: Exception) {
                SyncReport.failed("INTERNAL", error.message)
            }
        }

    /** One open request (handoff §5). Never retried: the owner answers on Cyclone's screen. */
    fun requestOpen(context: Context, profile: CycloneProfile): OpenRequests.Outcome = try {
        CycloneConnector.connect(context.applicationContext).use { OpenRequests.ask(ConnectorApi(it), profile) }
    } catch (error: CycloneConnectorException) {
        OpenRequests.outcomeFor(error.code, profile.label)
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private class AndroidLocal(private val context: Context, private val foreground: Boolean) : CloakLocal {
        private val prefs = prefs(context)
        override val myUser: Int = myUser()

        override fun bindings(): List<CloakBinding> = CloakBindingStore.all(context)

        override fun updateBindings(change: (List<CloakBinding>) -> List<CloakBinding>): Boolean =
            CloakBindingStore.update(context, change)

        override fun summaryFor(binding: CloakBinding): JSONObject =
            binding.mirroredSummary?.let { runCatching { JSONObject(it) }.getOrNull() }
                ?: CloakIdentity.summary(binding.cloakProfileId, CloakStore.find(context, binding.cloakProfileId))

        override var cursor: CycloneEvents.Cursor
            get() = CycloneEvents.Cursor(prefs.getLong(KEY_SINCE, 0L), prefs.getLong(KEY_LAST_SEQ, 0L))
            set(value) { prefs.edit().putLong(KEY_SINCE, value.since).putLong(KEY_LAST_SEQ, value.lastSeq).apply() }

        override var pushed: Set<String>?
            get() = prefs.getString(KEY_PUSHED, null)?.let { text ->
                runCatching { JSONArray(text).let { a -> (0 until a.length()).map { a.getString(it) }.toSet() } }.getOrNull()
            }
            set(value) {
                prefs.edit().apply {
                    if (value == null) remove(KEY_PUSHED) else putString(KEY_PUSHED, JSONArray(value.sorted()).toString())
                }.apply()
            }

        override fun savePlacement(placement: Placement, liveUsers: List<Int>) {
            prefs.edit()
                .putString(KEY_PLACEMENT, if (placement is Placement.InProfile) placement.profile.id else PLACEMENT_MAIN)
                .putString(KEY_LIVE_USERS, JSONArray(liveUsers.distinct().sorted()).toString())
                .apply()
        }

        override fun rootAllowed(): Boolean = foreground || CloakRootDoctor.verifiedForCurrentApp(context)

        override fun republish(): RootDoctorResult = CloakResolver.rebuildIndexDetailed(context)

        override fun readPublished(): Pair<RootDoctorResult, String?> = CloakRootDoctor.readPublished(context)
    }
}
