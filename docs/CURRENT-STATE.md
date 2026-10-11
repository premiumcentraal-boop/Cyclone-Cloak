# Current state

Verified locally on 2026-10-11; baseline is the latest published GitHub release,
[v0.10.0-alpha.1](https://github.com/premiumcentraal-boop/Cyclone-Cloak/releases/tag/v0.10.0-alpha.1)
(pre-release, published 2026-10-10 00:40:42 Europe/Berlin). Local `main` was
fast-forwarded from `v0.8.0-alpha.6` to release commit
`67274690ab83e6c38fe89ddbde9b3f92646e0993`. Local changes update documentation and
strengthen existing Python/Android validation, expand the shared phone catalog,
add runtime diagnostics and targeted hook fixes, and improve the phone builder;
see [VALIDATION.md](VALIDATION.md) and [CATALOG-SOURCES.md](CATALOG-SOURCES.md).
The local RC now bumps app/module metadata; profile/binding schemas and identity
storage formats remain unchanged.

## Release candidate

Prepared locally: **v0.10.0-rc.1**, not tagged or published. The latest published
release above remains the source baseline; no RC device checks have run.
[Release notes](RELEASE_0.10.0-rc.1.md) and [upgrade guide](UPGRADE_0.10.0-rc.1.md)
state the evidence boundary. Release workflow requires full Android/module and
Python tests, matching version metadata, and both verified artifacts before
attachment. It has not been dispatched locally.

## Versions and components

| Component | Version / layout |
| --- | --- |
| Android app | `dev.cyclone.cloak`, versionName `0.10.0-rc.1`, versionCode `22`; minimum API 29, target/compile API 36 |
| Magisk/Zygisk module | `cyclone_cloak`, version `v0.10.0-rc.1`, versionCode `22`; arm64-v8a and armeabi-v7a, minimum API 26 |
| Profile JSON | `schema_version: "0.2"`; no schema changes in this task |
| Cyclone connector | `cyclone.connector/1.3`; Cyclone 5.0.0-alpha.122 or newer |

- **App:** `MainActivity` hosts `ui/CloakApp` and `CloakViewModel`; Phones,
  Identities, Profiles and Health tabs, plus the phone editor.
- **Phone builder:** a live profile summary, separate device/version/build/network/
  display/region cards, and field-local error or warning messages. Both save
  buttons block on errors or busy state; warnings allow saving. Existing phone
  and identity storage paths are unchanged; existing identities retain their copy.
- **Binding review:** the existing Profiles tab has a stable-ID Cyclone profile
  selector and a case-insensitive app-package search, matching/total counts and a
  clear-filters action. Cards separate enabled/disabled status from detailed
  health, display the Cloak identity, and warn about absent profiles only when a
  Cyclone snapshot is available. Mirrored bindings remain managed in Main.
  Filtering is a read-only UI projection; reconciliation, state writers and
  module readers are unchanged by this UI task.
- **Health Doctor v2:** `ui/HealthDiagnostics` builds an overall verdict and
  pass/warn/fail checks from existing setup/connector/provider/binding facts;
  `HealthDoctorCard` renders them and an allowlisted redacted copy report.
  Release-name matching does not infer the version loaded after a queued repair.
  See [HEALTH-DOCTOR-V2.md](HEALTH-DOCTOR-V2.md) for scope and privacy rules.
  Existing diagnostics action wiring and identity storage are retained;
  publication is hardened separately below.
- **Health coverage:** `ui/CompatibilityCoverage` parses the checked-in
  `src/main/assets/compatibility-coverage.json`; `CoverageMatrix` renders all 13
  requested areas in Health. [HEALTH-COVERAGE.md](HEALTH-COVERAGE.md) is generated
  from the same source. Named baseline Build/serial reads have partial device
  proof; other areas show failed/blocked/unmapped results and no verification
  date. Callback tests and device proof are separate. This local bundled record
  does not replace live Root Doctor or change profile schema/storage.
- **App layers:** `forge` (generation/import/validation), `data` (atomic JSON
  storage), `root` (Root Doctor/publishing), `cyclone` (sync/bindings/events/health),
  `ui` (screens/state). `ArchitectureTest` enforces dependency direction.
- **Shared data:** `catalog/phones.json` supplies seven templates: Pixel 4, Pixel 6,
  Pixel 6a, Pixel 7, Pixel 7a, Pixel 8 and Galaxy S23. Python `forge/cloak_forge`
  and Kotlin use shared test vectors and validate every catalog entry.
- **Reconciliation:** focused lifecycle/failure tests retain ambiguous records
  and surface warnings; remote cleanup skips deferred bindings. Confirmed
  removal still forgets the profile's bindings, not its identity files.
  [RECONCILIATION-TESTS.md](RECONCILIATION-TESTS.md) records the cases and limits.
- **Publication:** nonblocking kernel lock serializes all app publishers;
  stale interruption markers and missing previous trees/shares recover under
  that lock. Root Doctor and Health Doctor expose locked/failed/recovered/completed
  reasons. Schema 0.2, binding storage and module index format are unchanged.
  See [STATE_LAYOUT.md](STATE_LAYOUT.md) for retry behavior and limitations.
- **Runtime:** root publisher shares merge into `state-v1/`; the native module
  resolves Android user/package bindings and applies Build/properties and
  embedded Pine Java callbacks. The app bundles the matching module for repair.

See [ARCHITECTURE.md](ARCHITECTURE.md) and [STATE_LAYOUT.md](STATE_LAYOUT.md).
Dated checkpoints and handoffs retain historical observations, not current
device-install status.

## Local verification

Toolchain: Temurin JDK 17.0.20, Gradle 8.11.1, Android SDK at
`C:/Users/Agent/android-sdk`, NDK 27.0.12077973 and CMake 3.22.1.
This checkout has no Gradle wrapper; on this machine invoke Gradle via
`& "$env:TEMP\cloak-build\gradle-8.11.1\bin\gradle.bat"` if it is not on PATH.
`android/local.properties` remains local and untracked.

```powershell
uv run --with pytest -- python -m pytest forge/tests -q
gradle -p android :app:testDebugUnitTest --no-daemon
gradle -p android :app:assembleDebug :module:packageModule --no-daemon
```

- Python forge: **138 passed**, including catalog/runtime harness checks and
  16 coverage checks tying every Health claim to device evidence, checking the
  recorded module hash and rejecting stale generated documentation.
- Coverage parser JVM tests: **6 passed** against the bundled JSON, including
  missing/duplicate areas and unsupported date/proof/callback claims. Two new
  `CoverageMatrixScreenTest` cases cover rendering all areas and honest fallback
  on unavailable data; rendering execution requires the Robolectric dependency.
- Health Doctor JVM tests: **10 passed**, including verdict precedence, every
  root code, release matching, queued/unconfirmed versions, disabled bindings,
  missing/unknown profiles, permission/provider conditions and allowlist-based
  redaction against injected private values. Three new doctor rendering tests
  and the updated Health screenshot test await the Robolectric dependency.
- Binding-review JVM tests: **5 passed**, compiled directly with Kotlin/JUnit
  against the real binding model. These exercise combined filters, stable IDs,
  whitespace/case handling, retention of disabled rows, unchanged binding objects
  and the known-snapshot requirement for missing-profile warnings. Three new
  `BindingListScreenTest` rendering/interaction tests are pending the Robolectric
  dependency download; they cover filter/reset controls, health/missing-profile
  rows, action arguments and mirrored management restrictions.
- Pure Android forge/validation tests run directly on the JVM: **44 passed**,
  including exact Python/Kotlin parity for the shared diagnostic vectors.
- Runtime sprint app/module/probe build and module callback tests: **BUILD SUCCESSFUL**,
  31 s, 124 tasks (28 executed), using `:module:testDebugUnitTest
  :app:assembleDebug :module:packageModule :probe:assembleDebug
  -PprobeAdvertisingSdk=true --offline --no-daemon`. Five module callback tests
  passed. Both ARM
  native targets were checked and the debug APK and module ZIP were produced.
  The APK packages the expanded catalog; its bundled module metadata and both
  libraries match the module ZIP payloads.
- Phone-builder app/module build: **BUILD SUCCESSFUL**, 37 s, 86 tasks
  (15 executed), using `:app:assembleDebug :module:packageModule
  :module:testDebugUnitTest --offline --no-daemon`. The five passing module
  callback tests were up to date. No warnings were emitted by this build.
- Binding-review app/module build: **BUILD SUCCESSFUL**, 26 s, 74 tasks
  (14 executed), using `:app:assembleDebug :module:packageModule --offline
  --no-daemon`. No warnings were emitted by the build or blocked test attempt.
- Health-matrix app/module build: **BUILD SUCCESSFUL**, 41 s, 74 tasks
  (17 executed), using `:app:assembleDebug :module:packageModule --offline
  --no-daemon`. The packaged coverage asset matches the checked-in JSON byte for
  byte. No build warnings were emitted.
- Health Doctor v2 app/module build: **BUILD SUCCESSFUL**, 38 s, 74 tasks
  (15 executed), using `:app:assembleDebug :module:packageModule --offline
  --no-daemon`. No build warnings were emitted.
- Publication sprint: **40 JVM tests passed** (11 recovery/concurrency,
  9 existing merge, 8 Root Doctor, 12 Health Doctor), compiling production Kotlin
  sources directly and exercising the real publication shell with WSL Ubuntu
  kernel locks. **138 Python tests passed**. App/module build succeeded;
  no build warnings. Device checks remain deferred.
- Reconciliation sprint: **61 JVM tests passed**, including 18 focused cases
  and existing sync/vector/storage tests; **138 Python tests passed**. App/module
  build succeeded with no warnings. No phone tests were run.
- RC preparation: debug and unsigned release APK plus module ZIP build locally;
  APK manifest reports RC/22 and both ARM ABI libraries are present. The APK
  embeds the byte-identical packaged module ZIP. Final sequential RC build: **BUILD SUCCESSFUL**, 49 s, 123 tasks
  (18 executed); artifact verification passed. No build warnings were emitted.
  Cached module callback tests remain **5 passed** (`UP-TO-DATE`).
  App/module release names and codes match (`0.10.0-rc.1` / 22).
  **148 Python tests passed**, including 10 release artifact validation cases;
  **61 reconciliation JVM tests** and **40 publication/diagnostics JVM tests**
  rerun against current sources. Release artifacts are checked with
  `uv run -- python tools/verify_release.py --tag v0.10.0-rc.1 --artifacts`.
- Android unit/screen tests: **blocked before test execution** at
  `:app:copyRobolectricRuntime` in the offline RC run (54 s),
  due to the uncached `android-all-instrumented-14-robolectric-10818077-i7.jar`.
  The earlier phone-builder online attempt stalled downloading the framework
  and was interrupted.
  Earlier runs also lacked Robolectric and Compose test dependencies.
  Four new `PhoneEditorScreenTest` cases cover summary edits/label fallback,
  inline errors and recovery of both save actions, nonblocking warnings, and
  busy state; they have not executed because of the dependency blocker.
  Rerun the normal test command when downloads can complete. No tests were
  removed or bypassed.
- Documentation links and `git diff --check`: passed. Runtime sources have local
  fixes; identity storage and profile schema remain identical to the
  release tag. See [RUNTIME-COVERAGE.md](RUNTIME-COVERAGE.md) for device evidence.

Build warnings: none emitted by the successful build. Earlier combined runs
were interrupted during dependency downloads; those are not successful test
runs. No release APK signing was performed. Phone-builder checks are local;
runtime device evidence and the deferred reboot checks are documented separately.
The blocked builder test attempt emitted `Detected multiple Kotlin daemon
sessions`; it did not execute tests.

Outputs: `android/app/build/outputs/apk/debug/app-debug.apk` and
`android/module/build/module/cyclone-cloak-0.10.0-rc.1.zip`.
The RC also builds `android/app/build/outputs/apk/release/app-release-unsigned.apk`;
release signing and signed upgrade remain unverified locally.

## Known unresolved items

- Runtime inspection now has real API36 device evidence: see
  [RUNTIME-COVERAGE.md](RUNTIME-COVERAGE.md). The measured installed binary has
  13 verified reads among 70; many identifier callbacks fail. Local runtime
  patches and a separate probe APK build successfully, and five module callback
  tests plus 122 Python tests pass. The patched module has not been installed
  or rebooted into the phone, so its device results remain pending.
  On 2026-10-11 the owner deferred reboot-dependent tests while away from home;
  the durable resume checklist is [DEFERRED-DEVICE-CHECKS.md](DEFERRED-DEVICE-CHECKS.md).

- The three legacy catalog entries still need a source audit. New entries use
  documented representative defaults rather than captured factory firmware;
  see [CATALOG-SOURCES.md](CATALOG-SOURCES.md).

- This release still needs physical-device validation: approval/revocation across
  users, restore/reconciliation, health pills, open requests, signed upgrades,
  module callbacks and Root Doctor after reboot. Local builds do not verify these.
- Merge-publication tests require POSIX `sh` and `flock`; Windows can opt into
  Ubuntu WSL with `CLOAK_TEST_WSL=1` (exercised locally). Android
  mksh/toybox behavior, legacy state migration and secondary-user root reads still
  need device testing (see [CYCLONE_PROFILES_REPORT.md](CYCLONE_PROFILES_REPORT.md)).
- `pif.json` publication does not verify Play Integrity; automatic attestation,
  keybox handling, fingerprint rotation and reset-triggered reseeding are absent.
- App Set ID is stored/exposed as a property, but its SDK callback is absent.
  Display geometry, uptime offsets, sensor renaming and network egress hints
  remain pending in [COMPAT_SURFACE.md](COMPAT_SURFACE.md).
