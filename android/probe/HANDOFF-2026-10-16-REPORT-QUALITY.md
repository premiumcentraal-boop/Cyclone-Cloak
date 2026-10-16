# Diagnostics report quality — 2026-10-16

Based on main after v0.11.0-alpha.15 / code 39. No release/version change,
device actions, or edits to production app/module, connector or binding storage.
Pre-existing untracked handoffs and evidence were preserved.

## Changes

- `build.gradle`: diagnostics-only Play Services ads identifier 18.3.0;
  standard probe no longer gains the SDK through a global opt-in property.
- `src/diagnostics/AndroidManifest.xml`: the four requested Android permissions
  already existed. Kept them and NEARBY_WIFI_DEVICES; added AD_ID only here,
  overriding the standard probe's removal so the real SDK can read the ID.
- `DiagnosticsProbeActivity`: precise, API-aware per-read permission checks;
  repeated launches do not accumulate permission requests. Headless runs still
  neither grant permissions nor prompt. Denial stays BLOCKED with its reason.
- `ProbeActivity`, new `ProbeReportPolicy` and `AdvertisingIdRead`: real SDK
  invocation on the worker, explicit blocked failures, complete fixture-body
  comparison, external labels, and optional null observations.
- `tools/profile_check.py`: carry/check a/b/external labels alongside the exact
  profile hash. Unmapped changes with no expectation on either side are INFO.
  OBSERVED/no-expectation rows have a separate `not_covered_rows` count and
  summary line. Blocked/missing reads, mismatches and one-sided expectations
  retain their warning/failure semantics. No row is promoted to VERIFIED.
- Tests: two new Java test classes, extended probe host tests and forge harness
  tests for classification, launch labels/hash mismatch, permissions and SDK.

## Local checks

All passed: app **522**, module **12**, probe **15 per flavor**, forge **293**,
schema **33**, probe host unittest **7**. No compiler/build warnings emitted.
`git diff --check` and existing alpha.15 metadata verification passed.

```powershell
$env:JAVA_HOME='C:/Program Files/Eclipse Adoptium/jdk-17.0.20.101-hotspot'
$env:CLOAK_TEST_WSL='1'
& "$env:TEMP/cloak-build/gradle-8.11.1/bin/gradle.bat" -p android :probe:testStandardDebugUnitTest :probe:testDiagnosticsDebugUnitTest :probe:assembleStandardDebug :probe:assembleDiagnosticsDebug :app:testDebugUnitTest :module:testDebugUnitTest :app:assembleDebug :module:packageModule --console=plain
# Rechecked after the final probe-policy tests/error-reason extraction:
& "$env:TEMP/cloak-build/gradle-8.11.1/bin/gradle.bat" -p android :probe:testStandardDebugUnitTest :probe:testDiagnosticsDebugUnitTest :probe:assembleStandardDebug :probe:assembleDiagnosticsDebug --console=plain
uv run --with pytest -- python -X utf8 -m pytest forge/tests -q
uv run --with pytest --with jsonschema -- python -X utf8 -m pytest forge/schema_tests -q
uv run -- python -X utf8 -m unittest discover -s android/probe/tests -v
uv run -- python -X utf8 tools/verify_release.py --tag v0.11.0-alpha.15
git diff --check
```

Built APKs:

- Standard: `android/probe/build/outputs/apk/debug/probe-debug.apk` (unchanged
  location). Merged manifest has zero permissions; DEX has no AdvertisingIdClient.
- Diagnostics: `android/probe/build/outputs/apk/diagnostics/debug/probe-diagnostics-debug.apk`.
  Merged manifest and DEX confirm the permissions and actual SDK are present.
- Companion debug APK and module ZIP also built successfully.

## Remaining gaps

On-device advertising-ID measurement and permission outcomes are UNVERIFIED for
this change. Missing/unavailable Play Services, denied access or other API errors
remain explicit collection gaps; the SDK does not guarantee a successful match.
Historical evidence was read only, not rewritten or presented as new device proof.
Reclassifying existing alpha.15 evidence retains zero failures: small has 1 warning,
1 blocked row and 12 uncovered rows; full transition has 7 warnings, 5 blocked rows
and 14 uncovered rows. Both remain DEGRADED for actual coverage gaps, rather than
merely for optional observations. Counts are distinct row IDs across snapshots.
