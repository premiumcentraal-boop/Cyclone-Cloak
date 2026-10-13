# Current state

Updated 2026-10-13. **v0.11.0-alpha.6 / versionCode 30** is the
[latest published prerelease](https://github.com/premiumcentraal-boop/Cyclone-Cloak/releases/tag/v0.11.0-alpha.6),
release commit `f4ff3992759ef1652a25aceda8d07fb9c3dd0daa`.
[Release CI](https://github.com/premiumcentraal-boop/Cyclone-Cloak/actions/runs/38472829633) passed all four suites,
signed builds and matching-artifact checks before attaching the APK and module ZIP.
[Alpha.6 notes](RELEASE_0.11.0-alpha.6.md) describe the read-only library-quality
summary and upgrade limits. Later publication-documentation commits on main do
not change the tagged payload.

## Versions and major components

| Component | Current source version / responsibility |
| --- | --- |
| App | `dev.cyclone.cloak`, `0.11.0-alpha.6`, code 30; minimum API 29, target/compile API 36 |
| Module | `cyclone_cloak`, `v0.11.0-alpha.6`, code 30; ARM64/ARMv7, minimum API 26; packaging metadata update only |
| Profile | Schema `0.3`, legacy `0.2` accepted; existing JSON storage unchanged |
| Binding / reader | Binding schema 2; module index `schemaVersion: 2`; existing key lookup unchanged |
| Connector | `cyclone.connector/1.3`; Cyclone 5.0.0-alpha.122 or newer |

- Phones/Identities: catalog-based generation, coherent validation, deterministic
  uniqueness retries, engine bundle import and separate local engine metadata.
- Profiles: multi-app/All apps binding review from either entry point, sharing
  the existing per-app record and bulk-binding path.
- Health: Readiness Doctor, local duplicate diagnostics, the new read-only
  **Library quality** summary, and the compatibility coverage matrix. Library
  counts use readable profiles and existing validation, binding and local-record
  facts; unknown inventory is not presented as an unreadable count.
- Local compatibility reports: preview/save, provenance, profile/artifact matches,
  freshness and guarded rotation/reset actions. Existing diagnostics classification
  distinguishes missing/blocked/incomplete proof. All states are local evidence,
  not platform verdicts; the library summary does not change posture decisions.
- Data/root/connector: existing atomic profile storage and backup behavior,
  conservative reconciliation, publisher shares and recovery. This release
  changes neither storage layout nor synchronization/publication behavior.
- Module/probe: existing runtime inspection implementation and separate diagnostics
  app. Module/runtime behavior is unchanged; artifact-specific device evidence is
  tracked independently in [RUNTIME-COVERAGE.md](RUNTIME-COVERAGE.md).

## Local verification and release gate

The preparation checks passed: app **398 tests (375 passed, 23 existing Windows
POSIX-shell skips)**, module **12 passed**, forge **227 passed**, schema **33 passed**.
Debug assembly succeeded. No tests were removed or bypassed. The old offline
Robolectric dependency blocker no longer applies to this environment.

```powershell
$env:JAVA_HOME='C:/Program Files/Eclipse Adoptium/jdk-17.0.20.101-hotspot'
& "$env:TEMP/cloak-build/gradle-8.11.1/bin/gradle.bat" -p android :app:testDebugUnitTest :module:testDebugUnitTest :app:assembleDebug --offline --no-daemon
uv run --with pytest -- python -m pytest forge/tests -q
uv run --with pytest --with jsonschema -- python -m pytest forge/schema_tests -q
uv run -- python tools/verify_release.py --tag v0.11.0-alpha.6
git diff --check
```

Release CI passed all four suites on Linux, built/signed the APK and packaged
the module ZIP, verified version agreement and exact embedded-module bytes, then
published both prerelease assets. Signing uses the existing CI secrets.
Local debug output: `android/app/build/outputs/apk/debug/app-debug.apk`.
Release asset names: `app-release.apk`, `cyclone-cloak-0.11.0-alpha.6.zip`.

## Known limits and separately verified device evidence

- Local quality, readiness and PASS records do not prove interception or a
  platform verdict. Runtime coverage is partial. Probe fixture results concern
  their measured artifact; they do not verify arbitrary saved identities or the
  currently installed module. No device actions were performed for this release.
- Existing physical-device checks remain separate, including reboot/activation,
  multi-user behavior, publication/recovery and backup/import UI. See
  [DEFERRED-DEVICE-CHECKS.md](DEFERRED-DEVICE-CHECKS.md) and the runtime coverage ledger.
  Earlier artifact measurements are not alpha.6 verification.
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
