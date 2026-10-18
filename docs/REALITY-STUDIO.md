# Reality Studio and Fleet Diversity

In Identities, expand a saved identity and open **Reality Studio**. Schema 0.4
identities show the existing twelve Device Reality sections. Each field has its
value, availability, source and local validation status. Known facts, derived
facts, user-set descriptions, unknowns and unavailable facts are labelled distinctly.
Schema 0.2/0.3 identities stay readable and read-only in Studio, without migration.

**Edit** uses text/number inputs, allowed-value choices and boolean controls.
Choose **Remove optional value** to clear a field. Arrays use an explicit structured
JSON editor and are validated as one complete field.
Contradictory edits show the precise field/reason code and leave the draft intact.
Configured radio labels and input-method values are hidden until explicitly
revealed. Seeds, inventory IDs and base identifier fields are not Studio editors.
User edits use the existing `needs verification` provenance and an explicit
user-set field marker in the section reason. This does not claim observed hardware.

**Preserve** locks a field (including a missing field) during repair/regeneration.
Locks and unsaved drafts exist only while this identity's editor is open.
**Preview missing-field repair** fills only absent optional fields supported by
the chosen exact model/firmware/API/hardware-compatible template and saved seed.
**Regenerate [section] preview** restores unlocked catalog/seed values in that
section. Unsupported/unknown hardware is never guessed; explicit unavailable
sections remain unavailable. Regeneration of fixed hardware may make no changes.

**Review Reality changes** lists every changed path and its before/after value,
with sensitive labels hidden. **Confirm and save identity** is the only write.
Saving rechecks the original hash, complete profile validation and current library
uniqueness. A stale draft must be reopened. A failed atomic write is reported by
the existing app error path; it is not reported as success. Existing bindings and
publication are untouched. Published state is not automatically refreshed; old
hash-bound local evidence and engine metadata may no longer match an edited body.

The **Fleet diversity** disclosure scans readable identities. It shows repeated
model, firmware, camera, display, storage and SoC characteristics and correlated
clusters using only field paths, counts and identity labels. The score counts
different known characteristics across all pairs; missing facts earn no credit.
No comparable pair means no score. It describes local data similarity only.
Recommendations identify sections to review; fixed hardware requires selecting
different catalog templates for new drafts, not reseeding or fabricating hardware.
Unreadable identities are excluded, so this is not a complete disk integrity audit.

Batch Workshop generates balanced rounds over selected template clusters. Counts
remain per template; repeated reality clusters are warned when distinct selected
combinations are exhausted. There is no automatic saving, replacement or binding.
Plan format 1 supports optional `request.diversity_policy` values `balanced` and
`legacy`; missing means legacy. Private seeds and existing export confirmation
remain unchanged. Imports reproduce and revalidate drafts rather than trusting
saved validation/diversity summaries. No destructive plan/profile migrations.

Physical UI interaction is **UNVERIFIED** in this sprint. Compose, pure planner
and atomic-store tests provide local evidence only. No platform verdict, new
permissions, network calls, hooks or module behavior changes.
