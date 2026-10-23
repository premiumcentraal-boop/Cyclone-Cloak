# User-17 lifecycle launch path — standalone validation

**Result: COMPLETE.** The one authorized implementation-and-device run completed all three captures without retry or timeout.

Commit: `d4f0a5cb5f4d70bacaeb0b8ecdff184eff154150`, main plus preserved dirty prerequisites.
APK SHA-256: `3f3a611307024ba2fa3bbe301df4e2358a876182616f71a23f0326fc4abfbabf` (3644336 bytes).
The rebuild reused the same probe binary as the preceding run because this fix changes only runner/tests. Installed APK hashes matched for users 0 and 17.
Production companion snapshot: 0.11.0-alpha.27 / code 51. Installed module identity was not established or changed.

## Captures

| User | Capture | UID | PID | Markers | Unique legacy rows | Filesystem OBSERVED | BLOCKED | Schema |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 0 | 1 | 10324 | 7142 | STARTED / COMPLETED | 89 | 2 | 2 | valid |
| 17 | 1 | 1710324 | 7552 | STARTED / COMPLETED | 89 | 2 | 2 | valid |
| 17 | 2 | 1710324 | 9841 | STARTED / COMPLETED | 89 | 2 | 2 | valid |

All three request IDs, reported Android users, marker UID/PID, fixture labels and expected-profile hashes matched. The user-17 cold captures used different PIDs, demonstrating separate processes. `schema-and-binding-validation.json` preserves the explicit checks; full reports remain redacted.

## Exact lifecycle mechanism ported

The runner checks RUNNING_UNLOCKED, issues `am switch-user 17` when not already active, and polls `am get-current-user` every 0.3 seconds with a 60-second deadline, matching the lifecycle runner's activation mechanism. Capture 2 checks the same activation condition and does not issue a redundant switch.

Each capture stages the synthetic `assets/profile-a.json` from the built APK into the diagnostics package's private `files/expected-profile.json` and verifies its SHA-256. This is required by the lifecycle command's `external_profile=true` extra; it creates no binding, publication entry, or production file.

```text
am force-stop --user 17 dev.cyclone.cloak.probe.diag
am start --user 17 -n dev.cyclone.cloak.probe.diag/dev.cyclone.cloak.probe.DiagnosticsProbeActivity --activity-clear-top --activity-single-top --ez external_profile true --ez no_permission_prompt true --ei expected_user 17 --es fixture a --es request_id <request>
run-as dev.cyclone.cloak.probe.diag --user 17 cat files/report.json
run-as dev.cyclone.cloak.probe.diag --user 17 cat files/capture-start.json
run-as dev.cyclone.cloak.probe.diag --user 17 cat files/capture-end.json
```

Verbatim launch stdout/stderr/exit status, process and package state, bounded target logs, marker observations, and additional visibility transports remain recorded per capture. The lifecycle run-as reader was READABLE for all three captures. User-17 external storage readers still return explicit permission denials; private target-user run-as succeeds, so external namespace access is not required and no permissions were changed.

The combined foreground-user/lifecycle launch path resolves the measured user-17 blocker. This run does not independently isolate which individual flag contributed, or claim that background-user launches work.

## Round trip and cleanup

`user-switches.json` records exactly two actual switch commands: **0 -> 17 -> 0**. Both completed; the second user-17 activation was a verified no-op. The finally path returned to active user 0 before uninstalling the diagnostics probe. `cleanup.json` records `returned_to_user_0=true`, uninstall Success, and absent package paths for both users. No other switch mechanism, package enabling, user creation/removal, reboot or forced unlock was used.

The synthetic expected-profile/report/marker files belonged only to the uninstalled diagnostics app. Package removal is verified; user-17 external-file byte cleanup was not independently inspected through its denied external namespace.

## Determinism and status policy

- User-17 `/proc/cpuinfo` digests are identical across both cold captures. They remain OBSERVED, not VERIFIED.
- `/system/build.prop` and `/proc/version` remain BLOCKED/PERMISSION_DENIED in all captures, with null digests and NOT_COMPARABLE. No escalation or workaround was attempted.
- `/proc/self/maps` remains OBSERVED; its different process-specific hashes are SKIPPED_VOLATILE.
- User 0 was deliberately captured once, so no new user-0 repeat claim is made.
- Legacy/linked/Reality comparison results are retained unchanged in `report-section-counts.json`. This is a standalone unbound fixture run, not profile-backed runtime parity validation. No failures were normalized into passes.

## Changed files in this run

- `tools/run_standalone_probe_validation.py`: lifecycle user activation and settled round trip, exact launch command/extras, synthetic fixture staging, primary lifecycle run-as reader, cleanup return to user 0, binding/completeness checks, and filesystem determinism comparison.
- `android/probe/tests/test_user_switch_capture.py`: 8 new host tests for exact command, switch/wait/launch/read ordering, same-user activation, locked-user/timeout behavior and cleanup return.
- `android/probe/tests/test_user_visibility.py`: existing transport/launch tests updated for the primary lifecycle reader and staged-fixture launch.

No probe Java/native/manifest code changed in this run. All prior tracked changes and preexisting untracked probe/tool files outside these two modified files matched the run-start baseline. Nothing staged or committed. `user17-switch-only.patch` isolates this run; prior dirty-prerequisite source copies and `source.patch` remain in the archive. Older evidence was not overwritten.

## Local verification

**144 standard + 144 diagnostics JVM tests; 31 host tests passed.** Diagnostics APK rebuild succeeded. No compiler warnings found. Both diff checks passed; git printed existing LF/CRLF normalization notices.

```powershell
$env:JAVA_HOME='C:/Program Files/Eclipse Adoptium/jdk-17.0.20.101-hotspot'
& "$env:TEMP/cloak-build/gradle-8.11.1/bin/gradle.bat" -p android :probe:testStandardDebugUnitTest :probe:testDiagnosticsDebugUnitTest :probe:assembleDiagnosticsDebug --offline --no-daemon --console=plain
uv run --offline -- python -X utf8 -m unittest discover -s android/probe/tests -v
uv run --offline --with jsonschema -- python -X utf8 tools/run_standalone_probe_validation.py
git diff --check
git diff --cached --check
```

The device runner exited 0. Its exact ADB commands are in `commands.json`; source/test/build logs, source hashes and the run-only patch are archived.

## Preservation and limits

Production APK/version/stable-permission snapshots matched before/after. No production APK installation or launch command, module action, binding/publication mutation, permission grant/reset, or AppOps mode-change command occurred. Normal OS activity on the authorized user switch was not represented as a byte-level production-store audit. Private production stores, module files and AppOps equality were not independently audited.

No release, tag, version bump, platform verdict, parity verdict, VERIFIED promotion or coverage claim. The standalone capture blocker is closed for the measured diagnostics artifact and user-switch sequence only.
