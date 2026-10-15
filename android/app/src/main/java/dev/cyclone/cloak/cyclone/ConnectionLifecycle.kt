package dev.cyclone.cloak.cyclone

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import java.util.concurrent.atomic.AtomicBoolean
import com.cyclone.connector.client.CycloneConnector
import dev.cyclone.cloak.diagnostics.LogBus
import dev.cyclone.cloak.diagnostics.Transition
import kotlinx.coroutines.flow.StateFlow

/** Application-owned: activity recreation and broadcast bursts share the same retry cycle. */
object ConnectionLifecycle {
    @Volatile private var coordinator: ReconnectCoordinator? = null

    @Synchronized private fun get(context: Context): ReconnectCoordinator {
        coordinator?.let { return it }
        val app = context.applicationContext
        val cache = RegistryCache(app)
        return ReconnectCoordinator(connect = { _ ->
            val installed = CycloneConnector.isCycloneInstalled(app)
            var provider = "not registered"
            var report = if (!installed) SyncReport.failed("CYCLONE_NOT_FOUND", "Install Cyclone in this Android profile.")
                // Reconnection never reopens a root approval window, even on resume/manual retry.
                else CycloneBridge.sync(app, foreground = false, allowRoot = false) { cyclone, result ->
                    val gate = result.gate
                    provider = when {
                        gate?.approved != true -> "not registered"
                        gate.minor < 1 -> "Cyclone too old"
                        !gate.startup -> "not approved"
                        else -> runCatching { cyclone.registerProfileProvider(CloakStartupProvider(app)) }
                            .fold({ "ready" }, { "failed" })
                    }
                }
            if (provider == "failed") report = report.copy(failureCode = "PROVIDER_REGISTRATION_FAILED",
                message = "Cyclone answered, but the startup callback could not be registered. Reconnecting will retry registration.")
            ReconnectOutcome(report, provider, installed)
        }, scheduler = reconnectScheduler(), cache = cache.read(), save = cache::save,
            log = { reason, attempt, outcome ->
                LogBus.event("connector", "reconnect.${reason.name.lowercase()}.$attempt",
                    when (outcome) { "STARTED" -> Transition.STARTED; "CONNECTED" -> Transition.COMPLETED; else -> Transition.DEGRADED },
                    reason = if (outcome == "STARTED") reason.name else outcome)
            }).also { coordinator = it }
    }

    fun state(context: Context): StateFlow<ConnectionStatus> = get(context).state
    fun trigger(context: Context, reason: ReconnectReason) = get(context).trigger(reason)

    fun wake(context: Context, reason: ReconnectReason, finish: () -> Unit) {
        val finished = AtomicBoolean()
        val handler = Handler(Looper.getMainLooper())
        val complete = Runnable { if (finished.compareAndSet(false, true)) finish() }
        // Bound broadcast lifetime; later retries are best-effort while the process remains alive.
        handler.postDelayed(complete, 8_000)
        get(context).trigger(reason) { handler.removeCallbacks(complete); complete.run() }
    }
}

/** Only Android protected package broadcasts; registry notifications keep the existing protected wake receiver. */
class ConnectorPackageReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            val pending = goAsync()
            ConnectionLifecycle.wake(context, ReconnectReason.SELF_UPDATE, pending::finish)
        } else if (intent.action in setOf(Intent.ACTION_PACKAGE_ADDED, Intent.ACTION_PACKAGE_REPLACED,
                Intent.ACTION_PACKAGE_CHANGED, Intent.ACTION_PACKAGE_REMOVED) &&
            intent.data?.schemeSpecificPart == CycloneConnector.CYCLONE_PACKAGE) {
            val pending = goAsync()
            ConnectionLifecycle.wake(context, ReconnectReason.CYCLONE_PACKAGE_CHANGE, pending::finish)
        }
    }
}
