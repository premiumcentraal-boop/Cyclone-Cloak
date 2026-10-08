package dev.cyclone.cloak

import org.json.JSONArray
import org.json.JSONObject
import java.math.BigInteger
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * On-device profile forge, mirroring forge/cloak_forge (derive.py + forge.py).
 * Parity vectors in CloakForgeTest pin every identifier to the Python output,
 * so a fleet forged on the phone matches the same seed forged on the desktop.
 */
object CloakForge {
    private val BASE36 = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    private val VARIANT_NIBBLES = charArrayOf('8', '9', 'a', 'b')
    private val NAMESPACE_URL = "6ba7b811-9dad-11d1-80b4-00c04fd430c8"

    // MARK: public surface

    fun templateNames(): List<String> = listOf("pixel_7", "galaxy_s23")

    /** Builds a full, coherent, deterministic cloak profile for a seed. */
    fun forgeProfile(name: String, seed: String, templateName: String = "pixel_7"): JSONObject {
        val template = requireNotNull(TEMPLATES[templateName]) { "unknown template '$templateName'" }
        val device = template["device"] as Map<String, Any>
        val display = template["display"] as Map<String, Any>
        val screenClass = display["screen_size_class"] as String
        val json = JSONObject()
            .put("schema_version", "0.2")
            .put("id", uuid5("https://cloak.cyclone.dev/$seed/$name"))
            .put("name", name)
            .put("seed", seed)
            .put("device", toJsonObject(template["device"] as Map<String, Any>))
            .put("identifiers", toJsonObject(deriveIdentifierBundle(seed)))
            .put("telephony", toJsonObject(template["telephony"] as Map<String, Any>))
            .put("network", JSONObject().put("egress_hint", template["egress_hint"] as String))
            .put("display", toJsonObject(display))
            .put("locale", toStringJsonObject(template["locale"] as Map<String, String>))
            .put("ua", JSONObject().put("value", buildUa(device, screenClass, seed)))
            .put(
                "integrity",
                JSONObject().put("verdict", "unknown").put("checked_at", JSONObject.NULL).put("keybox_tag", JSONObject.NULL),
            )
            .put("health", JSONObject().put("state", "new").put("last_seen", JSONObject.NULL))
            .put("meta", JSONObject().put("source", "forge").put("forge_version", "0.2.0").put("template", templateName))
        return json
    }

    /** Deterministic fleet seed: same name + template, same identity. */
    fun fleetSeed(template: String, name: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest("fleet-seed:$template:$name".toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    /** Builds the pif-style posture body from a profile (see CloakPif). */
    fun buildPif(profile: JSONObject): JSONObject = CloakPif.fromProfile(profile)

    // MARK: identifier derivation (must mirror derive.py exactly)

    fun deriveIdentifierBundle(seed: String): Map<String, String> = mapOf(
        "android_id" to deriveAndroidId(seed),
        "advertising_id" to deriveUuid(seed, "advertising_id"),
        "app_set_id" to deriveUuid(seed, "app_set_id"),
        "mac" to deriveMac(seed, "wlan0"),
        "bt_mac" to deriveMac(seed, "bt"),
        "imei_primary" to deriveImei(seed, 0),
        "imei_secondary" to deriveImei(seed, 1),
        "sim_serial" to deriveSimSerial(seed),
        "gsf_id" to hexDigest(seed, "gsf_id", 8),
        "widevine_id" to hexDigest(seed, "widevine_id", 32),
        "serial" to deriveSerial(seed),
    )

    fun deriveAndroidId(seed: String): String {
        var value = hexDigest(seed, "android_id", 8)
        if (value == "0".repeat(16)) value = hexDigest(seed, "android_id:1", 8)
        return value
    }

    fun deriveMac(seed: String, iface: String): String {
        val raw = digest(seed, "mac:$iface", 6)
        val first = (raw[0].toInt() and 0xFF or 0x02) and 0xFE
        val octets = intArrayOf(first) + raw.sliceArray(1..5).map { it.toInt() and 0xFF }
        return octets.joinToString(":") { "%02X".format(it) }
    }

    fun deriveImei(seed: String, slot: Int): String {
        var digits = toBigInteger(digest(seed, "imei:$slot", 8))
            .mod(BigInteger.TEN.pow(14)).toString().padStart(14, '0')
        if (digits[0] == '0') digits = "9" + digits.substring(1)
        return digits + luhnCheckDigit(digits)
    }

    fun deriveSimSerial(seed: String): String {
        var digits = toBigInteger(digest(seed, "sim_serial", 8))
            .mod(BigInteger.TEN.pow(18)).toString().padStart(18, '0')
        if (digits[0] == '0') digits = "9" + digits.substring(1)
        return digits + luhnCheckDigit(digits)
    }

    fun deriveSerial(seed: String): String {
        val raw = digest(seed, "serial", 8)
        var value = BigInteger(1, raw)
        val chars = mutableListOf<Char>()
        while (value.signum() > 0 && chars.size < 12) {
            val rem = value.divideAndRemainder(BigInteger.valueOf(36))
            chars.add(BASE36[rem[1].toInt()])
            value = rem[0]
        }
        return chars.reversed().joinToString("").padStart(12, '0')
    }

    fun luhnCheckDigit(firstDigits: String): Int {
        var sum = 0
        val padded = firstDigits + "0"
        padded.reversed().forEachIndexed { index, ch ->
            var value = ch - '0'
            if (index % 2 == 1) {
                value *= 2
                if (value > 9) value -= 9
            }
            sum += value
        }
        return (10 - sum % 10) % 10
    }

    private fun toBigInteger(bytes: ByteArray) = BigInteger(1, bytes)

    private fun hexDigest(seed: String, field: String, nbytes: Int): String =
        digest(seed, field, nbytes).joinToString("") { "%02x".format(it) }

    private fun digest(seed: String, field: String, nbytes: Int): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(seed.toByteArray(Charsets.US_ASCII), "HmacSHA256"))
        return mac.doFinal(field.toByteArray(Charsets.US_ASCII)).copyOf(nbytes)
    }

    private fun deriveUuid(seed: String, field: String): String {
        val h = hexDigest(seed, field, 16)
        val variant = VARIANT_NIBBLES[Character.digit(h[16], 16) % 4]
        val shaped = h.substring(0, 12) + "4" + h.substring(13, 16) + variant + h.substring(17)
        return shaped.substring(0, 8) + "-" + shaped.substring(8, 12) + "-" + shaped.substring(12, 16) +
            "-" + shaped.substring(16, 20) + "-" + shaped.substring(20)
    }

    private fun uuid5(name: String): String {
        val namespace = hexToBytes(NAMESPACE_URL.replace("-", ""))
        val digest = MessageDigest.getInstance("SHA-1")
            .digest(namespace + name.toByteArray(Charsets.US_ASCII)).copyOf(16)
        digest[6] = ((digest[6].toInt() and 0x0F) or 0x50).toByte()
        digest[8] = ((digest[8].toInt() and 0x3F) or 0x80).toByte()
        val hex = digest.joinToString("") { "%02x".format(it) }
        return hex.substring(0, 8) + "-" + hex.substring(8, 12) + "-" + hex.substring(12, 16) +
            "-" + hex.substring(16, 20) + "-" + hex.substring(20)
    }

    private fun hexToBytes(hex: String): ByteArray =
        ByteArray(hex.length / 2) { ((Character.digit(hex[2 * it], 16) shl 4) + Character.digit(hex[2 * it + 1], 16)).toByte() }

    // MARK: user agent (mirrors forge.py build_ua)

    fun buildUa(device: Map<String, Any>, screenClass: String, seed: String): String {
        val raw = digest(seed, "chrome_version", 8)
        val value = toBigInteger(raw)
        val major = 100 + (value % BigInteger.valueOf(31)).toInt()
        val build = 1000 + (value.shiftRight(5).mod(BigInteger.valueOf(9000))).toInt()
        val patch = 1 + (value.shiftRight(17).mod(BigInteger.valueOf(99))).toInt()
        val token = if (screenClass != "large") "Mobile Safari" else "Safari"
        return "Mozilla/5.0 (Linux; Android ${device["version_release"]}; ${device["model"]}) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/$major.0.$build.$patch $token/537.36"
    }

    // MARK: templates (mirrored from forge/cloak_forge/templates.py)

    private val TEMPLATES: Map<String, Map<String, Any>> = mapOf(
        "pixel_7" to mapOf(
            "device" to mapOf(
                "manufacturer" to "Google",
                "brand" to "google",
                "model" to "Pixel 7",
                "product" to "panther",
                "device" to "panther",
                "hardware" to "gs101",
                "fingerprint" to "google/panther/panther:13/TQ3A.230805.001/10193186:user/release-keys",
                "version_release" to "13",
                "sdk_int" to 33,
                "security_patch" to "2023-08-05",
                "build_id" to "TQ3A.230805.001",
                "version_incremental" to "10193186",
                "build_date_utc" to 1691712000L,
                "first_api_level" to 33,
                "bootloader" to "panther-1.0-8769422",
                "baseband" to "g5123-230712-230712-B10046521",
            ),
            "telephony" to mapOf(
                "sim_slot_count" to 1,
                "carrier_name" to "T-Mobile",
                "mcc" to "310",
                "mnc" to "260",
                "network_type" to "5G",
            ),
            "egress_hint" to "us-east",
            "display" to mapOf(
                "width" to 1080,
                "height" to 2400,
                "density" to 420,
                "refresh_rate_hz" to 90,
                "screen_size_class" to "large",
            ),
            "locale" to mapOf(
                "language" to "en",
                "country" to "US",
                "timezone" to "America/New_York",
            ),
        ),
        "galaxy_s23" to mapOf(
            "device" to mapOf(
                "manufacturer" to "samsung",
                "brand" to "samsung",
                "model" to "SM-S911B",
                "product" to "beyond1q",
                "device" to "beyond1q",
                "hardware" to "exynos2200",
                "fingerprint" to "samsung/beyond1q/beyond1q:13/TP1A.220624.014/S911BXXU2AWA1:user/release-keys",
                "version_release" to "13",
                "sdk_int" to 33,
                "security_patch" to "2023-08-01",
                "build_id" to "TP1A.220624.014",
                "version_incremental" to "S911BXXU2AWA1",
                "build_date_utc" to 1691952000L,
                "first_api_level" to 33,
                "bootloader" to "beyond1q-1.0-24551412",
                "baseband" to "S911BXXU2AWA1",
            ),
            "telephony" to mapOf(
                "sim_slot_count" to 2,
                "carrier_name" to "Vodafone",
                "mcc" to "262",
                "mnc" to "02",
                "network_type" to "5G",
            ),
            "egress_hint" to "eu-central",
            "display" to mapOf(
                "width" to 1080,
                "height" to 2340,
                "density" to 420,
                "refresh_rate_hz" to 120,
                "screen_size_class" to "large",
            ),
            "locale" to mapOf(
                "language" to "de",
                "country" to "DE",
                "timezone" to "Europe/Berlin",
            ),
        ),
    )

    private fun toJsonObject(map: Map<String, Any>): JSONObject {
        val json = JSONObject()
        for ((key, value) in map) {
            when (value) {
                is Int -> json.put(key, value)
                is Long -> json.put(key, value)
                else -> json.put(key, value.toString())
            }
        }
        return json
    }

    private fun toStringJsonObject(map: Map<String, String>): JSONObject {
        val json = JSONObject()
        for ((key, value) in map) json.put(key, value)
        return json
    }
}
