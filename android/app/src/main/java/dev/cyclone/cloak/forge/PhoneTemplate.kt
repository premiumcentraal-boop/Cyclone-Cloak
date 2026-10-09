package dev.cyclone.cloak.forge

import org.json.JSONArray
import org.json.JSONObject

/**
 * A phone Cloak can make identities from: everything about the device except what is unique per identity
 * (identifiers, user agent, seed). Built-in phones come from `catalog/phones.json`; the owner adds their own by
 * importing a profile or a `build.prop`, cloning, or building one in the app.
 */
data class PhoneTemplate(
    val id: String,
    val label: String,
    val device: JSONObject,
    val telephony: JSONObject,
    val network: JSONObject,
    val display: JSONObject,
    val locale: JSONObject,
    val builtIn: Boolean = false,
) {
    /** "Google · Android 13 · panther". */
    val summary: String get() = listOf(
        device.optString("manufacturer"),
        device.optString("version_release").takeIf { it.isNotBlank() }?.let { "Android $it" }.orEmpty(),
        device.optString("device"),
    ).filter { it.isNotBlank() }.joinToString(" · ")

    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("label", label)
        .put("device", JSONObject(device.toString()))
        .put("telephony", JSONObject(telephony.toString()))
        .put("network", JSONObject(network.toString()))
        .put("display", JSONObject(display.toString()))
        .put("locale", JSONObject(locale.toString()))

    companion object {
        val ID = Regex("^[a-z0-9][a-z0-9_-]{0,47}$")
        private val BLOCKS = listOf("device", "telephony", "network", "display", "locale")

        fun fromJson(o: JSONObject, builtIn: Boolean = false): PhoneTemplate {
            val id = o.optString("id")
            require(ID.matches(id)) { "phone id must be lowercase letters, digits, - or _" }
            val label = o.optString("label").trim().ifBlank { id }
            val blocks = BLOCKS.associateWith { JSONObject((o.optJSONObject(it) ?: JSONObject()).toString()) }
            return PhoneTemplate(id, label.take(48), blocks.getValue("device"), blocks.getValue("telephony"),
                blocks.getValue("network"), blocks.getValue("display"), blocks.getValue("locale"), builtIn)
        }

        /** The phone a profile was made from (any profile: forged, imported or from a dump). */
        fun fromProfile(id: String, label: String, profile: JSONObject): PhoneTemplate = fromJson(
            JSONObject().put("id", id).put("label", label).also { o ->
                BLOCKS.forEach { block -> profile.optJSONObject(block)?.let { o.put(block, it) } }
            },
        )

        /** A safe, unique id for a new phone from its label. */
        fun idFor(label: String, taken: Set<String>): String {
            val base = label.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_').take(40).ifBlank { "phone" }
            if (base !in taken) return base
            return (2..999).asSequence().map { "${base}_$it" }.first { it !in taken }
        }
    }
}

/** The built-in phones (`catalog/phones.json`, packaged as an asset). */
object PhoneCatalog {
    const val ASSET = "phones.json"

    fun parse(text: String): List<PhoneTemplate> {
        val root = JSONObject(text)
        require(root.optInt("schemaVersion") == 1) { "unsupported phone catalog" }
        val phones = root.optJSONArray("phones") ?: JSONArray()
        return (0 until phones.length()).map { PhoneTemplate.fromJson(phones.getJSONObject(it), builtIn = true) }
    }
}
