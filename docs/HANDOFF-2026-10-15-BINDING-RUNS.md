# Sprint B — binding run history and debug bundles

## Delivered

Health now includes **Runs**, newest first, with expandable targets, Android users, packages, device profile references, outcomes, reason codes, duration, versions and publication markers. Failed and needs-attention runs use distinct card colors. **Retry reconciliation** reuses the existing privileged batch and publication/sync path; historical records are never restored, enabled, or recreated. If the target identity or expected enabled/present state has changed, the user is directed to review Profiles.

One operation record covers one bind batch, guided bind, explicit reconciliation retry, enable, disable, or remove. Multi-profile bulk operations retain their individual target groups. Root preflight failures are journaled before binding work starts. The existing binding mutations, root transport, publication scripts, connector contract, module, and binding/profile storage formats remain unchanged. Sprint A's uncommitted connection work is preserved.

**Download debug bundle** is available per run and for the last 24 hours. It opens Android's standard share chooser so a local file manager or another user-selected app can save/send the ZIP. Creating the bundle itself performs no network access or root checks. The app does not send anything automatically.

## Storage and bounds

- App-private `no_backup/binding-runs/runs.jsonl`, plus `runs.1.jsonl` through `runs.3.jsonl`.
- Append-only completed records; rotation retains approximately 150–200 typical runs, with a hard maximum of 200. Each segment is capped at 50 records and 2 MiB; large runs can reduce retention.
- A bounded atomic `pending.json` marker recovers an interrupted operation as needs-attention / `INTERRUPTED` on reopening. A completed operation is not duplicated after interruption.
- Run-local producer capture retains up to 256 sanitized diagnostic events independently of the general ring. Overflow is counted and included in exports. Publication phase markers come from observed events; absent phases say not observed.
- Missing/corrupt journal rows are ignored. Storage failures do not abort binding work; the Health section displays an incomplete-history warning.
- Exports are written and synchronized to a temporary file, then atomically renamed before sharing. Only `cache/run-bundles/` is exposed through a non-exported FileProvider with a grant for the selected URI. Five completed ZIPs are retained. No new Android permission was added.

Run JSON schema version 1 contains run UUID, wall-clock start timestamp, operation, target groups (profile ID, Android user, packages, device profile ID, expected presence/enabled state), outcome, fixed reason codes, duration, publication phases, sanitized events/overflow, and observed app/module/handshake metadata. Unknown custom identity references are hashed to stable short references rather than exporting arbitrary user text. This is a separate diagnostics format, not a binding migration.

## Example ZIP contents

```text
cloak-runs-<timestamp>-<unique>.zip
  manifest.json             # versions, time window, capture time, retention limits
  runs.json                 # selected run records and their retained events
  diagnostics.json          # deduplicated events within the requested time window
  bindings-index.json       # current local binding metadata projection; no summaries
  module-state.json         # observed Root Doctor state and its SHA-256
  connector-handshake.json  # current/recorded approval, minor version and failure code
```

Exports use allowlists and the existing diagnostics privacy rules. They exclude profile bodies, mirrored identity summaries, seeds, identifiers inside identities, exception messages, raw root output, account/message/clipboard content, credentials and auth headers. Package names and non-secret profile references remain useful for debugging.

## Changed files in this sprint

- `android/app/src/main/java/dev/cyclone/cloak/diagnostics/BindingRunJournal.kt` — record model, privacy projection, bounded rotating journal, interruption recovery.
- `android/app/src/main/java/dev/cyclone/cloak/diagnostics/DiagnosticsRegistry.kt` — bounded synchronous producer capture for a run, leaving the async registry intact.
- `android/app/src/main/java/dev/cyclone/cloak/ui/BindingRunResult.kt` — observational outcome assessment.
- `android/app/src/main/java/dev/cyclone/cloak/ui/BindingRunBundle.kt` — allowlisted ZIP assembly, atomic export/cache retention, share intent.
- `android/app/src/main/java/dev/cyclone/cloak/ui/BindingRunsSection.kt` — history, expandable cards, export/retry controls and failures.
- `android/app/src/main/java/dev/cyclone/cloak/ui/CloakViewModel.kt` — journal existing operation entry points and attach results without changing mutations.
- `android/app/src/main/java/dev/cyclone/cloak/ui/HealthScreen.kt` — Runs section.
- `android/app/src/main/AndroidManifest.xml` and `android/app/src/main/res/xml/run_bundle_paths.xml` — narrow FileProvider configuration.
- `android/app/src/test/java/dev/cyclone/cloak/diagnostics/BindingRunJournalTest.kt` — nine schema, persistence, rotation, corruption, interruption, capture and version tests.
- `android/app/src/test/java/dev/cyclone/cloak/diagnostics/BindingRunBundleTest.kt` — eight ZIP, redaction, time-window, retention, phase and hash tests.
- `android/app/src/test/java/dev/cyclone/cloak/diagnostics/BindingRunResultTest.kt` — five observational outcome tests.
- `android/app/src/test/java/dev/cyclone/cloak/diagnostics/RunBundleShareTest.kt` — two export/cache/URI grant/provider configuration tests.
- `android/app/src/test/java/dev/cyclone/cloak/ui/BindingRunCardTest.kt` — three Compose detail, retry, export and disabled-state tests.
- This handoff.

## Commands and results

From `C:/Users/Agent/Cyclone/cloak-repo`, PowerShell:

```powershell
$env:JAVA_HOME='C:/Program Files/Eclipse Adoptium/jdk-17.0.20.101-hotspot'
$env:CLOAK_TEST_WSL='1'
& "$env:TEMP/cloak-build/gradle-8.11.1/bin/gradle.bat" -p android :app:compileDebugKotlin --offline --no-daemon
& "$env:TEMP/cloak-build/gradle-8.11.1/bin/gradle.bat" -p android :app:testDebugUnitTest :module:testDebugUnitTest :app:assembleDebug --offline --no-daemon
uv run --with pytest -- python -m pytest forge/tests -q
uv run --with pytest --with jsonschema -- python -m pytest forge/schema_tests -q
git diff --check
```

Final results: **497 app tests (27 added this sprint), 12 module tests, 227 forge tests, 33 schema tests passed**. Debug APK built successfully at `android/app/build/outputs/apk/debug/app-debug.apk`; no compiler warnings. `git diff --check` passed; Git emitted only LF/CRLF normalization notices for diagnostics source files. The combined Gradle command was repeated while resolving failures; the final pass completed successfully in 1m 33s.

The initial compile exposed an invalid chained Android setter; corrected. Earlier test passes exposed the architecture dependency rule and Windows file/export behavior; the adapters now respect the existing layer rules and exports use an atomic filesystem move. Redaction tests include hostile extra fields and metadata. The architecture/redaction tests were not weakened.

## Evidence limits

- No device tests, installs, root commands, reboots, data clearing, release or commit were performed in this sprint. On-device share chooser behavior remains unverified.
- The module-state SHA-256 covers the redacted last observed Root Doctor state. It is **not** a hash of the installed module binary or a fresh read of the root-only published tree; `installed_binary_hash` is explicitly null. Export-time snapshots and run-time observations are distinguished. A Root Doctor check is not triggered by export.
- A hard process kill can leave only the pending marker; recovery reports interruption rather than inventing completed phases. Corrupt/evicted records and event overflow cannot be reconstructed. Retention limits are included in the manifest.
- Desktop share tests verify complete ZIP-file writes, URI/intent grants, cache bounds and the actual manifest/path configuration. Android FileProvider's path canonicalization cannot be exercised end-to-end on this Windows Robolectric host; no product workaround was added for Windows paths.
