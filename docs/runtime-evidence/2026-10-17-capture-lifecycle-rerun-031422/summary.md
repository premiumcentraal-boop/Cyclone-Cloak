# Alpha.17 cross-user diagnostics

Result: DEGRADED
Duration: 37.2 seconds
Errors: []

| Phase | User | Fixture | PASS | OBSERVED | BLOCKED | FAIL | MISSING | Reused process |
|---|---:|---|---:|---:|---:|---:|---:|---|
| owner-initial | 0 | a | 82 | 7 | 0 | 0 | 0 | True |
| secondary-warm | 17 | b | 81 | 4 | 4 | 0 | 0 | True |
| secondary-cold | 17 | b | 81 | 4 | 4 | 0 | 0 | False |
| owner-return-warm | 0 | a | 82 | 7 | 0 | 0 | 0 | True |
| owner-return-cold | 0 | a | 82 | 7 | 0 | 0 | 0 | False |

Paired classification: {"result": "DEGRADED", "critical_failures": 0, "warnings": 4, "blocked_rows": 4, "not_covered_rows": 0, "statuses": {"PASS": 408, "OBSERVED": 29, "BLOCKED": 8}, "external_state_rows": 6, "pending_rows": 0}
Preservation: {"production_entry_count": true, "index_sha256": true, "index_bytes_sha256": true, "entry_hashes": true, "published_bodies": true, "publisher_shares": true, "module_files": true, "binding_stores": true}
Permissions/app-op modes unchanged: True

Each warm capture has a retained primer and proves process reuse. Cold captures prove replacement. Primers are excluded from the five-phase aggregate. Only diagnostics APK and temporary probe overlay were written. Normal user switches may run existing system/profile apps. No permissions changed, forced unlocks, user creation, production app/module installation or reboot. Other locked users private stores remain outside the accessible-file audit. Installed-file digest is not in-process module identity proof. External rows remain observations; denied reads are BLOCKED. No platform verdict.

## Expected permission-state limitations

- wifi.bssid: PERMISSION_DENIED:android.permission.ACCESS_FINE_LOCATION
- wifi.ssid: PERMISSION_DENIED:android.permission.ACCESS_FINE_LOCATION
- bluetooth.address: PERMISSION_DENIED:android.permission.BLUETOOTH_CONNECT
- bluetooth.name: PERMISSION_DENIED:android.permission.BLUETOOTH_CONNECT

These four user-17 rows account for all warnings. No remaining pending stability checks. No duplicate or incomplete captures occurred.

Production counts unchanged: {"entries": 327, "published_files": 654, "publisher_share_files": 1310, "module_files": 3, "accessible_binding_stores": 2}.

Tests: {"app": 522, "module": 12, "probe_standard": 91, "probe_diagnostics": 91, "forge": 438, "schema": 33, "probe_host": 7}. Diagnostics build successful; no compiler warnings. Commit: `8e7ab1407c5ff18a2a50aa6e727b34c52739396c`. Exact commands are in verification.json and build-provenance.json.
