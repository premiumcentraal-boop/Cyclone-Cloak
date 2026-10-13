package dev.cyclone.cloak.ui

import dev.cyclone.cloak.data.*
import dev.cyclone.cloak.forge.CloakForge
import dev.cyclone.cloak.forge.TestPhones
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class LibraryQualitySummaryTest {
    private val now = Instant.parse("2026-10-13T12:00:00Z")
    private fun profile(id: String = "profile-1", seed: String = "ab") = CloakForge.forgeProfile("Example", seed.repeat(32), TestPhones["pixel_7"]).put("id", id)
    private fun state(vararg profiles: org.json.JSONObject) = CloakState(identities = profiles.map { it.getString("id") to it }, identityInventoryKnown = true)
    private fun assess(state: CloakState) = LibraryQuality.assess(state, now)
    private fun record(profile: org.json.JSONObject, status: PostureState = PostureState.PASS) = PostureResult(status, now.toString(),
        profileDigest = EngineMetadataStore.digest(profile)).recordFor(profile.getString("id"))
    @Test fun emptyLibraryHasZeroCountsAndUnknownInventoryIsDistinct() {
        val empty = assess(CloakState(identityInventoryKnown = true))
        assertTrue(empty.rows.all { it.count == 0 && it.profileIds.isEmpty() })
        assertTrue(empty.attention.isEmpty())
        assertNull(assess(CloakState()).row(LibraryQualityArea.UNREADABLE).count)
    }
    @Test fun readableCoherentProfileHasNoValidationFindings() {
        val profile = profile(); val summary = assess(state(profile).copy(postures = mapOf("profile-1" to record(profile))))
        assertEquals(1, summary.row(LibraryQualityArea.READABLE).count)
        assertEquals(0, summary.row(LibraryQualityArea.VALIDATION_ERRORS).count)
        assertEquals(0, summary.row(LibraryQualityArea.VALIDATION_WARNINGS).count)
        assertEquals(0, summary.row(LibraryQualityArea.ATTENTION_RECORDS).count)
    }
    @Test fun duplicateGroupCountsGroupsAndDistinctAffectedProfiles() {
        val a = profile(); val b = profile("profile-2", "cd")
        b.getJSONObject("identifiers").put("android_id", a.getJSONObject("identifiers").getString("android_id"))
        val summary = assess(state(a, b))
        assertEquals(1, summary.row(LibraryQualityArea.DUPLICATES).count)
        assertEquals(listOf("profile-1", "profile-2"), summary.row(LibraryQualityArea.DUPLICATES).profileIds)
    }
    @Test fun errorAndWarningProfileCountsAreSeparateAndCanOverlap() {
        val invalid = profile(); invalid.getJSONObject("identifiers").put("android_id", "invalid")
        val warning = profile("profile-2", "cd"); warning.getJSONObject("device").put("manufacturer", "Samsung")
        val summary = assess(state(invalid, warning))
        assertEquals(listOf("profile-1"), summary.row(LibraryQualityArea.VALIDATION_ERRORS).profileIds)
        assertEquals(listOf("profile-2"), summary.row(LibraryQualityArea.VALIDATION_WARNINGS).profileIds)
        invalid.getJSONObject("device").put("manufacturer", "Samsung")
        assertEquals(2, assess(state(invalid, warning)).row(LibraryQualityArea.VALIDATION_WARNINGS).count)
    }
    @Test fun missingCompatibilityRecordIsCountedSeparately() {
        val summary = assess(state(profile()))
        assertEquals(1, summary.row(LibraryQualityArea.MISSING_RECORDS).count)
        assertEquals(0, summary.row(LibraryQualityArea.ATTENTION_RECORDS).count)
    }
    @Test fun stalePassRecordIsAttentionWithoutChangingItsState() {
        val profile = profile(); val record = record(profile).copy(checkedAt = now.minusSeconds(8 * 86400).toString())
        val summary = assess(state(profile).copy(postures = mapOf("profile-1" to record)))
        assertEquals(1, summary.row(LibraryQualityArea.ATTENTION_RECORDS).count)
        assertTrue(summary.attention.single().freshnessWarning)
        assertEquals(PostureState.PASS, summary.attention.single().recordedState)
        assertEquals(PostureState.PASS, record.state)
    }
    @Test fun everyNonPassRecordedStateNeedsAttention() {
        val profile = profile()
        for (status in PostureState.entries.filter { it != PostureState.PASS }) {
            val summary = assess(state(profile).copy(postures = mapOf("profile-1" to record(profile, status))))
            assertEquals(status, summary.attention.single().recordedState)
            assertEquals(1, summary.row(LibraryQualityArea.ATTENTION_RECORDS).count)
            assertFalse(summary.attention.single().freshnessWarning)
        }
    }
    @Test fun boundIncludesDisabledBindingsAndIgnoresOrphanReferences() {
        val binding = CloakBinding("Cyclone_aaaaaaaaaaaaaaaa", 10, "com.example.app", "profile-1", 1, false, 0)
        val summary = assess(state(profile(), profile("profile-2", "cd")).copy(bindings = listOf(binding, binding.copy(cloakProfileId = "absent"))))
        assertEquals(listOf("profile-1"), summary.row(LibraryQualityArea.BOUND).profileIds)
        assertEquals(listOf("profile-2"), summary.row(LibraryQualityArea.UNBOUND).profileIds)
    }
    @Test fun unreadableCountsRequireKnownInventoryAndNeverExposeReferences() {
        val state = CloakState(unreadableIdentityIds = setOf("unreadable-private-id"))
        assertNull(assess(state).row(LibraryQualityArea.UNREADABLE).count)
        val summary = assess(state.copy(identityInventoryKnown = true))
        assertEquals(1, summary.row(LibraryQualityArea.UNREADABLE).count)
        assertFalse(summary.toString().contains("unreadable-private-id"))
    }
    @Test fun summaryOmitsBodiesIdentifierValuesAndProviderContentAndDoesNotMutate() {
        val profile = profile(); val before = profile.toString()
        val record = record(profile).copy(reason = "Private reason", providerId = "Private provider", artifactDigest = "e".repeat(64))
        val state = state(profile).copy(postures = mapOf("profile-1" to record))
        val summary = assess(state).toString()
        val identifiers = profile.getJSONObject("identifiers")
        identifiers.keys().asSequence().forEach { assertFalse(summary.contains(identifiers.get(it).toString())) }
        for (value in listOf("Private reason", "Private provider", record.artifactDigest!!, profile.getString("seed"), before)) assertFalse(summary.contains(value))
        assertEquals(before, profile.toString())
        assertEquals(record, state.postures.getValue("profile-1"))
    }
    @Test fun suppliedClockDrivesValidationAndFreshnessDeterministically() {
        val profile = profile(); val state = state(profile).copy(postures = mapOf("profile-1" to record(profile)))
        assertEquals(assess(state), assess(state))
        val earlier = LibraryQuality.assess(state, Instant.parse("2020-01-01T00:00:00Z"))
        assertEquals(1, earlier.row(LibraryQualityArea.VALIDATION_WARNINGS).count)
        assertTrue(earlier.attention.single().freshnessWarning)
    }
}
