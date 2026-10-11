package dev.cyclone.cloak.forge

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ProfileUniquenessTest {
    private fun profile(id: String, vararg identifiers: Pair<String, Any>) = JSONObject().put("id", id)
        .put("identifiers", JSONObject(mapOf(*identifiers)))
    private fun index(vararg profiles: JSONObject) = ProfileUniqueness(profiles.map { it.getString("id") to it })

    @Test fun noDuplicatesAndOwnProfileExcluded() {
        val a = profile("a", "android_id" to "abcdef1234567890")
        val b = profile("b", "android_id" to "1234567890abcdef")
        val index = index(a, b)
        assertTrue(index.duplicates.isEmpty())
        assertTrue(index.findings(a).isEmpty())
    }
    @Test fun oneDuplicateHasStableFieldReasonAndNoRawValue() {
        val a = profile("a", "android_id" to "abcdef1234567890")
        val b = profile("b", "android_id" to "ABCDEF1234567890")
        val result = index(a, b)
        assertEquals(1, result.duplicates.size)
        val finding = result.findings(b).single()
        assertEquals("DUPLICATE_IDENTIFIER", finding.code)
        assertEquals("warning", finding.severity)
        assertEquals("identifiers.android_id", finding.field)
        assertFalse(finding.message.contains("abcdef1234567890"))
    }
    @Test fun multipleDuplicatesCoverEverySupportedFieldAndAreDeterministic() {
        val bundle = CloakForge.deriveIdentifierBundle("ab".repeat(32))
        val a = profile("a", *bundle.entries.map { it.key to it.value }.toTypedArray())
        val b = JSONObject(a.toString()).put("id", "b")
        assertEquals(ProfileUniqueness.fields.size, index(a, b).duplicates.size)
        assertEquals(index(a,b).findings(a), index(b,a).findings(a))
        assertEquals(index(a,b).findings(a).map { it.field }.sortedBy { it }, index(a,b).findings(a).map { it.field })
    }
    @Test fun regeneratingResolvesConflictWithoutMutatingSourceOrOtherFields() {
        val a = profile("a", "android_id" to "abcdef1234567890", "serial" to "KEEP")
        val b = JSONObject(a.toString()).put("id", "b").put("seed", "original")
        val before = b.toString()
        val index = index(a,b)
        val draft = index.regenerate(b, "identifiers.android_id") { "cd".repeat(32) }
        assertTrue(index.findings(draft).none { it.field == "identifiers.android_id" })
        assertEquals("KEEP", draft.getJSONObject("identifiers").getString("serial"))
        assertEquals("original", draft.getString("seed"))
        assertEquals("b", draft.getString("id"))
        assertEquals(before, b.toString())
    }
    @Test fun missingAndCorruptedEntriesCannotPoisonRebuiltIndex() {
        val missing = JSONObject().put("id", "missing")
        val broken = JSONObject().put("id", "broken").put("identifiers", "bad")
        val malformed = profile("malformed", "android_id" to 42, "serial" to JSONObject.NULL)
        val a = profile("a", "android_id" to "1234567890abcdef")
        val b = JSONObject(a.toString()).put("id", "b")
        val old = index(missing,broken,malformed)
        assertTrue(old.duplicates.isEmpty())
        assertEquals(1, index(missing,broken,malformed,a,b).duplicates.size)
        assertTrue(index(a).duplicates.isEmpty())
    }
    @Test fun boundedRegenerationFailurePreservesDraft() {
        val seed = "ab".repeat(32)
        val id = CloakForge.deriveIdentifierBundle(seed).getValue("android_id")
        val a = profile("a", "android_id" to id)
        val before = a.toString()
        var calls = 0
        try { index(a).regenerate(a, "android_id") { calls++; seed }; fail("Must fail on repeated collisions") }
        catch (_: IllegalStateException) { assertEquals(32, calls) }
        assertEquals(before, a.toString())
    }
    @Test fun everySupportedRegenerationPreservesCoherence() {
        val original = CloakForge.forgeProfile("Original", "ab".repeat(32), TestPhones["pixel_7"])
        val copy = JSONObject(original.toString()).put("id", java.util.UUID.randomUUID().toString()).put("name", "Copy")
        val index = index(original, copy)
        for (field in ProfileUniqueness.fields) {
            val draft = index.regenerate(copy, field) { "cd".repeat(32) }
            assertFalse(field, ProfileValidator.validateProfileResult(draft).hasErrors)
            assertTrue(field, index.findings(draft).none { it.field == "identifiers.$field" })
        }
    }

}
