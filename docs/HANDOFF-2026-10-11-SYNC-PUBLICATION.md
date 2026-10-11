# Synchronization and publication hardening — 2026-10-11

## Summary

Read the shared agent brief and repository orientation instructions. Worked on
`main` at `a240456`, which contains the latest published tag `v0.10.0-rc.1`.
Kept existing working-tree changes from previous runs intact.

The existing process-safe publication lock remains held throughout share merge
and final swap. This run adds explicit stale-marker reporting after kernel lock
acquisition, retains the previous complete tree through post-swap checks, rolls
back a failed candidate, and recovers a missing or empty-index interrupted
candidate from the previous tree. A live lock cannot be stolen based on time or
PID. Publication outcomes remain locked, completed, failed, and recovered; a
separate stale-lock detail is displayed and safely copied in diagnostics.

Reconciliation retains a retired Android-user tuple when replacement reads,
value writes, or health updates fail. Confirmed values stay tracked even if
health updates fail. Trashed profiles do not trigger user moves. Ambiguous
snapshot omissions, unknown users, duplicates, and unavailable/missing apps
retain last-known publisher users so Main cannot prune their shares. Failed
first-run sweep checks report warnings and retain retry bookkeeping. Connector
loss during a retry stops cleanup. Existing explicit disable/unbind and
confirmed removal behavior is preserved.

## Files changed in this run

- `android/app/src/main/java/dev/cyclone/cloak/root/CloakRootDoctor.kt`
- `android/app/src/main/java/dev/cyclone/cloak/cyclone/Reconcile.kt`
- `android/app/src/main/java/dev/cyclone/cloak/cyclone/SyncEngine.kt`
- `android/app/src/main/java/dev/cyclone/cloak/ui/HealthDiagnostics.kt` — only stale-lock reason additions in this run; earlier v3 edits preserved.
- `android/app/src/test/java/dev/cyclone/cloak/root/PublicationRecoveryTest.kt`
- `android/app/src/test/java/dev/cyclone/cloak/cyclone/ReconciliationBehaviorTest.kt`
- `android/app/src/test/java/dev/cyclone/cloak/ui/HealthDiagnosticsTest.kt`
- `tools/run-local-sync-tests.ps1` — reproducible supplemental JVM/WSL runner; missing WSL/flock fails visibly.
- `docs/STATE_LAYOUT.md`
- `docs/RECONCILIATION-TESTS.md`
- This handoff.

## Exact verification commands

Run from `C:\Users\Agent\Cyclone\cloak-repo` in PowerShell:

```powershell
$env:JAVA_HOME='C:/Program Files/Eclipse Adoptium/jdk-17.0.20.101-hotspot'
& "$env:TEMP/cloak-build/gradle-8.11.1/bin/gradle.bat" -p android :app:assembleDebug :module:packageModule --offline --no-daemon
& "$env:TEMP/cloak-build/gradle-8.11.1/bin/gradle.bat" -p android :app:testDebugUnitTest --offline --no-daemon
./tools/run-local-sync-tests.ps1
uv run --with pytest -- python -m pytest forge/tests -q
git -c core.safecrlf=false diff --check
```

The build command was repeated after production changes. The supplemental
runner compiles the changed production Kotlin sources and checked-in tests
against built app classes with Kotlin/JUnit and real org.json. It executes
publication shell tests through Ubuntu WSL with real kernel process locks,
including concurrent publishers, SIGKILL, stale owners, swap failure, post-swap
check failure, failed rollback, and interrupted-tree/share recovery. It also
runs existing merge, Root Doctor, diagnostics, sync, connector-vector and binding
store tests. It does not replace Android/Robolectric verification.

Exploratory commands before the durable runner were:

```powershell
& "$env:TEMP/run-cloak-publication-tests.ps1"
& "$env:TEMP/run-cloak-reconciliation-tests.ps1"
```

Initial fixture failures (crash process targeting and a retired-user read) and
a cross-module Kotlin smart-cast compilation failure were corrected before the
final verification.

## Results

- **117 focused JVM tests passed**, including all 14 publication shell cases through real WSL locks, 24 reconciliation behavior cases and existing regression suites.
- **185 Python forge tests passed**.
- **App APK and module zip built successfully**: final build took 41 seconds, 74 tasks (15 executed, 59 up-to-date). No build warnings.
- `git diff --check` passed with no whitespace errors.
- **Full Android target did not pass**: `:app:copyRobolectricRuntime` failed before test execution because offline mode has no cached `org.robolectric:android-all-instrumented:14-robolectric-10818077-i7` jar. The full Android-tests definition of done remains outstanding. Supplemental JVM results must not be reported as a full Android test pass.

Build artifacts: `android/app/build/outputs/apk/debug/app-debug.apk` and
`android/module/build/module/cyclone-cloak-0.10.0-rc.1.zip`.

## Assumptions and limits

- Binding identity remains profile ID plus package name. Connector contract,
  profile/binding schemas, reader format and runtime module are unchanged.
- Publication stages and backups remain private implementation artifacts in
  the existing shared publication directory, not new reader/storage formats.
- A fresh removal event plus absence from a successful snapshot remains the
  existing positive deletion signal. Snapshot absence alone retains records.
- Cooperating publishers must use the same lock. Older/external writers that
  ignore it cannot be protected by this change.
- The existing two-rename reader contract has a brief missing-tree interval.
  A failed rollback leaves the complete backup available for next-attempt
  recovery. This does not promise power-loss durability or arbitrary corruption
  repair. Recovery uses existence/nonempty-index checks, not full JSON auditing.
- Android Toybox/device concurrency is unverified. No phone install, reboot,
  or hardware test was performed; deferred device checks remain deferred.
- Version metadata remains `0.10.0-rc.1` for app and module. No release, tag,
  commit or push was made in this run.
