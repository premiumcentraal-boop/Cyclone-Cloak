package dev.cyclone.cloak.cyclone

import org.junit.Assert.*
import org.junit.Test

class CompanionPresenceTest {
    private val yes = PresenceCheck.YES
    private val no = PresenceCheck.NO
    private val ready = CompanionFacts(yes, yes, yes, yes, yes)
    private fun row(facts: CompanionFacts, user: Int? = 16) = CompanionPresence("profile", "Work", user, facts)
    private fun profile(id: String, user: Int?) = CycloneProfile(id, id, "profile", "ready", user, emptyList())
    @Test fun readyRequiresEveryCheck() { assertEquals(CompanionState.READY, row(ready).state) }
    @Test fun missingPackageIsActionable() {
        val row = row(ready.copy(installed = no))
        assertEquals(CompanionState.MISSING, row.state)
        assertTrue(row.reason.contains("profile app selection"))
    }
    @Test fun stoppedOrLockedUserIsNotAMissingApp() {
        val row = row(ready.copy(unlocked = no, launchable = no))
        assertEquals(CompanionState.NOT_LAUNCHABLE, row.state)
        assertTrue(row.reason.contains("unlock"))
        assertEquals(yes, row.facts.installed)
    }
    @Test fun unresolvedLauncherIsNotReady() { assertEquals(CompanionState.NOT_LAUNCHABLE, row(ready.copy(launchable = no)).state) }
    @Test fun missingDirectoryIsNotReady() { assertEquals(CompanionState.STORAGE_UNAVAILABLE, row(ready.copy(storageExists = no)).state) }
    @Test fun unwritableDirectoryIsNotReady() { assertEquals(CompanionState.STORAGE_UNAVAILABLE, row(ready.copy(storageWritable = no)).state) }
    @Test fun rootDirectoryPresenceDoesNotProveAppWriteAccess() {
        val row = row(ready.copy(storageWritable = PresenceCheck.UNKNOWN))
        assertEquals(CompanionState.NOT_VERIFIED, row.state)
        assertTrue(row.reason.contains("app-process"))
    }
    @Test fun missingAssignmentNeverQueriesUserZero() {
        val queried = mutableListOf<Int>()
        val rows = CompanionPresenceLookup.inspect(listOf(profile("missing", null), profile("invalid", -1), profile("work", 16))) {
            queried += it; ready
        }
        assertEquals(listOf(16), queried)
        assertEquals(listOf(null, null, 16), rows.map { it.user })
    }
    @Test fun exactUserAssignmentIsUsedAndRepeatedUsersAreQueriedOnce() {
        val queried = mutableListOf<Int>()
        val rows = CompanionPresenceLookup.inspect(listOf(profile("first", 14), profile("second", 16), profile("third", 14))) {
            queried += it; if (it == 16) ready else CompanionFacts(installed = no)
        }
        assertEquals(listOf(14, 16), queried)
        assertEquals(listOf(CompanionState.MISSING, CompanionState.READY, CompanionState.MISSING), rows.map { it.state })
    }
    @Test fun probeFailureIsUnverifiedNotMissing() {
        val rows = CompanionPresenceLookup.inspect(listOf(profile("work", 16))) { error("root refused") }
        assertEquals(CompanionState.NOT_VERIFIED, rows.single().state)
    }
    @Test fun fixedMarkerParsingPreservesUnknownWriteAccess() {
        val result = CompanionPresenceLookup.parse("CLOAK_INSTALLED=YES\nCLOAK_UNLOCKED=NO\nCLOAK_LAUNCHABLE=NO\nCLOAK_STORAGE=YES\nCLOAK_PRESENCE_END\n")
        assertEquals(CompanionFacts(yes, no, no, yes), result)
    }
    @Test fun malformedTruncatedOrDuplicatedMarkersNeverPromoteReady() {
        assertEquals(CompanionFacts(), CompanionPresenceLookup.parse("CLOAK_INSTALLED=YES"))
        assertEquals(PresenceCheck.UNKNOWN, CompanionPresenceLookup.parse("CLOAK_INSTALLED=YES\nCLOAK_INSTALLED=NO\nCLOAK_PRESENCE_END").installed)
        assertEquals(CompanionFacts(), CompanionPresenceLookup.parse("Permission denied\nCLOAK_PRESENCE_END"))
    }
}
