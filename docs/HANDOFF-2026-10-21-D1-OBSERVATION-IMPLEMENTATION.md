# D1 diagnostics observations — 2026-10-21

## Result and scope

Implemented the read-only diagnostics/contract portion of the supplied follow-up.
Four filesystem observation definitions are generated together with the existing
consumers. The 97 reviewed Reality paths and profile schema remain unchanged.
Definitions live in a separate `observation_surfaces` table; measurements live in
an optional diagnostics-only `filesystem_observations` report section, format 1.
Successful reads remain OBSERVED, never PASS or VERIFIED.

The requested module renderer changes were not made: those synthetic renderers
remain coupled to the concealment implementation identified in D1 review.
No native source, version metadata, permissions, bindings, publication or connector
logic changed. No device actions, commit, tag or release performed.

## Changed files

- `catalog/reality-runtime-fields.json`: four separately namespaced observation definitions.
- `tools/generate_runtime_contract.py`: validates the allowlist/unverified metadata;
  generates the observation schema as well as existing consumers.
- `android/probe/src/main/assets/reality-runtime-fields.json`: generated definitions.
- `android/app/src/main/java/dev/cyclone/cloak/forge/RealityRuntimeTable.kt`: generated metadata only.
- `schema/filesystem-observations.schema.json`: strict optional report extension.
- `android/probe/src/main/java/dev/cyclone/cloak/probe/FilesystemObservation.java`:
  allowlisted, streaming, bounded reads; digest/counts only; fixed failure reasons.
- `android/probe/src/main/java/dev/cyclone/cloak/probe/ProbeActivity.java`:
  collect only in the diagnostics variant, within the existing capture session.
- `android/probe/src/test/java/dev/cyclone/cloak/probe/FilesystemObservationTest.java`:
  six tests covering hashing/counts, empty/terminated lines, denial/missing,
  oversize rejection, stream closure and unknown/duplicate surfaces.
- `android/probe/tests/test_runner.py`: generated metadata and non-verification checks.
- `forge/tests/test_runtime_contract.py`: namespace separation and strict observation schema tests.
- `docs/FILESYSTEM-OBSERVATIONS.md` and this handoff.

Existing profile contract schema/vector regenerate without changes. The Kotlin
profile-contract reader continues using only the 97 `fields` entries.

## Exact commands and results

From `C:/Users/Agent/Cyclone/cloak-repo`:

```powershell
uv run --offline -- python -X utf8 tools/generate_runtime_contract.py
uv run --offline -- python -X utf8 tools/generate_runtime_contract.py --check
uv run --offline --with pytest --with jsonschema -- python -X utf8 -m pytest forge/tests/test_runtime_contract.py -q
uv run --offline --with pytest --with jsonschema --with cryptography -- python -X utf8 -m pytest forge/tests forge/schema_tests -q
uv run --offline -- python -X utf8 -m unittest discover -s android/probe/tests -v

$env:JAVA_HOME='C:/Program Files/Eclipse Adoptium/jdk-17.0.20.101-hotspot'
$env:ANDROID_HOME='C:/Users/Agent/android-sdk'
$env:CLOAK_TEST_WSL='1'
& "$env:TEMP/cloak-build/gradle-8.11.1/bin/gradle.bat" -p android :probe:testStandardDebugUnitTest :probe:testDiagnosticsDebugUnitTest :probe:assembleDiagnosticsDebug :app:testDebugUnitTest --tests 'dev.cyclone.cloak.forge.RealityRuntimeContractTest' --offline --no-daemon --console=plain
& "$env:TEMP/cloak-build/gradle-8.11.1/bin/gradle.bat" -p android :module:testDebugUnitTest --offline --no-daemon --console=plain
git diff --check
git diff --cached --check
```

- Focused Python contract/schema: **27 passed**.
- Full forge/schema: **1,292 passed**.
- Host probe tests: **8 passed**.
- Standard probe: **120 passed**; diagnostics probe: **120 passed**.
- Kotlin contract tests: **6 passed** (not a full app-suite invocation).
- Module test task: UP-TO-DATE; existing XML results: **80 passed**.
- Diagnostics debug build successful; no compiler warnings in the build log.
- Generator consistency and whitespace checks passed. Git emitted LF/CRLF
  conversion advisories for two test files, not compiler warnings.

The first broad Python run failed collection because the new schema lacked `$id`.
The generator was corrected and the complete suite reran successfully.

Build output: `android/probe/build/outputs/apk/diagnostics/debug/probe-diagnostics-debug.apk`.
Build logs: `%TEMP%/cloak-d1-observations-gradle.log`,
`%TEMP%/cloak-d1-observations-module.log`, `%TEMP%/cloak-d1-observations-python.log`.

## Limitations

Physical observations are pending. A digest reports the caller-visible file,
not independent host truth or runtime parity. Maps are volatile. Missing/denied
reads stay explicit limitations; a file exceeding 1 MiB has no partial digest.
No raw file content, exception message or parity expectation is exported.
Unrelated untracked handoffs and evidence were preserved. No platform verdict
or broader compatibility claim is made.
