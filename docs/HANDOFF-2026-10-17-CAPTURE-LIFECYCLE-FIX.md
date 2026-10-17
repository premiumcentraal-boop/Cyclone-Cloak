# Diagnostics capture lifecycle fix — 2026-10-17

Scope: diagnostics probe and evidence tooling only. No production app/module,
connector, binding storage, permission, schema or release metadata changes.

The previous cross-user run stopped at the owner-return warm primer with
`INVALID_OR_DUPLICATE_ROW_ID`. Capture readers shared mutable activity fields;
`onNewIntent` could reset them while an older worker was still collecting.

Each capture now owns its immutable request metadata and mutable profile,
expectations and rows. A process-wide generation gate serializes new captures
with final atomic writes. Superseded workers cannot publish or update the UI.
Before success, the probe requires exactly the 89 known, non-empty, unique row
IDs and every required row field. Invalid collections produce structured failures.

The transition runner preserves bounded failure evidence before cleanup: fixed
reason/status, known row IDs, hashes, trusted request metadata and error type.
Raw readings and exception messages are excluded. Unknown row IDs are hashed.
The maximum retained row count is 256. Both user overlays are always cleaned.

## Local commands

```powershell
$env:JAVA_HOME='C:/Program Files/Eclipse Adoptium/jdk-17.0.20.101-hotspot'
$env:CLOAK_TEST_WSL='1'
& "$env:TEMP/cloak-build/gradle-8.11.1/bin/gradle.bat" -p android :app:testDebugUnitTest :module:testDebugUnitTest :probe:testStandardDebugUnitTest :probe:testDiagnosticsDebugUnitTest --offline --no-daemon --console=plain
uv run --with pytest -- python -X utf8 -m pytest forge/tests -q
uv run --with pytest --with jsonschema -- python -X utf8 -m pytest forge/schema_tests -q
uv run -- python -X utf8 -m unittest discover -s android/probe/tests -v
git diff --check
git diff --cached --check
```

Forge: 438 passed. Schema: 33 passed. Probe host checks: 7 passed.
Android: 522 app, 12 module, 91 standard probe and 91 diagnostics probe tests
passed. No compiler warnings. Physical results are recorded alongside the rerun evidence.
The new Java tests cover isolation, generation ownership, overlap, stable request
metadata, duplicate/empty/missing/unknown rows and exact completeness.

## Reproduction after committing

Build `:probe:assembleDiagnosticsDebug` with the same Gradle flags. Record the
commit and APK SHA-256, then run:

```powershell
uv run -- python -X utf8 tools/run_user_transition.py --source-checkout C:/Users/Agent/Cyclone/cloak-repo --source-commit <commit> --adb C:/Users/Agent/android-sdk/platform-tools/adb.exe --serial 3B171FDJH0061G --secondary 17 --output <new-evidence-directory>
```

The runner requires a clean tracked checkout at that commit and installed
production alpha.17/code 41. It installs diagnostics only, verifies its installed
hash for both users, and captures owner A, secondary B warm/cold, owner A warm/cold.
Warm captures retain primers and prove process reuse. Cold captures prove process
replacement. Audits cover production entries, published bodies, publisher shares,
module files and accessible binding stores. Permissions/app-op modes are compared
before and after; no permission changes are requested.

The four existing user-17 permission blocks are expected limitations. Other locked
users' private stores cannot be audited. Evidence is local diagnostics only and
does not establish a platform verdict. No tag or release is created by this run.
