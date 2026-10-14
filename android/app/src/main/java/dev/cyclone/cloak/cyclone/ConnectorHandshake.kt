package dev.cyclone.cloak.cyclone

import com.cyclone.connector.client.CycloneConnectorException
import org.json.JSONObject

/** Interprets existing hello fields; sends no additional wire fields or version numbers. */
object ConnectorHandshake {
    fun validate(hello: JSONObject): JSONObject {
        val contract = hello.opt("contract") as? String ?: return hello // older compatible hosts may omit it
        val major = Regex("cyclone\\.connector/(\\d+)(?:\\.\\d+)?").matchEntire(contract)?.groupValues?.get(1)?.toIntOrNull()
            ?: throw CycloneConnectorException("HANDSHAKE_INVALID", "Cyclone returned an unrecognized connector contract. Install matching Cyclone and Cloak versions, then retry.")
        if (major > 1) throw CycloneConnectorException("CLOAK_NEEDS_UPDATE",
            "Cloak supports connector major 1; Cyclone reports major $major. Update Cyclone Cloak.")
        if (major < 1) throw CycloneConnectorException("CYCLONE_NEEDS_UPDATE",
            "Cyclone reports connector major $major; Cloak requires major 1. Update Cyclone.")
        return hello
    }
}
