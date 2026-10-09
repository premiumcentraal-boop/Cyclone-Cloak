package dev.cyclone.cloak.root

import android.content.Context
import dev.cyclone.cloak.data.CloakBinding
import dev.cyclone.cloak.data.InstallPlacement

/** Which bindings this install publishes into the shared root state, and for whom. */
data class PublishScope(val androidUserId: Int, val isMain: Boolean, val liveUsers: List<Int>?) {
    /** Main publishes its own bindings for every profile; a profile's Cloak only its own bindings for its own user. */
    fun publishes(binding: CloakBinding): Boolean =
        binding.origin == CloakBinding.ORIGIN_LOCAL && (isMain || binding.androidUserId == androidUserId)

    companion object {
        val MAIN_DEFAULT = PublishScope(0, true, null)

        /**
         * Until Cyclone has told us where we are, user 0 is Main and any other user publishes only its own apps
         * (never anyone else's).
         */
        fun forThisInstall(context: Context): PublishScope {
            val me = InstallPlacement.myUser()
            val isMain = InstallPlacement.placement(context)?.let { it == InstallPlacement.MAIN } ?: (me == 0)
            return PublishScope(me, isMain, if (isMain) InstallPlacement.liveUsers(context) else null)
        }
    }
}
