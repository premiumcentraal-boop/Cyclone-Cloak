# SIM-state fix full validation

Result: INCOMPLETE
Failure: PROBE_REPORT_TIMEOUT_USER_0_CAPTURE_2
APK SHA-256: 0dba2f261dac5774c59945c1deba9c43b5084b63adb0078ff449791f190d2034
Production APK/version/permission snapshot unchanged: True
Diagnostics uninstall confirmed: True

| User | Capture | OBSERVED | BLOCKED | MISSING |
| --- | --- | --- | --- | --- |
| 0 | 1 | 2 | 2 | 0 |

Determinism:
- user 0 diagnostics.filesystem.build_prop: NOT_COMPARABLE
- user 0 diagnostics.filesystem.proc_version: NOT_COMPARABLE
- user 17 diagnostics.filesystem.build_prop: NOT_COMPARABLE
- user 17 diagnostics.filesystem.proc_version: NOT_COMPARABLE

Limits: probe-only caller-visible observations; no parity verdict or VERIFIED promotion.
No module install, root shell, reboot, binding overlay, user switch, permission grant/reset or production app launch was performed.
Private production stores/module files were not independently audited. APK/version/permission equality is not proof of private-store byte equality.
Installed module provenance was not established; the handoff alpha.28 description is not independently confirmed.
The diagnostics app was absent before this run, installed only for this validation, and globally uninstalled afterward.
Legacy probe comparisons used the packaged fixture without creating a runtime binding and are not interpreted as parity validation.
Gate: any failed capture stops the run. No additional code fixes follow the device failure.
Reports are redacted exports retaining filesystem digests/counts and existing row statuses/digests only. No raw file content retained.

## SIM fix and local gate

Changed source: `android/probe/src/main/java/dev/cyclone/cloak/probe/RuntimeEnvelopeReport.java`.
Added tests: `android/probe/src/test/java/dev/cyclone/cloak/probe/SimStateExpectationTest.java`.
Root cause: the nested numeric/null conditional unboxed a null optional expectation. Explicit boxed returns preserve a null expectation without throwing. The existing known mappings are unchanged.
Red regression: 3 tests, 2 NPE failures. Green: 137 standard + 137 diagnostics tests; 8 Python host tests. Diagnostics APK build passed. No compiler warnings found; git reports preexisting LF/CRLF normalization notices. Both diff checks passed.

Exact commands (PowerShell, repository root):
```powershell
$env:JAVA_HOME='C:/Program Files/Eclipse Adoptium/jdk-17.0.20.101-hotspot'
& "$env:TEMP/cloak-build/gradle-8.11.1/bin/gradle.bat" -p android :probe:testDiagnosticsDebugUnitTest --tests 'dev.cyclone.cloak.probe.SimStateExpectationTest' --offline --no-daemon --console=plain
& "$env:TEMP/cloak-build/gradle-8.11.1/bin/gradle.bat" -p android :probe:testStandardDebugUnitTest :probe:testDiagnosticsDebugUnitTest :probe:assembleDiagnosticsDebug --offline --no-daemon --console=plain
uv run --offline -- python -X utf8 -m unittest discover -s android/probe/tests -v
uv run --offline --with jsonschema -- python -X utf8 "$env:TEMP/cloak_probe_sim_fix_validation.py"
git diff --check
git diff --cached --check
```

## Device stop gate

Both installed user copies matched the recorded local APK SHA-256 before capture.
User 0 capture 1 completed; its filesystem section passed schema validation. Both SIM-state rows returned OBSERVED with no expectation. Legacy fixture comparisons were 25 PASS / 48 FAIL / 12 BLOCKED / 4 OBSERVED; this standalone unbound run does not validate fixture parity.
User 0 capture 2 received no matching completed or enriched failure report within the 60-second polling window: `PROBE_REPORT_TIMEOUT_USER_0_CAPTURE_2`. The completed capture-1 report is retained, never relabeled as capture 2. No crash type or new failing row can be established from a missing report. User 17 captures and repeated digest comparisons were not reached. No additional fix or retry followed.
Filesystem build.prop and proc/version remain BLOCKED/PERMISSION_DENIED as expected; cpuinfo and maps are observations. Determinism for denied rows is not measurable.
The diagnostics uninstall succeeded and package paths were absent for both users. Production APK/version/stable-permission snapshots matched before/after; private stores and module files were not independently audited. No AppOps mutation was requested; AppOps equality was not independently audited.

All earlier tracked changes outside RuntimeEnvelopeReport.java are unchanged against the run-start patch. Nothing was staged or committed. `sim-fix-only.patch` contains only this run's source/test changes; `source/` and `source.patch` retain the preexisting dirty probe prerequisites for artifact provenance. No release, tag, version change, module action or platform verdict.
