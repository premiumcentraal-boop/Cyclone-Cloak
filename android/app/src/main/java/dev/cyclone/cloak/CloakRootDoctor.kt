package dev.cyclone.cloak

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.zip.ZipFile

enum class RootDoctorCode {
    NOT_CHECKED,
    CHECKING,
    READY,
    ROOT_REQUIRED,
    ROOT_TIMEOUT,
    ROOT_UNAVAILABLE,
    MODULE_MISSING,
    MODULE_DISABLED,
    MODULE_PENDING_REMOVAL,
    MODULE_REBOOT_REQUIRED,
    MODULE_OUTDATED,
    MODULE_BUNDLE_INVALID,
    MODULE_INSTALL_FAILED,
    ZYGISK_DISABLED,
    ZYGISK_STATUS_UNKNOWN,
    ABI_UNSUPPORTED,
    PUBLISH_FAILED,
}

data class RootDoctorResult(
    val code: RootDoctorCode,
    val moduleVersion: String? = null,
    val diagnostic: String? = null,
) {
    val published: Boolean get() = code == RootDoctorCode.READY

    val title: String get() = when (code) {
        RootDoctorCode.NOT_CHECKED -> "Not checked"
        RootDoctorCode.CHECKING -> "Checking…"
        RootDoctorCode.READY -> "Ready"
        RootDoctorCode.ROOT_REQUIRED -> "Approve in Magisk"
        RootDoctorCode.ROOT_TIMEOUT -> "Approval timed out"
        RootDoctorCode.ROOT_UNAVAILABLE -> "Root unavailable"
        RootDoctorCode.MODULE_MISSING -> "Module missing"
        RootDoctorCode.MODULE_DISABLED -> "Module disabled"
        RootDoctorCode.MODULE_PENDING_REMOVAL -> "Removal pending"
        RootDoctorCode.MODULE_REBOOT_REQUIRED -> "Reboot required"
        RootDoctorCode.MODULE_OUTDATED -> "Module outdated"
        RootDoctorCode.MODULE_BUNDLE_INVALID -> "Module package invalid"
        RootDoctorCode.MODULE_INSTALL_FAILED -> "Module install failed"
        RootDoctorCode.ZYGISK_DISABLED -> "Zygisk disabled"
        RootDoctorCode.ZYGISK_STATUS_UNKNOWN -> "Zygisk status unknown"
        RootDoctorCode.ABI_UNSUPPORTED -> "ABI unsupported"
        RootDoctorCode.PUBLISH_FAILED -> "Publish failed"
    }

    val message: String get() {
        val base = when (code) {
            RootDoctorCode.NOT_CHECKED -> "Check Magisk access, Zygisk, the installed module, and profile publishing together. The matching module is bundled with this app and can be scheduled for repair."
            RootDoctorCode.CHECKING -> "Approve Cyclone Cloak in the Magisk prompt to finish the check."
            RootDoctorCode.READY -> "Magisk access, module version, and rooted profile publishing are working."
            RootDoctorCode.ROOT_REQUIRED -> "Open Magisk → Superuser, allow Cyclone Cloak, then run the check again."
            RootDoctorCode.ROOT_TIMEOUT -> "The Magisk prompt expired. Run the check again and approve the prompt right away."
            RootDoctorCode.ROOT_UNAVAILABLE -> "Install or open Magisk, enable its superuser service, then try again."
            RootDoctorCode.MODULE_MISSING -> "Install the Cyclone Cloak module ZIP from this release in Magisk, then restart."
            RootDoctorCode.MODULE_DISABLED -> "Enable Cyclone Cloak in Magisk → Modules, then restart the device."
            RootDoctorCode.MODULE_PENDING_REMOVAL -> "Magisk has marked the module for removal. Reinstall this release's ZIP and restart."
            RootDoctorCode.MODULE_REBOOT_REQUIRED -> "The matching module is queued in Magisk${moduleVersion?.let { " ($it)" }.orEmpty()}. Restart the device, then run Root Doctor again to publish your profile state."
            RootDoctorCode.MODULE_OUTDATED -> "The installed module${moduleVersion?.let { " ($it)" }.orEmpty()} does not match this app. Root Doctor will install the matching version and ask you to restart."
            RootDoctorCode.MODULE_BUNDLE_INVALID -> "The module bundled with this app did not pass its identity, version, or device-architecture check. Use Get module ZIP to install the release module manually."
            RootDoctorCode.MODULE_INSTALL_FAILED -> "Magisk couldn't schedule the matching module. Review the detail below, or use Get module ZIP to install it manually."
            RootDoctorCode.ZYGISK_DISABLED -> "Enable Zygisk in Magisk → Settings and restart the device before using rooted profile routes."
            RootDoctorCode.ZYGISK_STATUS_UNKNOWN -> "Root Doctor couldn't read Magisk's Zygisk setting. Open Magisk → Settings, check that Zygisk is enabled, and restart if you changed it."
            RootDoctorCode.ABI_UNSUPPORTED -> "This release has no Zygisk library for this device's Android architecture."
            RootDoctorCode.PUBLISH_FAILED -> "Root access worked, but profile state could not be published."
        }
        return diagnostic?.takeIf { code in setOf(RootDoctorCode.PUBLISH_FAILED, RootDoctorCode.MODULE_BUNDLE_INVALID, RootDoctorCode.MODULE_INSTALL_FAILED) }
            ?.let { "$base\nDetail: $it" } ?: base
    }
}

/** Checks Magisk, repairs the matching module when asked, then publishes profile state. */
object CloakRootDoctor {
    private val supportedAbis = setOf("arm64-v8a", "armeabi-v7a")
    private const val preferencesName = "cloak-root-doctor"
    private const val verifiedVersionKey = "verified-app-version"
    private const val maxModuleZipBytes = 32L * 1024L * 1024L
    private const val maxModuleContentsBytes = 32L * 1024L * 1024L

    fun verifiedForCurrentApp(context: Context): Boolean {
        @Suppress("DEPRECATION")
        val appVersion = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull()
        return appVersion != null &&
            context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
                .getString(verifiedVersionKey, null) == appVersion
    }

    /**
     * The explicit Root Doctor action may repair this app's module. Binding changes only verify and publish,
     * so routine UI actions never install software.
     */
    fun run(context: Context, staging: File, repairModule: Boolean = false): RootDoctorResult {
        val abi = Build.SUPPORTED_ABIS.firstOrNull { it in supportedAbis }
            ?: return rememberResult(context, RootDoctorResult(RootDoctorCode.ABI_UNSUPPORTED), null)
        @Suppress("DEPRECATION")
        val appVersion = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull().orEmpty()
        if (!appVersion.matches(Regex("[A-Za-z0-9._-]+"))) {
            return rememberResult(context, RootDoctorResult(RootDoctorCode.PUBLISH_FAILED), null)
        }

        val legacyDirs = CloakBindingStore.all(context)
            .map { it.androidUserId }.distinct()
            .map { "/data/user/$it/dev.cyclone.cloak/no_backup/${CloakStateLayout.ROOT_DIR}" }
        val shell = try {
            runRootScript(publishScript(staging, abi, appVersion, legacyDirs), 25_000)
        } catch (error: Exception) {
            Log.e("CloakRootDoctor", "Could not run root check", error)
            return rememberResult(context, RootDoctorResult(
                if (error is IOException) RootDoctorCode.ROOT_UNAVAILABLE else RootDoctorCode.PUBLISH_FAILED,
                diagnostic = error.message?.take(180),
            ), appVersion)
        }
        if (shell.timedOut) {
            Log.w("CloakRootDoctor", "Root check timed out")
            return rememberResult(context, RootDoctorResult(RootDoctorCode.ROOT_TIMEOUT), appVersion)
        }

        val initial = parseResult(shell.output, shell.exitCode ?: 1)
        Log.i("CloakRootDoctor", "Root check ${initial.code}; exit=${shell.exitCode}; output=${shell.output}")
        if (repairModule && initial.code in setOf(
                RootDoctorCode.MODULE_MISSING,
                RootDoctorCode.MODULE_DISABLED,
                RootDoctorCode.MODULE_PENDING_REMOVAL,
                RootDoctorCode.MODULE_OUTDATED,
            )
        ) {
            val repaired = installMatchingModule(context, appVersion, abi)
            return rememberResult(context, repaired, null)
        }
        return rememberResult(context, initial, appVersion)
    }

    private fun installMatchingModule(context: Context, appVersion: String, abi: String): RootDoctorResult {
        val archive = try {
            stageBundledModule(context, appVersion, abi)
        } catch (error: Exception) {
            Log.w("CloakRootDoctor", "Could not stage bundled module", error)
            return RootDoctorResult(RootDoctorCode.MODULE_BUNDLE_INVALID, diagnostic = error.message?.take(180))
        }
        val shell = try {
            runRootScript(installModuleScript(archive, appVersion), 120_000)
        } catch (error: Exception) {
            Log.e("CloakRootDoctor", "Could not install matching module", error)
            return RootDoctorResult(RootDoctorCode.MODULE_INSTALL_FAILED, diagnostic = error.message?.take(180))
        }
        if (shell.timedOut) {
            return RootDoctorResult(
                RootDoctorCode.MODULE_INSTALL_FAILED,
                diagnostic = "Magisk did not finish within two minutes. Check Magisk → Modules before trying again.",
            )
        }
        val result = parseInstallResult(shell.output, shell.exitCode ?: 1, appVersion)
        Log.i("CloakRootDoctor", "Module repair ${result.code}; exit=${shell.exitCode}; output=${shell.output}")
        return result
    }

    private fun stageBundledModule(context: Context, appVersion: String, abi: String): File {
        val archive = File(context.cacheDir, "cyclone-cloak-$appVersion.zip")
        if (archive.isFile && validateModuleArchive(archive, appVersion, abi)) return archive
        archive.delete()
        val partial = File(context.cacheDir, "cyclone-cloak-$appVersion.zip.copy")
        partial.delete()
        try {
            context.assets.open("cyclone-cloak-module.zip").use { input ->
                partial.outputStream().buffered().use { output ->
                    val buffer = ByteArray(8192)
                    var total = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        if (total > maxModuleZipBytes) throw IOException("The module archive is larger than expected.")
                        output.write(buffer, 0, count)
                    }
                }
            }
            if (!validateModuleArchive(partial, appVersion, abi)) {
                throw IOException("The bundled ZIP does not contain the matching Cyclone Cloak module and device library.")
            }
            if (!partial.renameTo(archive)) throw IOException("The verified module archive could not be copied to app cache for Magisk.")
            return archive
        } catch (error: Exception) {
            partial.delete()
            throw error
        }
    }

    internal fun validateModuleArchive(archive: File, appVersion: String, abi: String): Boolean = runCatching {
        if (!archive.isFile || archive.length() !in 1..maxModuleZipBytes) return false
        ZipFile(archive).use { zip ->
            var expandedBytes = 0L
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                val name = entry.name.replace('\\', '/')
                if (name.startsWith('/') || name.split('/').any { it == ".." }) return false
                if (entry.size < 0) return false
                expandedBytes += entry.size
                if (expandedBytes > maxModuleContentsBytes) return false
            }
            val props = zip.getEntry("module.prop") ?: return false
            if (props.isDirectory || props.size !in 1..16_384) return false
            val properties = zip.getInputStream(props).bufferedReader().use { it.readText() }
                .lineSequence()
                .mapNotNull { line ->
                    val split = line.indexOf('=')
                    if (split <= 0) null else line.substring(0, split).trim() to line.substring(split + 1).trim()
                }
                .toMap()
            if (properties["id"] != "cyclone_cloak" || properties["version"] != "v$appVersion") return false
            val library = zip.getEntry("zygisk/$abi.so") ?: return false
            !library.isDirectory && library.size > 0
        }
    }.getOrDefault(false)

    private fun rememberResult(context: Context, result: RootDoctorResult, appVersion: String?): RootDoctorResult {
        val preferences = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE).edit()
        if (result.published && appVersion != null) preferences.putString(verifiedVersionKey, appVersion)
        else preferences.remove(verifiedVersionKey)
        preferences.apply()
        return result
    }

    internal fun parseResult(output: String, exitCode: Int): RootDoctorResult {
        val marker = Regex("CLOAK_DOCTOR=([A-Z_]+)").find(output)?.groupValues?.get(1)
        val moduleVersion = Regex("CLOAK_MODULE_VERSION=([^\\r\\n]+)").find(output)?.groupValues?.get(1)?.trim()
        val code = when (marker) {
            "READY" -> RootDoctorCode.READY
            "ROOT_REQUIRED" -> RootDoctorCode.ROOT_REQUIRED
            "MODULE_MISSING" -> RootDoctorCode.MODULE_MISSING
            "MODULE_DISABLED" -> RootDoctorCode.MODULE_DISABLED
            "MODULE_PENDING_REMOVAL" -> RootDoctorCode.MODULE_PENDING_REMOVAL
            "MODULE_REBOOT_REQUIRED" -> RootDoctorCode.MODULE_REBOOT_REQUIRED
            "MODULE_OUTDATED" -> RootDoctorCode.MODULE_OUTDATED
            "ZYGISK_DISABLED" -> RootDoctorCode.ZYGISK_DISABLED
            "ZYGISK_STATUS_UNKNOWN" -> RootDoctorCode.ZYGISK_STATUS_UNKNOWN
            "ABI_UNSUPPORTED" -> RootDoctorCode.ABI_UNSUPPORTED
            "PUBLISH_FAILED" -> RootDoctorCode.PUBLISH_FAILED
            else -> when {
                output.contains("CLOAK_ROOT=OK") -> RootDoctorCode.PUBLISH_FAILED
                exitCode != 0 -> RootDoctorCode.ROOT_REQUIRED
                else -> RootDoctorCode.PUBLISH_FAILED
            }
        }
        val resultCode = if (marker == "READY" && exitCode != 0) RootDoctorCode.PUBLISH_FAILED else code
        return RootDoctorResult(resultCode, moduleVersion, diagnosticText(output))
    }

    internal fun parseInstallResult(output: String, exitCode: Int, appVersion: String): RootDoctorResult {
        val marker = Regex("CLOAK_DOCTOR=([A-Z_]+)").find(output)?.groupValues?.get(1)
        if (marker == "MODULE_REBOOT_REQUIRED" && exitCode == 0) {
            return RootDoctorResult(RootDoctorCode.MODULE_REBOOT_REQUIRED, "v$appVersion")
        }
        val code = when {
            marker == "ROOT_REQUIRED" -> RootDoctorCode.ROOT_REQUIRED
            else -> RootDoctorCode.MODULE_INSTALL_FAILED
        }
        return RootDoctorResult(code, diagnostic = diagnosticText(output))
    }

    private fun diagnosticText(output: String): String? = output.lineSequence()
        .filterNot { it.startsWith("CLOAK_") }
        .joinToString(" ") { it.trim() }
        .replace(Regex("\\s+"), " ")
        .take(180)
        .takeIf { it.isNotBlank() }

    private data class RootShellResult(val output: String, val exitCode: Int?, val timedOut: Boolean)

    private fun runRootScript(script: String, timeoutMs: Long): RootShellResult {
        val process = ProcessBuilder("su", "--mount-master", "-c", script)
            .redirectErrorStream(true)
            .start()
        val output = StringBuilder()
        val outputReader = Thread {
            runCatching {
                process.inputStream.bufferedReader().use { reader ->
                    val buffer = CharArray(512)
                    while (true) {
                        val count = reader.read(buffer)
                        if (count < 0) break
                        synchronized(output) {
                            if (output.length < 4096) output.append(buffer, 0, minOf(count, 4096 - output.length))
                        }
                    }
                }
            }
        }.apply { isDaemon = true; start() }

        val finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
        if (!finished) {
            process.destroyForcibly()
            process.waitFor(1, TimeUnit.SECONDS)
        }
        outputReader.join(1000)
        val details = synchronized(output) { output.toString().trim() }
        return RootShellResult(details, if (finished) process.exitValue() else null, timedOut = !finished)
    }

    private fun quote(value: String) = "'" + value.replace("'", "'\\''") + "'"

    internal fun installModuleScript(zip: File, appVersion: String): String = buildString {
        append("set -eu; umask 077; ")
        append("uid=$(id -u); if [ \"\$uid\" != 0 ]; then echo CLOAK_DOCTOR=ROOT_REQUIRED; exit 51; fi; ")
        append("echo CLOAK_ROOT=OK; zip=${quote(zip.absolutePath)}; ")
        append("[ -s \"\$zip\" ] || { echo CLOAK_DOCTOR=MODULE_INSTALL_FAILED; exit 52; }; ")
        append("if command -v magisk >/dev/null 2>&1; then magisk_bin=$(command -v magisk); ")
        append("elif [ -x /data/adb/magisk ]; then magisk_bin=/data/adb/magisk/magisk; ")
        append("else echo CLOAK_DOCTOR=MODULE_INSTALL_FAILED; exit 53; fi; ")
        append("if \"\$magisk_bin\" --install-module \"\$zip\"; then :; ")
        append("else status=\$?; echo CLOAK_DOCTOR=MODULE_INSTALL_FAILED; exit \"\$status\"; fi; ")
        append("rm -f /data/adb/modules/cyclone_cloak/disable /data/adb/modules/cyclone_cloak/remove; ")
        append("if [ -d /data/adb/modules_update/cyclone_cloak ]; then echo CLOAK_DOCTOR=MODULE_REBOOT_REQUIRED; exit 0; fi; ")
        append("module_version=$(sed -n 's/^version=//p' /data/adb/modules/cyclone_cloak/module.prop 2>/dev/null | head -n 1); ")
        append("if [ \"\$module_version\" = ${quote("v$appVersion")} ]; then echo CLOAK_DOCTOR=MODULE_REBOOT_REQUIRED; exit 0; fi; ")
        append("echo CLOAK_DOCTOR=MODULE_INSTALL_FAILED; exit 54")
    }

    internal fun publishScript(staging: File, abi: String, appVersion: String, legacyDirs: List<String>): String {
        val module = "/data/adb/modules/cyclone_cloak"
        val root = "/data/adb/cyclone_cloak"
        val temp = "${CloakStateLayout.MODULE_STATE_DIR}.tmp"
        val backup = "${CloakStateLayout.MODULE_STATE_DIR}.previous"
        return buildString {
            append("set -eu; umask 077; ")
            append("uid=$(id -u); if [ \"\$uid\" != 0 ]; then echo CLOAK_DOCTOR=ROOT_REQUIRED; exit 41; fi; ")
            append("echo CLOAK_ROOT=OK; ")
            append("module=${quote(module)}; ")
            append("if [ ! -d \"\$module\" ]; then echo CLOAK_DOCTOR=MODULE_MISSING; exit 42; fi; ")
            append("if [ -e \"\$module/remove\" ]; then echo CLOAK_DOCTOR=MODULE_PENDING_REMOVAL; exit 43; fi; ")
            append("if [ -e /data/adb/modules_update/cyclone_cloak ]; then pending_version=$(sed -n 's/^version=//p' /data/adb/modules_update/cyclone_cloak/module.prop 2>/dev/null | head -n 1 || true); ")
            append("echo CLOAK_MODULE_VERSION=\"\$pending_version\"; if [ \"\$pending_version\" = ${quote("v$appVersion")} ]; then echo CLOAK_DOCTOR=MODULE_REBOOT_REQUIRED; exit 44; ")
            append("else echo CLOAK_DOCTOR=MODULE_OUTDATED; exit 46; fi; fi; ")
            append("if [ -e \"\$module/disable\" ]; then echo CLOAK_DOCTOR=MODULE_DISABLED; exit 45; fi; ")
            append("module_version=$(sed -n 's/^version=//p' \"\$module/module.prop\" | head -n 1); ")
            append("echo CLOAK_MODULE_VERSION=\"\$module_version\"; ")
            append("if [ \"\$module_version\" != ${quote("v$appVersion")} ]; then echo CLOAK_DOCTOR=MODULE_OUTDATED; exit 46; fi; ")
            append("abi=${quote(abi)}; if [ ! -s \"\$module/zygisk/\$abi.so\" ]; then echo CLOAK_DOCTOR=ABI_UNSUPPORTED; exit 47; fi; ")
            append("if command -v magisk >/dev/null 2>&1; then magisk_bin=$(command -v magisk); ")
            append("elif [ -x /data/adb/magisk ]; then magisk_bin=/data/adb/magisk/magisk; ")
            append("else echo CLOAK_DOCTOR=ZYGISK_STATUS_UNKNOWN; exit 55; fi; ")
            append("zygisk_state=$(\"\$magisk_bin\" --sqlite \"SELECT value FROM settings WHERE key='zygisk';\" 2>/dev/null | tr -d '\\r\\n'); ")
            append("if [ \"\$zygisk_state\" = 0 ]; then echo CLOAK_DOCTOR=ZYGISK_DISABLED; exit 56; fi; ")
            append("if [ \"\$zygisk_state\" != 1 ]; then echo CLOAK_DOCTOR=ZYGISK_STATUS_UNKNOWN; exit 57; fi; ")
            append("mkdir -p ${quote(root)}; rm -rf ${quote(temp)} ${quote(backup)}; ")
            append("cp -R ${quote(staging.absolutePath)} ${quote(temp)}; ")
            append("test -s ${quote("$temp/${CloakStateLayout.INDEX_FILE}")}; chmod -R go-rwx ${quote(temp)}; ")
            append("chmod 700 ${quote(root)} ${quote(temp)}; ")
            append("if [ -e ${quote(CloakStateLayout.MODULE_STATE_DIR)} ]; then mv ${quote(CloakStateLayout.MODULE_STATE_DIR)} ${quote(backup)}; fi; ")
            append("if mv ${quote(temp)} ${quote(CloakStateLayout.MODULE_STATE_DIR)}; then rm -rf ${quote(backup)} || true; ")
            append("else if [ -e ${quote(backup)} ]; then mv ${quote(backup)} ${quote(CloakStateLayout.MODULE_STATE_DIR)}; fi; echo CLOAK_DOCTOR=PUBLISH_FAILED; exit 48; fi; ")
            for (dir in legacyDirs) append("rm -rf ${quote(dir)} || true; ")
            append("test -s ${quote("${CloakStateLayout.MODULE_STATE_DIR}/${CloakStateLayout.INDEX_FILE}")} || { echo CLOAK_DOCTOR=PUBLISH_FAILED; exit 49; }; ")
            append("echo CLOAK_DOCTOR=READY")
        }
    }
}
