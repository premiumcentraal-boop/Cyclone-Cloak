package dev.cyclone.cloak.ui

import dev.cyclone.cloak.data.CloakBinding
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class BindingReviewTest {
    private val a = "Cyclone_aaaaaaaaaaaaaaaa"
    private val b = "Cyclone_bbbbbbbbbbbbbbbb"
    private fun binding(profile: String, pkg: String, enabled: Boolean = true) =
        CloakBinding(profile, 11, pkg, "identity", 3, enabled, 100, if (enabled) "ready" else "disabled")
    private val rows = listOf(binding(b, "com.example.chat"), binding(a, "com.example.maps", false), binding(a, "com.example.chat"))

    @Test
    fun unfilteredListIsSortedWithoutLosingDisabledBindings() {
        val original = rows.toList()
        assertEquals(listOf(rows[2], rows[1], rows[0]), filterBindings(rows, null, ""))
        assertEquals(original, rows)
        assertSame(rows[1], filterBindings(rows, a, "maps").single())
    }

    @Test
    fun profileFilterUsesStableIdsAndCombinesWithPackageSearch() {
        assertEquals(listOf(rows[2]), filterBindings(rows, a, "chat"))
        assertEquals(listOf(rows[0]), filterBindings(rows, b, "chat"))
        assertTrue(filterBindings(rows, b, "maps").isEmpty())
    }

    @Test
    fun packageSearchIsTrimmedCaseInsensitiveAndSupportsPartialNames() {
        assertEquals(listOf(rows[2], rows[0]), filterBindings(rows, null, "  EXAMPLE.CH  "))
        assertEquals(3, filterBindings(rows, null, "   ").size)
    }

    @Test
    fun missingAndUnknownFiltersDoNotFallBackToAllBindings() {
        assertTrue(filterBindings(rows, "missing", "").isEmpty())
        assertTrue(filterBindings(rows, null, "absent.package").isEmpty())
    }

    @Test
    fun missingProfileRequiresAKnownSnapshot() {
        assertFalse(bindingProfileMissing(a, null))
        assertFalse(bindingProfileMissing(a, setOf(a)))
        assertTrue(bindingProfileMissing(b, setOf(a)))
        assertTrue(bindingProfileMissing(a, emptySet()))
    }
}
