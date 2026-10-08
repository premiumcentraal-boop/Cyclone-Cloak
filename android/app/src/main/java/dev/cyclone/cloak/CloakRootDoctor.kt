package dev.cyclone.cloak

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File
import java.util.concurrent.TimeUnit

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
    ABI_UNSUPPORTED,
    PUBLISH_FAILED,
}

data class RootDoctorResult(
    val code: RootDoctorCode,
    val moduleVersion: String? = null,
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
        RootDoctorCode.ABI_UNSUPPORTED -> "ABI unsupported"
        RootDoctorCode.PUBLISH_FAILED -> "Publish failed"
    }

    val message: String get() = when (code) {
        RootDoctorCode.NOT_CHECKED -> "Check Magisk access, the installed module, and profile publishing together."
        RootDoctorCode.CHECKING -> "Approve Cyclone Cloak in the Magisk prompt to finish the check."
        RootDoctorCode.READY -> "Magisk access, module version, and rooted profile publishing are working."
        RootDoctorCode.ROOT_REQUIRED -> "Open Magisk → Superuser, allow Cyclone Cloak, then run the check again."
        RootDoctorCode.ROOT_TIMEOUT -> "The Magisk prompt expired. Run the check again and approve the prompt right away."
        RootDoctorCode.ROOT_UNAVAILABLE -> "Install or open Magisk, enable its superuser service, then try again."
        RootDoctorCode.MODULE_MISSING -> "Install the Cyclone Cloak module ZIP from this release in Magisk, then restart."
        RootDoctorCode.MODULE_DISABLED -> "Enable Cyclone Cloak in Magisk → Modules, then restart the device."
        RootDoctorCode.MODULE_PENDING_REMOVAL -> "Magisk has marked the module for removal. Reinstall this release's ZIP and restart."
        RootDoctorCode.MODULE_REBOOT_REQUIRED -> "Magisk has a module update waiting. Restart the device, then check again."
        RootDoctorCode.MODULE_OUTDATED -> "The installed module${moduleVersion?.let { " ($it)" }.orEmpty()} does not match this app. Install this release's ZIP in Magisk and restart."
        RootDoctorCode.ABI_UNSUPPORTED -> "This release has no Zygisk library for this device's Android architecture."
        RootDoctorCode.PUBLISH_FAILED -> "Root access was checked, but the profile state could not be written. Retry; if it persists, reinstall the module and restart."
    }
}

/** Checks Magisk, the installed module, and publishes current bindings in one root request. */
object CloakRootDoctor {
    private val supportedAbis = setOf("arm64-v8a", "armeabi-v7a")
    private const val preferencesName = "cloak-root-doctor"
    private const val verifiedVersionKey = "verified-app-version"

    fun verifiedForCurrentApp(context: Context): Boolean {
        @Suppress("DEPRECATION")
        val appVersion = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull()
        return appVersion != null &&
            context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
                .getString(verifiedVersionKey, null) == appVersion
    }

    fun run(context: Context, staging: File): RootDoctorResult {
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
        val script = publishScript(staging, abi, appVersion, legacyDirs)
        return try {
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
                                if (output.length < 2048) output.append(buffer, 0, minOf(count, 2048 - output.length))
                            }
                        }
                    }
                }
            }.apply { isDaemon = true; start() }

            val finished = process.waitFor(25, TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                process.waitFor(1, TimeUnit.SECONDS)
                outputReader.join(1000)
                Log.w("CloakRootDoctor", "Root check timed out")
                rememberResult(context, RootDoctorResult(RootDoctorCode.ROOT_TIMEOUT), appVersion)
            } else {
                outputReader.join(1000)
                val details = synchronized(output) { output.toString().trim() }
                val result = parseResult(details, process.exitValue())
                Log.i("CloakRootDoctor", "Root check ${result.code}; exit=${process.exitValue()}; output=$details")
                rememberResult(context, result, appVersion)
            }
        } catch (error: Exception) {
            Log.e("CloakRootDoctor", "Could not run root check", error)
            rememberResult(context, RootDoctorResult(
                if (error is java.io.IOException) RootDoctorCode.ROOT_UNAVAILABLE else RootDoctorCode.PUBLISH_FAILED,
            ), appVersion)
        }
    }

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
            "ABI_UNSUPPORTED" -> RootDoctorCode.ABI_UNSUPPORTED
            "PUBLISH_FAILED" -> RootDoctorCode.PUBLISH_FAILED
            else -> when {
                output.contains("CLOAK_ROOT=OK") -> RootDoctorCode.PUBLISH_FAILED
                exitCode != 0 -> RootDoctorCode.ROOT_REQUIRED
                else -> RootDoctorCode.PUBLISH_FAILED
            }
        }
        return RootDoctorResult(if (marker == "READY" && exitCode != 0) RootDoctorCode.PUBLISH_FAILED else code, moduleVersion)
    }

    internal fun publishScript(staging: File, abi: String, appVersion: String, legacyDirs: List<String>): String {
        val module = "/data/adb/modules/cyclone_cloak"
        val root = "/data/adb/cyclone_cloak"
        val temp = "${CloakStateLayout.MODULE_STATE_DIR}.tmp"
        val backup = "${CloakStateLayout.MODULE_STATE_DIR}.previous"
        fun quote(value: String) = "'" + value.replace("'", "'\\''") + "'"
        return buildString {
            append("set -eu; umask 077; ")
            append("uid=$(id -u); if [ \"\$uid\" != 0 ]; then echo CLOAK_DOCTOR=ROOT_REQUIRED; exit 41; fi; ")
            append("echo CLOAK_ROOT=OK; ")
            append("module=${quote(module)}; ")
            append("if [ ! -d \"\$module\" ]; then echo CLOAK_DOCTOR=MODULE_MISSING; exit 42; fi; ")
            append("if [ -e \"\$module/remove\" ]; then echo CLOAK_DOCTOR=MODULE_PENDING_REMOVAL; exit 43; fi; ")
            append("if [ -e /data/adb/modules_update/cyclone_cloak ]; then echo CLOAK_DOCTOR=MODULE_REBOOT_REQUIRED; exit 44; fi; ")
            append("if [ -e \"\$module/disable\" ]; then echo CLOAK_DOCTOR=MODULE_DISABLED; exit 45; fi; ")
            append("module_version=$(sed -n 's/^version=//p' \"\$module/module.prop\" | head -n 1); ")
            append("echo CLOAK_MODULE_VERSION=\"\$module_version\"; ")
            append("if [ \"\$module_version\" != ${quote("v$appVersion")} ]; then echo CLOAK_DOCTOR=MODULE_OUTDATED; exit 46; fi; ")
            append("abi=${quote(abi)}; if [ ! -s \"\$module/zygisk/\$abi.so\" ]; then echo CLOAK_DOCTOR=ABI_UNSUPPORTED; exit 47; fi; ")
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
