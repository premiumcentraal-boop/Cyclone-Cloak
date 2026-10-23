# User-17 switch-path fixture A/B standalone validation

Result: COMPLETE
Failure: None
APK SHA-256: 3f3a611307024ba2fa3bbe301df4e2358a876182616f71a23f0326fc4abfbabf
Production APK/version/permission snapshot unchanged: True
Diagnostics uninstall confirmed: True

| User | Capture | OBSERVED | BLOCKED | MISSING |
| --- | --- | --- | --- | --- |
| 0 | 1 | 2 | 2 | 0 |
| 17 | 1 | 2 | 2 | 0 |
| 17 | 2 | 2 | 2 | 0 |

Determinism:
- user 0 diagnostics.filesystem.build_prop: NOT_COMPARABLE
- user 0 diagnostics.filesystem.proc_version: NOT_COMPARABLE
- user 0 diagnostics.filesystem.proc_cpuinfo: NOT_COMPARABLE
- user 0 diagnostics.filesystem.maps_snapshot: SKIPPED_VOLATILE
- user 17 diagnostics.filesystem.build_prop: NOT_COMPARABLE
- user 17 diagnostics.filesystem.proc_version: NOT_COMPARABLE
- user 17 diagnostics.filesystem.proc_cpuinfo: IDENTICAL
- user 17 diagnostics.filesystem.maps_snapshot: SKIPPED_VOLATILE

Limits: probe-only caller-visible observations; no parity verdict or VERIFIED promotion.
No module install, root shell, reboot, binding overlay, permission grant/reset or production app launch was performed. Only the authorized user 0 to 17 to 0 round trip was used.
Private production stores/module files were not independently audited. APK/version/permission equality is not proof of private-store byte equality.
Installed module provenance was not established; the handoff alpha.28 description is not independently confirmed.
The diagnostics-only installation was globally uninstalled at cleanup; no private probe files are retained on-device.
Legacy probe comparisons used the packaged fixture without creating a runtime binding and are not interpreted as parity validation.
Cold-start policy: diagnostics-only force-stop before every capture; request/process-bound start/end markers preserved.
Gate: any failed capture stops the run. No additional code fixes follow the device failure.
Reports are redacted exports retaining filesystem digests/counts and existing row statuses/digests only. No raw file content retained.

## Verification details

All reports have 89 unique legacy row IDs, correct fixture/asset hash and matching STARTED/COMPLETED markers. Both user-17 cold captures have distinct PIDs. All filesystem sections passed schema validation.

| User | Capture | Fixture | PID | Legacy PASS / FAIL / BLOCKED / OBSERVED |
| --- | --- | --- | --- | --- |
| 0 | 1 | A | 12128 | 25 / 48 / 12 / 4 |
| 17 | 1 | B | 13608 | 15 / 58 / 12 / 4 |
| 17 | 2 | B | 15796 | 15 / 58 / 12 / 4 |

Legacy FAIL comparisons are retained: synthetic expected profiles were supplied without runtime bindings. COMPLETE means capture/launch/schema/digest/cleanup completion, not runtime parity. build.prop and proc/version remain PERMISSION_DENIED, BLOCKED and NOT_COMPARABLE. Maps remain volatile observations. No row promoted to VERIFIED.

Preparation: owner authorized switching from initially active user 18 to existing user 17; it reached RUNNING_UNLOCKED normally. Returned to user 0 before starting the recorded 0 → 17 → 0 validation round trip. No credentials or force-unlock mechanism used.

Local checks: both probe builds, 288 probe unit tests and 34 host tests passed; Git whitespace checks passed. Existing Git LF/CRLF notices only. Run-only patch and dirty prerequisite source copies included. Nothing staged, committed, tagged or released.
