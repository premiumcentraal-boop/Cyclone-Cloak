# Reality Runtime Bridge — 2026-10-18

Prepared alpha.22 / code 46. Shared source table covers all 97 schema 0.4
leaf/array paths: 0 applied, 10 partial, 5 pending, 42 diagnostic-only,
37 host-only and 3 unsupported. No new hooks, permissions, networking,
connector, publication or stored-profile changes. Module metadata only.

Public pure APIs:
- Kotlin: `RealityRuntimeContract.generate(JSONObject): JSONObject`,
  `validate(JSONObject)`, `validateObservation(JSONObject)`,
  `readiness(JSONObject): Readiness`, `table(): JSONArray`.
- Python: `generate_contract(profile)`, `validate_contract(contract)`.

The generated format 1 contract includes schema version, presence and ordered
field definitions plus presence flags; no profile values. Readiness counts are
capabilities, not physical verification. The optional read-only probe extension
reuses hash-backed comparisons; nested item observations remain unmapped.
Legacy profiles are not migrated; unknown future sections are ignored.
See DEVICE-REALITY.md and REALITY-RUNTIME-BACKLOG.md for formats and limitations.

Changed areas: shared table/generator/schema and Python parity vector; pure
Kotlin/Python contracts; probe table, capture integration and report; coverage
model/card; focused Kotlin/Python/probe/schema/UI tests; release metadata/docs.

Local results: app 572, module 12, standard probe 101, diagnostics probe 101,
forge 577, schema 68, probe host 7. Total 1,438 tests; zero failures/skips.
Debug/release app, module ZIP and both probe APK builds passed. No compiler
warnings. Git may report Windows LF/CRLF conversion notices.

Exact commands (PowerShell, repository root):
```powershell
$env:JAVA_HOME='C:/Program Files/Eclipse Adoptium/jdk-17.0.20.101-hotspot'
$env:CLOAK_TEST_WSL='1'
& "$env:TEMP/cloak-build/gradle-8.11.1/bin/gradle.bat" -p android :app:testDebugUnitTest :module:testDebugUnitTest :probe:testStandardDebugUnitTest :probe:testDiagnosticsDebugUnitTest :app:assembleDebug :app:assembleRelease :module:packageModule :probe:assembleStandardDebug :probe:assembleDiagnosticsDebug --offline --no-daemon --console=plain
& "$env:TEMP/cloak-build/gradle-8.11.1/bin/gradle.bat" -p android :app:testDebugUnitTest --offline --no-daemon --console=plain
uv run --with pytest -- python -X utf8 -m pytest forge/tests -q
uv run --with pytest -- python -X utf8 -m pytest forge/tests/test_runtime_contract.py -q
uv run --with pytest --with jsonschema -- python -X utf8 -m pytest forge/schema_tests -q
uv run --with pytest --with jsonschema -- python -X utf8 -m pytest forge/schema_tests/test_runtime_contract_schema.py -q
uv run -- python -X utf8 -m unittest discover -s android/probe/tests -v
uv run -- python -X utf8 tools/generate_reality_rules.py --check
uv run -- python -X utf8 tools/generate_runtime_contract.py --check
uv run -- python -X utf8 tools/verify_release.py --tag v0.11.0-alpha.22 --artifacts
git diff --check
git diff --cached --check
```

Local release APK is unsigned; signed validation and release use the existing
CI signer. Physical verification of the new contract/probe extension remains
UNVERIFIED. Historical alpha.18 attachments are not evidence for this build.
No platform verdict is claimed. Unrelated untracked handoffs/evidence excluded.
