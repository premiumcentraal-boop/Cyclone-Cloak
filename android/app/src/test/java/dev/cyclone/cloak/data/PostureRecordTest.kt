package dev.cyclone.cloak.data

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class PostureRecordTest {
    private fun record(state: PostureState = PostureState.UNKNOWN) = PostureRecord("profile-1", state,
        "2026-10-13T15:00:00Z", "local-test", "Explicit local observation", "test-provider", "ab".repeat(32))

    @Test fun everyStateSerializesByStableNameAndRoundTrips() {
        for (state in PostureState.entries) {
            val value = record(state)
            assertEquals(state.name, value.toJson().getString("state"))
            assertEquals(value, PostureRecord.fromJson(JSONObject(value.toJson().toString())))
        }
    }
    @Test fun unknownNoResultAndOptionalFieldsAreValid() {
        val value = PostureResult().recordFor("profile-1")
        assertEquals(PostureState.UNKNOWN, value.state)
        assertNull(value.checkedAt)
        assertEquals(value, PostureRecord.fromJson(value.toJson().also { it.remove("provider_id"); it.remove("artifact_digest") }))
    }
    @Test fun malformedValuesVersionsAndFieldsAreRejected() {
        val bad = listOf(record().toJson().put("state", "CERTIFIED"), record().toJson().put("format", 2),
            record().toJson().put("checked_at", "yesterday"), record().toJson().put("profile_id", 12),
            record().toJson().put("source", ""), record().toJson().put("reason", ""),
            record().toJson().put("artifact_digest", "invalid"), record().toJson().put("provider_id", 1),
            record().toJson().put("extra", true), record().toJson().also { it.remove("checked_at") })
        bad.forEach { assertTrue(runCatching { PostureRecord.fromJson(it) }.isFailure) }
    }
    @Test fun defaultProviderNeverReturnsAnythingOtherThanUnknownOrSaves() {
        val provider: CompatibilityPostureProvider = LocalCompatibilityPostureProvider()
        for (id in listOf("profile-1", "profile-2", "unknown-profile")) {
            val result = provider.evaluate(id)
            assertEquals(PostureState.UNKNOWN, result.state)
            assertNull(result.checkedAt)
            assertNull(result.artifactDigest)
            assertEquals(id, result.recordFor(id).profileId)
        }
    }
    @Test fun markRotationThenExplicitResetReturnsUnknownAndDropsStaleEvidence() {
        val marked = PostureRotation.markNeedsRotation("profile-1", "Local owner requested reseeding.")
        assertEquals(PostureState.NEEDS_ROTATION, marked.state)
        assertEquals("local-rotation", marked.source)
        assertTrue(runCatching { PostureRotation.afterExplicitReset(marked, false) }.isFailure)
        val cleared = PostureRotation.afterExplicitReset(marked, true, "2026-10-13T15:00:00Z")
        assertEquals(PostureState.UNKNOWN, cleared.state)
        assertEquals(marked.profileId, cleared.profileId)
        assertEquals(PostureState.NEEDS_ROTATION, marked.state)
        assertNull(cleared.providerId)
        assertNull(cleared.artifactDigest)
    }
    @Test fun resetHelperDoesNotChangeUnrelatedStates() {
        for (state in PostureState.entries.filter { it != PostureState.NEEDS_ROTATION }) {
            assertEquals(record(state), PostureRotation.afterExplicitReset(record(state), true))
        }
    }
    @Test fun reasonSourceIdsAndArtifactAreBoundedAndValidated() {
        for (change in listOf<() -> PostureRecord>({ record().copy(reason = "x".repeat(1025)) },
            { record().copy(source = "invalid\nsource") }, { record().copy(profileId = "") },
            { record().copy(providerId = "") }, { record().copy(artifactDigest = "AB".repeat(32)) })) {
            assertTrue(runCatching(change).isFailure)
        }
    }
}
