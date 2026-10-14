# Current state

Updated 2026-10-14. **v0.11.0-alpha.9 / versionCode 33** is the
[latest prerelease](https://github.com/premiumcentraal-boop/Cyclone-Cloak/releases/tag/v0.11.0-alpha.9).
[Alpha.9 notes](RELEASE_0.11.0-alpha.9.md) describe bounded local diagnostics and
the Android shell publication locking repair. Signed-artifact installation, normal
app publication and two-profile binding preservation passed on Pixel 8; see the
[publication proof handoff](HANDOFF-2026-10-14-ALPHA9-PUBLICATION-PROOF.md).
Runtime hooks and reader/storage formats are unchanged.

## Versions and major components

| Component | Current source version / responsibility |
| --- | --- |
| App | `dev.cyclone.cloak`, `0.11.0-alpha.9`, code 33; minimum API 29, target/compile API 36 |
| Module | `cyclone_cloak`, `v0.11.0-alpha.9`, code 33; ARM64/ARMv7, minimum API 26; native property callback path added on main |
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
  preserves storage layout and synchronization behavior; publication uses an inherited lock descriptor with explicit phase diagnostics.
- Module/probe: existing runtime inspection implementation and separate diagnostics
  app. The native property callback hook and probe row are implemented; device
  verification remains pending. Earlier evidence does not cover the new row; evidence is
  tracked independently in [RUNTIME-COVERAGE.md](RUNTIME-COVERAGE.md).

## Local verification and release gate

The alpha.9 preparation checks passed: app **428 passed, zero skipped**, module
**12 passed**, forge **227 passed**, schema **33 passed**, diagnostics helper **3 passed**.
WSL enabled the actual publication shell tests on Windows. Debug assembly and module
packaging succeeded without compiler warnings. Release CI passed, signed both
artifacts and verified the APK embeds the exact released module ZIP.

```powershell
$env:JAVA_HOME='C:/Program Files/Eclipse Adoptium/jdk-17.0.20.101-hotspot'
$env:CLOAK_TEST_WSL='1'
& "$env:TEMP/cloak-build/gradle-8.11.1/bin/gradle.bat" -p android :app:testDebugUnitTest :module:testDebugUnitTest :app:assembleDebug :module:packageModule --offline --no-daemon
uv run --with pytest -- python -m pytest forge/tests tools/tests -q
uv run --with pytest --with jsonschema -- python -m pytest forge/schema_tests -q
uv run -- python tools/verify_release.py --tag v0.11.0-alpha.9
git diff --check
```

The release workflow reruns tests, signs the APK using existing CI secrets, packages
the module and checks exact embedded-module agreement before uploading the prerelease.
Local debug output: `android/app/build/outputs/apk/debug/app-debug.apk`.
Release asset names: `app-release.apk`, `cyclone-cloak-0.11.0-alpha.9.zip`.

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
  Earlier artifact measurements are not alpha.7 verification, including the new
  `properties.nativeCallback.ro.product.model` row.
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
