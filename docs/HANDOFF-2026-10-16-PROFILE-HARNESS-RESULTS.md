# Profile harness results — 2026-10-16

Implemented a phase-based local runner, expanded structured probe reports,
three-snapshot comparison, bounded scoped crash collection, automatic compact
summary and redacted ZIP. Existing production records and runtime code are
unchanged. The actual starting release was alpha.14/code 38, not alpha.13.

## Changed files

- tools/run-profile-check.ps1
- tools/profile_check.py
- android/probe/src/main/java/dev/cyclone/cloak/probe/ProbeActivity.java
- android/probe/src/diagnostics/java/dev/cyclone/cloak/probe/DiagnosticsProbeActivity.java
- forge/tests/test_profile_check.py
- docs/PROFILE-TEST-HARNESS.md
- docs/RELEASE_PROFILE-HARNESS_DRAFT.md
- this handoff

Existing untracked handoffs and device evidence were preserved. New local run
directories ignore their evidence contents in Git; bundle paths below exist on
this workstation and are not automatically committed.

## Device results

Pixel 8 / shiba, Android 16, adb serial 3B171FDJH0061G. No reboot, permission
grant, data clearing, module installation, or production app force-stop.

| Run | Result | Duration | Before | After switch | After probe cold restart |
| --- | --- | --- | --- | --- | --- |
| harness-small-r2, one probe app, user 0 → 0 | DEGRADED | 10.3 s | 76 PASS / 12 OBSERVED / 1 BLOCKED | same | same |
| harness-full-transition-r2, 327 published targets audited, user 17 → 0 | DEGRADED | 40.2 s | 73 PASS / 11 OBSERVED / 5 BLOCKED | 76 PASS / 12 OBSERVED / 1 BLOCKED | same as after switch |

Each snapshot contains 89 rows. Small-run summary: zero critical failures,
13 warning IDs, one blocked ID. Full-run summary: zero critical failures,
16 warning IDs, five blocked IDs. These are distinct IDs, not summed snapshots.

The full transition observed 70 expected field changes, four unmapped changes,
ten stable rows, and five blocked/missing comparisons. The four unmapped changes
remain warnings. After cold restart, 88 comparable rows stayed stable and one
Advertising ID comparison stayed blocked. Small-run repeat reused PID 24169;
explicit cold restart changed it to 24351. Full run: user 17 PID 25840,
user 0 PID 26364, cold-restarted user 0 PID 28275.

User 17 blocked Wi-Fi service MAC/BSSID/SSID (fine location denied), Bluetooth
name (connect denied), and Advertising ID (optional SDK/API absent). In user 0,
only Advertising ID remained blocked. Unmapped fields remain OBSERVED; no
external verdict or fabricated PASS is emitted.

Production index before and after: 327 entries, user 17, one device profile,
publisher 0. Canonical index digest after both successful runs:
`409d6d5386be07a6b14b888872cb8467feeb1bf5c21647e716c90bd8a57ba99c`.
Temporary probe entries were removed and original foreground user 0 restored.

Evidence bundles:

- docs/runtime-evidence/2026-10-16-harness-small-r2/evidence-bundle.zip (19,427 bytes)
- docs/runtime-evidence/2026-10-16-harness-full-transition-r2/evidence-bundle.zip (23,906 bytes)

Initial failed runs were retained: `harness-small` exposed warm activity request
delivery timing; CLEAR_TOP/SINGLE_TOP and explicit onNewIntent collection fixed
it. `harness-full-transition` exposed the Windows process command-line limit;
bounded 32-path audit batches fixed it. Failure evidence was not overwritten.

## Exact commands and checks

```powershell
$env:JAVA_HOME='C:/Program Files/Eclipse Adoptium/jdk-17.0.20.101-hotspot'
$env:CLOAK_TEST_WSL='1'
& "$env:TEMP/cloak-build/gradle-8.11.1/bin/gradle.bat" -p android :probe:assembleStandardDebug :probe:assembleDiagnosticsDebug --offline --no-daemon
& "$env:TEMP/cloak-build/gradle-8.11.1/bin/gradle.bat" -p android :app:testDebugUnitTest :module:testDebugUnitTest :app:assembleDebug :module:packageModule :probe:assembleStandardDebug :probe:assembleDiagnosticsDebug --offline --no-daemon
uv run --with pytest -- python -m pytest forge/tests/test_profile_check.py -q
uv run --with pytest -- python -m pytest forge/tests -q
uv run --with pytest --with jsonschema -- python -m pytest forge/schema_tests -q
uv run -- python -m unittest discover -s android/probe/tests -v
& "$env:TEMP/cloak-build/gradle-8.11.1/bin/gradle.bat" -p android :app:assembleRelease :module:packageModule --offline --no-daemon
uv run -- python tools/verify_release.py --tag v0.11.0-alpha.14
uv run -- python tools/verify_release.py --tag v0.11.0-alpha.14 --artifacts
git diff --check

powershell -NoProfile -ExecutionPolicy Bypass -File tools/run-profile-check.ps1 -Scenario harness-small -Phase environment -Serial 3B171FDJH0061G -User 0 -TargetUser 0
powershell -NoProfile -ExecutionPolicy Bypass -File tools/run-profile-check.ps1 -Scenario harness-small -Phase all -Serial 3B171FDJH0061G -User 0 -TargetUser 0 -TimeoutSeconds 45
powershell -NoProfile -ExecutionPolicy Bypass -File tools/run-profile-check.ps1 -Scenario harness-small-r2 -Phase all -Serial 3B171FDJH0061G -User 0 -TargetUser 0 -TimeoutSeconds 45
powershell -NoProfile -ExecutionPolicy Bypass -File tools/run-profile-check.ps1 -Scenario harness-full-transition -Phase all -Serial 3B171FDJH0061G -User 17 -TargetUser 0 -FullProfile -TimeoutSeconds 90
powershell -NoProfile -ExecutionPolicy Bypass -File tools/run-profile-check.ps1 -Scenario harness-full-transition-r2 -Phase all -Serial 3B171FDJH0061G -User 17 -TargetUser 0 -FullProfile -TimeoutSeconds 90
powershell -NoProfile -ExecutionPolicy Bypass -File tools/run-profile-check.ps1 -Scenario harness-small -Phase summary -Serial 3B171FDJH0061G -User 0 -TargetUser 0
```

Final tests: **522 app, 12 module, 271 forge (42 new harness cases), 33 schema,
six existing probe-runner tests**, zero failed/skipped Android tests.
The final duration-preservation case was included in the full forge rerun.
Debug app, module ZIP and both probe variants built successfully. Captured build
logs contain no compiler warnings. Metadata check and whitespace check passed.
Release assembly and lint passed (2m 4s). The release artifact gate passed:
app-release-unsigned.apk embeds the exact generated alpha.14 module ZIP and
versions/code agree. This is an unsigned local packaging result, not a signed
release approval. Running packaging again regenerates the module ZIP, so a debug
APK from an earlier invocation need not match that later archive byte for byte;
the verified release pair was built in the same invocation.

Probe APK paths:

- android/probe/build/outputs/apk/debug/probe-debug.apk (historical default)
- android/probe/build/outputs/apk/diagnostics/debug/probe-diagnostics-debug.apk

## Boundaries / remaining verification

The production app exposes no existing CLI for its guided binding/publication
flow. This harness audits existing bindings and uses a temporary probe-only
index overlay. It does not prove normal app publication performance, every
production app's observations, Cyclone bootstrap acknowledgement, or platform
compatibility. The installed module hash describes the installed file, not a
loaded-image attestation. Probe package visibility is Android-filtered.

At the end of this initial harness run, release notes were drafted without
changing version metadata. Signing credentials were not configured in the local
shell, and no commit, tag, push, or release publication had been performed.
The separately authorized follow-up uses alpha.15/code 39 because alpha.14 was
already published; its signed CI and asset verification are recorded in
HANDOFF-2026-10-16-ALPHA15-HARNESS-RELEASE.md.
