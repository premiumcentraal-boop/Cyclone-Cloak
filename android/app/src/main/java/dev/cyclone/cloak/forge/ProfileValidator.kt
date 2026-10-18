package dev.cyclone.cloak.forge

import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Coherence rules for device-configuration profiles, mirrored in forge/cloak_forge/validate.py.
 * `forge/tests/vectors/validation.json` holds cases both implementations must answer identically.
 */
object ProfileValidator {
    const val ERROR = "error"
    const val WARNING = "warning"
    const val INFO = "info"
    const val SCHEMA_VERSION = "0.4"
    val LEGACY_SCHEMA_VERSIONS = setOf("0.2", "0.3")

    /** One problem. [field] is the dotted path it is about ("device.fingerprint"), when there is one. */
    data class Finding(val severity: String, val code: String, val message: String, val field: String? = null) {
        init {
            require(severity in setOf(ERROR, WARNING, INFO)) { "severity must be info, warning or error" }
            require(Regex("[A-Z][A-Z0-9_]*").matches(code) && message.isNotBlank()) {
                "finding needs a stable reason code and explanation"
            }
        }
        val isError: Boolean get() = severity == ERROR
    }

    /** Diagnostic summary only: not serialized into the profile or identity store. */
    class ValidationResult internal constructor(findings: Iterable<Finding>) {
        val findings: List<Finding> = java.util.Collections.unmodifiableList(findings.sortedWith(FINDING_ORDER))
        val errorCount: Int get() = this.findings.count { it.severity == ERROR }
        val warningCount: Int get() = this.findings.count { it.severity == WARNING }
        val infoCount: Int get() = this.findings.count { it.severity == INFO }
        val hasErrors: Boolean get() = errorCount > 0
    }

    private val FINDING_ORDER = compareBy<Finding>(
        { when (it.severity) { ERROR -> 0; WARNING -> 1; else -> 2 } },
        { it.field.orEmpty() }, { it.code }, { it.message },
    )

    fun summarize(findings: Iterable<Finding>): ValidationResult = ValidationResult(findings)
    fun validateProfileResult(profile: JSONObject, today: LocalDate = LocalDate.now(ZoneOffset.UTC)): ValidationResult =
        summarize(validateProfile(profile, today))
    fun validatePhoneResult(phone: PhoneTemplate, today: LocalDate = LocalDate.now(ZoneOffset.UTC)): ValidationResult =
        summarize(validatePhone(phone, today))

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
    private val TIMEZONE = Regex("^(?:UTC|GMT|GMT0|GMT[+-]0|Greenwich|UCT|Universal|Zulu|CET|EET|MET|WET|EST|HST|MST|EST5EDT|CST6CDT|MST7MDT|PST8PDT|Cuba|Egypt|Eire|GB|GB-Eire|Hongkong|Iceland|Iran|Israel|Jamaica|Japan|Kwajalein|Libya|Navajo|NZ|NZ-CHAT|Poland|Portugal|PRC|ROC|ROK|Singapore|Turkey|W-SU|[A-Za-z]+/[A-Za-z0-9_+-]+(?:/[A-Za-z0-9_+-]+)*)$")
    private val MCC = Regex("^[0-9]{3}$")
    private val MNC = Regex("^[0-9]{2,3}$")
    private val UA = Regex(
        "^Mozilla/5\\.0 \\(Linux; Android [0-9.]+; [^()]+\\) AppleWebKit/537\\.36 " +
            "\\(KHTML, like Gecko\\) Chrome/[0-9]+\\.0\\.[0-9]+\\.[0-9]+ (Mobile )?Safari/537\\.36$",
    )

    /** brand/product/device:release/build_id/incremental:build_type/tags */
    val FINGERPRINT = Regex(
        "^([^/:\\s]+)/([^/:\\s]+)/([^/:\\s]+):([0-9]+(?:\\.[0-9]+){0,2})/([^/:\\s]+)/([^/:\\s]+):([^/:\\s]+)/(release-keys|dev-keys)$",
    )

    val REQUIRED_DEVICE_FIELDS = listOf(
        "manufacturer", "brand", "model", "product", "device", "hardware", "fingerprint", "version_release",
        "sdk_int", "security_patch", "build_id", "version_incremental", "build_date_utc", "first_api_level",
    )

    /** Default SDK for the builder; validation also accepts API 32 for Android 12 (12L). */
    val RELEASE_TO_SDK = mapOf("8" to 26, "8.0" to 26, "8.1" to 27, "9" to 28, "10" to 29, "11" to 30, "12" to 31, "12L" to 32, "13" to 33, "14" to 34, "15" to 35, "16" to 36)
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
        if (profile.opt("schema_version") != SCHEMA_VERSION && profile.opt("schema_version") !in LEGACY_SCHEMA_VERSIONS) {
            findings.error("SCHEMA_VERSION", "schema_version must be $SCHEMA_VERSION", "schema_version")
        }
        if (profile.opt("seed") !is String || !SEED.matches(profile.optString("seed"))) findings.error("SEED", "seed must be 64 lowercase hex characters", "seed")
        if ((profile.opt("name") as? String).isNullOrBlank()) findings.error("NAME", "profile name must be a non-empty string", "name")
        val device = block(findings, profile, "device")
        requiredDevice(findings, device)
        if (findings.any { it.isError }) return summarize(findings).findings

        findings += DeviceReality.validate(profile)
        // Summary ordering is independent of rule execution and JSON key order.
        fingerprint(findings, device)
        versions(findings, device)
        patch(findings, device, today)
        identifiers(findings, block(findings, profile, "identifiers"))
        camera(findings, profile.opt("camera"))
        surroundings(findings, profile)
        hardware(findings, device, profile.optJSONObject("telephony") ?: JSONObject())
        val ua = profile.opt("ua")
        if (ua == null || ua == JSONObject.NULL) {
            findings.warning("UA_MISSING", "profile carries no user agent block", "ua")
        } else {
            userAgent(findings, ua, device)
        }
        return summarize(findings).findings
    }

    /**
     * Findings for a phone on its own (what the builder and the importers check): the device block and its
     * fingerprint, patch level, telephony, display and locale. Nothing about seeds or identifiers.
     */
    fun validatePhone(phone: PhoneTemplate, today: LocalDate = LocalDate.now(ZoneOffset.UTC)): List<Finding> {
        val findings = mutableListOf<Finding>()
        requiredDevice(findings, phone.device)
        if (findings.any { it.isError }) return summarize(findings).findings
        val asProfile = JSONObject().put("telephony", phone.telephony).put("display", phone.display).put("locale", phone.locale).put("network", phone.network)
        fingerprint(findings, phone.device)
        versions(findings, phone.device)
        patch(findings, phone.device, today)
        surroundings(findings, asProfile)
        hardware(findings, phone.device, phone.telephony)
        if (phone.camera.length() > 0) camera(findings, phone.camera)
        if (phone.reality.length() > 0) findings += DeviceReality.validate(asProfile.put("schema_version", "0.4").put("device", phone.device).put("reality", phone.reality))
        val claims = JSONObject(phone.corpus.toString())
        if (!claims.has("confidence")) phone.confidence?.let { claims.put("confidence", it) }
        corpus(findings, claims)
        val width = phone.display.integer("width")
        val height = phone.display.integer("height")
        if (width != null && height != null && minOf(width, height) > 0 &&
            maxOf(width, height).toDouble() / minOf(width, height) > 4) findings.error(
            "CATALOG_DISPLAY_ASPECT", "display.width and display.height imply an aspect ratio above 4:1 for a phone template", "display.width")
        return summarize(findings).findings
    }

    /** The error messages only, for a one-line refusal. */
    fun errors(profile: JSONObject): List<String> = validateProfile(profile).filter { it.isError }.map { it.message }

    private fun requiredDevice(findings: MutableList<Finding>, device: JSONObject) {
        for (field in REQUIRED_DEVICE_FIELDS) {
            val value = device.opt(field)
            if (value == null || value == JSONObject.NULL || value == "") {
                findings.error("MISSING_DEVICE_FIELD", "device.$field is required", "device.$field")
            } else if (field !in setOf("sdk_int", "first_api_level", "build_date_utc") &&
                (value !is String || value.isBlank() || value.any { it.code < 32 || it.code == 127 })) {
                findings.error("DEVICE_FIELD", "device.$field must be non-blank text without control characters", "device.$field")
            }
        }
    }

    private fun surroundings(findings: MutableList<Finding>, profile: JSONObject) {
        telephony(findings, block(findings, profile, "telephony"))
        display(findings, block(findings, profile, "display"))
        locale(findings, block(findings, profile, "locale"))
        network(findings, block(findings, profile, "network"))
    }

    private fun block(findings: MutableList<Finding>, profile: JSONObject, key: String): JSONObject {
        if (!profile.has(key)) return JSONObject()
        return profile.optJSONObject(key) ?: JSONObject().also {
            findings.error("BLOCK_TYPE", "$key must be an object", key)
        }
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
    }

    private fun versions(findings: MutableList<Finding>, device: JSONObject) {
        val numbers = mutableMapOf<String, Long>()
        for (field in listOf("sdk_int", "first_api_level")) {
            val value = device.integer(field)
            if (value == null) findings.error("API_TYPE", "device.$field must be an integer", "device.$field")
            else {
                numbers[field] = value
                if (value !in 26L..36L) findings.error(if (field == "sdk_int") "SDK_RANGE" else "FIRST_API_RANGE",
                    "device.$field must be between 26 and 36", "device.$field")
            }
        }
        val sdk = numbers["sdk_int"]
        val firstApi = numbers["first_api_level"]
        val release = device.text("version_release")
        if (!Regex("[0-9]+(?:\\.[0-9]+){0,2}").matches(release)) {
            findings.error("RELEASE_FORMAT", "device.version_release must be a numeric Android version such as 8.1 or 14", "device.version_release")
        } else {
            val canonical = release.replace(Regex("(?:\\.0)+$"), "")
            val expected = RELEASE_TO_SDK[canonical]
            val allowed = if (canonical == "12") setOf(31L, 32L) else expected?.let { setOf(it.toLong()) } ?: emptySet()
            if (allowed.isEmpty()) findings.warning("RELEASE_UNKNOWN", "device.version_release is not in the supported Android 8–16 release map", "device.version_release")
            else if (sdk != null && sdk !in allowed) findings.error("RELEASE_SDK_MISMATCH",
                "device.sdk_int must be ${allowed.sorted().joinToString("/")} for Android $release; got $sdk", "device.sdk_int")
        }
        if (firstApi != null && sdk != null && firstApi > sdk) findings.error("FIRST_API_LEVEL",
            "device.first_api_level cannot exceed device.sdk_int", "device.first_api_level")
        val model = device.text("model")
        val manufacturer = device.text("manufacturer").lowercase(java.util.Locale.ROOT)
        val brand = device.text("brand").lowercase(java.util.Locale.ROOT)
        val expectedManufacturer = when {
            Regex("^Pixel(?:\\s|$)", RegexOption.IGNORE_CASE).containsMatchIn(model) -> "google"
            Regex("^(?:SM-|GT-|Galaxy(?:\\s|$))", RegexOption.IGNORE_CASE).containsMatchIn(model) -> "samsung"
            else -> null
        }
        val aliases = mapOf("google" to setOf("google", "google llc", "google inc."), "samsung" to setOf("samsung", "samsung electronics"))
        if (expectedManufacturer != null && manufacturer !in aliases.getValue(expectedManufacturer)) findings.warning(
            "MODEL_MANUFACTURER_MISMATCH", "device.manufacturer is unusual for device.model; expected $expectedManufacturer", "device.manufacturer")
        if (brand in aliases && manufacturer in aliases.values.flatten() && manufacturer !in aliases.getValue(brand)) findings.warning(
            "BRAND_MANUFACTURER_MISMATCH", "device.manufacturer disagrees with the known device.brand family", "device.manufacturer")
    }

    private fun patch(findings: MutableList<Finding>, device: JSONObject, today: LocalDate) {
        val timestamp = device.integer("build_date_utc")
        val built = if (timestamp == null || timestamp !in 0L..253402300799L) {
            findings.error("BUILD_DATE", "device.build_date_utc must be integer Unix seconds between 0 and 253402300799", "device.build_date_utc")
            null
        } else Instant.ofEpochSecond(timestamp).atZone(ZoneOffset.UTC).toLocalDate().also {
            if (it.isAfter(today)) findings.warning("BUILD_FUTURE", "device.build_date_utc is in the future", "device.build_date_utc")
        }
        val patch = device.text("security_patch").takeIf { Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}").matches(it) }
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        if (patch == null) {
            findings.error("PATCH_DATE", "security_patch must be an ISO date (YYYY-MM-DD)", "device.security_patch")
            return
        }
        if (patch.isAfter(today)) findings.warning("PATCH_FUTURE", "security_patch is in the future", "device.security_patch")
        if (built != null && patch.isAfter(built)) {
            findings.warning("PATCH_AFTER_BUILD", "security patch is newer than the build date", "device.security_patch")
        }
    }

    private fun identifiers(findings: MutableList<Finding>, identifiers: JSONObject) {
        for (key in identifiers.keys().asSequence().sorted()) {
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
            if (key in setOf("mac", "bt_mac") && (value.take(2).toInt(16) and 1) != 0) {
                findings.error("IDENTIFIER_ADDRESS", "identifiers.$key must be a unicast address (first octet's low bit must be zero)", "identifiers.$key")
            } else if (value.replace(":", "").toSet() == setOf('0')) {
                findings.warning("IDENTIFIER_PLACEHOLDER", "identifiers.$key is an all-zero placeholder", "identifiers.$key")
            }
        }
        val primary = identifiers.opt("imei_primary")
        if (primary is String && primary.isNotEmpty() && primary == identifiers.opt("imei_secondary")) findings.warning(
            "IDENTIFIER_DUPLICATE", "identifiers.imei_secondary duplicates identifiers.imei_primary", "identifiers.imei_secondary")
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
        if (telephony.opt("carrier_name") !is String || telephony.text("carrier_name").isBlank()) {
            findings.error("TELEPHONY_CARRIER", "telephony.carrier_name must be a non-empty string", "telephony.carrier_name")
        } else if (telephony.text("carrier_name").hasControls()) {
            findings.error("TELEPHONY_CARRIER_FORMAT", "telephony.carrier_name must not contain control characters", "telephony.carrier_name")
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
        if (telephony.opt("mcc") == "000") findings.info("TELEPHONY_PLACEHOLDER_OPERATOR",
            "telephony.mcc is all zero; confirm that a placeholder operator is intended", "telephony.mcc")
    }

    private fun network(findings: MutableList<Finding>, network: JSONObject) {
        if (!network.has("egress_hint")) return
        val hint = network.opt("egress_hint")
        if (hint !is String || hint.codePointCount(0, hint.length) > 128 || hint.hasControls()) findings.error(
            "NETWORK_HINT_FORMAT", "network.egress_hint must be text of at most 128 characters without control characters", "network.egress_hint")
    }

    private fun hardware(findings: MutableList<Finding>, device: JSONObject, telephony: JSONObject) {
        // Schema has no camera/sensor flags. Identifier presence does not imply SIM capability.
        for (field in listOf("bootloader", "baseband")) {
            if (device.has(field)) {
                val value = device.opt(field)
                if (value !is String || value.hasControls()) findings.error("HARDWARE_FIELD_FORMAT",
                    "device.$field must be text without control characters", "device.$field")
            }
        }
        if (device.text("hardware").lowercase(java.util.Locale.ROOT) in setOf("unknown", "generic")) findings.info(
            "HARDWARE_UNSPECIFIED", "device.hardware does not identify a specific hardware platform; capability consistency cannot be established", "device.hardware")
        val baseband = device.opt("baseband")
        if (telephony.text("network_type") in NETWORK_TYPES && baseband is String && baseband.trim().lowercase(java.util.Locale.ROOT) in setOf("none", "unknown", "n/a")) findings.warning(
            "HARDWARE_MODEM_UNSPECIFIED", "device.baseband does not identify a modem although telephony.network_type declares cellular service", "device.baseband")
        val firstApi = device.integer("first_api_level")
        if (telephony.text("network_type") == "5G" && firstApi != null && firstApi < 29) findings.warning(
            "HARDWARE_NETWORK_GENERATION", "telephony.network_type declares 5G on hardware launched before API 29; confirm modem support", "telephony.network_type")
    }

    private fun String.hasControls() = any { it.code < 32 || it.code == 127 }

    private fun corpus(findings: MutableList<Finding>, record: JSONObject) {
        for (field in listOf("confidence", "source_confidence")) {
            if (record.has(field) && record.opt(field) !in setOf("public reference", "approximated", "needs verification")) findings.error(
                "SOURCE_CONFIDENCE", "$field must be public reference, approximated, or needs verification", field)
        }
        for (field in listOf("camera_class", "board")) {
            if (record.has(field) && (record.opt(field) !is String || record.text(field).isBlank() || record.text(field).hasControls())) findings.error(
                "CORPUS_TEXT", "$field must be non-blank text without control characters", field)
        }
        for (field in listOf("expected_capability_set", "sensor_class_list", "sources")) {
            if (!record.has(field)) continue
            val array = record.optJSONArray(field)
            val values = array?.let { (0 until it.length()).map(it::opt) }
            if (values == null || values.any { it !is String || it.isBlank() }) findings.error(
                "CORPUS_LIST", "$field must be a list of non-blank strings", field)
            else if (values.size != values.toSet().size) findings.error(
                "CORPUS_LIST_DUPLICATE", "$field must not contain duplicates", field)
        }
        if (record.has("drm_security_level") && record.opt("drm_security_level") !in setOf("L1", "L2", "L3")) findings.error(
            "DRM_SECURITY_LEVEL", "drm_security_level must be L1, L2, or L3", "drm_security_level")
    }

    private fun camera(findings: MutableList<Finding>, raw: Any?) {
        if (raw == null || raw == JSONObject.NULL) return
        val cameras = (raw as? JSONObject)?.optJSONArray("cameras")
        if (cameras == null) {
            findings.error("CAMERA", "camera must be an object with a cameras list", "camera")
            return
        }
        if (cameras.length() !in 1..6) {
            findings.error("CAMERA_COUNT", "camera.cameras must hold 1-6 entries, got ${cameras.length()}", "camera.cameras")
            return
        }
        for (index in 0 until cameras.length()) {
            val cam = cameras.optJSONObject(index)
            if (cam == null) {
                findings.error("CAMERA_ENTRY", "camera.cameras[$index] must be an object", "camera.cameras.$index")
                continue
            }
            if (cam.integer("facing") !in 0L..2L) findings.error("CAMERA_FACING",
                "camera.cameras[$index].facing must be 0 (front), 1 (back) or 2 (external)", "camera.cameras.$index.facing")
            if (cam.integer("hardware_level") !in 0L..4L) findings.error("CAMERA_LEVEL",
                "camera.cameras[$index].hardware_level must be an integer in 0-4", "camera.cameras.$index.hardware_level")
            for ((field, bounds) in listOf("pixel_width" to (320L..20000L), "pixel_height" to (240L..20000L))) {
                if (cam.integer(field) !in bounds) findings.error("CAMERA_SIZE",
                    "camera.cameras[$index].$field must be an integer in ${bounds.first}-${bounds.last}", "camera.cameras.$index.$field")
            }
            val w = cam.integer("pixel_width")
            val h = cam.integer("pixel_height")
            if (w != null && h != null && w > 0 && h > 0 && w.toDouble() / h !in 0.5..2.0) findings.warning(
                "CAMERA_ASPECT", "camera.cameras[$index] aspect ratio $w/$h is unusual for a phone sensor", "camera.cameras.$index.pixel_width")
        }
    }

    private fun display(findings: MutableList<Finding>, display: JSONObject) {
        if (display.length() == 0) return
        for (field in listOf("width", "height", "density", "refresh_rate_hz")) {
            val value = display.opt(field)
            if (value !is Int && value !is Long || (value as Number).toLong() <= 0) {
                findings.error("DISPLAY_FIELD", "display.$field must be a positive integer", "display.$field")
                continue
            }
            val (low, high) = when (field) { "width", "height" -> 120L to 8192L; "density" -> 72L to 1000L; else -> 24L to 240L }
            if (value.toLong() !in low..high) findings.warning("DISPLAY_SUSPICIOUS", "display.$field is outside the usual range $low–$high", "display.$field")
        }
        val width = display.integer("width")
        val height = display.integer("height")
        val density = display.integer("density")
        if (width != null && height != null && density != null && width > 0 && height > 0 && density > 0) {
            // Floating point avoids overflowing Long for malformed, very large JSON integers.
            if (maxOf(width, height).toDouble() / minOf(width, height) > 4) findings.warning(
                "DISPLAY_ASPECT", "display.width and display.height imply an aspect ratio above 4:1", "display.width")
            val dp = minOf(width, height).toDouble() * 160 / density
            if (dp !in 120.0..1600.0) findings.warning("DISPLAY_DENSITY_MISMATCH",
                "display.density implies a shortest edge outside 120–1600 dp for these dimensions", "display.density")
        }
        if (display.text("screen_size_class") !in DISPLAY_CLASSES) {
            findings.error("DISPLAY_CLASS", "display.screen_size_class must be small, normal, or large", "display.screen_size_class")
        }
    }

    private fun locale(findings: MutableList<Finding>, locale: JSONObject) {
        for ((field, pattern, description) in listOf(Triple("language", LANG, "a two-letter lowercase code"),
            Triple("country", COUNTRY, "a two-letter uppercase code"), Triple("timezone", TIMEZONE, "an IANA zone like Europe/Berlin"))) {
            if (locale.has(field) && (locale.opt(field) !is String || !pattern.matches(locale.text(field)))) {
                findings.error("LOCALE", "locale.$field must be $description", "locale.$field")
            }
        }
    }

    fun luhnValid(digits: String): Boolean = digits.isNotEmpty() && digits.all { it.isDigit() } &&
        CloakForge.luhnCheckDigit(digits.dropLast(1)) == digits.last() - '0'

    private fun messageAt(message: String, field: String?) = if (field != null && !message.startsWith(field)) "$field: $message" else message
    private fun MutableList<Finding>.error(code: String, message: String, field: String?) = add(Finding(ERROR, code, messageAt(message, field), field))
    private fun MutableList<Finding>.warning(code: String, message: String, field: String?) = add(Finding(WARNING, code, messageAt(message, field), field))
    private fun MutableList<Finding>.info(code: String, message: String, field: String?) = add(Finding(INFO, code, messageAt(message, field), field))

    private fun JSONObject.integer(key: String): Long? = when (val value = opt(key)) {
        is Int -> value.toLong()
        is Long -> value
        else -> null
    }

    /** Python's str(): numbers print without a fraction. */
    private fun JSONObject.text(key: String): String = when (val v = opt(key)) {
        null, JSONObject.NULL -> ""
        is Double -> if (v % 1.0 == 0.0) v.toLong().toString() else v.toString()
        else -> v.toString()
    }

}
