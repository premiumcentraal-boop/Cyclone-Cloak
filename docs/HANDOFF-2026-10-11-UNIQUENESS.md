# Local uniqueness handoff — 2026-10-11

## Scope and outcome

Added local duplicate checking for eleven distinguishing identifier fields, including Android ID, advertising ID, serial, Widevine/DRM ID, Bluetooth MAC and Wi-Fi MAC. The rebuildable in-memory index uses existing readable profile JSON; it introduces no storage format or migration. Existing records remain readable and are never automatically rewritten or deleted.

All app profile write entry points now check uniqueness: create, one-click generation, import, fleet import/generation, duplication, rename, and identifier review. Findings are warning-level `DUPLICATE_IDENTIFIER` with exact field paths. Duplicate proposals pause for explicit review. Users can regenerate individual conflicting fields or deliberately save duplicates. A fresh-library check runs on reviewed saves. Coherence errors still block saves. Health shows duplicate groups and affected profile names without revealing identifier values or adding private data to copied diagnostics.

Regeneration modifies a draft copy, uses the existing forge, retries up to 32 collisions, validates before saving, and leaves the original seed, ID and other fields unchanged. It is an explicit identifier override: replaying the original seed reconstructs the original value. Explicit changes to bound profiles use the existing publication/synchronization function; no connector, runtime, writer or reader implementation changed.

Batch operations pause at the first finding; cancellation retains completed writes and leaves the remaining queue unsaved. Indexing is limited to readable profiles in this installation's current Android user. Empty/non-string identifier entries and unreadable files are skipped by the existing library path; coherence validation handles malformed imported values. No persisted cache exists to become stale or corrupt.

## Files changed in this run

- Added `android/app/src/main/java/dev/cyclone/cloak/forge/ProfileUniqueness.kt`.
- Updated `android/app/src/main/java/dev/cyclone/cloak/ui/CloakViewModel.kt`.
- Updated `android/app/src/main/java/dev/cyclone/cloak/ui/CloakApp.kt`.
- Updated `android/app/src/main/java/dev/cyclone/cloak/ui/IdentitiesScreen.kt`.
- Updated `android/app/src/main/java/dev/cyclone/cloak/ui/HealthScreen.kt`.
- Added `android/app/src/main/java/dev/cyclone/cloak/ui/UniquenessDiagnostics.kt`.
- Updated the previous run's uncommitted `ui/ProfileGeneration.kt` and `ui/ProfileGenerationCard.kt`.
- Added `android/app/src/test/java/dev/cyclone/cloak/forge/ProfileUniquenessTest.kt`.
- Updated the previous run's uncommitted `android/app/src/test/java/dev/cyclone/cloak/ui/ProfileGenerationTest.kt`.
- Added `tools/run-local-profile-tests.ps1` (supplementary JVM runner with configurable toolchain paths).
- Updated `docs/ARCHITECTURE.md` and added this handoff.

Previous coherence/generation edits and unrelated user-owned brief/mission files were preserved. This list describes this run, not the entire pre-existing working tree.

## Exact verification commands

Run from `C:\Users\Agent\Cyclone\cloak-repo`:

```powershell
$env:JAVA_HOME='C:/Program Files/Eclipse Adoptium/jdk-17.0.20.101-hotspot'
& "$env:TEMP/cloak-build/gradle-8.11.1/bin/gradle.bat" -p android :app:assembleDebug :module:packageModule --offline --no-daemon
& "$env:TEMP/cloak-build/gradle-8.11.1/bin/gradle.bat" -p android :app:testDebugUnitTest --offline --no-daemon
uv run --with pytest -- python -m pytest forge/tests -q
& ./tools/run-local-profile-tests.ps1
git -c core.safecrlf=false diff --check
```

During development the equivalent temporary runner was also invoked with `& "$env:TEMP/run-cloak-generation-tests.ps1"`. Its initial run found that the catalog test deliberately reused one seed for every template. The fixture now uses distinct valid SHA-256 seeds per template, so catalog generation also satisfies uniqueness. An intermediate fixture used non-hex seeds and failed validation; that was corrected before final verification.

## Results and limitations

- App APK and module ZIP build passed; final build: 43 seconds, 74 tasks (15 executed, 59 up-to-date). No compiler/build warnings were emitted. Git reports existing LF/CRLF conversion notices with plain `git diff`; the whitespace check uses `core.safecrlf=false`.
- Python: **176 tests passed**.
- Supplemental JVM forge/coherence/generation/uniqueness tests: **69 tests passed**. Cases cover no duplicates, one duplicate, all supported fields, deterministic ordering, self-exclusion on edits, regeneration, bounded collision failure, all-field regeneration coherence, duplicate generation withholding save, and missing/corrupted entries/files not rewriting readable records.
- Full Android unit-test target **blocked**, not passed: `:app:copyRobolectricRuntime` cannot download uncached `org.robolectric:android-all-instrumented:14-robolectric-10818077-i7` in offline mode. The supplemental JVM runner is not a replacement for Compose/Robolectric screen verification.
- No physical-device, reboot, or cross-user runtime checks performed. Existing deferred device checklist remains applicable. UI interactions and bound-profile publication after identifier edits require later device verification.
- Assumptions: uniqueness means same identifier field across the current local library; case differences do not distinguish values; duplicates warn and require acknowledgement rather than forcing destructive repair. Wi-Fi/Bluetooth are compared in their respective fields, not against each other.
- Release metadata unchanged: app/module **0.10.0-rc.1**; baseline includes published tag `v0.10.0-rc.1`. No commit, push, tag or release was made in this run.
