# Alpha.16 diagnostics-quality release — 2026-10-17

Release: v0.11.0-alpha.16, app/module code 40. Diagnostics-only: production
runtime, connector, binding, publication, storage and module behavior unchanged.

## Changed files

Probe build/flavor manifest, DiagnosticsProbeActivity, ProbeActivity,
AdvertisingIdRead, ProbeReportPolicy and their Java/host tests; tools/profile_check.py
and forge/tests/test_profile_check.py. Original redacted evidence and a separate
PROVENANCE-NOTE.md are checked in. Release metadata, README, ARCHITECTURE,
CURRENT-STATE, release notes and the two signed workflows describe/gate this release.
Unrelated pre-existing handoffs and evidence remain unstaged.

## Exact local checks

PowerShell environment: JAVA_HOME=C:/Program Files/Eclipse Adoptium/jdk-17.0.20.101-hotspot;
CLOAK_TEST_WSL=1. Gradle executable: $env:TEMP/cloak-build/gradle-8.11.1/bin/gradle.bat.

```powershell
& "$env:TEMP/cloak-build/gradle-8.11.1/bin/gradle.bat" -p android :app:testDebugUnitTest :module:testDebugUnitTest :probe:testStandardDebugUnitTest :probe:testDiagnosticsDebugUnitTest :app:assembleDebug :app:assembleRelease :module:packageModule :probe:assembleStandardDebug :probe:assembleDiagnosticsDebug --offline --no-daemon --console=plain
uv run --with pytest -- python -X utf8 -m pytest forge/tests -q
uv run --with pytest --with jsonschema -- python -X utf8 -m pytest forge/schema_tests -q
uv run -- python -X utf8 -m unittest discover -s android/probe/tests -v
uv run -- python -X utf8 tools/verify_release.py --tag v0.11.0-alpha.16 --artifacts
git diff --check
git diff --cached --check
```

PASS: 522 app, 12 module, 15 probe tests per flavor (30), 293 forge, 33 schema,
7 probe host tests: 897 executions, zero failures/skips. Debug/release and both
probe APK builds passed; module packaging/ELF/embedded-ZIP/version checks passed.
Local release APK is unsigned; protected CI supplies the existing signing key.
No compiler warnings in the captured Gradle log; Git emitted LF/CRLF notices.

## Evidence scope and gaps

Original evidence bundle SHA-256:
d80f3be6c014e611a266bd44f3a96751f86ad2295f3f83928ad356649a52138b.
Three alpha.15/code39 device captures: each 89 rows, 77 PASS, 12 OBSERVED/not
covered, zero warnings/FAIL/BLOCKED/MISSING. Fixture labels a/a/b match hashes.
327 production entries and audited files preserved. No new device work in this
release run. The unscoped manifest permission map is not a per-user grant record;
read PROVENANCE-NOTE.md. Alpha.16 physical behavior, denial paths, optional
expectations, multi-user transitions and arbitrary apps remain unverified here.
No platform verdict is claimed.

## Publication

Signed validation, release CI, published asset hashes/signature and release URL
will be recorded after the protected gates complete.
