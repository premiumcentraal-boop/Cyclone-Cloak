package dev.cyclone.cloak.playevidence

import org.json.JSONObject
import java.time.Instant

/** Injectable read-only boundary for policy tests; no account/package setters exist. */
interface SurfaceReader {
    fun packageInfo(name: String): JSONObject?
    fun playServicesCode(): Int
    fun webProvider(): JSONObject?
    fun settingsResolvable(): Boolean
    fun visibleGoogleAccount(): Boolean
    fun androidUser(): Int
}

object SurfaceCollector {
    fun collect(binding: EvidenceBinding, actualProfileHash: String?, reader: SurfaceReader,
        integrity: EvidenceRow = EvidenceRow("integrity.api", EvidenceStatus.NOT_RUN, "USER_NOT_REQUESTED"),
        now: Instant = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS)): JSONObject {
        val rows = mutableListOf<EvidenceRow>()
        fun read(id: String, block: () -> EvidenceRow) {
            val start = System.nanoTime()
            val row = try { block() } catch (_: SecurityException) { EvidenceRow(id, EvidenceStatus.BLOCKED, "PERMISSION_DENIED") }
                catch (_: Exception) { EvidenceRow(id, EvidenceStatus.BLOCKED, "API_ERROR") }
            rows += row.copy(durationMs = ((System.nanoTime() - start) / 1000000).coerceIn(0, 300000))
        }
        for (name in EvidenceReport.packages) read("package.$name") {
            val info = reader.packageInfo(name)
            when {
                info == null -> EvidenceRow("package.$name", EvidenceStatus.MISSING, "NOT_VISIBLE_OR_MISSING")
                info.opt("enabled") == false -> EvidenceRow("package.$name", EvidenceStatus.BLOCKED, "PACKAGE_DISABLED", info)
                info.opt("enabled") == true -> EvidenceRow("package.$name", EvidenceStatus.OBSERVED, "PACKAGE_OBSERVED", info)
                else -> EvidenceRow("package.$name", EvidenceStatus.BLOCKED, "API_ERROR")
            }
        }
        read("play_services.availability") {
            val code = reader.playServicesCode()
            EvidenceRow("play_services.availability", if (code == 0) EvidenceStatus.PASS else EvidenceStatus.FAIL,
                if (code == 0) "SERVICE_AVAILABLE" else "SERVICE_UNAVAILABLE", JSONObject().put("api_code", code))
        }
        read("play_store.availability") {
            val info = reader.packageInfo("com.android.vending")
            when {
                info == null -> EvidenceRow("play_store.availability", EvidenceStatus.MISSING, "NOT_VISIBLE_OR_MISSING")
                info.opt("enabled") == false -> EvidenceRow("play_store.availability", EvidenceStatus.BLOCKED, "PACKAGE_DISABLED", info)
                info.opt("enabled") == true -> EvidenceRow("play_store.availability", EvidenceStatus.OBSERVED, "PACKAGE_OBSERVED", info)
                else -> EvidenceRow("play_store.availability", EvidenceStatus.BLOCKED, "API_ERROR")
            }
        }
        rows += EvidenceRow("play_protect.state", EvidenceStatus.NOT_RUN, "NO_PUBLIC_OBSERVATION")
        read("web_provider.state") {
            val info = reader.webProvider()
            when {
                info == null -> EvidenceRow("web_provider.state", EvidenceStatus.MISSING, "NO_PROVIDER")
                info.opt("enabled") == false -> EvidenceRow("web_provider.state", EvidenceStatus.BLOCKED, "PACKAGE_DISABLED", info)
                info.opt("enabled") == true -> EvidenceRow("web_provider.state", EvidenceStatus.OBSERVED, "PROVIDER_OBSERVED", info)
                else -> EvidenceRow("web_provider.state", EvidenceStatus.BLOCKED, "API_ERROR")
            }
        }
        read("google_settings.access") {
            if (reader.settingsResolvable()) EvidenceRow("google_settings.access", EvidenceStatus.OBSERVED, "RESOLVABLE_NOT_ACCESS_PROOF")
            else EvidenceRow("google_settings.access", EvidenceStatus.MISSING, "SETTINGS_NOT_RESOLVABLE")
        }
        read("account.association") {
            val visible = reader.visibleGoogleAccount()
            EvidenceRow("account.association", EvidenceStatus.OBSERVED, if (visible) "VISIBLE_ACCOUNT" else "NO_CALLER_VISIBLE_ACCOUNT",
                JSONObject().put("visible_account", visible))
        }
        val user = reader.androidUser()
        rows += EvidenceRow("process.android_user", if (user == binding.androidUserId) EvidenceStatus.PASS else EvidenceStatus.FAIL,
            if (user == binding.androidUserId) "USER_MATCHED" else "USER_MISMATCH", JSONObject().put("android_user_id", user))
        rows += when (actualProfileHash) {
            null -> EvidenceRow("profile.binding", EvidenceStatus.MISSING, "PROFILE_INPUT_MISSING")
            binding.profileHash -> EvidenceRow("profile.binding", EvidenceStatus.PASS, "PROFILE_HASH_MATCHED", JSONObject().put("actual_profile_hash", actualProfileHash))
            else -> EvidenceRow("profile.binding", EvidenceStatus.FAIL, "PROFILE_HASH_MISMATCH", JSONObject().put("actual_profile_hash", actualProfileHash))
        }
        rows += integrity
        rows += EvidenceRow("integrity.verdict", EvidenceStatus.NOT_RUN, "BACKEND_VERIFICATION_REQUIRED")
        return EvidenceReport.build(binding.copy(androidUserId = user), rows, now)
    }
}
