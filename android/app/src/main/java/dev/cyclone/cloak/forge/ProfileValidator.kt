package dev.cyclone.cloak.forge

import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Coherence rules for cloak profiles, ported from forge/cloak_forge/validate.py. A profile is only as strong as its
 * least consistent field: every rule mirrors a cross-check an anti-fraud SDK can run against a real device.
 * `forge/tests/vectors/validation.json` holds cases both implementations must answer identically.
 */
object ProfileValidator {
    const val ERROR = "error"
    const val WARNING = "warning"
    const val SCHEMA_VERSION = "0.2"

    /** One problem. [field] is the dotted path it is about ("device.fingerprint"), when there is one. */
    data class Finding(val severity: String, val code: String, val message: String, val field: String? = null) {
        val isError: Boolean get() = severity == ERROR
    }

    private val SEED = Regex("^[0-9a-f]{64}$")
    private val ANDROID_ID = Regex("^[0-9a-f]{16}$")
    private val UUID4 = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$")
    private val MAC = Regex("^([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}$")
    private val HEX16 = Regex("^[0-9a-f]{16}$")
    private val HEX64 = Regex("^[0-9a-f]{64}$")
    private val SERIAL = Regex("^[0-9A-Z]{6,16}$")
    private val SIM_SERIAL = Regex("^[0-9]{19,20}$")
    private val IMEI = Regex("^[0-9]{15}$")
    private val LANG = Regex("^[a-z]{2}$")
    private val COUNTRY = Regex("^[A-Z]{2}$")
    private val TIMEZONE = Regex("^[A-Za-z0-9_+-]+(/[A-Za-z0-9_+-]+)*$")
    private val MCC = Regex("^\\d{3}$")
    private val MNC = Regex("^\\d{2,3}$")
    private val UA = Regex(
        "^Mozilla/5\\.0 \\(Linux; Android [0-9.]+; [^()]+\\) AppleWebKit/537\\.36 " +
            "\\(KHTML, like Gecko\\) Chrome/[0-9]+\\.0\\.[0-9]+\\.[0-9]+ (Mobile )?Safari/537\\.36$",
    )

    /** brand/product/device:release/build_id/incremental:build_type/tags */
    val FINGERPRINT = Regex(
        "^([^/:\\s]+)/([^/:\\s]+)/([^/:\\s]+):([0-9.]+)/([^/]+)/([^:/]+):([^:/]+)/(release-keys|dev-keys)$",
    )

    val REQUIRED_DEVICE_FIELDS = listOf(
        "manufacturer", "brand", "model", "product", "device", "hardware", "fingerprint", "version_release",
        "sdk_int", "security_patch", "build_id", "version_incremental", "build_date_utc", "first_api_level",
    )

    /** Unambiguous release → SDK pairs; older releases vary across OEMs. */
    val RELEASE_TO_SDK = mapOf("11" to 30, "12" to 31, "12L" to 32, "13" to 33, "14" to 34, "15" to 35, "16" to 36)
    val NETWORK_TYPES = listOf("5G", "LTE", "HSPA", "UMTS", "GSM")
    val DISPLAY_CLASSES = listOf("small", "normal", "large")

    private val IDENTIFIERS = mapOf(
        "android_id" to (ANDROID_ID to "16 lowercase hex characters"),
        "advertising_id" to (UUID4 to "a UUIDv4"),
        "app_set_id" to (UUID4 to "a UUIDv4"),
        "mac" to (MAC to "a MAC address like AA:BB:CC:DD:EE:FF"),
        "bt_mac" to (MAC to "a MAC address like AA:BB:CC:DD:EE:FF"),
        "imei_primary" to (IMEI to "15 digits with a valid Luhn check digit"),
        "imei_secondary" to (IMEI to "15 digits with a valid Luhn check digit"),
        "sim_serial" to (SIM_SERIAL to "19-20 digits"),
        "gsf_id" to (HEX16 to "16 lowercase hex characters"),
        "widevine_id" to (HEX64 to "64 lowercase hex characters"),
        "serial" to (SERIAL to "6-16 uppercase alphanumerics"),
    )

    /** Every finding for a full cloak profile. */
    fun validateProfile(profile: JSONObject, today: LocalDate = LocalDate.now(ZoneOffset.UTC)): List<Finding> {
        val findings = mutableListOf<Finding>()
        if (profile.opt("schema_version") != SCHEMA_VERSION) {
            findings.error("SCHEMA_VERSION", "schema_version must be $SCHEMA_VERSION", "schema_version")
        }
        if (!SEED.matches(profile.optString("seed"))) findings.error("SEED", "seed must be 64 lowercase hex characters", "seed")
        if ((profile.opt("name") as? String).isNullOrBlank()) findings.error("NAME", "profile name must be a non-empty string", "name")
        val device = profile.optJSONObject("device") ?: JSONObject()
        requiredDevice(findings, device)
        if (findings.any { it.isError }) return findings

        // Same order as validate.py, so both list the same findings in the same order.
        fingerprint(findings, device)
        patch(findings, device, today)
        identifiers(findings, profile.optJSONObject("identifiers") ?: JSONObject())
        surroundings(findings, profile)
        val ua = profile.opt("ua")
        if (ua == null || ua == JSONObject.NULL) {
            findings.warning("UA_MISSING", "profile carries no user agent block", "ua")
        } else {
            userAgent(findings, ua, device)
        }
        return findings
    }

    /**
     * Findings for a phone on its own (what the builder and the importers check): the device block and its
     * fingerprint, patch level, telephony, display and locale. Nothing about seeds or identifiers.
     */
    fun validatePhone(phone: PhoneTemplate, today: LocalDate = LocalDate.now(ZoneOffset.UTC)): List<Finding> {
        val findings = mutableListOf<Finding>()
        requiredDevice(findings, phone.device)
        if (findings.any { it.isError }) return findings
        val asProfile = JSONObject().put("telephony", phone.telephony).put("display", phone.display).put("locale", phone.locale)
        fingerprint(findings, phone.device)
        patch(findings, phone.device, today)
        surroundings(findings, asProfile)
        return findings
    }

    /** The error messages only, for a one-line refusal. */
    fun errors(profile: JSONObject): List<String> = validateProfile(profile).filter { it.isError }.map { it.message }

    private fun requiredDevice(findings: MutableList<Finding>, device: JSONObject) {
        for (field in REQUIRED_DEVICE_FIELDS) {
            val value = device.opt(field)
            if (value == null || value == JSONObject.NULL || value == "") {
                findings.error("MISSING_DEVICE_FIELD", "device.$field is required", "device.$field")
            }
        }
    }

    private fun surroundings(findings: MutableList<Finding>, profile: JSONObject) {
        telephony(findings, profile.optJSONObject("telephony") ?: JSONObject())
        display(findings, profile.optJSONObject("display") ?: JSONObject())
        locale(findings, profile.optJSONObject("locale") ?: JSONObject())
    }

    private fun fingerprint(findings: MutableList<Finding>, device: JSONObject) {
        val match = FINGERPRINT.matchEntire(device.text("fingerprint"))
        if (match == null) {
            findings.error("FINGERPRINT_FORMAT", "fingerprint does not match the AOSP grammar", "device.fingerprint")
            return
        }
        val parts = match.groupValues
        listOf(
            Triple(1, "brand", "FP_BRAND_MISMATCH"),
            Triple(2, "product", "FP_PRODUCT_MISMATCH"),
            Triple(3, "device", "FP_DEVICE_MISMATCH"),
            Triple(4, "version_release", "FP_RELEASE_MISMATCH"),
            Triple(5, "build_id", "FP_BUILD_ID_MISMATCH"),
            Triple(6, "version_incremental", "FP_INCREMENTAL_MISMATCH"),
        ).forEach { (group, field, code) ->
            if (parts[group] != device.text(field)) {
                val part = listOf("", "brand", "product", "device", "release", "build_id", "incremental")[group]
                findings.error(code, "fingerprint $part disagrees with device.$field", "device.$field")
            }
        }
        if (parts[7] != "user") findings.error("BUILD_TYPE", "fingerprint build type must be 'user'", "device.fingerprint")
        if (parts[8] != "release-keys") {
            findings.warning("TAGS", "dev-keys fingerprints look like development builds", "device.fingerprint")
        }
        val sdk = device.number("sdk_int")
        if (sdk == null || sdk !in 26..36) {
            findings.error("SDK_RANGE", "sdk_int must be between 26 (Android 8.0) and 36 (Android 16)", "device.sdk_int")
        }
        val expected = RELEASE_TO_SDK[device.text("version_release")]
        if (expected != null && sdk != null && expected != sdk) {
            findings.error(
                "RELEASE_SDK_MISMATCH",
                "release '${device.text("version_release")}' implies SDK $expected, profile says $sdk",
                "device.sdk_int",
            )
        }
        val firstApi = device.number("first_api_level")
        if (firstApi != null && sdk != null && firstApi > sdk) {
            findings.error("FIRST_API_LEVEL", "first_api_level cannot exceed sdk_int", "device.first_api_level")
        }
    }

    private fun patch(findings: MutableList<Finding>, device: JSONObject, today: LocalDate) {
        val patch = runCatching { LocalDate.parse(device.text("security_patch")) }.getOrNull()
        if (patch == null) {
            findings.error("PATCH_DATE", "security_patch must be an ISO date (YYYY-MM-DD)", "device.security_patch")
            return
        }
        if (patch.isAfter(today)) findings.warning("PATCH_FUTURE", "security_patch is in the future", "device.security_patch")
        val built = device.long("build_date_utc")?.let {
            runCatching { Instant.ofEpochSecond(it).atZone(ZoneOffset.UTC).toLocalDate() }.getOrNull()
        } ?: return
        if (patch.isAfter(built)) {
            findings.warning("PATCH_AFTER_BUILD", "security patch is newer than the build date", "device.security_patch")
        }
    }

    private fun identifiers(findings: MutableList<Finding>, identifiers: JSONObject) {
        for (key in identifiers.keys()) {
            val value = identifiers.opt(key)
            if (value !is String) {
                findings.error("IDENTIFIER_FORMAT", "identifiers.$key must be a string", "identifiers.$key")
                continue
            }
            val (pattern, description) = IDENTIFIERS[key] ?: run {
                findings.warning("UNKNOWN_IDENTIFIER", "identifiers.$key is not part of the v0 identifier set", "identifiers.$key")
                null
            } ?: continue
            if (!pattern.matches(value)) {
                findings.error("IDENTIFIER_FORMAT", "identifiers.$key must be $description", "identifiers.$key")
                continue
            }
            if (key.startsWith("imei_") && !luhnValid(value)) {
                findings.error("IDENTIFIER_CHECKSUM", "identifiers.$key fails the IMEI Luhn check", "identifiers.$key")
            }
        }
    }

    private fun userAgent(findings: MutableList<Finding>, ua: Any, device: JSONObject) {
        if (ua !is JSONObject) {
            findings.error("UA_BLOCK", "ua must be an object", "ua")
            return
        }
        val value = ua.opt("value") as? String
        if (value == null || !UA.matches(value)) {
            findings.error("UA_FORMAT", "ua.value must be a Chrome-on-Android user agent string", "ua.value")
            return
        }
        if ("; ${device.text("model")})" !in value) {
            findings.error("UA_MODEL_MISMATCH", "ua.value model disagrees with device.model", "ua.value")
        }
        if ("Android ${device.text("version_release")};" !in value) {
            findings.error("UA_RELEASE_MISMATCH", "ua.value Android release disagrees with device.version_release", "ua.value")
        }
    }

    private fun telephony(findings: MutableList<Finding>, telephony: JSONObject) {
        if (telephony.length() == 0) return
        if (telephony.text("carrier_name").isBlank()) {
            findings.error("TELEPHONY_CARRIER", "telephony.carrier_name must be a non-empty string", "telephony.carrier_name")
        }
        if (!MCC.matches(telephony.text("mcc"))) findings.error("TELEPHONY_MCC", "telephony.mcc must be three digits", "telephony.mcc")
        if (!MNC.matches(telephony.text("mnc"))) findings.error("TELEPHONY_MNC", "telephony.mnc must be two or three digits", "telephony.mnc")
        if (telephony.text("network_type") !in NETWORK_TYPES) {
            findings.error(
                "TELEPHONY_NETWORK_TYPE", "telephony.network_type must be one of 5G, LTE, HSPA, UMTS, GSM", "telephony.network_type",
            )
        }
        val slots = if (telephony.has("sim_slot_count")) telephony.text("sim_slot_count") else "1"
        if (slots !in setOf("1", "2")) {
            findings.error("TELEPHONY_SLOTS", "telephony.sim_slot_count must be 1 or 2", "telephony.sim_slot_count")
        }
    }

    private fun display(findings: MutableList<Finding>, display: JSONObject) {
        if (display.length() == 0) return
        for (field in listOf("width", "height", "density", "refresh_rate_hz")) {
            val value = display.opt(field)
            if (value !is Int && value !is Long || (value as Number).toLong() <= 0) {
                findings.error("DISPLAY_FIELD", "display.$field must be a positive integer", "display.$field")
            }
        }
        if (display.text("screen_size_class") !in DISPLAY_CLASSES) {
            findings.error("DISPLAY_CLASS", "display.screen_size_class must be small, normal, or large", "display.screen_size_class")
        }
    }

    private fun locale(findings: MutableList<Finding>, locale: JSONObject) {
        locale.textOrNull("language")?.let {
            if (!LANG.matches(it)) findings.error("LOCALE", "locale.language must be a two-letter lowercase code", "locale.language")
        }
        locale.textOrNull("country")?.let {
            if (!COUNTRY.matches(it)) findings.error("LOCALE", "locale.country must be a two-letter uppercase code", "locale.country")
        }
        locale.textOrNull("timezone")?.let {
            if (!TIMEZONE.matches(it)) findings.error("LOCALE", "locale.timezone must be an IANA zone like Europe/Berlin", "locale.timezone")
        }
    }

    fun luhnValid(digits: String): Boolean = digits.isNotEmpty() && digits.all { it.isDigit() } &&
        CloakForge.luhnCheckDigit(digits.dropLast(1)) == digits.last() - '0'

    private fun MutableList<Finding>.error(code: String, message: String, field: String?) = add(Finding(ERROR, code, message, field))
    private fun MutableList<Finding>.warning(code: String, message: String, field: String?) = add(Finding(WARNING, code, message, field))

    /** Python's str(): numbers print without a fraction. */
    private fun JSONObject.text(key: String): String = when (val v = opt(key)) {
        null, JSONObject.NULL -> ""
        is Double -> if (v % 1.0 == 0.0) v.toLong().toString() else v.toString()
        else -> v.toString()
    }

    private fun JSONObject.textOrNull(key: String): String? = opt(key)?.takeIf { it != JSONObject.NULL }?.let { text(key) }

    private fun JSONObject.number(key: String): Int? = when (val v = opt(key)) {
        is Number -> v.toInt()
        is String -> v.trim().toIntOrNull()
        else -> null
    }

    private fun JSONObject.long(key: String): Long? = when (val v = opt(key)) {
        is Number -> v.toLong()
        is String -> v.trim().toLongOrNull()
        else -> null
    }
}
