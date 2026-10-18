# Cyclone Cloak 0.11.0-alpha.19 — Batch Identity Workshop

App/module packaging version code: **43**. This prerelease adds local draft
planning and review. No profile schema, existing profile storage layout,
connector protocol, binding logic, publication, module runtime or permission
changes. No platform verdict is claimed.

## Workshop

- Open **Identities → Batch Identity Workshop**. Select one or more templates,
  choose 1–20 drafts per template (up to 100 total), and optionally add a label
  prefix. Preview before saving.
- Each draft has a deterministic seed, template fingerprint and derivation
  revision. Existing engine generation, coherence validation and uniqueness
  checks cover both the saved library and the batch, with deterministic retries
  for identifier collisions.
- Review template distribution, validation and uniqueness counts, warnings and
  engine/schema versions. Identifier values stay hidden in the list; explicitly
  open private draft details to inspect them. Regenerate or remove selected drafts.
- Select and confirm **Save approved drafts**. Every selected item is checked
  again against the current library and added atomically through existing storage.
  Results distinguish saved, failed and skipped items. Earlier successful saves
  survive a later failure; existing identities are never replaced. No bindings
  are created and saving does not invoke reconciliation or publication.
- Export/import versioned local JSON plans to reproduce a batch. Plans contain
  private seeds, labels and derivation metadata; they exclude profile bodies and
  raw identifier values. Treat exported plans as private backups. Export requires
  confirmation. Import checks versions, template fingerprints and derivations,
  then recomputes validation and uniqueness for preview; a stored summary cannot
  authorize saving.

## Verification and limitations

Local gate: **537 app**, **12 module**, **91 probe per flavor**, **503 forge**,
**33 schema** and **7 probe host** tests. Fifteen app tests were added.
Focused tests cover deterministic generation/regeneration, library and batch
uniqueness, retry derivation, invalid requests, schema failures, strict JSON
round-trip/tampering, partial saves, binding-file preservation and UI confirmation.
The full local Android, forge and schema gate and existing signed-validation CI
must pass before tagging. Release CI reruns the gate, signs with the existing key
and checks that the APK embeds the exact published module ZIP.

Workshop interaction and SAF document import/export on a physical device remain
**UNVERIFIED**. Drafts are in memory until saved or exported; closing the workshop
discards unsaved drafts. Plans require matching templates and compatible planner,
engine and schema versions. Library checks use readable profiles; atomic additive
writes also reject an occupied target file rather than overwrite an unreadable one.
Local validation and uniqueness do not establish runtime interception coverage.

This main also includes the already completed diagnostics lifecycle runner:
`--cycles`, install-once policy and per-cycle installed-APK hash checks. Its three
alpha.18 cycles and audits are recorded separately in
`docs/runtime-evidence/2026-10-18-015343-alpha18-lifecycle-install-once-3cycles/`.
They do not physically verify the workshop or alpha.19 binaries.

The existing release workflow attaches historical alpha.18 capture evidence
(`evidence-bundle.zip`, `summary.md`, `build-provenance.json`) from
`2026-10-17-capture-lifecycle-rerun-031422`. Its measured diagnostics commit and
installed companion/module versions are documented in alpha.18's release notes;
these attachments are historical evidence, not new alpha.19 device results.

## Upgrade

Update using the normal signed companion install flow and retain app data. No
migration is required. Module alpha.19/code 43 changes packaging metadata only;
runtime behavior is unchanged. No new permissions, networking or automatic
binding changes are introduced.

Primary assets: `app-release.apk`, `cyclone-cloak-0.11.0-alpha.19.zip`.
