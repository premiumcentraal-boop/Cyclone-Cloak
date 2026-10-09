package dev.cyclone.cloak.cyclone

import com.cyclone.connector.client.CycloneConnector
import com.cyclone.connector.client.CycloneConnectorException
import org.json.JSONObject

/** Cloak's side of Cyclone's profiles (contract cyclone.connector/1, minor 3; Cyclone 5.0.0-alpha.122). */

/** The slice of Cyclone's connector API Cloak uses. A refused call throws [CycloneConnectorException]. */
interface CycloneApi {
    fun hello(): JSONObject
    fun profiles(): JSONObject
    fun getConfig(profileId: String, androidUserId: Int, packageName: String): JSONObject
    fun setConfig(profileId: String, androidUserId: Int, packageName: String, value: JSONObject?): JSONObject
    fun configStatus(profileId: String, androidUserId: Int, packageName: String, state: String): JSONObject
    fun events(since: Long): JSONObject
    fun rootStatus(): JSONObject
    fun requestOpenProfile(profileId: String): JSONObject
}

class ConnectorApi(private val cyclone: CycloneConnector) : CycloneApi {
    override fun hello() = cyclone.hello()
    override fun profiles() = cyclone.profiles()
    override fun getConfig(profileId: String, androidUserId: Int, packageName: String) =
        cyclone.getConfig(profileId, androidUserId, packageName)
    override fun setConfig(profileId: String, androidUserId: Int, packageName: String, value: JSONObject?) =
        cyclone.setConfig(profileId, androidUserId, packageName, value)
    override fun configStatus(profileId: String, androidUserId: Int, packageName: String, state: String) =
        cyclone.configStatus(profileId, androidUserId, packageName, state)
    override fun events(since: Long) = cyclone.events(since)
    override fun rootStatus() = cyclone.rootStatus()
    override fun requestOpenProfile(profileId: String) = cyclone.requestOpenProfile(profileId)
}
