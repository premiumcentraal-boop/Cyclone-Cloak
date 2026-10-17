# Run 3 diagnostics expectation validation

Result: PASS
Duration: 174.43 seconds
Commit: fbdc0faa5330bb0672894eb3110318307d738816
APK SHA-256: 03c16645995ca8fd3c8f9daa3caf0cf6aff9aa8f2618a26d82a737628ba6cbb7
Errors: []

| Capture | PASS | OBSERVED | BLOCKED | FAIL | MISSING |
|---|---:|---:|---:|---:|---:|
| bound-a | 82 | 7 | 0 | 0 | 0 |
| bound-a-repeat | 82 | 7 | 0 | 0 | 0 |
| bound-b | 82 | 7 | 0 | 0 | 0 |

Paired classification: {"result": "PASS", "critical_failures": 0, "warnings": 0, "blocked_rows": 0, "not_covered_rows": 0, "statuses": {"PASS": 246, "OBSERVED": 21}, "external_state_rows": 6, "pending_rows": 0}
Stability: [{"from_run": "bound-a", "to_run": "bound-a-repeat", "id": "runtime.javaBridgeVisible", "changed": false, "expected_change": false, "reason": "STABLE_MATCH", "severity": "INFO", "status": "PASS", "evidence_pair": ["897ccff0dce14f059f75834d034e8d7b", "b296ab7d32f64ce0834ac614aff7c1e4"]}, {"from_run": "bound-a-repeat", "to_run": "bound-b", "id": "runtime.javaBridgeVisible", "changed": false, "expected_change": false, "reason": "STABLE_MATCH", "severity": "INFO", "status": "PASS", "evidence_pair": ["3ce80c373ba045df9607b71731ff4785", "897ccff0dce14f059f75834d034e8d7b"]}]
Production preservation: {"production_entry_count": true, "index_semantic_sha256": true, "index_raw_sha256": true, "module_files": true, "publisher_shares": true, "published_bodies": true, "binding_stores": true}
Fixture/hash checks: True
Permissions unchanged: True
Temporary state removed: True

Scope: diagnostics APK only. No production APK/module install, user switch, reboot, permission modification, connector call or production store write. Values redacted to comparison hashes. External observations are not passes. Paired stability here means fixture A/B in the same Android user, not an Android-user transition. Permission denial is not physically exercised unless already denied. Installed module file digest does not prove in-memory binary identity. Locked other users private stores are outside the audit. No historical evidence reclassified; no platform verdict.

## Per-row policy outcomes (identical across A / A repeat / B)

| Row | Policy | Outcome | Reason |
|---|---|---|---|
| `telephony.simState` | EXTERNAL_STATE | OBSERVED | EXTERNAL_STATE_AVAILABLE |
| `telephony.phoneType` | ENUM | PASS | ENUM_VALID |
| `telephony.roaming` | TYPED | PASS | TYPE_VALID |
| `settings.unrelatedControl` | EXTERNAL_STATE | OBSERVED | EXTERNAL_STATE_AVAILABLE |
| `wifi.bssid` | EXTERNAL_STATE | OBSERVED | EXTERNAL_STATE_AVAILABLE |
| `wifi.linkSpeed` | EXTERNAL_STATE | OBSERVED | EXTERNAL_STATE_AVAILABLE |
| `wifi.ssid` | EXTERNAL_STATE | OBSERVED | EXTERNAL_STATE_AVAILABLE |
| `bluetooth.name` | EXTERNAL_STATE | OBSERVED | EXTERNAL_STATE_AVAILABLE |
| `sensors.list` | TYPED | PASS | TYPE_VALID |
| `packages.visible` | TYPED | PASS | TYPE_VALID |
| `runtime.loadedLibraries` | TYPED | PASS | TYPE_VALID |
| `runtime.javaBridgeVisible` | STABLE_ACROSS_SWITCH | OBSERVED | STABILITY_BASELINE_REQUIRED |

The bridge row remains OBSERVED in each immutable capture. Both paired comparisons separately return PASS/STABLE_MATCH, with distinct capture IDs and matching installed-artifact and rule hashes. Negative controls in stability-metadata-guards.json are host-only copies of this new evidence; no historical data was reclassified.

Permissions were already granted. This run has no physical denial capture; denial reason mapping is covered by the existing probe tests, not claimed as renewed device denial proof. The six external rows all report EXTERNAL_STATE_AVAILABLE and remain observations. No silent external-state PASS.

Commands:

```powershell
$env:JAVA_HOME='C:/Program Files/Eclipse Adoptium/jdk-17.0.20.101-hotspot'
$env:CLOAK_TEST_WSL='1'
& "$env:TEMP/cloak-build/gradle-8.11.1/bin/gradle.bat" -p android :probe:assembleDiagnosticsDebug --offline --no-daemon --console=plain
uv run -- python -X utf8 "$env:TEMP/cloak-run3-device.py"
uv run -- python -X utf8 "$env:TEMP/cloak-run3-guards.py"
uv run --with pytest -- python -X utf8 -m pytest forge/tests/test_diagnostic_expectations.py -q
& "$env:TEMP/cloak-build/gradle-8.11.1/bin/gradle.bat" -p android :probe:testDiagnosticsDebugUnitTest --offline --no-daemon --console=plain
git diff HEAD^ HEAD --check
```

Build successful. Focused Python tests: 118 passed. Diagnostics unit task successful/up-to-date (79 prior passing results); no fresh execution claimed. No compiler warnings. Commit is local only; no push, tag or release.
