package dev.cyclone.cloak.ui

import dev.cyclone.cloak.data.CloakBinding

/** Read-only list projection: never alter binding state or reconciliation input. */
internal fun filterBindings(bindings: List<CloakBinding>, profileId: String?, packageQuery: String): List<CloakBinding> {
    val query = packageQuery.trim()
    return bindings.filter {
        (profileId == null || it.profileId == profileId) && it.packageName.contains(query, ignoreCase = true)
    }.sortedWith(compareBy({ it.profileId }, { it.packageName }))
}

/** Without a snapshot, absence cannot be distinguished from a disconnected connector. */
internal fun bindingProfileMissing(profileId: String, knownProfileIds: Set<String>?): Boolean =
    knownProfileIds != null && profileId !in knownProfileIds
