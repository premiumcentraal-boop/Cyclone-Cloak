package dev.cyclone.cloak.root

import dev.cyclone.cloak.data.CloakSpace

/** An app shown in a space: its package, on the space's Android user. */
data class SpaceTarget(val packageName: String, val androidUserId: Int)

/**
 * The fixed Android commands a space needs, and the parsers that decide whether each step worked. Commands are built
 * here from typed values only. Nothing takes shell text from a caller, a connector or a model.
 */
object SpacePlan {
    const val MAIN_USER = 0
    private val packagePattern = Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+")
    private val shellToken = Regex("[A-Za-z0-9_./:-]+")
    /** The managed-profile bit in the flags Android prints in `UserInfo{id:name:flags}`. */
    private const val FLAG_MANAGED_PROFILE = 0x20

    fun create(id: String): List<String> {
        require(CloakSpace.isSpaceId(id))
        return listOf("/system/bin/pm", "create-user", "--profileOf", MAIN_USER.toString(), "--managed", id)
    }

    fun listUsers(): List<String> = listOf("/system/bin/pm", "list", "users")

    fun start(user: Int): List<String> = listOf("/system/bin/am", "start-user", "-w", spaceUser(user).toString())

    fun startState(user: Int): List<String> =
        listOf("/system/bin/am", "get-started-user-state", spaceUser(user).toString())

    fun install(user: Int, packageName: String): List<String> {
        require(packageName.matches(packagePattern))
        return listOf("/system/bin/cmd", "package", "install-existing", "--user", spaceUser(user).toString(), packageName)
    }

    fun listPackages(user: Int, packageName: String): List<String> {
        require(packageName.matches(packagePattern))
        return listOf("/system/bin/pm", "list", "packages", "--user", spaceUser(user).toString(), packageName)
    }

    fun markSetupComplete(user: Int): List<String> =
        listOf("/system/bin/settings", "--user", spaceUser(user).toString(), "put", "secure", "user_setup_complete", "1")

    fun readSetupComplete(user: Int): List<String> =
        listOf("/system/bin/settings", "--user", spaceUser(user).toString(), "get", "secure", "user_setup_complete")

    fun removeUser(user: Int): List<String> = listOf("/system/bin/pm", "remove-user", spaceUser(user).toString())

    fun activities(): List<String> = listOf("/system/bin/dumpsys", "activity", "activities")

    /** Joins typed arguments into one shell line. Refuses any token outside the allowlist. */
    fun shell(args: List<String>): String {
        require(args.isNotEmpty() && args.all { it.matches(shellToken) })
        return args.joinToString(" ")
    }

    /** The user number Android printed for the created space, or null for anything else. */
    fun createdUser(output: String): Int? = Regex("Success: created user id ([0-9]+)").find(output)
        ?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it > 0 }

    /** The one managed user named exactly [id], or null when there is none or several. */
    fun ownedUser(output: String, id: String): Int? {
        require(CloakSpace.isSpaceId(id))
        return Regex("UserInfo\\{([0-9]+):${Regex.escape(id)}:([a-fA-F0-9]+)\\}")
            .findAll(output).mapNotNull { match ->
                val user = match.groupValues[1].toIntOrNull()
                val flags = match.groupValues[2].toIntOrNull(16)
                user?.takeIf { it > 0 && flags != null && (flags and FLAG_MANAGED_PROFILE) != 0 }
            }.toList().singleOrNull()
    }

    fun isRunningUnlocked(output: String): Boolean = output.contains("RUNNING_UNLOCKED")

    fun setupComplete(output: String): Boolean = output.trim() == "1"

    /** Package names from `pm list packages`, limited to lines that name a valid package. */
    fun listedPackages(output: String): Set<String> = output.lineSequence().filter { it.startsWith("package:") }
        .map { it.removePrefix("package:").trim() }.filter { it.matches(packagePattern) }.toSet()

    /**
     * The one resumed activity on display 0, read from `dumpsys activity activities`. Output with no display section,
     * several candidates, or a resumed activity on another display gives null. An unclear foreground is never reported
     * as the space.
     */
    fun resumedTarget(output: String): SpaceTarget? {
        var display: Int? = null
        val targets = mutableListOf<SpaceTarget>()
        output.lineSequence().forEach { line ->
            Regex("Display #([0-9]+)").find(line)?.let { display = it.groupValues[1].toInt() }
            if (line.contains("mResumedActivity:")) {
                val match = Regex("\\bu([0-9]+) ([A-Za-z0-9_.]+)/").find(line)
                if (display == null) return null
                if (match != null && display == 0) targets += SpaceTarget(match.groupValues[2], match.groupValues[1].toInt())
            }
        }
        return targets.singleOrNull()
    }

    private fun spaceUser(user: Int): Int {
        require(user > MAIN_USER)
        return user
    }
}
