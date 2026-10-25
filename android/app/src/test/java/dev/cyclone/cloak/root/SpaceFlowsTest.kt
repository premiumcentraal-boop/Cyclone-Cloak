package dev.cyclone.cloak.root

import dev.cyclone.cloak.data.CloakSpace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SpaceFlowsTest {
    private val id = "Cloak_0123456789abcdef"
    private val app = "com.example.app"

    /** A stand-in phone that answers the fixed space commands the way Android does, and logs every command. */
    private class FakePhone(private val log: MutableList<String>) {
        val users = linkedMapOf<Int, String>()
        var nextUser = 10
        var started = false
        var locked = false
        var refuseCreate = false
        var refuseInstall = false
        var setupDone = false
        var activities = ""
        val installed = mutableSetOf<String>()

        val shell = SpaceShell { args ->
            log += SpacePlan.shell(args)
            answer(args)
        }

        private fun answer(args: List<String>): String = when {
            args == SpacePlan.listUsers() -> users.entries.joinToString("\n") { (user, name) -> "\tUserInfo{$user:$name:30} running" }
            args.take(3) == listOf("/system/bin/pm", "create-user", "--profileOf") ->
                if (refuseCreate) {
                    "Error: maximum profiles reached"
                } else {
                    val user = nextUser++
                    users[user] = args.last()
                    "Success: created user id $user"
                }
            args[1] == "start-user" -> { started = true; "" }
            args[1] == "get-started-user-state" -> if (started && !locked) "RUNNING_UNLOCKED" else "NOT_RUNNING"
            args[1] == "package" && args[2] == "install-existing" -> { if (!refuseInstall) installed += args.last(); "" }
            args[1] == "list" && args[2] == "packages" -> if (args.last() in installed) "package:${args.last()}" else ""
            args[0] == "/system/bin/settings" && args[3] == "put" -> { setupDone = true; "" }
            args[0] == "/system/bin/settings" && args[3] == "get" -> if (setupDone) "1" else "0"
            args[1] == "remove-user" -> { users.remove(args.last().toInt()); "" }
            args[1] == "activity" -> activities
            else -> error("unexpected command ${SpacePlan.shell(args)}")
        }
    }

    private fun space(state: String = CloakSpace.STATE_CREATING, user: Int = 0, identity: String? = null) =
        CloakSpace(id, "Work", user, listOf(app), identity, state, 1L, 1L)

    private fun flows(phone: FakePhone, log: MutableList<String>, forgotten: MutableList<String> = mutableListOf()) =
        SpaceFlows(phone.shell, save = { log += "save:${it.state}:${it.androidUserId}" }, forget = { forgotten += it }, sleep = {})

    @Test
    fun createSavesTheUserBeforeAnyAppAndEndsReady() {
        val log = mutableListOf<String>()
        val phone = FakePhone(log)
        var seeded = 0
        val result = flows(phone, log).create(space()) { seeded++; "identity-1" }
        assertEquals(CloakSpace.STATE_READY, result.state)
        assertEquals(10, result.androidUserId)
        assertEquals("identity-1", result.identityId)
        assertEquals(1, seeded)
        val journaled = log.indexOf("save:creating:10")
        val installed = log.indexOfFirst { it.contains("install-existing") }
        assertTrue(journaled >= 0 && journaled < installed)
        assertEquals("save:ready:10", log.last())
    }

    @Test
    fun aRetryFindsTheUserByNameInsteadOfCreatingAnother() {
        val log = mutableListOf<String>()
        val phone = FakePhone(log)
        phone.users[10] = id
        phone.nextUser = 11
        val result = flows(phone, log).create(space()) { "identity-1" }
        assertEquals(10, result.androidUserId)
        assertTrue(log.none { it.contains("create-user") })
    }

    @Test
    fun aJournaledUserThatAndroidNoLongerListsIsNeverReplaced() {
        val log = mutableListOf<String>()
        val phone = FakePhone(log)
        val error = assertThrows(SpaceException::class.java) { flows(phone, log).create(space(user = 10)) { "identity-1" } }
        assertTrue(error.message.orEmpty().contains("no longer available"))
        assertTrue(log.none { it.contains("create-user") })
    }

    @Test
    fun aSpaceThatIsNotRunningUnlockedStopsBeforeAppsAndIdentity() {
        val log = mutableListOf<String>()
        val phone = FakePhone(log)
        phone.locked = true
        var seeded = 0
        assertThrows(SpaceException::class.java) { flows(phone, log).create(space()) { seeded++; "identity-1" } }
        assertEquals(0, seeded)
        assertTrue(log.none { it.contains("install-existing") })
    }

    @Test
    fun anAppThatDoesNotInstallStopsSetupBeforeIdentity() {
        val log = mutableListOf<String>()
        val phone = FakePhone(log)
        phone.refuseInstall = true
        var seeded = 0
        val error = assertThrows(SpaceException::class.java) { flows(phone, log).create(space()) { seeded++; "identity-1" } }
        assertTrue(error.message.orEmpty().contains(app))
        assertEquals(0, seeded)
    }

    @Test
    fun aRefusedCreationGivesNoUserNumber() {
        val log = mutableListOf<String>()
        val phone = FakePhone(log)
        phone.refuseCreate = true
        assertThrows(SpaceException::class.java) { flows(phone, log).create(space()) { "identity-1" } }
        assertTrue(phone.users.isEmpty())
    }

    @Test
    fun openStartsTheSpaceAndConfirmsTheForegroundApp() {
        val log = mutableListOf<String>()
        val phone = FakePhone(log)
        phone.users[10] = id
        phone.activities = "Display #0\n  mResumedActivity: ActivityRecord{1 u10 $app/.Main t1}"
        var launched: SpaceTarget? = null
        flows(phone, log).open(space(CloakSpace.STATE_READY, user = 10, identity = "identity-1"), app) { launched = it }
        assertEquals(SpaceTarget(app, 10), launched)
    }

    @Test
    fun openFailsWhenTheForegroundIsSomethingElse() {
        val log = mutableListOf<String>()
        val phone = FakePhone(log)
        phone.users[10] = id
        phone.activities = "Display #0\n  mResumedActivity: ActivityRecord{1 u0 com.example.other/.Main t1}"
        var sleeps = 0
        val flows = SpaceFlows(phone.shell, save = {}, forget = {}, sleep = { sleeps++ })
        assertThrows(SpaceException::class.java) {
            flows.open(space(CloakSpace.STATE_READY, user = 10, identity = "identity-1"), app) {}
        }
        assertEquals(9, sleeps)
    }

    @Test
    fun deleteUnbindsRepublishesThenRemovesTheUserAndTheRecord() {
        val log = mutableListOf<String>()
        val phone = FakePhone(log)
        phone.users[10] = id
        val forgotten = mutableListOf<String>()
        flows(phone, log, forgotten).delete(
            space(CloakSpace.STATE_READY, user = 10, identity = "identity-1"),
            unbind = { log += "unbind" },
            republish = { log += "republish"; true },
            forgetIdentity = { log += "identity:$it" },
        )
        val order = listOf("unbind", "republish").map { log.indexOf(it) } + log.indexOfFirst { it.contains("remove-user") }
        assertTrue(order.zipWithNext().all { (a, b) -> a >= 0 && a < b })
        assertTrue(phone.users.isEmpty())
        assertEquals(listOf(id), forgotten)
    }

    @Test
    fun aFailedRepublishKeepsTheUserAndTheRecord() {
        val log = mutableListOf<String>()
        val phone = FakePhone(log)
        phone.users[10] = id
        val forgotten = mutableListOf<String>()
        assertThrows(SpaceException::class.java) {
            flows(phone, log, forgotten).delete(
                space(CloakSpace.STATE_READY, user = 10, identity = "identity-1"),
                unbind = {},
                republish = { false },
                forgetIdentity = {},
            )
        }
        assertEquals(mapOf(10 to id), phone.users)
        assertTrue(log.none { it.contains("remove-user") })
        assertTrue(forgotten.isEmpty())
    }

    @Test
    fun deleteCanBeRepeatedAfterAnEarlierRunRemovedTheUser() {
        val log = mutableListOf<String>()
        val phone = FakePhone(log)
        val forgotten = mutableListOf<String>()
        flows(phone, log, forgotten).delete(
            space(CloakSpace.STATE_DELETING, user = 10, identity = null),
            unbind = {},
            republish = { true },
            forgetIdentity = {},
        )
        assertNull(log.firstOrNull { it.contains("remove-user") })
        assertEquals(listOf(id), forgotten)
    }
}
