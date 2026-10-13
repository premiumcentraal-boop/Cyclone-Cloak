package dev.cyclone.cloak.ui

import dev.cyclone.cloak.data.EngineMetadataStore
import dev.cyclone.cloak.data.PostureState
import dev.cyclone.cloak.forge.ProfileValidator
import java.time.Instant
import java.time.ZoneOffset

enum class LibraryQualityArea {
    READABLE, UNREADABLE, DUPLICATES, VALIDATION_ERRORS, VALIDATION_WARNINGS,
    MISSING_RECORDS, ATTENTION_RECORDS, BOUND, UNBOUND
}

/** Counts and local references only. No profile bodies, identifiers or report content. */
data class LibraryQualityRow(val area: LibraryQualityArea, val count: Int?, val profileIds: List<String>)
data class LibraryQualityAttention(val profileId: String, val recordedState: PostureState, val freshnessWarning: Boolean)
data class LibraryQualitySummary(val rows: List<LibraryQualityRow>, val attention: List<LibraryQualityAttention>) {
    fun row(area: LibraryQualityArea): LibraryQualityRow = rows.first { it.area == area }
}

object LibraryQuality {
    fun assess(state: CloakState, now: Instant): LibraryQualitySummary {
        val identities = state.identities.distinctBy { it.first }.sortedBy { it.first }
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        val missing = mutableListOf<String>()
        val attention = mutableListOf<LibraryQualityAttention>()
        val bound = mutableListOf<String>()
        val unbound = mutableListOf<String>()
        val today = now.atZone(ZoneOffset.UTC).toLocalDate()
        for ((id, profile) in identities) {
            val findings = ProfileValidator.validateProfileResult(profile, today).findings
            if (findings.any { it.isError }) errors += id
            if (findings.any { it.severity == ProfileValidator.WARNING }) warnings += id
            val record = state.postures[id]
            if (record == null) missing += id else {
                val freshness = PostureSummary.freshnessWarnings(record, id, EngineMetadataStore.digest(profile),
                    state.currentArtifactDigest, now).isNotEmpty()
                if (record.state != PostureState.PASS || freshness) attention += LibraryQualityAttention(id, record.state, freshness)
            }
            if (state.bindingsOf(id).isEmpty()) unbound += id else bound += id
        }
        val ids = identities.map { it.first }
        val duplicates = state.uniqueness.duplicates
        fun row(area: LibraryQualityArea, affected: List<String>, count: Int = affected.size) = LibraryQualityRow(area, count, affected.toList())
        return LibraryQualitySummary(listOf(
            row(LibraryQualityArea.READABLE, ids),
            LibraryQualityRow(LibraryQualityArea.UNREADABLE, if (state.identityInventoryKnown) state.unreadableIdentityIds.size else null, emptyList()),
            row(LibraryQualityArea.DUPLICATES, duplicates.flatMap { it.profiles }.distinct().sorted(), duplicates.size),
            row(LibraryQualityArea.VALIDATION_ERRORS, errors),
            row(LibraryQualityArea.VALIDATION_WARNINGS, warnings),
            row(LibraryQualityArea.MISSING_RECORDS, missing),
            row(LibraryQualityArea.ATTENTION_RECORDS, attention.map { it.profileId }),
            row(LibraryQualityArea.BOUND, bound), row(LibraryQualityArea.UNBOUND, unbound)), attention.toList())
    }
}
