# Play evidence runner fix and device rerun — 2026-10-20

Base: main a51c6d1b4e6144b754ec5fda2bdad6cdfc6813d1. Installed alpha.27/code 51. No release or production APK/module installation performed.

## Fixed

- The signed diagnostics APK deliberately disables run-as. Added explicit opt-in `--private-access root`, with diagnostics UID verification and a closed staging/report command allowlist. No silent fallback.
- Root-created input directories had incorrect per-user SELinux categories. Forced recursive relabeling of that diagnostics-only directory is required on this device; chown and non-recursive restorecon were insufficient.
- ADB exec-out does not reliably propagate remote exit codes and can leave stdin-fed cat waiting indefinitely. Root commands now carry a unique checked exit marker; writes use non-PTY shell-v2 with EOF forwarding and exact input bytes.
- Explicit activity flags deliver new request extras to a reused screen.
- `--saved-identity` reads only the selected saved profile into RAM, avoiding a private profile-body export to the PC.
- Seven focused regression tests added; existing capture test also checks fresh-intent flags.

## Changed files

- tools/run_play_evidence.py
- forge/tests/test_play_evidence_harness.py
- docs/PLAY-EVIDENCE-HARNESS-V1.md
- This handoff and new timestamped evidence directories. Older evidence/handoffs preserved.

## Final device result

Evidence: docs/runtime-evidence/2026-10-20-000729-alpha27-play-runner-verified/.

The final run needed no manual staging repair or relaunch. The authorized operator tapped Collect local evidence only. All 16 rows completed: 3 PASS, 8 OBSERVED, 2 MISSING, 3 NOT_RUN, 0 FAIL, 0 BLOCKED. Request, profile hash, artifact digest and user 17 matched. Production import/review/save succeeded, with stored projection matching the new capture exactly.

Readiness remains UNKNOWN correctly: account association is not visible, Play Protect and actual Google settings access are unmeasured. The alternative com.android.webview package is absent/not visible while Google WebView is available. The settings intent does not resolve. Integrity was not requested. These are not fabricated passes or platform verdicts.

Production binding files, saved profiles, publication bodies/index, publisher shares and module files matched the before/after audit. Diagnostic/companion permissions and AppOps modes were identical. Request-specific input/report cleanup passed. Only the intended readiness evidence was saved via UI. The valid redacted report remains in user 17 Downloads as cloak-alpha27-play-rerun.json. Audit coverage is limited to accessible paths; readiness preferences are intentionally separate from the preservation audit.

Intermediate attempts are retained separately: 2026-10-20-000008-alpha27-play-runner-fix (stdin timeout, cleanup passed), 2026-10-20-000207-alpha27-play-runner-fixed (manual SELinux repair/relaunch), 2026-10-20-000610-alpha27-play-runner-verified (manual recursive-label repair/relaunch). Only 000729 proves the final automatic staging/launch path.

## Separate connector issue remains

The UI still shows Reconnecting and historical Magisk timeout binding warnings. There are 327 local bindings and 256 remote Cyclone config files. The installed Cyclone APK itself contains the 256-config-tuple limit error message. This strongly supports capacity pressure, but no current remote error body was captured to prove every BAD_REQUEST cause. Connection completion also waits for full reconciliation in the current lifecycle implementation. This run does not claim to fix connector readiness. No bindings were deleted, no remote limit changed, and no root repair/publication was invoked. Magisk currently records allow for the companion UID; historical timeout labels are not evidence of current denial. Resolving capacity/connection-state behavior is separate from Play evidence collection.

## Exact verification commands

```powershell
$env:JAVA_HOME='C:/Program Files/Eclipse Adoptium/jdk-17.0.20.101-hotspot'
$env:CLOAK_TEST_WSL='1'
& "$env:TEMP/cloak-build/gradle-8.11.1/bin/gradle.bat" -p android :app:testDebugUnitTest :module:testDebugUnitTest :playEvidence:testDebugUnitTest :probe:testStandardDebugUnitTest :probe:testDiagnosticsDebugUnitTest :app:assembleDebug --offline --no-daemon --console=plain
uv run --offline --with pytest --with jsonschema -- python -X utf8 -m pytest forge/tests/test_play_evidence_harness.py -q
uv run --offline --with pytest --with jsonschema -- python -X utf8 -m pytest forge/tests forge/schema_tests -q
uv run --offline -- python -X utf8 -m unittest discover -s android/probe/tests -v
git diff --check
git diff --cached --check
uv run --offline --with jsonschema -- python -X utf8 tools/run_play_evidence.py --adb C:/Users/Agent/android-sdk/platform-tools/adb.exe --serial 3B171FDJH0061G --user 17 --private-access root --saved-identity 192f47b0-2c97-5d5e-8e59-461e9dc53abb --profile-id Cyclone_2ca4cf9432e64fbd --artifact-digest 7e8803535fcd011750a5c07c283bc83e10ed335860965ddecbb05883e88d2a62 --timeout 240 --output docs/runtime-evidence/2026-10-20-000729-alpha27-play-runner-verified
```

Results: app 692, module 40, Play diagnostics 20, standard probe 109, diagnostics probe 109, forge/schema 1045, host probe 7: **2022 tests passed**. Focused runner 38 passed (included in forge total). Debug build succeeded. No compiler warnings. Diff checks passed; Git emitted an informational LF/CRLF normalization warning for the Python test file.

No commit, tag, release or production behavior change was made in this run.
