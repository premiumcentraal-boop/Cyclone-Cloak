package dev.cyclone.cloak.forge

import org.json.JSONObject

/** Recognises whatever file the owner picks: a cloak profile, a fleet, a phone, or a real phone's dump. */
object Imports {
    const val PHONE_KIND = "cyclone-cloak-phone"
    const val FLEET_KIND = "cyclone-cloak-fleet"

    sealed class Parsed {
        /** One identity. [findings] are the coherence rules; any error blocks saving it. */
        data class Profile(val profile: JSONObject, val findings: List<ProfileValidator.Finding>) : Parsed()
        data class Fleet(val profiles: List<JSONObject>) : Parsed()
        /** A phone shared from Cloak, or drafted from a dump ([fromDump]): open it in the builder to check or finish. */
        data class Phone(val phone: PhoneTemplate, val findings: List<ProfileValidator.Finding>, val fromDump: Boolean) : Parsed()
    }

    fun parse(text: String, takenPhoneIds: Set<String>): Parsed {
        val trimmed = text.trimStart()
        if (!trimmed.startsWith("{")) {
            val phone = PropImport.draftPhone(text, takenPhoneIds)
            return Parsed.Phone(phone, ProfileValidator.validatePhone(phone), fromDump = true)
        }
        val json = runCatching { JSONObject(trimmed) }.getOrElse { throw IllegalArgumentException("That file isn't valid JSON.") }
        return when (json.optString("kind")) {
            FLEET_KIND -> Parsed.Fleet(CloakFleet.parseFleet(trimmed))
            PHONE_KIND -> {
                val body = json.optJSONObject("phone") ?: throw IllegalArgumentException("That phone file has no phone in it.")
                val id = body.optString("id").takeIf { PhoneTemplate.ID.matches(it) && it !in takenPhoneIds }
                    ?: PhoneTemplate.idFor(body.optString("label", "phone"), takenPhoneIds)
                val phone = PhoneTemplate.fromJson(body.put("id", id))
                Parsed.Phone(phone, ProfileValidator.validatePhone(phone), fromDump = false)
            }
            else -> {
                if (json.optJSONObject("device") == null) {
                    throw IllegalArgumentException("That file isn't a Cyclone Cloak profile, phone or fleet.")
                }
                Parsed.Profile(json, ProfileValidator.validateProfile(json))
            }
        }
    }

    fun exportPhone(phone: PhoneTemplate): JSONObject =
        JSONObject().put("kind", PHONE_KIND).put("schemaVersion", 1).put("phone", phone.toJson())
}
