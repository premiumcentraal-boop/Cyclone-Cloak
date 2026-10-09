package dev.cyclone.cloak

import java.security.MessageDigest

/**
 * Single source of truth for the per-app state layout, mirrored by the platform
 * module reader (docs/STATE_LAYOUT.md). Changing anything here requires updating
 * the module reader and both test vectors in the same commit.
 */
object CloakStateLayout {
    const val ROOT_DIR = "cyclone-profile-state-v1"
    const val MODULE_STATE_DIR = "/data/adb/cyclone_cloak/state-v1"
    const val STAGING_DIR = "state-staging"
    const val PROFILE_FILE = "profile.json"
    const val PIF_FILE = "pif.json"
    const val INDEX_FILE = "index.json"
    /** One install's share of the index: the members of `entries`, without the braces (see CloakRootDoctor). */
    const val INDEX_PART_FILE = "index.part"

    private val profileIdRegex = Regex("^Cyclone_[a-f0-9]{16}$")
    private val packageRegex = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+$")

    fun key(profileId: String, androidUserId: Int, packageName: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest("$profileId\n$androidUserId\n$packageName".toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    fun validateInputs(profileId: String, androidUserId: Int, packageName: String) {
        require(profileIdRegex.matches(profileId)) { "profileId must be a registry profile" }
        require(androidUserId >= 0) { "androidUserId must be non-negative" }
        require(packageRegex.matches(packageName)) { "packageName must be a valid Android package" }
    }
}