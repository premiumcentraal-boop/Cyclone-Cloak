package dev.cyclone.cloak.forge

import org.json.JSONObject

/**
 * Turns a real phone's `build.prop`, or `adb shell getprop` output, into a draft phone. Ported from
 * forge/cloak_forge/prop_parse.py (`parse_build_prop`, `draft_device`, `draft_phone`); `forge/tests/vectors/dumps.json`
 * pins both. What a dump can't tell (screen size, refresh rate, size class, egress) is left for the owner to fill in.
 */
object PropImport {
    private val GETPROP_LINE = Regex("^\\[([^\\]]+)\\]:\\s*\\[(.*)\\]$")

    private val DEVICE_KEYS = linkedMapOf(
        "manufacturer" to listOf("ro.product.manufacturer"),
        "brand" to listOf("ro.product.brand", "ro.product.manufacturer"),
        "model" to listOf("ro.product.model"),
        "product" to listOf("ro.product.name", "ro.product.model"),
        "device" to listOf("ro.product.device", "ro.product.model"),
        "hardware" to listOf("ro.hardware"),
        "fingerprint" to listOf("ro.build.fingerprint"),
        "version_release" to listOf("ro.build.version.release"),
        "sdk_int" to listOf("ro.build.version.sdk"),
        "security_patch" to listOf("ro.build.version.security_patch"),
        "build_id" to listOf("ro.build.id"),
        "version_incremental" to listOf("ro.build.version.incremental"),
        "build_date_utc" to listOf("ro.build.date.utc"),
        "first_api_level" to listOf("ro.product.first_api_level", "ro.board.first_api_level"),
        "bootloader" to listOf("ro.bootloader"),
        "baseband" to listOf("ro.baseband", "gsm.version.baseband"),
    )

    private val NETWORK_TYPES = mapOf(
        "NR" to "5G", "NR_NSA" to "5G", "NR_SA" to "5G", "5G" to "5G",
        "LTE" to "LTE", "LTE_CA" to "LTE", "IWLAN" to "LTE",
        "HSPA" to "HSPA", "HSPAP" to "HSPA", "HSDPA" to "HSPA", "HSUPA" to "HSPA",
        "UMTS" to "UMTS", "TD_SCDMA" to "UMTS",
        "GSM" to "GSM", "EDGE" to "GSM", "GPRS" to "GSM",
    )

    /** At most this much text is read: real dumps are a few hundred KB at most. */
    const val MAX_CHARS = 2_000_000

    fun parse(text: String): Map<String, String> {
        val props = linkedMapOf<String, String>()
        for (raw in text.take(MAX_CHARS).lineSequence()) {
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#")) continue
            val getprop = GETPROP_LINE.matchEntire(line)
            if (getprop != null) {
                props[getprop.groupValues[1].trim()] = getprop.groupValues[2].trim()
                continue
            }
            val eq = line.indexOf('=')
            if (eq < 0) continue
            props[line.substring(0, eq).trim()] = line.substring(eq + 1).trim()
        }
        return props
    }

    /** True when the text looks like a dump at all (has a model or fingerprint). */
    fun looksLikeDump(props: Map<String, String>): Boolean =
        !props["ro.product.model"].isNullOrBlank() || !props["ro.build.fingerprint"].isNullOrBlank()

    fun draftDevice(props: Map<String, String>): JSONObject {
        fun get(field: String) = DEVICE_KEYS.getValue(field).firstNotNullOfOrNull { props[it]?.takeIf(String::isNotEmpty) } ?: ""
        val sdk = toInt(get("sdk_int"), 0)
        val device = JSONObject()
        for (field in DEVICE_KEYS.keys) {
            when (field) {
                "sdk_int" -> device.put(field, sdk)
                "build_date_utc" -> device.put(field, toLong(get(field), 0))
                "first_api_level" -> device.put(field, toInt(get(field), sdk))
                else -> device.put(field, get(field))
            }
        }
        return device
    }

    /** The template blocks a dump can tell, as `draft_phone` builds them. */
    fun draftBlocks(props: Map<String, String>): JSONObject {
        val phone = JSONObject().put("device", draftDevice(props))

        val telephony = JSONObject()
        firstOfList(props["gsm.sim.operator.alpha"].orEmpty().ifEmpty { props["gsm.operator.alpha"].orEmpty() })
            .takeIf { it.isNotEmpty() }?.let { telephony.put("carrier_name", it) }
        val numeric = firstOfList(props["gsm.sim.operator.numeric"].orEmpty().ifEmpty { props["gsm.operator.numeric"].orEmpty() })
        if (Regex("\\d{5,6}").matches(numeric)) telephony.put("mcc", numeric.take(3)).put("mnc", numeric.drop(3))
        NETWORK_TYPES[firstOfList(props["gsm.network.type"].orEmpty()).uppercase()]?.let { telephony.put("network_type", it) }
        val multisim = props["persist.radio.multisim.config"].orEmpty().lowercase()
        if (multisim in setOf("dsds", "dsda", "tsts")) telephony.put("sim_slot_count", 2)
        else if (multisim.isNotEmpty() || telephony.length() > 0) telephony.put("sim_slot_count", 1)
        if (telephony.length() > 0) phone.put("telephony", telephony)

        val density = toInt(props["ro.sf.lcd_density"].orEmpty(), 0)
        if (density > 0) phone.put("display", JSONObject().put("density", density))

        val locale = JSONObject()
        val tag = props["persist.sys.locale"].orEmpty().ifEmpty { props["ro.product.locale"].orEmpty() }
        val match = Regex("([a-z]{2})[-_]([A-Z]{2}).*").matchEntire(tag)
        if (match != null) {
            locale.put("language", match.groupValues[1]).put("country", match.groupValues[2])
        } else {
            props["ro.product.locale.language"]?.takeIf { Regex("[a-z]{2}").matches(it) }?.let { locale.put("language", it) }
            props["ro.product.locale.region"]?.takeIf { Regex("[A-Z]{2}").matches(it) }?.let { locale.put("country", it) }
        }
        props["persist.sys.timezone"]?.takeIf { it.isNotEmpty() }?.let { locale.put("timezone", it) }
        if (locale.length() > 0) phone.put("locale", locale)
        return phone
    }

    /** A draft phone from a dump, labelled from the model. Unknown parts stay empty for the builder to fill in. */
    fun draftPhone(text: String, taken: Set<String>): PhoneTemplate {
        val props = parse(text)
        require(looksLikeDump(props)) { "That file isn't a build.prop or getprop dump: it names no model or fingerprint." }
        val blocks = draftBlocks(props)
        val device = blocks.getJSONObject("device")
        val label = listOf(device.optString("model"), device.optString("version_release").takeIf { it.isNotEmpty() }?.let { "Android $it" })
            .filterNotNull().filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "Imported phone" }
        return PhoneTemplate.fromJson(blocks.put("id", PhoneTemplate.idFor(label, taken)).put("label", label))
    }

    private fun firstOfList(value: String): String =
        value.split(',').map { it.trim() }.firstOrNull { it.isNotEmpty() && it.lowercase() !in setOf("unknown", "null") } ?: ""

    private fun toInt(value: String, default: Int) = value.trim().toIntOrNull() ?: default
    private fun toLong(value: String, default: Long) = value.trim().toLongOrNull() ?: default
}
