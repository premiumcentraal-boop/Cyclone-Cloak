# Cyclone Cloak — per-run handoff, 2026-10-11

Repository: `C:/Users/Agent/Cyclone/cloak-repo`  
GitHub: https://github.com/premiumcentraal-boop/Cyclone-Cloak  
Branch: `main`  
Published prerelease: [v0.10.0-rc.1](https://github.com/premiumcentraal-boop/Cyclone-Cloak/releases/tag/v0.10.0-rc.1)

## How to read this handoff

The numbered runs below follow the requested work packages. They are not separate
Git commits: runs 1–11 were committed together as `46944b3` (80 changed files).
Release execution then produced `433779f` and `aef15cb`. Exact per-run timestamps
and intermediate file snapshots are not retained; this handoff does not invent them.
The complete committed file inventory is included below.

## Run 1 — release alignment and repository orientation

- Aligned local `main` with published baseline `v0.10.0-alpha.1`, commit
  `67274690ab83e6c38fe89ddbde9b3f92646e0993`.
- Confirmed local Android app/module targets build.
- Updated README, architecture, state-layout and historical checkpoint/handoff
  documentation; added `docs/CURRENT-STATE.md`.
- Kept profile schema 0.2, connector contract and identity-storage formats compatible.
- Later RC work explicitly superseded baseline app/module version metadata.

## Run 2 — stronger existing profile validation

- Strengthened Python and Kotlin validation for build/version consistency,
  manufacturer/model relationships, release/SDK coherence, display dimensions and
  density, locale/timezone syntax, and identifier formats.
- Added field-specific machine-readable codes, severity and readable reasons.
- Added valid, suspicious and invalid cases and 88 shared diagnostic vectors.
- Updated import/vector/catalog tests and documented rules in `docs/VALIDATION.md`.
- Did not remove schema fields or change identity storage.

## Run 3 — coherent phone catalog expansion

- Added Pixel 6, Pixel 6a, Pixel 7a and Pixel 8 templates to `catalog/phones.json`.
- Catalog now has seven entries; retained the existing JSON schema and validation flow.
- Added Python/Kotlin tests validating every entry and a source/confidence table
  in `docs/CATALOG-SOURCES.md`.
- Three legacy entries still require a source audit; new values are representative
  public defaults, not captured factory firmware dumps.

## Run 4 — runtime inspection probe and evidence

- Added `android/probe` with Java/native inspection reads, two test profiles and
  optional advertising-ID SDK support; added host runner and matrix renderer.
- Added 70-read comparison coverage for profile A/B, repeats and unbound controls.
- Recorded redacted/hashed API36 baseline evidence and visible results:
  **13 verified, 39 failed, 7 blocked, 8 coincidental matches, 3 unmapped**.
- Baseline belongs to an older installed ARM64 binary, SHA256
  `137c9e084fd6825c2e506b0f4b4ae8078bdc742184bd886ea2d0c19610847f75`.
  Its version label does not prove byte identity with the RC.
- Corrected runtime handling of Build.VERSION.INCREMENTAL, bootloader/radio fields,
  property forwarding, native registration, Android ID/settings guards, typed
  telephony values, advertising-ID class loading and locale/resource configuration.
- Added module callback and probe-harness tests; documented results in
  `RUNTIME-COVERAGE.md` and `RUNTIME-MEASUREMENTS.md`.
- RC runtime patches have not been installed or verified on a phone. Camera/sensor
  metadata and display geometry remain incomplete; many other hooks lack device proof.

## Run 5 — phone builder readability

- Grouped fields into device/version/build/network/display/region sections.
- Added a compact live profile summary and field-local errors/warnings.
- Both save actions block on errors or busy state; warnings permit saving.
- Added four screen tests for summary edits, blocking errors and recovery,
  nonblocking warnings, and busy state. Preserved identity-storage behavior.
- Recorded the user's deferred reboot/device checks in `DEFERRED-DEVICE-CHECKS.md`.

## Run 6 — binding list readability

- Added stable Cyclone-profile filtering and case-insensitive app-package search,
  matching/total counts and clear filters.
- Distinguished enabled/disabled rows, showed health clearly and warned about
  absent profiles when a Cyclone snapshot is available.
- Retained mirrored-management restrictions and existing action arguments.
- Added filter-model and screen interaction tests. This run did not alter the
  reconciliation engine, state writer or module reader.

## Run 7 — Health compatibility matrix

- Added checked-in `compatibility-coverage.json`, Kotlin parser and Health matrix.
- Included all 13 requested areas with implementation status, test status,
  verification date or not verified, and notes.
- Generated `HEALTH-COVERAGE.md` from the same data and added parser, screen and
  Python evidence-consistency tests.
- Verification claims remain scoped to named older-binary device evidence;
  callback tests do not imply device compatibility.

## Run 8 — Health Doctor v2

- Added one readiness verdict and pass/warn/fail checks with readable reasons.
- Added app/module version matching and a redacted copy-diagnostics action.
- Used an allowlisted report excluding private identities and raw errors;
  added injected-private-value redaction tests and screen tests.
- Retained synchronization/publication behavior in this run. Publication outcome
  reasons were integrated during the subsequent locking work.

## Run 9 — process-safe publication and recovery

- Added nonblocking kernel `flock` around migration, publisher-share updates,
  pruning, merge and tree replacement; retained the lock inode.
- Added stale interruption-marker recovery under the acquired lock, missing-tree
  restoration, interrupted-share recovery and staged legacy migration.
- Preserved the previous complete tree after failed swaps/rollback, with retry
  recovery; isolated per-invocation cache staging and cleanup.
- Exposed LOCKED, FAILED, RECOVERED and COMPLETED publication reasons and explicit
  retained-tree/restore-pending/no-previous-tree details.
- Added 11 recovery/concurrency cases including stale markers, process death,
  concurrent real publishers and rename/copy failures.
- Direct JVM verification: **40 publication/diagnostics tests passed** under WSL
  Ubuntu for actual POSIX shell locking. Binding schema and reader format unchanged.
- Limits: old publishers/external writers do not honor this lock; two renames can
  briefly leave the reader path absent; no power-loss durability claim. Android
  Toybox/mksh behavior remains unverified on a device.
- Repeated identical publication requests were handled as one work package.

## Run 10 — focused reconciliation behavior

- Added 18 focused cases covering Android-user moves, removed/trashed profiles,
  missing packages, duplicates, rate-limit retry, failed writes and strict read-back.
- Preserved ambiguous local records and previous pushed tuples; deferred remote
  cleanup for missing/unready/unknown-user/package cases and duplicate ties.
- Confirmed explicit removal still removes bindings while retaining identity files;
  absent snapshots alone do not delete them. Stale removal events do not delete
  a profile already present again.
- Retained tied latest duplicate records for warnings, instead of silently choosing
  an identity; reported failed clears with per-binding bookkeeping.
- Cloned fake connector JSON responses to match real IPC semantics.
- **61 reconciliation-related JVM tests passed**. Connector contract and storage
  formats remain compatible. Orphans can persist after missed removal events.

## Run 11 — release candidate preparation

- Set app `0.10.0-rc.1` and module `v0.10.0-rc.1`, both version code **22**.
- App minimum API 29, target/compile API 36; module minimum API 26, ARM64/ARMv7.
- Added RC notes, compatibility updates and a short upgrade guide.
- Required Python, app and module tests in the release workflow before publication.
- Added `tools/verify_release.py` and 10 artifact-gate tests for metadata, notes,
  APK metadata, module ZIP integrity, ABIs and byte-identical APK embedding.
- Final sequential local debug/release APK and module build passed: 49 seconds,
  123 tasks, 18 executed. Local release APK is unsigned. No build warnings.
- Local offline full app tests were blocked by an uncached Robolectric framework;
  direct JVM tests passed. CI later resolved the dependency and ran the full suite.
- Committed runs 1–11 together as **46944b3**; user-supplied untracked `AGENTS.md`
  was excluded from the commit.

## Run 12 — GitHub release execution

- Pushed the prepared changes to GitHub `main` and dispatched release workflow.
- First release run [38100198520](https://github.com/premiumcentraal-boop/Cyclone-Cloak/actions/runs/38100198520)
  failed one of 192 app tests: an inline error was below the visible viewport.
- Fixed `PhoneEditorScreenTest` to scroll the error into view before asserting
  visibility. Kept error semantics, save-disable/recovery and callback assertions.
  Committed/pushed as **433779f**; no tests removed or bypassed.
- Rerun [38100436479](https://github.com/premiumcentraal-boop/Cyclone-Cloak/actions/runs/38100436479)
  passed **192 app tests, 5 module tests and 148 Python tests**, built/signed the APK,
  verified artifacts and published the prerelease at **2026-10-11 01:04:53 UTC**.
- Main CI [38100435993](https://github.com/premiumcentraal-boop/Cyclone-Cloak/actions/runs/38100435993)
  also passed. Tag points to the tested release source, **433779f**.
- Published assets:

| Asset | Size (bytes) | SHA256 |
| --- | ---: | --- |
| `app-release.apk` (signed) | 42,488,522 | `3f416c2bffd434c1f7d7f40891aa2b96ecfaa67697ba4dc449b70bc4aeb2c0d2` |
| `cyclone-cloak-0.10.0-rc.1.zip` | 1,560,998 | `5000ef64885517d4133d44d4ae189fdaae23e2609dfaa14acc740a75e1f5d98a` |

- No compiler/build warnings. Workflow warnings: deprecated setup-java v4 / Node 20
  actions and Node APIs; unmatched uv cache dependency glob. Earlier interrupted
  local tests reported multiple Kotlin daemon sessions.
- No RC phone installation, signed-upgrade check or reboot test was performed.

## Run 13 — publication documentation follow-up

- Updated `README.md`, `docs/ARCHITECTURE.md`, `docs/COMPAT_SURFACE.md`,
  `docs/CURRENT-STATE.md` and `docs/RELEASE_0.10.0-rc.1.md` to reflect publication,
  successful full CI tests, signed artifacts and workflow warnings.
- Kept old-binary runtime proof separate from unverified RC compatibility.
- Reran Python tests: **148 passed**. `git diff --check` passed.
- Committed/pushed as **aef15cb** and updated GitHub release notes from the saved file.
- Main CI [38100770696](https://github.com/premiumcentraal-boop/Cyclone-Cloak/actions/runs/38100770696)
  passed. Release tag was not moved for this documentation follow-up.

## Run 14 — this handoff

- Added this file with run-by-run changes, exact commit/workflow references,
  test evidence, asset hashes, warnings and deferred work.
- Documentation-only change; no application/module/runtime changes.
- The numbered runs above are reconstructed from recorded work packages and Git
  history, not asserted as independently retained execution sessions.

## Resume commands

This checkout has no Gradle wrapper. Use installed Gradle 8.11.1 and JDK 17.

```powershell
uv run --with pytest -- python -m pytest forge/tests -q
gradle -p android :app:testDebugUnitTest :module:testDebugUnitTest --no-daemon
gradle -p android :app:assembleDebug :app:assembleRelease :module:packageModule --no-daemon
uv run -- python tools/verify_release.py --tag v0.10.0-rc.1 --artifacts
```

Run packaging sequentially; overlapping module packaging can make the embedded
ZIP differ from the external ZIP. Release signing uses configured GitHub secrets;
local unsigned builds do not establish signed-upgrade compatibility.

Before phone tests, follow [DEFERRED-DEVICE-CHECKS.md](DEFERRED-DEVICE-CHECKS.md).
The user deferred reboot-dependent checks while away from home. Do not infer RC
compatibility from the old measured binary or from JVM/CI success.

## Exact file inventory — combined preparation commit 46944b3

`A` = added, `M` = modified. This is the committed inventory, not a claim of
separate file ownership for each run.

```text
M	.github/workflows/release.yml
M	README.md
M	android/app/build.gradle
A	android/app/src/main/assets/compatibility-coverage.json
M	android/app/src/main/java/dev/cyclone/cloak/cyclone/Reconcile.kt
M	android/app/src/main/java/dev/cyclone/cloak/cyclone/SyncEngine.kt
M	android/app/src/main/java/dev/cyclone/cloak/data/CloakBindingStore.kt
M	android/app/src/main/java/dev/cyclone/cloak/forge/ProfileValidator.kt
M	android/app/src/main/java/dev/cyclone/cloak/root/CloakResolver.kt
M	android/app/src/main/java/dev/cyclone/cloak/root/CloakRootDoctor.kt
A	android/app/src/main/java/dev/cyclone/cloak/ui/BindingReview.kt
M	android/app/src/main/java/dev/cyclone/cloak/ui/Cards.kt
A	android/app/src/main/java/dev/cyclone/cloak/ui/CompatibilityCoverage.kt
A	android/app/src/main/java/dev/cyclone/cloak/ui/CoverageMatrix.kt
A	android/app/src/main/java/dev/cyclone/cloak/ui/HealthDiagnostics.kt
A	android/app/src/main/java/dev/cyclone/cloak/ui/HealthDoctorCard.kt
M	android/app/src/main/java/dev/cyclone/cloak/ui/HealthScreen.kt
M	android/app/src/main/java/dev/cyclone/cloak/ui/PhoneEditorScreen.kt
M	android/app/src/main/java/dev/cyclone/cloak/ui/ProfilesScreen.kt
M	android/app/src/test/java/dev/cyclone/cloak/cyclone/FakeCyclone.kt
A	android/app/src/test/java/dev/cyclone/cloak/cyclone/ReconciliationBehaviorTest.kt
M	android/app/src/test/java/dev/cyclone/cloak/forge/ImportsTest.kt
M	android/app/src/test/java/dev/cyclone/cloak/forge/PhoneCatalogTest.kt
A	android/app/src/test/java/dev/cyclone/cloak/forge/ProfileValidatorTest.kt
M	android/app/src/test/java/dev/cyclone/cloak/forge/SharedVectorsTest.kt
M	android/app/src/test/java/dev/cyclone/cloak/root/CloakPublishMergeTest.kt
A	android/app/src/test/java/dev/cyclone/cloak/root/PublicationRecoveryTest.kt
A	android/app/src/test/java/dev/cyclone/cloak/ui/BindingListScreenTest.kt
A	android/app/src/test/java/dev/cyclone/cloak/ui/BindingReviewTest.kt
A	android/app/src/test/java/dev/cyclone/cloak/ui/CompatibilityCoverageTest.kt
A	android/app/src/test/java/dev/cyclone/cloak/ui/CoverageMatrixScreenTest.kt
A	android/app/src/test/java/dev/cyclone/cloak/ui/HealthDiagnosticsTest.kt
A	android/app/src/test/java/dev/cyclone/cloak/ui/HealthDoctorCardScreenTest.kt
A	android/app/src/test/java/dev/cyclone/cloak/ui/PhoneEditorScreenTest.kt
M	android/app/src/test/java/dev/cyclone/cloak/ui/ScreensTest.kt
M	android/module/build.gradle
M	android/module/module.prop
M	android/module/src/hook/java/dev/cyclone/cloak/hook/HookBridge.java
M	android/module/src/main/cpp/cloak.cpp
A	android/module/src/test/java/dev/cyclone/cloak/hook/HookBridgeTest.java
A	android/probe/build.gradle
A	android/probe/src/main/AndroidManifest.xml
A	android/probe/src/main/assets/profile-a.json
A	android/probe/src/main/assets/profile-b.json
A	android/probe/src/main/cpp/CMakeLists.txt
A	android/probe/src/main/cpp/probe.cpp
A	android/probe/src/main/java/dev/cyclone/cloak/probe/ProbeActivity.java
M	android/settings.gradle
M	catalog/phones.json
M	docs/ARCHITECTURE.md
A	docs/CATALOG-SOURCES.md
M	docs/CHECKPOINT-2026-10-05.md
M	docs/COMPAT_SURFACE.md
A	docs/CURRENT-STATE.md
M	docs/CYCLONE_PROFILES_REPORT.md
A	docs/DEFERRED-DEVICE-CHECKS.md
M	docs/HANDOFF-2026-10-09.md
A	docs/HEALTH-COVERAGE.md
A	docs/HEALTH-DOCTOR-V2.md
A	docs/RECONCILIATION-TESTS.md
A	docs/RELEASE_0.10.0-rc.1.md
A	docs/RUNTIME-COVERAGE.md
A	docs/RUNTIME-MEASUREMENTS.md
M	docs/STATE_LAYOUT.md
A	docs/UPGRADE_0.10.0-rc.1.md
A	docs/VALIDATION.md
A	docs/runtime-evidence/release-api36-advertising.json
M	forge/cloak_forge/validate.py
A	forge/tests/test_catalog.py
A	forge/tests/test_health_coverage.py
A	forge/tests/test_release_verification.py
A	forge/tests/test_runtime_probe.py
A	forge/tests/test_validation_rules.py
M	forge/tests/test_vectors.py
M	forge/tests/vectors/validation.json
M	forge/tools/make_vectors.py
A	tools/render_health_coverage.py
A	tools/render_runtime_matrix.py
A	tools/run_runtime_probe.py
A	tools/verify_release.py
```

Follow-up commit `433779f` changed only
`android/app/src/test/java/dev/cyclone/cloak/ui/PhoneEditorScreenTest.kt`.
Follow-up commit `aef15cb` changed the five documentation files listed in run 13.
