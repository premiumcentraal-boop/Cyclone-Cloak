package dev.cyclone.cloak.ui

import dev.cyclone.cloak.cyclone.CycloneProfile
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CloakSearchTest {
    private val work = CycloneProfile("Cyclone_bbbbbbbbbbbbbbbb", "Work", "profile", "ready", 11,
        listOf("com.example.bank", "com.example.chat"))

    @Test
    fun aBlankQueryMatchesEverything() {
        assertTrue(CloakSearch.matches("", "anything"))
        assertTrue(CloakSearch.matches("   "))
    }

    @Test
    fun matchingIgnoresCaseAndAnyWordOrder() {
        assertTrue(CloakSearch.matches("WORK", "Work profile"))
        assertTrue(CloakSearch.matches("pixel work", "Work Pixel"))
    }

    @Test
    fun everyWordMustAppearInSomeField() {
        assertFalse(CloakSearch.matches("work travel", "Work", "com.example.bank"))
        assertTrue(CloakSearch.matches("work bank", "Work", "com.example.bank"))
    }

    @Test
    fun missingFieldsAreSkipped() {
        assertFalse(CloakSearch.matches("pixel", null, null))
        assertTrue(CloakSearch.matches("pixel", null, "Pixel 7"))
    }

    @Test
    fun aProfileMatchesByLabelIdOrApp() {
        assertTrue(CloakSearch.profile("work", work))
        assertTrue(CloakSearch.profile("bbbb", work))
        assertTrue(CloakSearch.profile("example.bank", work))
        assertFalse(CloakSearch.profile("travel", work))
    }

    @Test
    fun anIdentityMatchesByNameModelOrId() {
        assertTrue(CloakSearch.identity("pixel", "id-1", "Work Pixel", "Pixel 7"))
        assertTrue(CloakSearch.identity("galaxy", "id-2", "Travel", "Galaxy S23"))
        assertTrue(CloakSearch.identity("id-2", "id-2", "Travel", null))
        assertFalse(CloakSearch.identity("galaxy", "id-1", "Work Pixel", "Pixel 7"))
    }
}
