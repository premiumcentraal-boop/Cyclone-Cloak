package dev.cyclone.cloak.cyclone

import dev.cyclone.cloak.data.*
import dev.cyclone.cloak.root.*
import android.content.Context
import com.cyclone.connector.client.ProfileBehaviorProvider
import org.json.JSONObject

class CloakStartupProvider(context: Context) : ProfileBehaviorProvider(context.applicationContext) {
    override fun beforeLaunch(event: JSONObject): JSONObject? {
        val profileId = event.optString("profileId")
        val androidUserId = event.optInt("androidUserId", -1)
        val packageName = event.optString("packageName")
        val answer = JSONObject().put("version", 1)
        val binding = CloakBindingStore.find(context, profileId, androidUserId, packageName)
        if (binding == null || !binding.enabled) {
            return answer.put("configRef", JSONObject.NULL).put("state", "ready")
        }
        val outcome = runCatching {
            CloakResolver.resolve(context, profileId, androidUserId, packageName)
        }
        val configRef = outcome.getOrNull()
        return when {
            outcome.isSuccess && configRef != null -> answer.put("configRef", configRef).put("state", "ready")
            outcome.isSuccess -> answer.put("configRef", JSONObject.NULL).put("state", "degraded")
            else -> answer.put("configRef", JSONObject.NULL).put("state", "degraded")
        }
    }
}
