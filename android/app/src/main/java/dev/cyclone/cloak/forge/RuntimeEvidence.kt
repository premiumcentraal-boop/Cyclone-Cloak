package dev.cyclone.cloak.forge

import org.json.JSONObject

/** Converts local probe artifacts to field-path test states. Evidence is about probe fixtures,
 * never certification of a saved identity or of the currently installed binary. No raw values retained.
 */
object RuntimeEvidence {
    private val phases = listOf("unbound-before", "bound-a", "bound-a-repeat", "bound-b", "unbound-after")
    private val mappings = linkedMapOf(
        "device.manufacturer" to listOf("build.MANUFACTURER"), "device.brand" to listOf("build.BRAND"),
        "device.model" to listOf("build.MODEL", "properties.java.ro.product.model", "properties.native.ro.product.model"),
        "device.device" to listOf("build.DEVICE"), "device.product" to listOf("build.PRODUCT"),
        "device.hardware" to listOf("build.HARDWARE"), "device.build_id" to listOf("build.ID"),
        "device.bootloader" to listOf("build.BOOTLOADER"), "device.build_date_utc" to listOf("build.TIME"),
        "device.baseband" to listOf("build.getRadioVersion"),
        "device.sdk_int" to listOf("version.SDK_INT", "properties.java.ro.build.version.sdk", "properties.native.ro.build.version.sdk"),
        "device.version_release" to listOf("version.RELEASE"), "device.version_incremental" to listOf("version.INCREMENTAL"),
        "device.security_patch" to listOf("version.SECURITY_PATCH"),
        "device.fingerprint" to listOf("build.FINGERPRINT", "properties.java.ro.build.fingerprint", "properties.native.ro.build.fingerprint"),
        "identifiers.serial" to listOf("build.SERIAL", "build.getSerial", "properties.java.ro.serialno", "properties.native.ro.serialno"),
        "identifiers.imei_primary" to listOf("telephony.imei0", "telephony.deviceId"),
        "identifiers.imei_secondary" to listOf("telephony.imei1"), "identifiers.sim_serial" to listOf("telephony.simSerial"),
        "telephony.sim_slot_count" to listOf("telephony.phoneCount", "telephony.activeModemCount"),
        "telephony.network_type" to listOf("telephony.networkType", "telephony.dataNetworkType"),
        "telephony.mcc" to listOf("operator.networkNumeric", "operator.simNumeric"),
        "telephony.mnc" to listOf("operator.networkNumeric", "operator.simNumeric"),
        "telephony.carrier_name" to listOf("operator.networkName", "operator.simName"),
        "identifiers.android_id" to listOf("settings.secure.androidId", "settings.provider.androidId"),
        "identifiers.mac" to listOf("wifi.objectMac", "wifi.serviceMac"), "identifiers.bt_mac" to listOf("bluetooth.address"),
        "identifiers.advertising_id" to listOf("advertising.id"), "identifiers.widevine_id" to listOf("drm.deviceUniqueId"),
        "display.width" to listOf("display.resources.width", "display.real.width"),
        "display.height" to listOf("display.resources.height", "display.real.height"),
        "display.density" to listOf("display.resources.density", "display.real.density", "properties.java.ro.sf.lcd_density", "properties.native.ro.sf.lcd_density"),
        "display.refresh_rate_hz" to listOf("display.refresh"), "camera.cameras" to listOf("camera.characteristics"),
        "locale.language" to listOf("locale.default.language", "locale.resources.language"),
        "locale.country" to listOf("locale.default.country", "locale.resources.country", "operator.networkCountry", "operator.simCountry"),
        "locale.timezone" to listOf("timezone.default", "properties.java.persist.sys.timezone", "properties.native.persist.sys.timezone"),
    )

    fun toScoreInput(evidence: JSONObject): Map<String, String> {
        require(evidence.opt("format") == 1) { "Unsupported probe evidence format." }
        val runs = evidence.optJSONArray("runs") ?: throw IllegalArgumentException("Probe evidence has no runs.")
        require(runs.length() in 1..5) { "Invalid probe phase count." }
        val reads = mutableMapOf<String, MutableMap<String, JSONObject>>()
        val seen = mutableSetOf<String>()
        val packages = mutableSetOf<String>()
        for (i in 0 until runs.length()) {
            val run = runs.getJSONObject(i)
            val phase = run.getString("run")
            require(phase in phases && seen.add(phase)) { "Unknown or duplicate probe phase." }
            val pkg = run.getString("package")
            require(pkg in setOf("dev.cyclone.cloak.probe", "dev.cyclone.cloak.probe.diag")) { "Unexpected probe package." }
            packages.add(pkg)
            val rows = run.getJSONArray("rows")
            require(rows.length() <= 1000) { "Too many probe rows." }
            val ids = mutableSetOf<String>()
            for (j in 0 until rows.length()) {
                val row = rows.getJSONObject(j)
                val id = row.getString("id")
                require(ids.add(id) && row.opt("status") in setOf("PASS", "FAIL", "BLOCKED", "OBSERVED")) { "Malformed or duplicate probe row." }
                reads.getOrPut(id) { mutableMapOf() }[phase] = row
            }
        }
        require(packages.size == 1) { "Mixed probe packages cannot establish evidence." }
        val artifact = evidence.opt("artifact") as? String
        val trustworthyArtifact = artifact != null && Regex("[0-9a-f]{64}").matches(artifact) &&
            evidence.optJSONObject("metadata")?.opt("module_arm64_sha256") == artifact
        fun status(id: String): String {
            val rows = reads[id].orEmpty()
            val bound = listOfNotNull(rows["bound-a"], rows["bound-a-repeat"], rows["bound-b"])
            if (bound.any { it.opt("status") == "FAIL" }) return "FAIL"
            if (!phases.all { rows.containsKey(it) } || !trustworthyArtifact) return "INCOMPLETE"
            val a = rows.getValue("bound-a"); val b = rows.getValue("bound-b")
            val repeat = rows.getValue("bound-a-repeat")
            if (bound.any { it.opt("status") == "BLOCKED" }) return "BLOCKED"
            if (bound.any { it.opt("status") != "PASS" }) return "UNMAPPED"
            if (rows.values.any { !Regex("[0-9a-f]{64}").matches(it.optString("actual_sha256")) }) return "INCOMPLETE"
            if (a.opt("actual_sha256") != repeat.opt("actual_sha256") ||
                rows.getValue("unbound-before").opt("actual_sha256") != rows.getValue("unbound-after").opt("actual_sha256")) return "FAIL"
            if (a.isNull("expected") || b.isNull("expected")) return "UNMAPPED"
            if (a.get("expected").toString() == b.get("expected").toString() || a.opt("actual_sha256") == b.opt("actual_sha256")) return "MATCH_ONLY"
            return "VERIFIED"
        }
        return mappings.mapValues { (_, ids) ->
            val states = ids.map(::status)
            when {
                "FAIL" in states -> "FAIL"
                states.all { it == "VERIFIED" } -> "VERIFIED"
                "BLOCKED" in states -> "BLOCKED"
                "INCOMPLETE" in states -> "INCOMPLETE"
                "UNMAPPED" in states -> "UNMAPPED"
                else -> "MATCH_ONLY"
            }
        }
    }

    data class FieldCoverage(val field: String, val state: String, val testStatus: String)
    fun rows(profile: JSONObject, input: Map<String, String> = emptyMap()): List<FieldCoverage> {
        val rows = IdentityEngine.score_coverage(profile, input).getJSONArray("rows")
        return (0 until rows.length()).map { index ->
            val row = rows.getJSONObject(index)
            val status = row.getString("test_status")
            val state = when {
                status == "FAIL" -> "failed"
                status == "VERIFIED" -> "verified"
                status in setOf("BLOCKED", "INCOMPLETE", "UNMAPPED", "OBSERVED", "MATCH_ONLY") -> "not_verified"
                row.getString("implementation") == "APPLIED" -> "applied"
                row.getString("implementation") == "PENDING" -> "pending"
                else -> "not_verified"
            }
            FieldCoverage(row.getString("field"), state, status)
        }
    }
}
