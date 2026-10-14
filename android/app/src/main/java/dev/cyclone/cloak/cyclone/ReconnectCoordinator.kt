package dev.cyclone.cloak.cyclone

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.random.Random

enum class ReconnectReason { PROCESS_START, RESUME, REGISTRY_CHANGE, MODULE_STATE_CHANGE, SELF_UPDATE, CYCLONE_PACKAGE_CHANGE, MANUAL }
enum class ConnectionPhase { CONNECTED, RECONNECTING, NEEDS_UPDATE, NOT_INSTALLED, NEEDS_APPROVAL, PAUSED }

data class ConnectionStatus(
    val phase: ConnectionPhase = ConnectionPhase.RECONNECTING,
    val attempt: Int = 0,
    val reason: String = "NOT_CHECKED",
    val detail: String = "Checking Cyclone in this Android profile.",
    val cached: CachedRegistry? = null,
    val report: SyncReport? = null,
    val provider: String = "not registered",
) {
    val label: String get() = when (phase) {
        ConnectionPhase.CONNECTED -> "Connected"
        ConnectionPhase.RECONNECTING -> "Reconnecting (attempt ${attempt.coerceAtLeast(1)})"
        ConnectionPhase.NEEDS_UPDATE -> "Needs update"
        ConnectionPhase.NOT_INSTALLED -> "Not installed"
        ConnectionPhase.NEEDS_APPROVAL -> "Needs approval"
        ConnectionPhase.PAUSED -> "Connection paused"
    }
}

data class ReconnectOutcome(val report: SyncReport, val provider: String = "not registered", val installed: Boolean = true) {
    val phase: ConnectionPhase get() = when {
        !installed -> ConnectionPhase.NOT_INSTALLED
        report.failureCode in setOf("CLOAK_NEEDS_UPDATE", "CYCLONE_NEEDS_UPDATE", "HANDSHAKE_INVALID", "UNSUPPORTED_CONTRACT", "CONTRACT_MISMATCH") -> ConnectionPhase.NEEDS_UPDATE
        report.headline == "Needs approval" -> ConnectionPhase.NEEDS_APPROVAL
        report.gate != null && report.gate.minor < 1 -> ConnectionPhase.NEEDS_UPDATE
        report.headline == "Connected" && provider != "failed" -> ConnectionPhase.CONNECTED
        else -> ConnectionPhase.RECONNECTING
    }
    val retryable: Boolean get() = phase == ConnectionPhase.RECONNECTING &&
        report.failureCode !in setOf("SCOPE_NOT_GRANTED", "BAD_REQUEST", "NOT_APPROVED")
    val detail: String get() = when {
        !installed -> "Install or enable Cyclone in this Android profile, then retry."
        report.headline == "Needs approval" -> OpenRequests.APPROVE_LINE
        report.failureCode == "CYCLONE_NOT_FOUND" -> "Cyclone is installed but its connector service is unavailable. Open or update Cyclone, then retry."
        report.gate != null && report.gate.minor < 1 -> "Cyclone connector minor ${report.gate.minor} is too old. Update Cyclone; Cloak needs minor 1 or newer for binding."
        else -> report.message.ifBlank { "Open Cyclone in this Android profile, then retry." }
    }
}

object ReconnectBackoff {
    /** Retry 1 is 250ms, capped at 4s including jitter. Five attempts per trigger cycle. */
    fun delayMs(retry: Int, sample: Double = Random.nextDouble()): Long {
        val base = minOf(3200L, 250L * (1L shl (retry - 1).coerceIn(0, 5)))
        return (base * (0.75 + sample.coerceIn(0.0, 1.0) * 0.5)).toLong().coerceIn(1, 4000)
    }
}

fun interface ReconnectScheduler { fun schedule(delayMs: Long, task: () -> Unit) }

/** Process-owned single flight. Triggers during an attempt produce at most one follow-up. */
class ReconnectCoordinator(
    private val connect: (ReconnectReason) -> ReconnectOutcome,
    private val scheduler: ReconnectScheduler,
    private val now: () -> Long = System::currentTimeMillis,
    private val save: (CachedRegistry) -> Unit = {},
    cache: CachedRegistry? = null,
    private val sample: () -> Double = { Random.nextDouble() },
    private val log: (ReconnectReason, Int, String) -> Unit = { _, _, _ -> },
    private val maxAttempts: Int = 5,
) {
    private val lock = Any()
    private val mutable = MutableStateFlow(ConnectionStatus(cached = cache))
    val state = mutable.asStateFlow()
    private var scheduled = false
    private var running = false
    private var dirty = false
    private var cause = ReconnectReason.PROCESS_START
    private var attempts = 0
    private val settled = mutableListOf<() -> Unit>()

    fun trigger(reason: ReconnectReason, onSettled: (() -> Unit)? = null) = synchronized(lock) {
        if (onSettled != null) {
            if (settled.size < 128) settled.add(onSettled) else onSettled()
        }
        cause = reason
        if (running) { dirty = true; return@synchronized }
        if (scheduled) return@synchronized
        attempts = 0
        enqueue(0)
    }

    private fun enqueue(delay: Long) {
        scheduled = true
        mutable.value = mutable.value.copy(phase = ConnectionPhase.RECONNECTING, attempt = attempts + 1)
        scheduler.schedule(delay, ::attempt)
    }

    private fun attempt() {
        val reason: ReconnectReason
        val number: Int
        synchronized(lock) {
            scheduled = false
            running = true
            dirty = false
            reason = cause
            number = ++attempts
        }
        log(reason, number, "STARTED")
        val result = try { connect(reason) } catch (_: Exception) {
            ReconnectOutcome(SyncReport.failed("INTERNAL", "Cyclone connection failed. Open Cyclone, then retry."))
        }
        val cached = result.report.snapshot?.let { CachedRegistry(it, now()) }
        if (cached != null) runCatching { save(cached) }
        val callbacks: List<() -> Unit>
        synchronized(lock) {
            running = false
            val retry = result.retryable && attempts < maxAttempts
            val phase = if (result.phase == ConnectionPhase.RECONNECTING && !retry) ConnectionPhase.PAUSED else result.phase
            mutable.value = ConnectionStatus(phase, number, result.report.failureCode ?: phase.name,
                result.detail + if (phase == ConnectionPhase.PAUSED) " Automatic retries stopped. Tap Retry after opening Cyclone." else "",
                cached ?: mutable.value.cached, result.report, result.provider)
            log(reason, number, phase.name)
            if (retry) enqueue(ReconnectBackoff.delayMs(number, sample()))
            else if (dirty && result.phase == ConnectionPhase.CONNECTED && attempts < maxAttempts) enqueue(0)
            callbacks = settled.toList()
            settled.clear()
        }
        callbacks.forEach { runCatching(it) }
    }
}

internal fun reconnectScheduler(): ReconnectScheduler {
    val worker = Executors.newSingleThreadScheduledExecutor { task -> Thread(task, "cloak-reconnect").apply { isDaemon = true } }
    return ReconnectScheduler { delay, task -> worker.schedule(task, delay, TimeUnit.MILLISECONDS); Unit }
}
