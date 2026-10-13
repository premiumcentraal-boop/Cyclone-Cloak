# Local compatibility report workflow polish

## Scope and behavior

Based on main `2a40fa6` (stable local report provenance), following the published
alpha.4 release. No release, device action or version change is part of this run.

Selected identities now have a Cyclone-style **Local compatibility report** card:

- A 12-character digest prefix is shown only when a report digest exists.
- Source, checked time, saved-profile match, bundled-artifact match and freshness
  are explicit. Missing information says **not recorded**. Different profiles,
  changed profile bodies, unavailable/different artifacts, stale reports and
  future timestamps have distinct labels.
- **Load compatibility report** validates the existing envelope and opens a
  preview. It no longer saves immediately. **Save local posture** remains the
  explicit confirmation, with existing freshness and replay/newer-report checks.
- **Review report details** displays the saved summary, reason and provider.
  Individual check rows remain available in the import preview; they are not
  persisted by the existing summary store.
- **Mark needs rotation** requires confirmation. It preserves existing report
  provenance and check time, and does not rotate or reset identity values.
- **Clear after explicit local reset** is enabled for Needs rotation only.
  The confirmation requires acknowledgement that a reset or reseed already
  completed. It records Unknown, clears the current evidence association and
  retains replay history. It never performs the reset itself.

Confirmation captures the reviewed record; a changed/missing record requires a
new review instead of overwriting it. View-model actions reload the profile and
record before applying transitions. Import/action errors appear inline on the
selected identity and in an open preview. Parser errors use safe fixed wording
instead of exposing imported content; load retry and cancel remain available.

This is local compatibility evidence, not a platform verdict. Artifact matching
describes the bundled payload, not the installed module. No new networking,
analytics, permissions, storage layout, profile migration, connector, binding,
publication or module behavior was introduced.

## Changed files

All Kotlin paths below are relative to `android/app/src/`:

- `main/java/dev/cyclone/cloak/ui/CompatibilityReportCard.kt` — summary/actions,
  detail dialog and confirmation dialogs.
- `main/java/dev/cyclone/cloak/ui/CompatibilityReportActions.kt` — guarded local
  mark/reset transitions, using the existing rotation helper.
- `main/java/dev/cyclone/cloak/ui/PostureSummary.kt` — explicit match/freshness labels.
- `main/java/dev/cyclone/cloak/ui/IdentitiesScreen.kt` — selected-card integration.
- `main/java/dev/cyclone/cloak/ui/CloakViewModel.kt` — preview-before-save loading,
  inline safe errors, guarded rotation/reset actions.
- `main/java/dev/cyclone/cloak/ui/DiagnosticsPostureDialog.kt` — shared neutral
  preview title and inline save errors.
- `test/java/dev/cyclone/cloak/ui/CompatibilityReportCardTest.kt` — Compose states,
  confirmations, changed-record blocking, retry, actual malformed-file loading,
  and actual compatibility import preview/save.
- `test/java/dev/cyclone/cloak/ui/CompatibilityReportActionsTest.kt` — transitions,
  provenance retention and missing/changed/wrong-profile rejection.
- `test/java/dev/cyclone/cloak/ui/DiagnosticsPostureDialogTest.kt` — shared preview
  title and visible save-error/cancel behavior.
- `docs/ARCHITECTURE.md` and this handoff — current workflow and evidence limits.

## Exact verification commands

```powershell
$env:JAVA_HOME='C:/Program Files/Eclipse Adoptium/jdk-17.0.20.101-hotspot'
& "$env:TEMP/cloak-build/gradle-8.11.1/bin/gradle.bat" -p android :app:testDebugUnitTest :module:testDebugUnitTest :app:assembleDebug --offline --no-daemon
uv run --with pytest --with jsonschema -- python -m pytest forge/schema_tests -q
git diff --check
```

Results: app 378 tests (355 passed, 23 existing Windows POSIX-shell skips),
module 12 passed, schema 33 passed. Debug APK and the existing module packaging
task succeeded; no new compiler/build warnings. `git diff --check` passed
(Git reports the existing Windows LF/CRLF normalization warning).
Changes remain local for review; no release or push was performed.
Physical-device behavior is not
verified by these local UI/unit tests. An intermediate test compile caught an
incorrect Robolectric test API invocation; it was corrected without changing
production behavior or bypassing tests.
