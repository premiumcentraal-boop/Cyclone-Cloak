# Current state

Updated 2026-10-17. **v0.11.0-alpha.17 / versionCode 41** is the
[latest prerelease](https://github.com/premiumcentraal-boop/Cyclone-Cloak/releases/tag/v0.11.0-alpha.17).
[Alpha.17 notes](RELEASE_0.11.0-alpha.17.md) describe diagnostics-only safe
expectations for 12 previously uncovered rows. Run 3 on Pixel 8 / Android user 0
measured diagnostics commit `fbdc0faa5330bb0672894eb3110318307d738816` against
the installed alpha.16/code 40 module: three captures, each 82 PASS, 7 OBSERVED,
0 FAIL/BLOCKED/MISSING. Paired classification: 246 PASS, 21 OBSERVED, zero
warnings, blocked or pending rows. Both stability comparisons passed; six
external-state rows remain observations. All 327 production entries and audited
files were preserved and temporary probe state removed. Permissions unchanged.
[Run 3 summary](runtime-evidence/2026-10-17-run3-expectations-022627/summary.md)
and [build provenance](runtime-evidence/2026-10-17-run3-expectations-022627/build-provenance.json)
identify the measured APK; this does not verify alpha.17 production binaries,
Android-user transitions or broader compatibility. No platform verdict is claimed.
No production runtime, connector, binding storage, publication, schemas, permissions
or module behavior changed. See [harness usage](PROFILE-TEST-HARNESS.md).

## Versions and major components

| Component | Current source version / responsibility |
| --- | --- |
| App | `dev.cyclone.cloak`, `0.11.0-alpha.17`, code 41; minimum API 29, target/compile API 36 |
| Module | `cyclone_cloak`, `v0.11.0-alpha.17`, code 41; ARM64/ARMv7, minimum API 26; metadata-only change from alpha.16 |
| Profile | Schema `0.3`, legacy `0.2` accepted; existing JSON storage unchanged |
| Binding / reader | Binding schema 2; module index `schemaVersion: 2`; existing key lookup unchanged |
| Connector | `cyclone.connector/1.3`; Cyclone 5.0.0-alpha.122 or newer |

- Phones/Identities: catalog-based generation, coherent validation, deterministic
  uniqueness retries, engine bundle import and separate local engine metadata.
- Profiles: multi-app/All apps binding review from either entry point, sharing
  the existing per-app record and bulk-binding path.
- Health: per-profile Companion copies checks, Readiness Doctor, local duplicate diagnostics, the read-only
  **Library quality** summary, and the compatibility coverage matrix. Library
  counts use readable profiles and existing validation, binding and local-record
  facts; unknown inventory is not presented as an unreadable count.
- Local compatibility reports: preview/save, provenance, profile/artifact matches,
  freshness and guarded rotation/reset actions. Existing diagnostics classification
  distinguishes missing/blocked/incomplete proof. All states are local evidence,
  not platform verdicts; the library summary does not change posture decisions.
- Data/root/connector: existing atomic profile storage and backup behavior,
  conservative reconciliation, publisher shares and recovery. This release
  preserves storage layout and connector behavior. Binding work reuses one root
  session after preflight; publication scripts retain their inherited lock descriptor and phase diagnostics.
- Module/probe: unchanged runtime implementation and separate diagnostics app.
  The real advertising-ID SDK and its permissions are diagnostics-only. Optional
  observations are counted separately, not promoted to verified coverage. Evidence
  remains artifact-specific; see [RUNTIME-COVERAGE.md](RUNTIME-COVERAGE.md).

## Local verification and release gate

The diagnostics-quality checks passed: app **522 passed, zero skipped**, module
**12 passed**, forge **411 passed**, schema **33 passed**, probe **79 per flavor**
and host probe checks **7 passed**.
WSL enabled the actual publication shell tests on Windows. Debug assembly and module
packaging succeeded without compiler warnings. The standard release CI gate signs the APK, packages the module and verifies
that the APK embeds the exact released module ZIP before publishing.

```powershell
$env:JAVA_HOME='C:/Program Files/Eclipse Adoptium/jdk-17.0.20.101-hotspot'
$env:CLOAK_TEST_WSL='1'
& "$env:TEMP/cloak-build/gradle-8.11.1/bin/gradle.bat" -p android :app:testDebugUnitTest :module:testDebugUnitTest :probe:testStandardDebugUnitTest :probe:testDiagnosticsDebugUnitTest :app:assembleDebug :app:assembleRelease :module:packageModule :probe:assembleStandardDebug :probe:assembleDiagnosticsDebug --offline --no-daemon
uv run --with pytest -- python -m pytest forge/tests -q
uv run --with pytest --with jsonschema -- python -m pytest forge/schema_tests -q
uv run -- python -m unittest discover -s android/probe/tests -v
uv run -- python tools/verify_release.py --tag v0.11.0-alpha.17 --artifacts
git diff --check
```

The release workflow reruns tests, signs the APK using existing CI secrets, packages
the module and checks exact embedded-module agreement before uploading the prerelease.
Local debug output: `android/app/build/outputs/apk/debug/app-debug.apk`.
Release asset names: `app-release.apk`, `cyclone-cloak-0.11.0-alpha.17.zip`,
`evidence-bundle.zip`, `summary.md`, `build-provenance.json`.

## Known limits and separately verified device evidence

- Local quality, readiness and PASS records do not prove interception or a
  platform verdict. Runtime coverage is partial. Probe fixture results concern
  their measured artifact; they do not verify arbitrary saved identities or the
  currently installed module. Alpha.9 publication proof covers normal Main-app
  publication on Pixel 8 and two Cyclone profiles; it does not verify runtime hooks
  or an independent profile-side publisher. Cyclone could not open the receiving
  profile for that additional check.
- Existing physical-device checks remain separate, including reboot/activation,
  multi-user behavior, publication/recovery and backup/import UI. See
  [DEFERRED-DEVICE-CHECKS.md](DEFERRED-DEVICE-CHECKS.md) and the runtime coverage ledger.
  Earlier artifact measurements are not physical verification of alpha.17.
- Publisher swaps retain the existing two-rename reader gap and do not guarantee
  power-loss safety or lock coordination with older/external writers.
- Profile batch commits coordinate cooperating in-process callers. Downloads
  and private backup commits are separate; private data is lost on uninstall.
  A full-state backup is not implemented.
- Catalog confidence indicates source quality, not firmware capture or device
  verification. Automatic resets/reseeding and network-egress control are absent.

Architecture and contracts: [ARCHITECTURE.md](ARCHITECTURE.md),
[STATE_LAYOUT.md](STATE_LAYOUT.md), [CYCLONE_PROFILES_REPORT.md](CYCLONE_PROFILES_REPORT.md).
Historical handoffs preserve their dated evidence; current library behavior is
in [Run E completion](HANDOFF-2026-10-13-RUN-E-LIBRARY-QUALITY-COMPLETED.md).
