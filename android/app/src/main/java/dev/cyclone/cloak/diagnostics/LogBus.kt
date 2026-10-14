package dev.cyclone.cloak.diagnostics

import android.app.Application
import android.os.Process
import android.util.Log
import java.io.File
import java.util.UUID

object LogBus {
    @Volatile var registry = DiagnosticsRegistry()
        private set
    @Volatile private var initialized = false

    @Synchronized fun initialize(app: Application) {
        if (initialized) return
        initialized = true
        registry.close()
        registry = DiagnosticsRegistry(File(app.noBackupFilesDir, "diagnostics"), process = Process.myPid().toString(),
            sink = { Log.i("CloakDiagnostics", it.json().toString()) })
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { registry.crash(error) }
            previous?.uncaughtException(thread, error)
        }
        registry.event("startup", "application", Transition.COMPLETED)
    }

    fun event(subsystem: String, action: String, transition: Transition, target: String? = null,
              reason: String? = null, error: Throwable? = null) {
        runCatching { registry.event(subsystem, action, transition, target, reason, error) }
    }

    inline fun <T> trace(subsystem: String, action: String, target: String? = null, block: () -> T): T {
        val id = "$action.${UUID.randomUUID().toString().take(8)}"
        val start = System.nanoTime()
        event(subsystem, id, Transition.STARTED, target)
        try {
            val result = block()
            runCatching { registry.event(subsystem, id, Transition.COMPLETED, target,
                durationMs = (System.nanoTime() - start) / 1_000_000) }
            return result
        } catch (error: Throwable) {
            runCatching { registry.event(subsystem, id, Transition.FAILED, target, "EXCEPTION", error,
                (System.nanoTime() - start) / 1_000_000) }
            throw error
        }
    }
}

class CloakApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        runCatching { LogBus.initialize(this) }
    }
}
