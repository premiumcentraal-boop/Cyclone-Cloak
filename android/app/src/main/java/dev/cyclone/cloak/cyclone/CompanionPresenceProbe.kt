package dev.cyclone.cloak.cyclone

import android.content.Context
import android.os.UserManager
import dev.cyclone.cloak.data.InstallPlacement
import java.util.concurrent.TimeUnit

/** Read-only inspection. Never installs packages, starts users, creates directories or probes by writing files. */
object CompanionPresenceProbe {
    fun local(context: Context): CompanionFacts {
        fun check(value: Boolean) = if (value) PresenceCheck.YES else PresenceCheck.NO
        val unlocked = context.getSystemService(UserManager::class.java).isUserUnlocked
        val directory = java.io.File(context.applicationInfo.dataDir, "files")
        return CompanionFacts(PresenceCheck.YES, check(unlocked),
            check(context.packageManager.getLaunchIntentForPackage(context.packageName) != null),
            if (unlocked) check(directory.isDirectory) else PresenceCheck.UNKNOWN,
            if (unlocked) check(directory.isDirectory && directory.canWrite()) else PresenceCheck.UNKNOWN)
    }

    fun inspect(context: Context, user: Int): CompanionFacts {
        if (user == InstallPlacement.myUser()) return local(context)
        return CompanionPresenceLookup.parse(runScript(script(user)))
    }

    internal fun script(user: Int): String {
        require(user >= 0)
        val pkg = CompanionPresenceLookup.PACKAGE
        // No direct-boot flags: the launcher is usable only after Android unlocks credential-encrypted storage.
        // Root's test -w is deliberately omitted: root access does not prove the app can write its private directory.
        return """
            packages=${'$'}(cmd package list packages --user $user $pkg 2>/dev/null)
            if [ ${'$'}? = 0 ]; then
              if printf '%s\n' "${'$'}packages" | grep -qx 'package:$pkg'; then echo CLOAK_INSTALLED=YES; else echo CLOAK_INSTALLED=NO; fi
            fi
            state=${'$'}(am get-started-user-state $user 2>/dev/null)
            case "${'$'}state" in
              *RUNNING_UNLOCKED*) echo CLOAK_UNLOCKED=YES;;
              *RUNNING_LOCKED*|*RUNNING_UNLOCKING*|*STOPPING*|*SHUTDOWN*|*'User is not started:'*) echo CLOAK_UNLOCKED=NO;;
            esac
            launcher=${'$'}(cmd package resolve-activity --brief --user $user -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -p $pkg 2>/dev/null)
            if [ ${'$'}? = 0 ]; then
              case "${'$'}launcher" in *'$pkg/.MainActivity'*) echo CLOAK_LAUNCHABLE=YES;; *'No activity found'*) echo CLOAK_LAUNCHABLE=NO;; esac
            fi
            case "${'$'}state" in *RUNNING_UNLOCKED*)
              if [ -d /data/user/$user/$pkg/files ]; then echo CLOAK_STORAGE=YES; fi;;
            esac
            echo CLOAK_PRESENCE_END
        """.trimIndent()
    }

    private fun runScript(script: String): String = runCatching {
        val process = ProcessBuilder("su", "--mount-master", "-c", script).redirectErrorStream(true).start()
        val output = StringBuilder()
        val reader = Thread {
            runCatching { process.inputStream.bufferedReader().useLines { lines -> lines.forEach {
                synchronized(output) { if (output.length < 8192) output.appendLine(it.take(512)) }
            } } }
        }.apply { isDaemon = true; start() }
        try {
            process.outputStream.close()
            if (!process.waitFor(8, TimeUnit.SECONDS)) return@runCatching ""
            reader.join(500)
            if (process.exitValue() == 0) synchronized(output) { output.toString() } else ""
        } finally { process.destroyForcibly() }
    }.getOrDefault("")
}
