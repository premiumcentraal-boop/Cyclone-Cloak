package dev.cyclone.cloak

import android.content.Context
import com.cyclone.connector.client.ProfileBehaviorProvider
import org.json.JSONObject

class CloakStartupProvider(context: Context) : ProfileBehaviorProvider(context.applicationContext) {
    override fun beforeLaunch(event: JSONObject): JSONObject? {
        val profileId = event.optString("profileId")
        val androidUserId = event.optInt("androidUserId", -1)
        val packageName = event.optString("packageName")
        val binding = CloakBindingStore.find(context, profileId, androidUserId, packageName)
        return when {
            binding == null || !binding.enabled -> JSONObject()
                .put("version", 1)
                .put("configRef", JSONObject.NULL)
                .put("state", "ready")
            else -> JSONObject()
                .put("version", 1)
                .put("configRef", binding.cloakProfileId)
                .put("state", "ready")
        }
    }
}
