# Rung 1a: coverage-gap closure on the physical device

Result: **STOPPED — CANDIDATE_MODULE_NOT_INSTALLED**. Device reachable; users 0 and 17 RUNNING_UNLOCKED. Both live and pending-update Cloak module directories are absent. This matches the completed removal stage of the prior real-app run. No candidate artifact can be measured without a module install/reboot, which this sprint forbids. No diagnostics install/capture/permission grant was attempted; no fixture/binding state created.

## PARTIAL inventory

The ledger states 17 PARTIAL runtime-envelope paths; the checked-in table confirms 17. Its older bridge subsection also contains historical zero-partial counts; these are historical sections, not new physical evidence. Counts remain unchanged.

| Canonical path | Existing probe | Limitation |
| --- | --- | --- |
| `reality.carrier.values.sim_state` | `envelope.telephony.simState + envelope.telephony.simStateSlot0` | Exact calls present; fixture must declare known carrier.sim_state. No missing expected state can be inferred from host. |
| `reality.compute.values.gpu` | `envelope.compute.gpuRenderer` | GL_RENDERER reader present; requires source-supported expected GPU and loaded candidate. |
| `reality.memory.values.free_storage_gib` | `envelope.storage.availableBytes` | Private-path available-byte reader present; requires declared free_storage_gib; not nominal capacity. |
| `reality.packages.values.installed` | `envelope.packages.visibleVersions` | Aggregate visible allowlisted getPackageInfo results only; no visibility expansion or independent leaf proof. |
| `reality.packages.values.installed[].package` | `envelope.packages.visibleVersions` | Aggregate visible allowlisted getPackageInfo results only; no visibility expansion or independent leaf proof. |
| `reality.packages.values.installed[].version_code` | `envelope.packages.visibleVersions` | Aggregate visible allowlisted getPackageInfo results only; no visibility expansion or independent leaf proof. |
| `reality.packages.values.installed[].version_name` | `envelope.packages.visibleVersions` | Aggregate visible allowlisted getPackageInfo results only; no visibility expansion or independent leaf proof. |
| `reality.sensors.values.inventory` | `envelope.sensors.inventory` | Aggregate covers type/name/delay/range/resolution; no independent per-leaf measurement claim. |
| `reality.sensors.values.inventory[].max_delay_us` | `envelope.sensors.inventory` | Aggregate covers type/name/delay/range/resolution; no independent per-leaf measurement claim. |
| `reality.sensors.values.inventory[].min_delay_us` | `envelope.sensors.inventory` | Aggregate covers type/name/delay/range/resolution; no independent per-leaf measurement claim. |
| `reality.sensors.values.inventory[].name` | `envelope.sensors.inventory` | Aggregate covers type/name/delay/range/resolution; no independent per-leaf measurement claim. |
| `reality.sensors.values.inventory[].range` | `envelope.sensors.inventory` | Aggregate covers type/name/delay/range/resolution; no independent per-leaf measurement claim. |
| `reality.sensors.values.inventory[].resolution` | `envelope.sensors.inventory` | Aggregate covers type/name/delay/range/resolution; no independent per-leaf measurement claim. |
| `reality.sensors.values.inventory[].type` | `envelope.sensors.inventory` | Aggregate covers type/name/delay/range/resolution; no independent per-leaf measurement claim. |
| `reality.webview.values.provider` | `envelope.webview.provider` | Current-provider package reader present. |
| `reality.webview.values.user_agent` | `envelope.webview.defaultUa (related only)` | Exact concrete WebSettings.getUserAgentString reader is absent; default-UA read cannot close instance getter path. |
| `reality.webview.values.version` | `envelope.webview.version` | Current-provider version reader present. |

## Permission and SIM limits

The current diagnostics manifest/request flow supports coarse/fine location, nearby Wi-Fi and Bluetooth connect (plus normal ACCESS_WIFI_STATE). It does not declare READ_PHONE_STATE. These grants cannot manufacture a SIM expectation. Current schema-0.3 fixture B has no Reality section and therefore no carrier.sim_state expectation; missing expectation remains OBSERVED/NOT_COMPARABLE. This is not evidence of a permanent Android block. No row is promoted, no runtime status changed, no platform/coverage claim.

## Audit and cleanup

Persisted before/after snapshots equal: original production APK hash, companion grant states/stable AppOps, accessible binding/index files, published bodies and shares. Both users were unlocked for snapshots. Probe absent for users 0 and 17 before/after. No install/uninstall, switch, reboot, module action, permission/AppOps write, source change, commit, push or release occurred. Exact read-only device commands/exit codes are in commands.jsonl. Static reader/manifest inspection only; local suites were not run because no code changed. Prior evidence preserved.

## Open questions

- A separately authorized preparatory run must load an artifact-proven alpha.28 candidate before this measurement lane can proceed; module changes/reboot are outside this sprint.
- Which existing source-supported schema-0.4 fixture supplies carrier.sim_state and the remaining Reality expectations? The current A/B probe assets cannot prove missing Reality values.
- The exact concrete WebSettings.getUserAgentString path is not called by the existing envelope probe; a default-UA result cannot close it. New probe code would require separate authorization.
- Sensor/package aggregate measurements must not be counted as independent leaf proof without the requested evidence policy.
- Explicit UI permission grants legitimately change the diagnostics app permission state during measurement; final equality scope must distinguish that temporary diagnostics state from production state, with global uninstall cleanup.

Only a dated evidence pointer was added to RUNTIME-COVERAGE.md. No measured counts were invented.
