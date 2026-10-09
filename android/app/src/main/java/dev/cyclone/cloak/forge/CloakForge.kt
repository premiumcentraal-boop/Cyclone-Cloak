package dev.cyclone.cloak.forge

import org.json.JSONArray
import org.json.JSONObject
import java.math.BigInteger
import java.security.MessageDigest
import java.security.SecureRandom
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

    /** Builds a full, coherent, deterministic cloak profile for a seed, from [phone]. */
    fun forgeProfile(name: String, seed: String, phone: PhoneTemplate): JSONObject {
        val device = phone.device
        val screenClass = phone.display.optString("screen_size_class", "large")
        return JSONObject()
            .put("schema_version", "0.2")
            .put("id", uuid5("https://cloak.cyclone.dev/$seed/$name"))
            .put("name", name)
            .put("seed", seed)
            .put("device", JSONObject(device.toString()))
            .put("identifiers", JSONObject(deriveIdentifierBundle(seed)))
            .put("telephony", JSONObject(phone.telephony.toString()))
            .put("network", JSONObject(phone.network.toString()))
            .put("display", JSONObject(phone.display.toString()))
            .put("locale", JSONObject(phone.locale.toString()))
            .put("ua", JSONObject().put("value", buildUa(device, screenClass, seed)))
            .put(
                "integrity",
                JSONObject().put("verdict", "unknown").put("checked_at", JSONObject.NULL).put("keybox_tag", JSONObject.NULL),
            )
            .put("health", JSONObject().put("state", "new").put("last_seen", JSONObject.NULL))
            .put("meta", JSONObject().put("source", "forge").put("forge_version", "0.2.0").put("template", phone.id))
    }

    /** Creates a fresh on-device identity with an independent cryptographic seed. */
    fun forgeNewProfile(name: String, phone: PhoneTemplate): JSONObject {
        val cleanName = name.trim()
        require(cleanName.isNotEmpty()) { "Enter a name for this device identity." }
        require(cleanName.length <= 64) { "Device identity names can be up to 64 characters." }
        val seedBytes = ByteArray(32).also(SecureRandom()::nextBytes)
        val seed = seedBytes.joinToString("") { "%02x".format(it) }
        seedBytes.fill(0)
        return forgeProfile(cleanName, seed, phone)
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

    fun buildUa(device: JSONObject, screenClass: String, seed: String): String {
        val raw = digest(seed, "chrome_version", 8)
        val value = toBigInteger(raw)
        val major = 100 + (value % BigInteger.valueOf(31)).toInt()
        val build = 1000 + (value.shiftRight(5).mod(BigInteger.valueOf(9000))).toInt()
        val patch = 1 + (value.shiftRight(17).mod(BigInteger.valueOf(99))).toInt()
        val token = if (screenClass != "large") "Mobile Safari" else "Safari"
        return "Mozilla/5.0 (Linux; Android ${device.optString("version_release")}; ${device.optString("model")}) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/$major.0.$build.$patch $token/537.36"
    }
}
