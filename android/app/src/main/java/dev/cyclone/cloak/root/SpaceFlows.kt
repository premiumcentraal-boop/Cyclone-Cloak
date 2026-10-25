package dev.cyclone.cloak.root

import dev.cyclone.cloak.data.CloakSpace

/** Runs one fixed Android command as root and returns its output. Throws [SpaceException] when it fails. */
fun interface SpaceShell {
    fun run(args: List<String>): String
}

/** A failure whose message is shown to the owner as it is. */
class SpaceException(message: String) : Exception(message)

/**
 * The create, open and delete sequences. Every step is verified before the next one starts, and every change to a
 * space is saved through [save] before the next Android command runs. A retry therefore resumes instead of duplicating.
 */
class SpaceFlows(
    private val shell: SpaceShell,
    private val save: (CloakSpace) -> Unit,
    private val forget: (String) -> Unit,
    private val sleep: (Long) -> Unit = { Thread.sleep(it) },
) {
    /**
     * Creates or resumes [start], which must be in [CloakSpace.STATE_CREATING]. [seedIdentity] runs only after the
     * Android user is verified, so a failed creation leaves no identity behind.
     */
    fun create(start: CloakSpace, seedIdentity: () -> String): CloakSpace {
        require(start.state == CloakSpace.STATE_CREATING)
        val stored = start.androidUserId.takeIf { it > 0 }
        val owned = SpacePlan.ownedUser(shell.run(SpacePlan.listUsers()), start.id)
        // A journaled user that Android no longer lists is never replaced silently.
        if (stored != null && owned != stored) throw SpaceException(GONE)
        val user = owned ?: SpacePlan.createdUser(shell.run(SpacePlan.create(start.id)))
            ?: throw SpaceException("Android couldn’t create another profile. Your phone may already have its maximum number of profiles.")
        var space = journal(start.copy(androidUserId = user))
        if (SpacePlan.ownedUser(shell.run(SpacePlan.listUsers()), space.id) != user) {
            throw SpaceException("Android couldn’t confirm the new profile.")
        }
        shell.run(SpacePlan.start(user))
        if (!SpacePlan.isRunningUnlocked(shell.run(SpacePlan.startState(user)))) {
            throw SpaceException("Unlock your phone and turn on ${space.label}, then try again.")
        }
        for (packageName in space.packages) {
            shell.run(SpacePlan.install(user, packageName))
            val listed = SpacePlan.listedPackages(shell.run(SpacePlan.listPackages(user, packageName)))
            if (packageName !in listed) throw SpaceException("$packageName couldn’t be added. Try again to continue.")
        }
        shell.run(SpacePlan.markSetupComplete(user))
        if (!SpacePlan.setupComplete(shell.run(SpacePlan.readSetupComplete(user)))) {
            throw SpaceException("${space.label} setup is not complete yet. Please retry.")
        }
        val identity = space.identityId ?: seedIdentity()
        space = journal(space.copy(identityId = identity, state = CloakSpace.STATE_READY))
        return space
    }

    /** Starts the space and asks [launch] to open [packageName] in it, then checks that it is the foreground app. */
    fun open(space: CloakSpace, packageName: String, launch: (SpaceTarget) -> Unit) {
        require(space.ready && packageName in space.packages)
        val expected = SpaceTarget(packageName, space.androidUserId)
        shell.run(SpacePlan.start(space.androidUserId))
        if (!SpacePlan.isRunningUnlocked(shell.run(SpacePlan.startState(space.androidUserId)))) {
            throw SpaceException("Unlock your phone and turn on ${space.label}, then try again.")
        }
        launch(expected)
        repeat(OBSERVE_ATTEMPTS) { attempt ->
            if (SpacePlan.resumedTarget(shell.run(SpacePlan.activities())) == expected) return
            if (attempt < OBSERVE_ATTEMPTS - 1) sleep(OBSERVE_DELAY_MS)
        }
        throw SpaceException("Couldn’t confirm that the app opened in ${space.label}. Try again.")
    }

    /**
     * Unbinds the space, republishes, and only then removes its Android user. Republishing first means no published
     * binding can point at a user that no longer exists. Each step can be repeated after a failure.
     */
    fun delete(
        space: CloakSpace,
        unbind: () -> Unit,
        republish: () -> Boolean,
        forgetIdentity: (String) -> Unit,
    ) {
        journal(space.copy(state = CloakSpace.STATE_DELETING))
        unbind()
        if (!republish()) throw SpaceException("Couldn’t update the published profiles, so ${space.label} was kept. Try again.")
        val user = SpacePlan.ownedUser(shell.run(SpacePlan.listUsers()), space.id)
        if (user != null) {
            shell.run(SpacePlan.removeUser(user))
            if (SpacePlan.ownedUser(shell.run(SpacePlan.listUsers()), space.id) != null) {
                throw SpaceException("Android kept ${space.label}. Try again after closing its apps.")
            }
        }
        space.identityId?.let(forgetIdentity)
        forget(space.id)
    }

    private fun journal(space: CloakSpace): CloakSpace = space.copy(updatedAt = System.currentTimeMillis()).also(save)

    private companion object {
        const val OBSERVE_ATTEMPTS = 10
        const val OBSERVE_DELAY_MS = 200L
        const val GONE = "This saved space is no longer available. Other spaces were left unchanged."
    }
}
