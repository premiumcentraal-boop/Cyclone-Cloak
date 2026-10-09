package dev.cyclone.cloak.cyclone

import dev.cyclone.cloak.data.*
import org.json.JSONObject

/**
 * Cloak in a Cyclone profile learns what Cloak in Main bound for it from the published root state: the index entries
 * for its own Android user and profile id. Pure.
 */
object CloakMirror {
    fun fromIndex(indexText: String, profileId: String, androidUserId: Int, mainUser: Int?): List<CloakBinding> {
        val entries = runCatching { JSONObject(indexText).optJSONObject("entries") }.getOrNull() ?: return emptyList()
        val prefix = "$androidUserId/"
        return entries.keys().asSequence().filter { it.startsWith(prefix) }.mapNotNull { key ->
            val entry = entries.optJSONObject(key) ?: return@mapNotNull null
            val packageName = key.removePrefix(prefix)
            if (entry.opt("profileId") != profileId) return@mapNotNull null
            val publisher = integer(entry.opt("publisher"))
            // Only Main's bindings are mirrored; this install's own are already local.
            if (publisher != null && publisher == androidUserId) return@mapNotNull null
            if (mainUser != null && publisher != null && publisher != mainUser) return@mapNotNull null
            val cloakProfileId = (entry.opt("cloakProfileId") as? String)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            CloakBinding(
                profileId = profileId,
                androidUserId = androidUserId,
                packageName = packageName,
                cloakProfileId = cloakProfileId,
                revision = 0,
                enabled = true,
                updatedAt = 0,
                state = "unknown",
                origin = CloakBinding.ORIGIN_MAIN,
                mirroredSummary = entry.optJSONObject("summary")?.toString(),
            )
        }.filter { runCatching { CloakBindingStore.validate(it.profileId, it.androidUserId, it.packageName) }.isSuccess }
            .sortedBy { it.packageName }
            .toList()
    }
}
