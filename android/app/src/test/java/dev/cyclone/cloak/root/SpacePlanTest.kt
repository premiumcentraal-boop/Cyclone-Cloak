package dev.cyclone.cloak.root

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SpacePlanTest {
    private val id = "Cloak_0123456789abcdef"

    @Test
    fun createUsesMainAsParentAndTheSpaceNameOnly() {
        assertEquals(listOf("/system/bin/pm", "create-user", "--profileOf", "0", "--managed", id), SpacePlan.create(id))
    }

    @Test
    fun createRefusesAnotherNamespace() {
        assertThrows(IllegalArgumentException::class.java) { SpacePlan.create("Cyclone_0123456789abcdef") }
    }

    @Test
    fun appCommandsAreExactAndNeverTargetMain() {
        assertEquals(
            listOf("/system/bin/cmd", "package", "install-existing", "--user", "10", "com.example.app"),
            SpacePlan.install(10, "com.example.app"),
        )
        assertEquals(
            listOf("/system/bin/pm", "list", "packages", "--user", "10", "com.example.app"),
            SpacePlan.listPackages(10, "com.example.app"),
        )
        assertThrows(IllegalArgumentException::class.java) { SpacePlan.install(0, "com.example.app") }
        assertThrows(IllegalArgumentException::class.java) { SpacePlan.removeUser(0) }
    }

    @Test
    fun packageNamesAreValidated() {
        assertThrows(IllegalArgumentException::class.java) { SpacePlan.install(10, "com.example.app;reboot") }
        assertThrows(IllegalArgumentException::class.java) { SpacePlan.listPackages(10, "\$(reboot)") }
    }

    @Test
    fun shellAcceptsOnlyTypedTokens() {
        assertEquals("/system/bin/am start-user -w 10", SpacePlan.shell(SpacePlan.start(10)))
        assertThrows(IllegalArgumentException::class.java) { SpacePlan.shell(listOf("/system/bin/pm", "\$(reboot)")) }
        assertThrows(IllegalArgumentException::class.java) { SpacePlan.shell(emptyList()) }
    }

    @Test
    fun createdUserNeedsASuccessLine() {
        assertEquals(10, SpacePlan.createdUser("Success: created user id 10"))
        assertNull(SpacePlan.createdUser("Error: maximum profiles reached"))
        assertNull(SpacePlan.createdUser("Success: created user id 0"))
    }

    @Test
    fun ownedUserMustBeTheExactManagedSpace() {
        assertEquals(10, SpacePlan.ownedUser("UserInfo{10:$id:30} running", id))
        assertNull(SpacePlan.ownedUser("UserInfo{10:Cloak_fedcba9876543210:30}", id))
        assertNull(SpacePlan.ownedUser("UserInfo{10:$id:10}", id))
        assertNull(SpacePlan.ownedUser("UserInfo{0:$id:30}", id))
        assertNull(SpacePlan.ownedUser("UserInfo{10:$id:30}\nUserInfo{11:$id:30}", id))
    }

    @Test
    fun startedUserNeedsRunningUnlocked() {
        assertTrue(SpacePlan.isRunningUnlocked("RUNNING_UNLOCKED"))
        assertFalse(SpacePlan.isRunningUnlocked("NOT_RUNNING"))
    }

    @Test
    fun listedPackagesIgnoreErrorsAndOtherText() {
        assertEquals(setOf("com.example.app"), SpacePlan.listedPackages("package:com.example.app\nError: unavailable\n"))
    }

    @Test
    fun setupFlagIsReadBackAsOne() {
        assertTrue(SpacePlan.setupComplete("1\n"))
        assertFalse(SpacePlan.setupComplete("0"))
    }

    private fun activities(display: Int, user: Int, packageName: String) = """
        Display #$display (activities from top to bottom):
          mResumedActivity: ActivityRecord{1a2b u$user $packageName/.Main t12}
    """.trimIndent()

    @Test
    fun foregroundOnDisplayZeroIsTheSpaceApp() {
        assertEquals(SpaceTarget("com.example.app", 10), SpacePlan.resumedTarget(activities(0, 10, "com.example.app")))
    }

    @Test
    fun otherDisplaysAndAmbiguousOutputAreNeverTheSpace() {
        assertNull(SpacePlan.resumedTarget(activities(1, 10, "com.example.app")))
        assertNull(
            SpacePlan.resumedTarget(activities(0, 10, "com.example.app") + "\n" + activities(0, 11, "com.example.other")),
        )
        assertNull(SpacePlan.resumedTarget("  mResumedActivity: ActivityRecord{1a2b u10 com.example.app/.Main t12}"))
    }
}
