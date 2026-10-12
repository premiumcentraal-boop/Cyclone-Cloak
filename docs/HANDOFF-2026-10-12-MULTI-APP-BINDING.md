# Multi-app binding handoff — 2026-10-12

## Summary

The guided binding flow now selects a set of app packages with checkboxes and an
All apps toggle. Both Profiles → Bind a device profile and Identities → Bind to
profile apps use PROFILE → APP → IDENTITY → REVIEW → RESULT. The identity step
is always shown, requires a readable selection, and displays a preselected device
profile as a summary. Generation remains available when no saved profile exists.
Review shows the Cyclone profile, Android user, selected device profile, selection
count/All apps, and every selected package.

Selection changes clear the review stamp and warning acknowledgement. The stamp
includes a sorted package set, so click order does not change the stamp. A changed
Cyclone app list, user, identity or local binding snapshot invalidates confirmation.
Per-app blocking errors name their package and prevent the entire submission.

Confirmation creates one existing-format record per selected app and commits all
records in one existing binding-store update. Existing selected-record revisions
and other metadata are preserved as in the old single-app flow; unselected records
remain untouched. Guided submission and the existing fleet bulk action now share
one view-model save helper and the same per-app upsert implementation. BulkBind's
planner, assignment semantics and Profiles Bind all action remain unchanged.
Retry and readiness require every selected target to remain saved and healthy;
retry never rewrites changed or removed records.

## Changed files

- `android/app/src/main/java/dev/cyclone/cloak/ui/BindingFlow.kt`: package-set draft,
  selection helpers, per-app validation, deterministic stamp, record list,
  common upserts and all-target completion checks.
- `android/app/src/main/java/dev/cyclone/cloak/ui/BindingFlowScreen.kt`: checkbox
  selection/All apps, clear step guidance, selected identity summary and full review.
- `android/app/src/main/java/dev/cyclone/cloak/ui/CloakViewModel.kt`: selection
  state, one atomic multi-record update, shared legacy/bulk helper, retry checks.
- `android/app/src/main/java/dev/cyclone/cloak/ui/CloakApp.kt`: set-selection callback.
- `android/app/src/main/java/dev/cyclone/cloak/ui/ProfilesScreen.kt`: entry wording
  and guidance; existing Bind all remains available.
- `android/app/src/main/java/dev/cyclone/cloak/ui/IdentitiesScreen.kt`: entry wording.
- `android/app/src/test/java/dev/cyclone/cloak/ui/BindingFlowTest.kt`: existing
  single-app cases adapted, seven multi-app/state/stamp/store/retry/bulk cases added.
- `android/app/src/test/java/dev/cyclone/cloak/ui/BindingFlowScreenTest.kt`: existing
  cases adapted, three new checkbox/entry-path/busy Compose cases added.
- `tools/run-local-binding-tests.ps1`: compile the unchanged store source in the
  same Kotlin test module for internal upsert access; include existing bulk tests.
- `README.md`, `docs/ARCHITECTURE.md`, `docs/CURRENT-STATE.md`: development workflow
  guidance; distinguish this unpublished change from released RC2.
- `docs/HANDOFF-2026-10-12-MULTI-APP-BINDING.md`: this per-run report.

No modifications to CloakBindingStore, BulkBind, connector, reconciliation,
publication code, native module, schemas, permissions or version metadata.
Pre-existing untracked brief/mission/integration/handoff files were preserved.

## Verification

- Focused JVM runner: **23 passed** (19 binding-flow tests and 4 existing fleet
  bulk-planner tests), including the common upsert result matching the old bulk
  record fields for every selected app.
- Python suite: **185 passed**.
- Debug app/module packaging and test-source compilation: **BUILD SUCCESSFUL**,
  final run 36 seconds, 76 tasks (16 executed), no warnings in that final run.
- All Android unit-test Kotlin sources, including Compose screen tests: compiled.
- Full offline app tests: **blocked**, not passed, at `:app:copyRobolectricRuntime`:
  no cached `org.robolectric:android-all-instrumented:14-robolectric-10818077-i7` JAR.
- Online app test retry stalled during framework downloads; its confirmed own
  Gradle daemon was stopped. Gradle reports daemon disappearance because of that
  cancellation, not a discovered application/test failure. Direct official Maven
  downloads and bounded ranged retries also timed out and were stopped. Incomplete
  files were confined to TEMP; none were installed as framework dependencies.
- Supplemental tests do not replace full Robolectric execution. The three new
  Compose cases are compiled but **not executed**. Previous RC2 CI success is
  historical and does not verify this change.
- Whitespace and scope checks passed. One test-source compilation emitted
  `Detected multiple Kotlin daemon sessions`; no other compiler warning observed.

## Exact test/build commands

Working directory: `C:/Users/Agent/Cyclone/cloak-repo`.

```powershell
$env:JAVA_HOME='C:/Program Files/Eclipse Adoptium/jdk-17.0.20.101-hotspot'
& "$env:TEMP/cloak-build/gradle-8.11.1/bin/gradle.bat" -p android :app:assembleDebug :module:packageModule --offline --no-daemon
& "$env:TEMP/cloak-build/gradle-8.11.1/bin/gradle.bat" -p android :app:testDebugUnitTest --offline --no-daemon
& "$env:TEMP/cloak-build/gradle-8.11.1/bin/gradle.bat" -p android :app:testDebugUnitTest --no-daemon
& "$env:TEMP/cloak-build/gradle-8.11.1/bin/gradle.bat" -p android :app:compileDebugUnitTestKotlin --offline --no-daemon
./tools/run-local-binding-tests.ps1
uv run --with pytest -- python -m pytest forge/tests -q
& "$env:TEMP/cloak-build/gradle-8.11.1/bin/gradle.bat" -p android :app:assembleDebug :module:packageModule :app:compileDebugUnitTestKotlin --offline --no-daemon
git -c core.safecrlf=false diff --check
```

The focused runner was first blocked by internal Kotlin visibility when the store
was only on the compiled classpath; including its unchanged source fixed that test
harness issue. The next run passed 18 tests; the final expanded run passed 23.

## Assumptions and remaining checks

All apps means the current synchronized package list at selection time. New apps
are not silently added after review; a changed snapshot requires fresh review.
Both entry paths intentionally show IDENTITY instead of skipping it so the user
can confirm or change the selected device profile. Transient UI draft shape
changes require no storage migration. Existing fleet bulk binding remains a
separate action for assigning unused identities across multiple Cyclone profiles.

Resolve framework downloads and run full Robolectric/Compose tests to satisfy
the remaining test gate. Multi-user/device UI behavior is **unverified**; no phone
installation, reboot or device testing occurred. Deferred reboot checks remain
in DEFERRED-DEVICE-CHECKS.md. No commit, push, tag or release was made in this run.
**Release metadata changed: no** (still RC2/code23).
