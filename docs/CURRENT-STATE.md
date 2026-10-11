# Current state

Updated on 2026-10-11. The latest published release is
[v0.10.0-rc.1](https://github.com/premiumcentraal-boop/Cyclone-Cloak/releases/tag/v0.10.0-rc.1), a prerelease built from
commit `433779f` on GitHub `main`. Source baseline: `v0.10.0-alpha.1`.
Profile/binding schemas and identity storage formats remain unchanged.

## Release candidate

Published: **v0.10.0-rc.1**. The release includes a signed APK and module ZIP;
no RC device checks have run.
[Release notes](RELEASE_0.10.0-rc.1.md) and [upgrade guide](UPGRADE_0.10.0-rc.1.md)
state the evidence boundary. [Release workflow run](https://github.com/premiumcentraal-boop/Cyclone-Cloak/actions/runs/38100436479)
passed Android/module and Python tests, checked matching metadata and both
artifacts, and published them. Main CI also passed.

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

- GitHub release CI: **192 app unit/screen tests, 5 module callback tests,
  and 148 Python tests passed**, including 10 release artifact validation cases.
  Full app tests include the new builder, binding, Health and coverage screens.
  An inline error visibility assertion now scrolls the field into view; no
  tests were removed or bypassed.
- Local direct JVM runs: **61 reconciliation tests** and **40 publication/diagnostics
  tests passed**, including real shell publication concurrency/recovery under WSL.
- Final local debug/unsigned-release APK and module build: **BUILD SUCCESSFUL**,
  49 s, 123 tasks (18 executed). Artifact verification confirmed RC/22 metadata,
  both ARM ABIs and byte-identical module ZIP embedding. No build warnings.
- GitHub built the signed release APK and module ZIP and passed
  `python3 tools/verify_release.py --tag v0.10.0-rc.1 --artifacts` before upload.
- Earlier local offline app tests were blocked by an uncached Robolectric framework;
  CI resolved that dependency and ran the full suite successfully.
- Runtime/device verification remains scoped to the older recorded binary;
  see [RUNTIME-COVERAGE.md](RUNTIME-COVERAGE.md). RC device checks are unverified.

Build warnings: no compiler/build warnings in the successful release run.
Workflow tooling warned about deprecated setup-java v4 / Node 20 actions and
Node APIs, and an unmatched uv cache dependency glob. Earlier interrupted local
testing reported multiple Kotlin daemon sessions.

Published artifacts: `app-release.apk` (signed) and `cyclone-cloak-0.10.0-rc.1.zip`.
Local outputs include `android/app/build/outputs/apk/debug/app-debug.apk`,
`android/app/build/outputs/apk/release/app-release-unsigned.apk`, and
`android/module/build/module/cyclone-cloak-0.10.0-rc.1.zip`.
Signed upgrade behavior remains unverified on a device.

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
