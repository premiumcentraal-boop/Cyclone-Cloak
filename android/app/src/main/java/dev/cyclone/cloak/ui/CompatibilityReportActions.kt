package dev.cyclone.cloak.ui

import dev.cyclone.cloak.data.PostureRecord
import dev.cyclone.cloak.data.PostureRotation
import dev.cyclone.cloak.data.PostureState

/** Explicit local state transitions; report import/replay history is not cleared. */
object CompatibilityReportActions {
    fun mark(profileId: String, current: PostureRecord?, reviewed: PostureRecord?): PostureRecord {
        require(current == reviewed) { "The saved report changed. Review it again before continuing." }
        require(current == null || current.profileId == profileId) { "This report is from another profile." }
        return current?.copy(state = PostureState.NEEDS_ROTATION, source = "local-rotation",
            reason = "Owner marked this identity as needing rotation. No automatic reset or reseed was performed.")
            ?: PostureRotation.markNeedsRotation(profileId, "Owner marked this identity as needing rotation. No automatic reset or reseed was performed.")
    }
    fun clear(current: PostureRecord?, reviewed: PostureRecord, resetCompleted: Boolean): PostureRecord {
        require(current == reviewed) { "The saved report changed. Review it again before continuing." }
        val record = requireNotNull(current) { "The saved report changed. Review it again before continuing." }
        require(record.state == PostureState.NEEDS_ROTATION) { "This identity is not marked as needing rotation." }
        return PostureRotation.afterExplicitReset(record, resetCompleted)
    }
}
