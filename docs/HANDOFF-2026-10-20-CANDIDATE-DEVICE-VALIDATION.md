# Candidate device validation — 2026-10-20

Result: **NOT VALIDATED; stopped after cycle 1 restoration/audit failures. No release.**

## Commits and build

Four focused commits pushed to main, preserving unrelated untracked files:

- `7cadb24` — cohort separation engine (9 files).
- `d717a5f` — instance realism expansion (23 files).
- `304e86f` — scoped runtime envelope (25 files).
- `1c9ce900930b7c52d3d7130b8ca8a741e183b73a` — service surface isolation (14 files).

Each commit includes its corresponding documentation/tests. Shared architecture/current-state updates were split between commits. See `git show --stat <commit>` for exact changed-file lists.

Fresh combined gates: **781 app, 80 module, 30 Play diagnostics, 114 standard probe, 114 diagnostics probe, 1,245 forge/schema, 7 host probe tests passed**, no failures/errors or compiler warnings observed. Generated-source checks, synthetic-only offline backend self-test (8 checks), and whitespace checks passed.

Signed validation: https://github.com/premiumcentraal-boop/Cyclone-Cloak/actions/runs/39497618039 — success at exact candidate commit. No release workflow or tag created. Retained alpha.27/code 51 metadata does not identify these binaries uniquely; hashes below do.

| Candidate artifact | Bytes | SHA-256 |
|---|---:|---|
| app-release.apk | 44455358 | df37f86da7ea617fcd1aad65504b23c95aaad56a1f9726ca3270846f8165cffc |
| cyclone-cloak-0.11.0-alpha.27.zip | 1809335 | 53649be46759c3b8ba461d122a20d214727f310ceb34ee9fa296a4a2e7454b3f |
| playEvidence-release.apk | 3451657 | b129bd72c88a38817a29abf9c400e83416fc1605955b9b5a4e8af0896d368ef0 |
| probe-diagnostics-debug.apk | see provenance | e4f6d3d13a810f1e17ef4d3ae0317e01e78d9cdc37bbb8d483f1565bf1f4e9d8 |

Both signed APK certificates match the installed production signer. The APK embeds the exact signed module ZIP. Debug companion was deliberately not installed because its signer differs. No uninstall or app-data clearing occurred.

## Device outcome

Pixel 8, serial 3B171FDJH0061G, API 36. Signed app/Play diagnostics updated; module installed through Magisk's existing --install-module path. Owner explicitly authorized one reboot. Both users became available via normal switching without forced unlock.

Factory pixel_8/user 0 (scenario A), pixel_7/user 17 (scenario B), schema 0.4, were used without inventing missing hardware facts. Reports correctly say external; A/B scenario labels remain separate. Fixture/report/published-body hashes match for both bound captures, with correct user UIDs. Warm-removal processes were reused; cold captures used new processes.

| Bound user | Legacy PASS | OBSERVED | BLOCKED | FAIL | Linked PASS | Envelope PASS/OBSERVED/MISSING |
|---|---:|---:|---:|---:|---:|---|
| 0 | 76 | 11 | 2 | 0 | 13 | 4/3/8 |
| 17 | 75 | 8 | 6 | 0 | 13 | 4/3/8 |

All eight captures completed without a detected probe crash. The gate correctly stopped before cycles 2–3:

- Cold restoration: packages.visible changed for users 0 and 17; settings.unrelatedControl changed for user 17. Exact before/after hashes are preserved in user cold-restoration JSON. Cause is unresolved; no reclassification to PASS.
- Raw index bytes changed; canonical index and every one of 327 production entries are identical. Existing overlay cleanup reserializes JSON. Exact-byte preservation failed; no later normalization/repair was performed.
- All temporary entries, bodies, reports, expected-profile files and scratch paths were removed for both users. Published bodies, shares, bindings and saved identities match the baseline. Module changes are the intended update.
- Permissions/AppOps serialized snapshots match. No grant/reset commands ran. A discovered existing snapshot-parser issue overwrites runtime grant dictionaries on repeated dumpsys User sections; current independent read confirms four diagnostics grants for user 0 and four denials for user 17, but historical grant-flag preservation is not fully proven. Fix/test that collector before a repeat proof.

Camera/sensor/free-storage/package/WebView/DRM coverage remains incomplete. WebView default-UA returned AndroidRuntimeException. Bounded fixed module-log collection found no matching retained lines. No optional authenticated Integrity verdict requested. Further cycles, Play capture and service-isolation UI verification remain pending after the failed gate.

An initial preserved attempt failed before collection because my candidate orchestration incorrectly labelled custom Factory profiles as built-in fixtures. The probe correctly rejected FIXTURE_PROFILE_MISMATCH. Only the evidence runner was corrected; no product source changed.

## Exact local gate commands

```powershell
$env:JAVA_HOME='C:/Program Files/Eclipse Adoptium/jdk-17.0.20.101-hotspot'
$env:ANDROID_HOME='C:/Users/Agent/android-sdk'
$env:CLOAK_TEST_WSL='1'
& "$env:TEMP/cloak-build/gradle-8.11.1/bin/gradle.bat" -p android :app:testDebugUnitTest :module:testDebugUnitTest :playEvidence:testDebugUnitTest :probe:testStandardDebugUnitTest :probe:testDiagnosticsDebugUnitTest :app:assembleDebug :module:packageModule :playEvidence:assembleDebug :probe:assembleStandardDebug :probe:assembleDiagnosticsDebug --offline --no-daemon --console=plain
uv run --offline --with pytest --with jsonschema --with cryptography -- python -X utf8 -m pytest forge/tests forge/schema_tests -q
uv run --offline -- python -X utf8 -m unittest discover -s android/probe/tests -v
uv run --offline -- python -X utf8 tools/generate_instance_realism.py --check
uv run --offline -- python -X utf8 tools/generate_runtime_contract.py --check
uv run --offline --with cryptography -- python tools/integrity_backend.py --self-test
git diff --check
git diff --cached --check
```

## Device command record

```powershell
gh workflow run signed-validation.yml --ref main -f tag=v0.11.0-alpha.27
# Signed candidate downloads verified before installation; no release workflow.
adb -s 3B171FDJH0061G install -r <signed-candidate-app-release.apk>
adb -s 3B171FDJH0061G install -r <signed-candidate-playEvidence-release.apk>
adb -s 3B171FDJH0061G push <signed-candidate-module.zip> /data/local/tmp/cloak-candidate-1c9ce90.zip
adb -s 3B171FDJH0061G shell su --mount-master -c 'magisk --install-module /data/local/tmp/cloak-candidate-1c9ce90.zip'
adb -s 3B171FDJH0061G reboot
adb -s 3B171FDJH0061G shell am switch-user 17
adb -s 3B171FDJH0061G install -r android/probe/build/outputs/apk/diagnostics/debug/probe-diagnostics-debug.apk
adb -s 3B171FDJH0061G shell am switch-user 0
```

The candidate runner is an explicitly adapted evidence-only copy of run_probe_reversibility.py: current committed source instead of old pinned alpha.18; source-supported schema-0.4 Factory profiles; digest-only envelope rows; disconnect latch; missing data remains limitations. Product sources were not modified. Its source/hash and all exact local paths are preserved with provenance.

```powershell
uv run --offline -- python -X utf8 'docs/runtime-evidence/2026-10-20-225427-candidate-runtime-validation/candidate-runner.py' --source-checkout C:/Users/Agent/Cyclone/cloak-repo --adb C:/Users/Agent/android-sdk/platform-tools/adb.exe --serial 3B171FDJH0061G --secondary 17 --cycles 3 --output 'docs/runtime-evidence/2026-10-20-225427-candidate-runtime-validation/runtime-lifecycle-external'
```

## Evidence and next steps

docs/runtime-evidence/2026-10-20-225427-candidate-runtime-validation

See summary.md, capture-results.json, signed-build-provenance.json, module-after-reboot.json, permission snapshots, all captures/audits, and cleanup proofs. The initial failed attempt is retained separately in runtime-lifecycle. No previous evidence directory was overwritten.

Next: investigate package-visibility/accessibility-control deltas without guessing a cause; add tested permission-collector correction and byte-preserving overlay cleanup in a separately scoped diagnostics change; then repeat the complete lifecycle and Play/UI checks. Candidate binaries remain installed; module native hash provenance is saved. Do not identify the candidate as the published alpha.27 solely by version string.

No platform certification, permanent trust, hardware equivalence or broader compatibility claim.
