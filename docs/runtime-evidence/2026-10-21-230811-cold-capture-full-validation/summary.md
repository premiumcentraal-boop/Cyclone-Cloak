# Cold-capture standalone validation

Result: INCOMPLETE
Failure: PROBE_REPORT_TIMEOUT_USER_17_CAPTURE_1: CAPTURE_NEVER_STARTED
APK SHA-256: fff18f960787295afb77352be819bd2dd23548a441213edd2abe435662a45679
Production APK/version/permission snapshot unchanged: True
Diagnostics uninstall confirmed: True

| User | Capture | OBSERVED | BLOCKED | MISSING |
| --- | --- | --- | --- | --- |
| 0 | 1 | 2 | 2 | 0 |
| 0 | 2 | 2 | 2 | 0 |

Determinism:
- user 0 diagnostics.filesystem.build_prop: NOT_COMPARABLE
- user 0 diagnostics.filesystem.proc_version: NOT_COMPARABLE
- user 17 diagnostics.filesystem.build_prop: NOT_COMPARABLE
- user 17 diagnostics.filesystem.proc_version: NOT_COMPARABLE

Limits: probe-only caller-visible observations; no parity verdict or VERIFIED promotion.
No module install, root shell, reboot, binding overlay, user switch, permission grant/reset or production app launch was performed.
Private production stores/module files were not independently audited. APK/version/permission equality is not proof of private-store byte equality.
Installed module provenance was not established; the handoff alpha.28 description is not independently confirmed.
The diagnostics-only installation was globally uninstalled at cleanup; no private probe files are retained on-device.
Legacy probe comparisons used the packaged fixture without creating a runtime binding and are not interpreted as parity validation.
Cold-start policy: diagnostics-only force-stop before every capture; request/process-bound start/end markers preserved.
Gate: any failed capture stops the run. No additional code fixes follow the device failure.
Reports are redacted exports retaining filesystem digests/counts and existing row statuses/digests only. No raw file content retained.

## Focused handoff

**Final result: INCOMPLETE; stop gate observed.**

The original user-0 consecutive-capture blocker did not recur with the cold-start policy: captures 1 and 2 both completed with different PIDs, matching request/process-bound STARTED and COMPLETED markers, 89 unique legacy row IDs, all expected report sections, and filesystem schema validation. This establishes the cold-start workaround on user 0; it does not retrospectively prove the precise Activity launch behavior of the earlier timeout.

User 17 capture 1 timed out: `PROBE_REPORT_TIMEOUT_USER_17_CAPTURE_1: CAPTURE_NEVER_STARTED`. No matching report or start/end marker was observable. `timeout-diagnosis.json` and `user-17-capture-1-markers.json` preserve that evidence. Missing markers are not proof of a native crash, specific failed reader, or successful marker-file access; the underlying launch/read-access cause remains unresolved. User 17 capture 2 was not attempted. Both users had reported RUNNING_UNLOCKED at preflight. No user switch or further device retry was performed.

| User | Capture | Process | Start/end | Filesystem OBSERVED | BLOCKED | Schema |
| --- | --- | --- | --- | --- | --- | --- |
| 0 | 1 | 13083 | STARTED / COMPLETED | 2 | 2 | valid |
| 0 | 2 | 13276 | STARTED / COMPLETED | 2 | 2 | valid |
| 17 | 1 | not established | neither visible | not reached | not reached | not reached |
| 17 | 2 | not run | not run | not run | not run | not run |

Digest checks: build.prop and proc/version are both BLOCKED/PERMISSION_DENIED and explicitly NOT_COMPARABLE, not deterministic passes. User-0 cpuinfo comparison and intentionally skipped volatile maps digests are in `additional-digest-observations.json`. No row was promoted to VERIFIED.

The standalone probe used fixture A without a runtime binding. Legacy and linked/Reality comparison failures remain recorded in `report-section-counts.json`; they are not reclassified as passes or used as a parity verdict. This run tests capture/report reliability and caller-visible filesystem observations only.

## Changed files in this run

- `android/probe/src/main/java/dev/cyclone/cloak/probe/ProbeActivity.java`: generation-guarded private capture markers before reads and after atomic report publication.
- `android/probe/src/main/java/dev/cyclone/cloak/probe/CaptureMarkers.java`: atomic metadata-only start/end files.
- `android/probe/src/test/java/dev/cyclone/cloak/probe/CaptureMarkersTest.java`: 3 focused marker tests.
- `android/probe/tests/test_standalone_capture.py`: 8 host transport/timeout-marker tests.
- `tools/run_standalone_probe_validation.py`: standalone validation flow consolidated from the prior temporary runner; per-user diagnostics-only force-stop before each launch, request/process-bound marker diagnosis and failure exit status.

All preexisting dirty tracked changes outside ProbeActivity.java, and preexisting untracked probe/tool files, matched the run-start baseline. Nothing staged or committed. `cold-capture-only.patch` separates this run from the dirty-prerequisite `source.patch`; `source/` and `source-manifest.json` preserve exact probe/build/runner prerequisites. Older evidence was not changed.

## Local verification

140 standard + 140 diagnostics JVM tests passed; 16 Python host tests passed. Diagnostics APK build succeeded. No compiler warnings found. Git emits LF/CRLF normalization notices for preexisting dirty files; both diff checks passed.

```powershell
$env:JAVA_HOME='C:/Program Files/Eclipse Adoptium/jdk-17.0.20.101-hotspot'
& "$env:TEMP/cloak-build/gradle-8.11.1/bin/gradle.bat" -p android :probe:testStandardDebugUnitTest :probe:testDiagnosticsDebugUnitTest :probe:assembleDiagnosticsDebug --offline --no-daemon --console=plain
uv run --offline -- python -X utf8 -m unittest discover -s android/probe/tests -v
uv run --offline --with jsonschema -- python -X utf8 tools/run_standalone_probe_validation.py
git diff --check
git diff --cached --check
```

The first host-test invocation failed to import jsonschema; moving its import into the device runner entry point keeps pure host tests dependency-free. The final host suite passed. Device runner exited 1 as expected for this incomplete run.

## Cleanup and limits

Installed APK hashes matched the local diagnostics APK for both users. Diagnostics uninstall returned Success; both package paths were absent afterward. Production APK/version/stable-permission snapshots matched before/after. No production app/module was installed or launched, no root shell, reboot, binding edit, permission/AppOps command, or user switch was performed. Production private stores, module identity and AppOps equality were not independently audited. No platform verdict, parity verdict, release, tag, version bump or coverage claim.
