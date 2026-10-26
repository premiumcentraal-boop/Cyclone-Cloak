package dev.cyclone.cloak.root

import org.junit.Assert.assertEquals
import org.junit.Test

class CornerstoneAppsTest {
    private val none: (String) -> Boolean = { false }
    private val cyclone: (String) -> Boolean = { it == CornerstoneApps.CYCLONE }

    @Test
    fun cloakIsAlwaysAnEssentialAndCyclonePresenceDecidesTheRest() {
        assertEquals(listOf(CornerstoneApps.CLOAK), CornerstoneApps.essentials(none))
        assertEquals(listOf(CornerstoneApps.CLOAK, CornerstoneApps.CYCLONE), CornerstoneApps.essentials(cyclone))
    }

    @Test
    fun essentialsComeFirstThenThePickedAppsInOrder() {
        assertEquals(
            listOf(CornerstoneApps.CLOAK, CornerstoneApps.CYCLONE, "com.example.bank", "com.example.chat"),
            CornerstoneApps.plan(listOf("com.example.bank", "com.example.chat"), cyclone),
        )
    }

    @Test
    fun anEssentialPickedByHandIsNotInstalledTwice() {
        assertEquals(
            listOf(CornerstoneApps.CLOAK, "com.example.bank"),
            CornerstoneApps.plan(listOf(CornerstoneApps.CLOAK, "com.example.bank", CornerstoneApps.CLOAK), none),
        )
    }

    @Test
    fun withoutCyclonePickedAppsAreKeptAsTheyAre() {
        assertEquals(listOf(CornerstoneApps.CLOAK), CornerstoneApps.plan(emptyList(), none))
    }
}
