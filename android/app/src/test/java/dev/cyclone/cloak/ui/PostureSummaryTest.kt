package dev.cyclone.cloak.ui

import dev.cyclone.cloak.data.PostureResult
import dev.cyclone.cloak.data.PostureState
import org.junit.Assert.*
import org.junit.Test

class PostureSummaryTest {
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
}
