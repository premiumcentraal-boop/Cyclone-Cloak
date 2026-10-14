package dev.cyclone.cloak.root

import java.io.Closeable
import java.io.IOException
import java.util.UUID
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/** One su grant and one deadline for a synchronous batch. Scripts run unchanged in child sh shells. */
class PrivilegedSession(
    budgetMs: Long = 30_000,
    private val waiting: () -> Unit = {},
    private val resumed: () -> Unit = {},
    startProcess: () -> Process = { ProcessBuilder("su", "--mount-master").redirectErrorStream(true).start() },
) : Closeable {
    data class Result(val output: String, val exitCode: Int?, val timedOut: Boolean)
    private val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(budgetMs)
    private val process = startProcess()
    private val writer = process.outputStream.bufferedWriter()
    private val lines = LinkedBlockingQueue<String>(1024)
    private val end = UUID.randomUUID().toString()
    private val reader = Thread {
        try {
            process.inputStream.bufferedReader().use { input ->
                while (true) lines.put(input.readLine() ?: break)
            }
        } catch (_: IOException) {
            // EOF/process closure is represented below, never logged with command contents.
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        } finally { lines.offer(end) }
    }.apply { isDaemon = true; this.start() }

    @Synchronized
    fun execute(script: String, maxOutput: Int = 4096): Result {
        if (System.nanoTime() >= deadline) return Result("", null, true)
        val marker = "CLOAK_SESSION_${UUID.randomUUID().toString().replace("-", "")}:"
        val quoted = "'" + script.replace("'", "'\\''") + "'"
        try {
            writer.write("sh -c $quoted; printf '\\n${marker}%s\\n' \"\$?\"\n")
            writer.flush()
        } catch (_: IOException) { return Result("", 1, false) }
        val output = StringBuilder()
        var announced = false
        val started = System.nanoTime()
        while (true) {
            val remaining = deadline - System.nanoTime()
            if (remaining <= 0) return Result(output.toString().trim(), null, true)
            val line = lines.poll(minOf(remaining, TimeUnit.MILLISECONDS.toNanos(100)), TimeUnit.NANOSECONDS)
            if (line == end) return Result(output.toString().trim(), 1, false)
            if (line?.startsWith(marker) == true) {
                if (announced) resumed()
                return Result(output.toString().trim(), line.removePrefix(marker).toIntOrNull() ?: 1, false)
            }
            if (line != null && output.length < maxOutput) {
                output.append((line + "\n").take(maxOutput - output.length))
            }
            if (!announced && System.nanoTime() - started >= TimeUnit.SECONDS.toNanos(1)) {
                announced = true
                waiting()
            }
        }
    }

    fun preflight(): RootDoctorResult {
        val result = execute("if [ \"\$(id -u)\" = 0 ]; then echo CLOAK_PRIVILEGE_OK; else exit 41; fi")
        return RootDoctorResult(when {
            result.timedOut -> RootDoctorCode.ROOT_TIMEOUT
            result.exitCode == 0 && result.output.lineSequence().any { it == "CLOAK_PRIVILEGE_OK" } -> RootDoctorCode.READY
            else -> RootDoctorCode.ROOT_REQUIRED
        })
    }

    override fun close() {
        runCatching { writer.write("exit\n"); writer.flush(); writer.close() }
        if (!process.waitFor(200, TimeUnit.MILLISECONDS)) process.destroyForcibly()
        reader.interrupt()
    }
}

/** Synchronous scope: sync, publication and mirror calls on this worker reuse the batch session. */
object RootSessions {
    private val active = ThreadLocal<PrivilegedSession>()
    fun current(): PrivilegedSession? = active.get()
    fun <T> use(session: PrivilegedSession, block: () -> T): T {
        check(active.get() == null) { "Root session already active" }
        active.set(session)
        return try { block() } finally { active.remove() }
    }

    fun batch(session: PrivilegedSession, work: () -> Unit): RootDoctorResult = session.use {
        val preflight = session.preflight()
        if (preflight.published) use(session, work)
        preflight
    }

    fun profileHint(user: Int): String? = if (user == 0) null else
        "This Android profile needs its own one-time Magisk approval for Cyclone Cloak. Choose grant until reboot if you prefer fewer prompts."
}
