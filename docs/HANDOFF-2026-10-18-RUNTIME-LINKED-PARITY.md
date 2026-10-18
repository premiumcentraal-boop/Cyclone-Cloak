# Runtime Linked Parity Phase 1 — 2026-10-18

Prepared as v0.11.0-alpha.23 / app and module code 47.

Ten pure Kotlin/Python links use authoritative base values, with MATCHED,
MISMATCHED, NOT_PRESENT and BASE_MISSING states. Density uses existing
`display.density`; the requested `display.density_dpi` base field does not exist.
Contract format 2 records parity and conditionally promotes matching implemented
links; format 1 and schema 0.2/0.3 profiles remain readable. No profile migrations.

Scoped Display.getMode/getSupportedModes return profile-backed mode objects.
Original objects/arrays and physical display configuration remain unchanged.
Existing metric/refresh, locale/timezone and telephony behavior is preserved.
The optional linked_parity report adds 13 hash-only rows without changing the
legacy 89 rows. Coverage UI shows parity and mismatched paths, with no raw values.
No binding, connector, publication, profile schema, storage or permission changes.

## Changed files

- Pure links/contracts: RealityRuntimeLink.kt, RealityRuntimeContract.kt,
  generated RealityRuntimeTable.kt; runtime_link.py, runtime_contract.py;
  catalog/reality-runtime-fields.json and frozen format-1 table;
  runtime contract schemas, generator and shared parity vector.
- Runtime: HookBridge.java; module build.gradle adds test-only Robolectric.
- Probe: LinkedParityReport.java, RealityProbe.java, RealityReport.java, ProbeActivity.java and
  generated runtime table asset.
- UI: RealityCoverageCard.kt.
- Tests: RealityRuntimeLinkTest.kt, RealityRuntimeContractTest.kt,
  RealityCoverageCardTest.kt, DisplayModeParityTest.java, HookBridgeTest.java,
  LinkedParityReportTest.java, RealityReportTest.java, test_runtime_link.py, test_runtime_contract_schema.py.
- Release metadata: app/build.gradle, module/module.prop.
- Docs: README, DEVICE-REALITY, REALITY-RUNTIME-BACKLOG, ARCHITECTURE,
  CURRENT-STATE, RUNTIME-COVERAGE, release notes and this handoff.

## Verification commands

```powershell
$env:JAVA_HOME='C:/Program Files/Eclipse Adoptium/jdk-17.0.20.101-hotspot'
$env:CLOAK_TEST_WSL='1'
& "$env:TEMP/cloak-build/gradle-8.11.1/bin/gradle.bat" -p android :app:testDebugUnitTest :module:testDebugUnitTest :probe:testStandardDebugUnitTest :probe:testDiagnosticsDebugUnitTest :app:assembleDebug :app:assembleRelease :module:packageModule :probe:assembleStandardDebug :probe:assembleDiagnosticsDebug --offline --no-daemon --console=plain
uv run --with pytest -- python -X utf8 -m pytest forge/tests -q
uv run --with pytest --with jsonschema -- python -X utf8 -m pytest forge/schema_tests -q
uv run -- python -X utf8 -m unittest discover -s android/probe/tests -v
uv run -- python -X utf8 tools/generate_runtime_contract.py --check
uv run -- python -X utf8 tools/verify_release.py --tag v0.11.0-alpha.23 --artifacts
git diff --check
git diff --cached --check
```

Test totals: app 577; module 23; probe 107 per flavor; forge 594;
schema 69; probe host 7. Total 1,484, zero failures/skips. Debug and release
packaging gates passed; signing and published artifact checks use the existing CI lane.
No compiler warnings; Git may report existing LF/CRLF normalization notices.

## Physical limits

Physical verification is PENDING. The connected phone still runs module
alpha.18/code 42. No app/module installation, permission changes or restart was
performed. API 34/35 framework-object and callback tests are local evidence only;
ART installation and older constructor fallback still need device verification.
Historical attached alpha.18 evidence does not verify this build. No platform
verdict or broader compatibility is claimed. A separately coordinated matching
module update/restart and existing probe run are the next device check.

Pre-publication review caught scalar numeric hash differences (90 versus 90.0).
The cancelled first release workflow published nothing. Scalar numeric comparison
is now normalized without coercing strings/booleans or changing historical evidence.
