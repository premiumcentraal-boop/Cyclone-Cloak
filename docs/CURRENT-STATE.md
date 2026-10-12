# Current state

Updated 2026-10-12. **v0.10.0-rc.2 / versionCode 23** is a
[published prerelease](https://github.com/premiumcentraal-boop/Cyclone-Cloak/releases/tag/v0.10.0-rc.2) from commit `b9d8606`.
[Release CI](https://github.com/premiumcentraal-boop/Cyclone-Cloak/actions/runs/38180047567) passed full Android/module and Python tests, signed builds
and artifact checks before attaching the APK and module ZIP. Main CI on the same
commit also passed. The checkout is on `main`; later documentation commits record
publication evidence without changing the released payload.

[RC2 release notes](RELEASE_0.10.0-rc.2.md) · [Upgrade guide](UPGRADE_0.10.0-rc.2.md).

## Development after RC2

The local binding UI now selects multiple apps or All apps from either Profiles
or Identities. Both paths show the required device-profile step, with a selected
summary for a preselected identity; review lists all packages. The existing
fleet bulk planner remains intact and shares the per-app store-update helper.
Storage schema, connector, synchronization and publication are unchanged.
This change has not been published; current-run evidence is in
[HANDOFF-2026-10-12-MULTI-APP-BINDING.md](HANDOFF-2026-10-12-MULTI-APP-BINDING.md).

## Versions and major components

| Component | Version / responsibility |
| --- | --- |
| App | `dev.cyclone.cloak`, `0.10.0-rc.2`, code 23; minimum API 29, target/compile API 36 |
| Module | `cyclone_cloak`, `v0.10.0-rc.2`, code 23; ARM64/ARMv7, minimum API 26 |
| Profile | schema `0.2`; existing JSON storage unchanged |
| Binding / reader | binding schema 2; module index `schemaVersion: 2`, existing key lookup unchanged |
| Connector | `cyclone.connector/1.3`; Cyclone 5.0.0-alpha.122 or newer |

- MainActivity → CloakApp/CloakViewModel hosts Phones, Identities, Profiles,
  Health and the grouped phone editor. Blocking coherence errors prevent saves.
- `forge`: fifteen shared catalog templates, confidence metadata, stable seeded
  generation, Kotlin/Python coherence findings, local uniqueness and profile
  backup projection/validation. Phones offers one-click validated generation.
- `data`: atomic profile JSON storage; whole-batch import stages/commits with
  rollback. Backup export writes an app-private file and a pending Downloads
  document. Default backups omit seeds/IDs; explicit selection includes them.
  Imports preview and confirm; invalid/mismatched batches save nothing.
- `ui`: filtered binding review, guided profile/app/identity/review/confirmation,
  actionable warnings and retry; Readiness Doctor v3 with per-check reasons,
  redacted diagnostics and a checked-in thirteen-area compatibility matrix.
- `cyclone`: existing connector, stable profile-ID/package binding identity,
  conservative reconciliation, retained ambiguous records and health warnings.
- `root`: kernel-locked publisher shares, staged merged tree, rollback/recovery,
  explicit locked/completed/failed/recovered and stale-marker diagnostics.
- `module`: Build/properties and Pine Java callbacks; runtime coverage is partial.
  The APK embeds the matching module for explicit repair. `android/probe` is a
  separate inspection app. Runtime behavior was not changed in this RC run.

[ARCHITECTURE.md](ARCHITECTURE.md), [STATE_LAYOUT.md](STATE_LAYOUT.md),
[CATALOG-SOURCES.md](CATALOG-SOURCES.md) describe current behavior. Historical
release/checkpoint/handoff files record their dated results, not RC2 verification.

## Verification and commands

Toolchain: Temurin JDK 17.0.20, Gradle 8.11.1, SDK
`C:/Users/Agent/android-sdk`, NDK 27.0.12077973, CMake 3.22.1.
No Gradle wrapper is checked in. Exact local commands:

```powershell
$env:JAVA_HOME='C:/Program Files/Eclipse Adoptium/jdk-17.0.20.101-hotspot'
& "$env:TEMP/cloak-build/gradle-8.11.1/bin/gradle.bat" -p android :app:assembleDebug :app:assembleRelease :module:packageModule --offline --no-daemon
& "$env:TEMP/cloak-build/gradle-8.11.1/bin/gradle.bat" -p android :app:testDebugUnitTest :module:testDebugUnitTest --offline --no-daemon
& "$env:TEMP/cloak-build/gradle-8.11.1/bin/gradle.bat" -p android :module:testDebugUnitTest --offline --no-daemon
& "$env:TEMP/cloak-build/gradle-8.11.1/bin/gradle.bat" -p android :module:testDebugUnitTest --offline --no-daemon --rerun-tasks
uv run --with pytest -- python -m pytest forge/tests -q
./tools/run-local-profile-tests.ps1
./tools/run-local-sync-tests.ps1
./tools/run-local-binding-tests.ps1
./tools/run-local-backup-tests.ps1
uv run -- python tools/verify_release.py --tag v0.10.0-rc.2 --artifacts
git -c core.safecrlf=false diff --check
```

- Python: **185 passed** (including catalog/shared vectors and release checks).
- Module Gradle target: **5 passed**, 0 skipped/failures, explicitly rerun.
- Supplemental JVM suites: profile **100**, sync/publication **117**, binding
  **12**, backup **20** passed. Suites overlap; these are not a total unique count.
  Sync includes fourteen real WSL kernel-lock/recovery cases. They do not replace
  full Android/Compose/Robolectric tests, which now passed in release CI.
- Full app/module combined test command: **blocked** at
  `:app:copyRobolectricRuntime`: no cached
  `org.robolectric:android-all-instrumented:14-robolectric-10818077-i7` JAR in
  offline mode. Full current app/Compose suite has not passed locally; the same released source
  passed full Android/module tests in GitHub release CI on 2026-10-12.
  The first CI attempt exposed a corrupted progress-label ellipsis; the text
  encoding was fixed without removing the assertion, then the full suite passed.
- Debug APK, unsigned release APK and module ZIP build locally. Artifact checking
  checks versions/code, both ARM ELFs, ZIP integrity and exact embedded module.
  A first check caught a mismatch after overlapping Gradle commands regenerated
  the module ZIP; the final sequential rebuild and artifact verification passed.
  Final build: 50 seconds, 123 tasks (18 executed).
- Release workflow tests both targets and builds/verifies/attaches APK and ZIP.
  RC2 release CI built and verified the signed APK and module ZIP and published
  both. Android/module tests passed in 1m43s; signed builds passed in 1m30s.

Local outputs: `android/app/build/outputs/apk/debug/app-debug.apk`,
`android/app/build/outputs/apk/release/app-release-unsigned.apk`,
`android/module/build/module/cyclone-cloak-0.10.0-rc.2.zip`.
No compiler/build warnings in successful builds or fresh module tests. CI tooling
warned about Node20/setup-java v4 and Node API deprecations, an unmatched uv
cache glob and a future Ubuntu runner migration. The missing
Robolectric dependency is a test blocker, not a compiler warning.

## Unresolved release gates and limitations

- Full Android/module tests passed in release CI; local app execution still needs
  resolved Robolectric dependencies. No tests were removed or bypassed.
- **RC2 physical-device behavior is unverified**: signed upgrades, activation
  after reboot, Android Toybox publication/recovery, multi-user health/bindings,
  secondary-user root reads, MediaStore Downloads and backup/import UI behavior.
  Reboot-dependent checks are deferred at the owner's request; see
  [DEFERRED-DEVICE-CHECKS.md](DEFERRED-DEVICE-CHECKS.md).
- [RUNTIME-COVERAGE.md](RUNTIME-COVERAGE.md) identifies the older measured API36
  binary: 13 verified reads among 70, many failed identifiers. It is not RC2
  evidence. Display geometry, camera/sensor metadata and App Set SDK callbacks
  remain incomplete. Ready does not prove interception or Play Integrity.
- Publication has a two-rename reader gap and no power-loss guarantee or lock
  coordination with older/external writers. Failed rollback retains the backup
  for the next recovery attempt; all publishers should be upgraded together.
- Local profile batch commits coordinate in-process storage callers, not multiple
  processes or power loss. Downloads/private export commits are separate. Private
  backups disappear on uninstall/data clear; full-state backup is absent.
- Legacy catalog source audits remain outstanding; representative defaults and
  confidence categories do not imply firmware capture or physical verification.
- Attestation/keybox management, automatic fingerprint rotation/reset reseeding,
  uptime offsets and network-egress behavior are not implemented.

Preparation report: [HANDOFF-2026-10-12-RC2.md](HANDOFF-2026-10-12-RC2.md).
Publication evidence: [HANDOFF-2026-10-12-PUBLISH-RC2.md](HANDOFF-2026-10-12-PUBLISH-RC2.md).
