# User-17 standalone capture launch and visibility

Result: **INCOMPLETE — stopped; no release or parity claim.**

Commit: d4f0a5cb5f4d70bacaeb0b8ecdff184eff154150 plus the preserved dirty prerequisites and this run-only patch.
APK SHA-256: `3f3a611307024ba2fa3bbe301df4e2358a876182616f71a23f0326fc4abfbabf` (3644336 bytes).
Installed diagnostics APK SHA-256 matched for users 0 and 17.
Production companion version recorded: 0.11.0-alpha.27 / code 51.
Installed module identity was not established or changed.

| User | Capture | Outcome | Filesystem OBSERVED | BLOCKED | Schema |
| --- | --- | --- | --- | --- | --- |
| 0 | smoke only | Completed, 89 unique legacy rows, STARTED/COMPLETED | 2 | 2 | valid |
| 17 | 1 | Timeout, no target-user process/report/markers | not reached | not reached | not reached |
| 17 | 2 | Not run after failure | not reached | not reached | not reached |

## Exact launch/visibility findings

`am start --user 17` returned exit 0, stdout `Starting: Intent { cmp=dev.cyclone.cloak.probe.diag/dev.cyclone.cloak.probe.DiagnosticsProbeActivity (has extras) }`, and empty stderr. It did not establish a running user-17 capture.

- User 17 was RUNNING_UNLOCKED. User 0 remained active; no user switch was attempted.
- Per-user package path existed, installed=true, hidden=false, suspended=false, enabled=0 (default). The user-17 package remained stopped=true and notLaunched=true before, immediately after, and after the capture wait.
- `pidof` listed only PID 25683, UID 10323 (user 0). No UID 1710323 process appeared in the target snapshots. No marker/report for user 17 was obtained.
- `run-as ... --user 17 id` succeeded as UID 1710323; `pwd` correctly returned `/data/user/17/dev.cyclone.cloak.probe.diag`. Thus the old relative run-as directory was not demonstrated to be user-0-only.
- The explicit private user-17 report and both marker paths returned **No such file or directory**. The two external transports independently returned **Permission denied**. These are distinct outcomes, not conflated.
- Timeout reason: `PROBE_REPORT_TIMEOUT_USER_17_CAPTURE_1: CAPTURE_START_UNOBSERVABLE`. Marker flags are false/false. The external namespace remains unreadable from this non-root shell, even using target-user run-as; no permission workaround was attempted.
- Taken together, no user-17 capture execution is demonstrated, rather than a completed report merely being lost at the former relative path. The exact framework reason why the successful start command leaves user 17 stopped is still **unresolved**; background-user visibility is a possibility, not a proven cause.
- No wrong-user flag, missing installation, or disabled-package defect was found to justify a targeted fix/retry. No speculative second launch, user switch, package enable command, or headless component was added.

`user-17-capture-1-launch.json` retains verbatim am-start streams/exit code, pidof and UID process snapshots, per-user package flags/path, run-as identity/directory, and bounded logcat. Target-UID logcat was empty. The bounded system tail contains package-filtered shell read activity, not a decisive framework launch reason; the additional ActivityManager/ActivityTaskManager collection also yielded no matching lines. This logging limitation is explicit.
`user-17-capture-1-markers.json`, `user-17-capture-1-report-visibility.json`, and `timeout-diagnosis.json` preserve each transport's exact path, exit code, stderr, and classification.

## Implemented changes

- `android/probe/src/main/java/dev/cyclone/cloak/probe/ProbeEvidenceExport.java`: atomic redacted exports under the app's **getExternalFilesDir(null)/standalone-evidence**, naturally scoped to its running Android user. No raw readings/profile bodies are exported there; the prior private report remains for compatibility.
- `android/probe/src/main/java/dev/cyclone/cloak/probe/ProbeActivity.java`: diagnostics-only report/marker exports inside existing generation guards. Standard-probe report behavior unchanged.
- `android/probe/src/main/java/dev/cyclone/cloak/probe/CaptureMarkers.java`: returns the same fixed metadata object for export.
- `android/probe/src/test/java/dev/cyclone/cloak/probe/ProbeEvidenceExportTest.java`: per-user path, absent storage, redaction, and filename tests.
- `android/probe/tests/test_user_visibility.py`: transport paths, absent/unreadable diagnosis, verbatim launch evidence, private fallback tests.
- `tools/run_standalone_probe_validation.py`: one user-0 smoke plus two planned user-17 cold captures; full launch evidence; explicit user-scoped external/private readers; access-aware diagnosis.

A per-user external export was implemented and readable on user 0, but universal non-root external readability could **not** be established on this device. Reporting the denial is the safe outcome within the no-permission/no-user-switch boundary.

## Local gate

144 standard + 144 diagnostics unit tests passed; 23 host tests passed. Diagnostics APK build succeeded, no compiler warnings found. Both diff checks passed. Git emits existing LF/CRLF normalization notices.

```powershell
$env:JAVA_HOME='C:/Program Files/Eclipse Adoptium/jdk-17.0.20.101-hotspot'
& "$env:TEMP/cloak-build/gradle-8.11.1/bin/gradle.bat" -p android :probe:testStandardDebugUnitTest :probe:testDiagnosticsDebugUnitTest :probe:assembleDiagnosticsDebug --offline --no-daemon --console=plain
uv run --offline -- python -X utf8 -m unittest discover -s android/probe/tests -v
uv run --offline --with jsonschema -- python -X utf8 tools/run_standalone_probe_validation.py
git diff --check
git diff --cached --check
```

The device runner exited 1 as required for an incomplete run. Full device commands are in `commands.json`; the extra bounded logcat read and post-uninstall external path checks are represented by their exact evidence files.

## Counts, determinism and cleanup

User-0 filesystem results remain 2 OBSERVED / 2 BLOCKED / 0 MISSING. build.prop and proc/version are BLOCKED/PERMISSION_DENIED and NOT_COMPARABLE. User-17 repeats and digest comparison were not reached; the single smoke is not a repeated determinism proof. Nothing was promoted to VERIFIED.

Legacy standalone fixture comparisons: 25 PASS / 48 FAIL / 12 BLOCKED / 4 OBSERVED. They remain unbound comparisons, not a parity verdict; other section counts remain in `report-section-counts.json`.

Diagnostics uninstall returned Success; both users' package paths were absent afterward. User-0 external evidence directory was independently absent; user-17 external cleanup visibility remains denied, so its byte-level external cleanup cannot be independently asserted. See `cleanup.json` and `external-cleanup-visibility.json`.

Production APK hashes, versions and stable permission snapshot matched before/after. Production private stores, module files and AppOps equality were not independently audited. No production app launch/install, module action, reboot, binding mutation, permission/AppOps change command, account action or user switch occurred.

## Provenance and policy

Nothing staged, committed, tagged or released. All earlier tracked changes outside ProbeActivity.java and untracked probe/tool prerequisites outside the explicitly modified files matched their run-start baseline. Prior evidence was preserved. `user17-launch-only.patch` isolates this run; `source.patch`, `source/`, `source-manifest.json`, and the previous runner copy preserve dirty-prerequisite provenance. No platform, coverage or broader compatibility claim.
