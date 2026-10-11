# Local profile export/import/backup — 2026-10-11

## Summary

Read `docs/AGENT-BRIEF.md` first and the repository orientation files. Fetched
tags, inspected status/log and confirmed `main` at `a240456` contains the latest
published `v0.10.0-rc.1` (`gh release list --limit 1`). Preserved earlier sprint work.

Added local export of one saved profile or the readable profile library. A
versioned backup envelope contains schema version, app version, creation time,
profile count and an optional label. Default backups omit seeds, profile IDs and
identifiers; an explicit checkbox includes those identity values. Binding state,
Cyclone IDs, publication data, arbitrary metadata, health, network hints and
integrity/keybox information are excluded. No advanced full-state backup exists.
Names and optional user-entered labels are retained as selected profile metadata.
Default imports create fresh seeds/identifiers/IDs while retaining compatibility
settings. Explicit identity-value backups preserve the selected values.

Exports atomically create an app-private backup and publish a new local file in
`Downloads/CycloneCloak` only after writing/syncing complete bytes. Download failure
retains the complete in-app file and reports that the public copy failed. This
is two complete independent artifacts, not a distributed transaction. No shared
partial document is published. Existing files are not overwritten.

Imports accept single profiles, legacy fleets and the new envelope. Every profile
is validated before any write, and the whole batch requires confirmation.
Duplicate values within the batch and against saved profiles require warning
acknowledgement. Version/count/type mismatches, malformed entries and trailing
JSON data fail safely. ID/name collisions receive fresh IDs/names in the preview;
existing records are not replaced. A changed library invalidates import review.

Batch saving stages the existing library plus all additions, swaps directories,
and rolls back failures. A failed rollback retains the previous complete tree
for recovery on the next store access. Cooperating store reads/writes share an
in-process lock. Existing per-profile JSON storage, bindings, connector, sync and
runtime contracts remain unchanged. Generation/fleet creation behavior outside
import remains unchanged.

## Changed files in this run

- `android/app/src/main/java/dev/cyclone/cloak/forge/ProfileBackup.kt`
- `android/app/src/main/java/dev/cyclone/cloak/data/JsonDirectory.kt`
- `android/app/src/main/java/dev/cyclone/cloak/data/CloakStore.kt`
- `android/app/src/main/java/dev/cyclone/cloak/data/LocalDocumentExport.kt`
- `android/app/src/main/java/dev/cyclone/cloak/ui/CloakViewModel.kt`
- `android/app/src/main/java/dev/cyclone/cloak/ui/IdentitiesScreen.kt`
- `android/app/src/main/java/dev/cyclone/cloak/ui/CloakApp.kt`
- `android/app/src/test/java/dev/cyclone/cloak/forge/ProfileBackupTest.kt`
- `android/app/src/test/java/dev/cyclone/cloak/data/AtomicLibraryImportTest.kt`
- `android/app/src/test/java/dev/cyclone/cloak/data/LocalDocumentExportTest.kt`
- `tools/run-local-backup-tests.ps1`
- `docs/ARCHITECTURE.md`
- This handoff.

## Exact commands run

From `C:\Users\Agent\Cyclone\cloak-repo` in PowerShell:

```powershell
$env:JAVA_HOME='C:/Program Files/Eclipse Adoptium/jdk-17.0.20.101-hotspot'
& "$env:TEMP/cloak-build/gradle-8.11.1/bin/gradle.bat" -p android :app:assembleDebug :module:packageModule --offline --no-daemon
./tools/run-local-backup-tests.ps1
./tools/run-local-profile-tests.ps1
uv run --with pytest -- python -m pytest forge/tests -q
& "$env:TEMP/cloak-build/gradle-8.11.1/bin/gradle.bat" -p android :app:testDebugUnitTest --offline --no-daemon
git -c core.safecrlf=false diff --check
```

## Verification

- 20 backup/storage JVM tests passed: 8 new backup codec/review tests,
  6 batch-storage/failure tests, 3 pending-download transaction tests, and
  3 existing JsonDirectory regressions.
- 100 profile/generation/uniqueness/doctor JVM tests passed, including the new
  backup codec tests. Counts overlap; these are supplemental direct Kotlin/JUnit
  checks, not a full Android test result.
- 185 Python forge tests passed. Python source was unchanged in this run.
- Final APK and module zip build passed in 39 seconds (74 tasks: 15 executed,
  59 up-to-date), with no build warnings. Initial nullable-parent warnings were
  fixed before the final build. Verification completed on 2026-10-12 after this
  run began on 2026-10-11.
- Full Android tests remain blocked before execution at
  `:app:copyRobolectricRuntime` by uncached
  `android-all-instrumented-14-robolectric-10818077-i7.jar` in offline mode.
  The Android-tests definition of done remains outstanding. Earlier online
  dependency attempts stalled; no test/build configuration was bypassed here.
- `git diff --check` passed.

## Assumptions and limitations

- Backups are profile-only: selecting sensitive identity values does not select
  full binding/connector/module state. That advanced feature is not added.
- The export/import envelope is new as explicitly requested; the existing
  on-device profile schema and storage format are unchanged.
- Default exports are compatibility settings backups and will not reproduce
  the original identity. Explicit seed/identifier selection is necessary for that.
- In-app backup files are removed by app-data clearing/uninstall. The completed
  Downloads copy is the persistent user-accessible export; the UI reports if
  that copy cannot be created. No share action or cloud destination was added.
- Inputs are bounded to 20 million characters and 1..1000 profiles per import/
  backup. A full-library export refuses unreadable entries rather than silently
  exporting an incomplete library.
- Atomic visibility applies to cooperating JsonDirectory consumers and new
  pending Downloads documents. The library uses two renames with next-access
  recovery, not a power-loss durability or external-process-writer guarantee.
- Actual Android MediaStore/provider behavior and Compose UI execution are
  unverified because Android tests are blocked. Pending export ordering and
  failure cleanup were tested using injected callbacks. No phone/reboot tests
  were run; deferred device checks remain deferred.
- Phone-template export remains its existing separate flow; this run changes
  saved profile import/export and backups only.
- App/module version remains `0.10.0-rc.1`. No release metadata changes, commit,
  push or tag in this run.
