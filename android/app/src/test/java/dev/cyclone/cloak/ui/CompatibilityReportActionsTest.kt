package dev.cyclone.cloak.ui

import dev.cyclone.cloak.data.*
import org.junit.Assert.*
import org.junit.Test

class CompatibilityReportActionsTest {
    private val original = PostureResult(PostureState.PASS, "2026-10-13T12:00:00Z", artifactDigest = "a".repeat(64),
        profileDigest = "b".repeat(64), reportDigest = "c".repeat(64), sequence = 4).recordFor("profile-1")
    @Test fun markKeepsProvenanceAndNeverChangesOriginal() {
        val marked = CompatibilityReportActions.mark(original.profileId, original, original)
        assertEquals(PostureState.NEEDS_ROTATION, marked.state)
        assertEquals(original.reportDigest, marked.reportDigest)
        assertEquals(original.sequence, marked.sequence)
        assertEquals(original.checkedAt, marked.checkedAt)
        assertEquals(PostureState.PASS, original.state)
    }
    @Test fun missingRecordCanBeMarkedWithoutInventingEvidence() {
        val marked = CompatibilityReportActions.mark("profile-1", null, null)
        assertEquals(PostureState.NEEDS_ROTATION, marked.state)
        assertNull(marked.reportDigest)
        assertNull(marked.checkedAt)
    }
    @Test fun resetAcknowledgementIsRequiredAndClearsToUnknown() {
        val marked = CompatibilityReportActions.mark(original.profileId, original, original)
        assertTrue(runCatching { CompatibilityReportActions.clear(marked, marked, false) }.isFailure)
        val reset = CompatibilityReportActions.clear(marked, marked, true)
        assertEquals(PostureState.UNKNOWN, reset.state)
        assertNull(reset.reportDigest)
        assertNull(reset.profileDigest)
        assertNull(reset.artifactDigest)
        assertNull(reset.checkedAt)
    }
    @Test fun changedMissingAndWrongProfileRecordsAreRejected() {
        assertTrue(runCatching { CompatibilityReportActions.mark("other", original, original) }.isFailure)
        assertTrue(runCatching { CompatibilityReportActions.mark(original.profileId, null, original) }.isFailure)
        assertTrue(runCatching { CompatibilityReportActions.clear(null, original, true) }.isFailure)
        assertTrue(runCatching { CompatibilityReportActions.clear(original, original, true) }.isFailure)
    }
}
