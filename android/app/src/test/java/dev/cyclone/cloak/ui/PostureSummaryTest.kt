package dev.cyclone.cloak.ui

import dev.cyclone.cloak.data.PostureResult
import dev.cyclone.cloak.data.PostureState
import org.junit.Assert.*
import org.junit.Test

class PostureSummaryTest {
    @Test fun freshnessWarningsDescribeProfileArtifactAndTimeDriftWithoutChangingState() {
        val now = java.time.Instant.parse("2026-10-13T12:00:00Z")
        val record = PostureResult(PostureState.PASS, now.minusSeconds(8 * 86400).toString(),
            artifactDigest = "a".repeat(64), profileDigest = "b".repeat(64)).recordFor("profile-1")
        val warnings = PostureSummary.freshnessWarnings(record, "profile-2", "c".repeat(64), "d".repeat(64), now)
        assertEquals(4, warnings.size)
        assertEquals(PostureState.PASS, record.state)
        assertEquals(1, PostureSummary.freshnessWarnings(record.copy(checkedAt = now.plusSeconds(120).toString()),
            "profile-1", "b".repeat(64), "a".repeat(64), now).size)
        assertTrue(PostureSummary.freshnessWarnings(record.copy(checkedAt = now.toString()),
            "profile-1", "b".repeat(64), "a".repeat(64), now).isEmpty())
    }
    @Test fun everyStateHasANeutralLocalLabel() {
        val expected = mapOf(PostureState.UNKNOWN to "Unknown", PostureState.PASS to "Pass", PostureState.DEGRADED to "Degraded",
            PostureState.FAILED to "Failed", PostureState.NEEDS_ROTATION to "Needs rotation")
        expected.forEach { (state, label) -> assertEquals("Local compatibility posture: $label", PostureSummary.label(PostureResult(state).recordFor("profile-1"))) }
    }
    @Test fun missingIsDistinctFromExplicitUnknown() {
        assertEquals("Local compatibility posture: not recorded", PostureSummary.label(null))
        assertNotEquals(PostureSummary.label(null), PostureSummary.label(PostureResult().recordFor("profile-1")))
    }
    @Test fun compactLabelDoesNotInterpolateProviderReasonOrArtifact() {
        val record = PostureResult(reason = "Private local note", artifactDigest = "ab".repeat(32)).recordFor("private-profile")
        val label = PostureSummary.label(record)
        for (privateValue in listOf(record.profileId, record.reason, record.artifactDigest!!)) assertFalse(label.contains(privateValue))
    }

    @Test fun reportIdentifierIsShortAndOnlyShownWhenPresent() {
        val record = PostureResult(reportDigest = "ab".repeat(32)).recordFor("profile-1")
        assertEquals("ab".repeat(6), PostureSummary.reportId(record))
        assertNull(PostureSummary.reportId(PostureResult().recordFor("profile-1")))
        assertNull(PostureSummary.reportId(null))
    }
}
