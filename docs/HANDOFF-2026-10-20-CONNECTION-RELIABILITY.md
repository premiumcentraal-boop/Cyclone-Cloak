# Connection and large-library reliability — 2026-10-20

Base: Cloak main a51c6d1; published alpha.27/code 51. This is a repair candidate,
not a new release or a claim of complete device compatibility.

## Changes

- Report a live approved connector handshake and registry immediately, while the
  existing serialized reconciliation continues. Cached state cannot establish a
  connection. Health explicitly warns when reconciliation is still running.
- Keep single-flight reconnect behavior, preserve Connected during a coalesced
  follow-up, and still surface a later failure instead of hiding it.
- Stop describing every BAD_REQUEST as a missing app. Capacity and payload
  rejection use the same code; retain local records and show an honest reason.
- Put Check & repair near the top of Health. Paginate readiness checks in groups
  of twenty; verdicts, counts and copied diagnostics still cover every check.
- Make intentionally permission-blocked probe reads lint-clean and guard two
  Android 30 APIs for the probe's Android 29 minimum. No permission changes.

## Dependency repair

The device has 327 enabled local bindings but only 256 Cyclone configuration
records. Cyclone's installed APK and current dev source enforce a 256-record
quota per connector installation. A separate Cyclone feature branch raises the
bounded quota to 8,192 and preserves the existing files, validation, namespace
isolation and error envelope. No existing records are rewritten by that change.
This is evidence of capacity pressure, not proof that every historical
BAD_REQUEST or Magisk timeout had that cause.

## Verification

```powershell
$env:JAVA_HOME='C:/Program Files/Eclipse Adoptium/jdk-17.0.20.101-hotspot'
$env:CLOAK_TEST_WSL='1'
& "$env:TEMP/cloak-build/gradle-8.11.1/bin/gradle.bat" -p android :app:testDebugUnitTest :module:testDebugUnitTest :playEvidence:testDebugUnitTest :probe:testStandardDebugUnitTest :probe:testDiagnosticsDebugUnitTest :app:assembleDebug :app:lintDebug :playEvidence:lintDebug :probe:lintStandardDebug :probe:lintDiagnosticsDebug --offline --no-daemon --console=plain
uv run --offline --with pytest --with jsonschema -- python -X utf8 -m pytest forge/tests forge/schema_tests -q
uv run --offline -- python -X utf8 -m unittest discover -s android/probe/tests -v
git diff --check
```

Final results and signed-candidate/device evidence are recorded separately when
complete. Existing Play readiness remains UNKNOWN where required evidence is
unavailable; this repair does not turn missing observations into passes.

No module hooks, publication scripts, binding schemas, permissions or profile
bodies changed. No release or module update is part of this candidate run.
