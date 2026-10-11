# Guided binding workflow — 2026-10-11

## Summary

Read `docs/AGENT-BRIEF.md` first, fetched tags, inspected the working tree and
orientation files. Worked on `main` at `a240456`, containing the latest published
prerelease `v0.10.0-rc.1` (confirmed with `gh release list --limit 3`). Preserved
all pre-existing uncommitted sprint work.

Added a five-step workflow: choose Cyclone profile, choose app, choose an existing
or generated compatibility profile, review, and confirm with a result. Profiles
has a Bind an app entry point; the identity binding action opens the same flow
with that identity preselected. Only the selected app is bound. Existing bulk
binding remains available with its existing behavior.

Inline findings reuse the coherence finding model and uniqueness index. Unready
profiles, missing Android users/apps, unreadable/invalid identities, duplicate
target records and Main-managed bindings block confirmation. Shared identities,
duplicate distinguishing values and replacement of an existing target require
explicit acknowledgement. The review includes Cyclone profile name, package,
Android user and compatibility profile name without exposing identifier values.

Confirmation rechecks the current local library/records under the existing work
mutex. A changed review invalidates approval and offers Review latest summary.
The existing binding store updates one logical profile-ID/package target
atomically. Generation uses the existing catalog, forge, validation and atomic
profile store. A profile generated before cancellation remains saved.

Success uses existing publication and reconciliation/health evidence. Failed or
unconfirmed checks retain the locally saved binding and show corrective reasons,
Retry reconciliation and Return to review. Retry never re-creates or overwrites a
changed target. No connector/sync engine/runtime/storage format/permission changes.

## Changed files in this run

- `android/app/src/main/java/dev/cyclone/cloak/ui/BindingFlow.kt` — findings, step guards, review snapshot, binding projection and completion state.
- `android/app/src/main/java/dev/cyclone/cloak/ui/BindingFlowScreen.kt` — guided Compose screen, summary, acknowledgement and result/retry actions.
- `android/app/src/main/java/dev/cyclone/cloak/ui/CloakViewModel.kt` — transient flow state and existing-store/generation/publication orchestration.
- `android/app/src/main/java/dev/cyclone/cloak/ui/CloakApp.kt` — display the flow without adding tabs.
- `android/app/src/main/java/dev/cyclone/cloak/ui/ProfilesScreen.kt` — flow entry, empty-state guidance and duplicate-safe list row keys.
- `android/app/src/main/java/dev/cyclone/cloak/ui/IdentitiesScreen.kt` — replace the immediate all-app dialog with the guided entry.
- `android/app/src/test/java/dev/cyclone/cloak/ui/BindingFlowTest.kt` — 12 model/UI-state cases.
- `android/app/src/test/java/dev/cyclone/cloak/ui/BindingFlowScreenTest.kt` — 5 Compose cases for steps/success, warning, blocked, retry and changed/running reviews.
- `tools/run-local-binding-tests.ps1` — supplemental JVM model test runner with explicit failure exit codes.
- `docs/ARCHITECTURE.md`
- This handoff.

## Exact test/build commands

From `C:\Users\Agent\Cyclone\cloak-repo` in PowerShell:

```powershell
$env:JAVA_HOME='C:/Program Files/Eclipse Adoptium/jdk-17.0.20.101-hotspot'
& "$env:TEMP/cloak-build/gradle-8.11.1/bin/gradle.bat" -p android :app:assembleDebug :module:packageModule --offline --no-daemon
./tools/run-local-binding-tests.ps1
./tools/run-local-sync-tests.ps1
uv run --with pytest -- python -m pytest forge/tests -q
& "$env:TEMP/cloak-build/gradle-8.11.1/bin/gradle.bat" -p android :app:compileDebugUnitTestKotlin :app:testDebugUnitTest --offline --no-daemon
& "$env:TEMP/cloak-build/gradle-8.11.1/bin/gradle.bat" -p android :app:compileDebugUnitTestKotlin --offline --no-daemon
& "$env:TEMP/cloak-build/gradle-8.11.1/bin/gradle.bat" -p android :app:compileDebugUnitTestKotlin --no-daemon
git -c core.safecrlf=false diff --check
```

## Results

- 12 new model/UI-state JVM tests passed, including step progression, success,
  warning acknowledgement, blocked records, changed review data and retry
  completion evidence. Initial test fixtures used an invalid seed string; they
  were corrected to the existing 64-hex seed contract before the passing run.
- Existing synchronization/publication regression suite: 117 tests passed,
  including real WSL process-lock/recovery tests. Sync engine files were not
  modified in this run.
- Python: 185 tests passed.
- Final app APK and module zip built successfully in 36 seconds (74 tasks,
  15 executed, 59 up-to-date), with no build warnings.
- `git diff --check` passed.
- Full Android target blocked at `:app:copyRobolectricRuntime`: uncached
  `android-all-instrumented-14-robolectric-10818077-i7.jar` in offline mode.
- Separate offline UI-test compilation blocked by uncached Robolectric 4.14.1
  and Compose UI test 1.7.0 dependencies. Online compilation also remained
  blocked in dependency downloads (confirmed by Gradle download thread stacks);
  its task-specific daemon was stopped after more than five minutes without
  compilation output or new test classes. No test or build configuration was
  changed to bypass the dependency failures. Compose test compilation/execution
  remains unverified; supplemental JVM tests do not
  satisfy the full Android test definition of done.

## Assumptions, limitations and follow-ups

- App choices come from the last successful synchronized Cyclone snapshot; no
  new transport or networking was introduced. Refresh uses the existing bridge.
  Confirmation rechecks local data but cannot promise that Cyclone won't change
  immediately afterward; reconciliation reports such races and retains records.
- The transient wizard is not a new persisted format. Closing or process death
  before confirmation abandons its draft; existing profiles/bindings are retained.
- Generated duplicates are not silently saved: generate again or review an
  existing profile in Identities. Existing duplicated values require review.
- Readiness refers to publication/synchronization health, not physical hook
  verification. No phone tests or reboot were run; deferred checks stay deferred.
- Version metadata is unchanged: app/module remain `0.10.0-rc.1`. No release,
  tag, commit or push was made.
