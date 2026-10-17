# Run 2 â€” diagnostics expectation policies (2026-10-17)

Implemented on main after v0.11.0-alpha.16. No commit/release/device run requested.
No production runtime, connector, binding storage, module behavior, profile schema,
permissions or version metadata changed. Existing untracked handoffs are untouched.

See [the policy table and examples](DIAGNOSTIC-EXPECTATION-POLICIES.md) for all 12
rows and precise meanings. Six external-state observations remain observations,
five enum/type checks can pass locally, and one bridge-visibility stability check
needs a connected set of distinct comparable captures before the host comparison
can pass. No platform or runtime-hook verdict follows from these local checks.

## Changed files

- android/probe/src/main/assets/expectations-a.json and expectations-b.json:
  versioned diagnostics fixture sidecars; profile-a/b bodies and hashes untouched.
- android/probe/src/main/java/dev/cyclone/cloak/probe/DiagnosticExpectation.java:
  strict validation/evaluation of EXACT, STABLE_ACROSS_SWITCH, PRESENT, TYPED,
  RANGE, ENUM and EXTERNAL_STATE; fixed reasons and no raw values in rule metadata.
- android/probe/src/main/java/dev/cyclone/cloak/probe/ProbeActivity.java:
  select sidecar after fixture validation; attach bounded policy metadata;
  preserve permission gates; missing external adapter/connection values are explicit.
- android/probe/src/test/java/dev/cyclone/cloak/probe/DiagnosticExpectationTest.java,
  DiagnosticFixtureTest.java and src/test/resources/expectation-cases.json:
  62 shared Java/Python evaluation cases plus fixture validation tests.
- tools/diagnostic_expectations.py: matching host validator/evaluator, safe metadata
  checks, paired stability checks and complete comparison-edge coverage.
- tools/profile_check.py: explicit external/pending counts, policy-aware diff,
  failure precedence and idempotent hash-preserving redaction.
- tools/run_runtime_probe.py: per-phase request ID/artifact context, expected-value
  redaction as well as actual-value redaction.
- tools/render_runtime_matrix.py: POLICY_ONLY proof label prevents shape checks
  or observed external values from becoming VERIFIED.
- forge/tests/test_diagnostic_expectations.py: cross-language cases, every assigned
  row, transitions/missing baselines/artifact/rule mismatch, malformed metadata,
  redaction, original fixture hashes and unchanged historical classification.
- docs/DIAGNOSTIC-EXPECTATION-POLICIES.md and this handoff.

## Exact verification commands

PowerShell environment:

```powershell
$env:JAVA_HOME='C:/Program Files/Eclipse Adoptium/jdk-17.0.20.101-hotspot'
$env:CLOAK_TEST_WSL='1'
& "$env:TEMP/cloak-build/gradle-8.11.1/bin/gradle.bat" -p android :probe:testStandardDebugUnitTest :probe:testDiagnosticsDebugUnitTest :probe:assembleStandardDebug :probe:assembleDiagnosticsDebug :app:testDebugUnitTest :module:testDebugUnitTest :app:assembleDebug --offline --no-daemon --console=plain
uv run --with pytest -- python -X utf8 -m pytest forge/tests -q
uv run --with pytest --with jsonschema -- python -X utf8 -m pytest forge/schema_tests -q
uv run -- python -X utf8 -m unittest discover -s android/probe/tests -v
git diff --check
```

Final gate PASS: 411 forge, 33 schema, 7 probe host, 79 standard probe,
79 diagnostics probe, 522 app and 12 module tests; 1,143 test executions,
zero failures/errors/skips in reported results. Both probe APKs and app debug
build pass (Gradle BUILD SUCCESSFUL, 2m 1s). No compiler warnings in the final
Gradle log. The unchanged module test task was up-to-date, with 12 passing XML
results; the app and both changed probe suites ran. Git diff --check passes
(Git emits existing LF/CRLF notices).
The first compiler run caught use of JSONObject.keySet (not in Android API);
replaced with supported keys() iteration. The first focused Python run caught a
legacy diff-reason regression; restored its existing reason while keeping FAIL
precedence. No test was removed or bypassed.

## Remaining limits

Updated policies have not been physically exercised. Do not reclassify stored
alpha.16 evidence using hashes as though raw values/types were still available.
The retained evidence test still reports 77 PASS / 12 uncovered for that old run.
Wi-Fi, SIM, Bluetooth and per-user settings require real external state;
inventories do not assert exact devices/packages/libraries. Manual permission
UX and Android-user transitions need their own future device evidence.
Installed artifact matching is not proof of in-process code identity.
