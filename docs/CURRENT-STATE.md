# Current state

Updated 2026-10-18. Current source **v0.11.0-alpha.19 / versionCode 43** adds
Batch Identity Workshop. The release is published only after the full local gate
and existing signed-validation CI pass. See [release notes](RELEASE_0.11.0-alpha.19.md).

Identities now offers multi-template planning (1–20 per template, up to 100 total),
deterministic per-draft seed derivation with uniqueness retries, an in-memory
preview, regeneration/removal, private details and explicit save confirmation.
Each selected item is revalidated against the current library, atomically added
without replacement and reported as saved, failed or skipped. Earlier successes
survive later failures. No binding is created and no connector/publication path
is called. Existing engine metadata remains in its separate hash-bound sidecar.

Versioned local JSON plans preserve seeds, labels, counts, revisions and template
hashes for reproduction, without profile bodies or raw identifiers. Seeds are
private: export requires an explicit warning/confirmation. Import always previews
and recomputes validation; incompatible templates or derivations fail safely.
No profile migration, schema/storage-layout change, new permissions, networking,
connector or module runtime change. Workshop/SAF device verification: UNVERIFIED.
No platform verdict or broader compatibility is claimed.

Separately, the alpha.18 diagnostics lifecycle harness completed three cycles
(24 captures) with zero cold-restoration failures. Production and permission audits
and cleanup passed each cycle, with zero APK installations. Four user-17 bound
permission blocks remain; advertising.id was BLOCKED/IOException in cycle 3's
cold post-removal capture and remains a limitation, not a pass.
[Evidence](runtime-evidence/2026-10-18-015343-alpha18-lifecycle-install-once-3cycles/EXECUTION-REPORT.md)
does not verify the workshop or newly packaged alpha.19 binaries.

## Versions and major components

| Component | Current source version / responsibility |
| --- | --- |
| App | `dev.cyclone.cloak`, `0.11.0-alpha.19`, code 43; minimum API 29, target/compile API 36 |
| Module | `cyclone_cloak`, `v0.11.0-alpha.19`, code 43; ARM64/ARMv7, minimum API 26; metadata-only module change from alpha.18 |
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

The workshop checks passed: app **537 passed, zero skipped**, module
**12 passed**, forge **503 passed**, schema **33 passed**, probe **91 per flavor**
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
uv run -- python tools/verify_release.py --tag v0.11.0-alpha.19 --artifacts
git diff --check
```

The release workflow reruns tests, signs the APK using existing CI secrets, packages
the module and checks exact embedded-module agreement before uploading the prerelease.
Local debug output: `android/app/build/outputs/apk/debug/app-debug.apk`.
Release asset names: `app-release.apk`, `cyclone-cloak-0.11.0-alpha.19.zip`,
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
  multi-user behavior beyond the recorded diagnostics transition, publication/recovery and backup/import UI. See
  [DEFERRED-DEVICE-CHECKS.md](DEFERRED-DEVICE-CHECKS.md) and the runtime coverage ledger.
  Earlier artifact measurements are not physical verification of alpha.19.
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
