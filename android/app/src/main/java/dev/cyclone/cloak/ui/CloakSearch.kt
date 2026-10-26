package dev.cyclone.cloak.ui

import java.util.Locale

/**
 * Instant search for the Profiles screen. Every word typed must appear in one of a record's fields, in any order and
 * ignoring case, so "work bank" finds a profile named "Work" that binds a bank app. A blank query matches everything.
 */
internal object CloakSearch {
    private val WORDS = Regex("\\s+")

    fun matches(query: String, vararg fields: String?): Boolean {
        val words = query.trim().lowercase(Locale.ROOT).split(WORDS).filter { it.isNotEmpty() }
        if (words.isEmpty()) return true
        val haystack = fields.mapNotNull { it?.lowercase(Locale.ROOT) }
        return words.all { word -> haystack.any { it.contains(word) } }
    }

    /** A Cyclone profile: its name, id and the apps it holds. */
    fun profile(query: String, profile: dev.cyclone.cloak.cyclone.CycloneProfile): Boolean =
        matches(query, profile.label, profile.id, *profile.packages.orEmpty().toTypedArray())

    /** A saved identity: its name, the device model it was built on, and its id. */
    fun identity(query: String, id: String, name: String, model: String?): Boolean = matches(query, name, model, id)
}
