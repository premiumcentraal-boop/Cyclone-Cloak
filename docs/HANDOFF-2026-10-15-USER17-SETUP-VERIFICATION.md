# User 17 setup verification — 2026-10-15

**Stopped at root setup after two failed attempts**, following the requested stop condition. No identity or binding was created, and Chrome was not launched as a bound app.

## Results

1. ADB device `3B171FDJH0061G` was attached; active Android user was **17**. Installed versions: Cyclone `5.0.0-alpha.132.dev1` / code 286 and Cloak `0.11.0-alpha.12` / code 36. The phone semantic connector returned `PAIRING_REQUIRED`, so UI verification used ADB hierarchy reads and input.
2. Home's **Root setup not checked** attention item navigated to Health. It did not scroll to the repair button: reaching **CHECK AND REPAIR → Check & repair** required scrolling past Readiness checks. This was navigable, rather than a dead end.
3. The first Check & repair returned **“Root approval timed out. Run the check again and approve the prompt.”** The same control was retried once. The second attempt returned the same message. No Magisk Grant control was observed or tapped. Initial hierarchy polling may miss a short-lived prompt; two screenshots during the retry showed Cloak with its repair button disabled, not a Magisk dialog. Do not interpret this as proof that Magisk never attempted to display a prompt or that a user explicitly denied it.
4. Final Readiness: **Degraded — 5 pass · 4 warning · 0 fail**; **0 enabled bindings · 0 disabled**. Profile publication remained unconfirmed; installed module version was not confirmed by Root Doctor. Connector approval remained true in exported evidence. No connector approval prompt was encountered.
5. Health **RUNS** remained **0 / “No binding runs recorded yet.”** Root Doctor attempts are not binding operations, so no per-binding-run bundle exists. Exported **Download debug bundle · last 24h** instead.
6. The standard share sheet opened successfully, offering **Quick Share, Gmail, Drive and Bluetooth**. No local Save/Downloads target was exposed, including after an upward swipe. Nothing was sent through those apps. The exact app-generated redacted ZIP was recovered read-only from the app export cache; **it was not saved through the share sheet to Downloads**, and `adb pull` from Downloads could not be demonstrated.
7. Identity generation, binding review/submission, user-17 publisher isolation and bound-app launch remain **NOT RUN** because of the stop condition. No third repair attempt was made.

## Exported evidence

- `docs/runtime-evidence/2026-10-15-user17-setup-root-timeout-24h.zip` — **2,834 bytes**, SHA-256 `5a7d248e34b73325010f437fc2fc186f9b67124aa2cc104071a5e329a245074c`.
- ZIP files: `manifest.json`, `runs.json`, `diagnostics.json`, `bindings-index.json`, `module-state.json`, `connector-handshake.json`.
- ZIP CRC check passed. Manifest `run_count=0`; local bindings snapshot empty; 67 diagnostic events. Two publication-result `ROOT_TIMEOUT` events (each with a corresponding module warning). Module state `code=ROOT_TIMEOUT`, version/publication null. Connector handshake `approved=true`, contract `cyclone.connector/1`, host minor 4.
- `docs/runtime-evidence/2026-10-15-user17-root-timeout-screen.png` — final on-screen evidence.
- Export source: `/data/user/17/dev.cyclone.cloak/cache/run-bundles/cloak-runs-1792059913856-4502844750965052861.zip`. Recovery used binary-safe `adb exec-out` with read-only `su --mount-master -c 'cat …'` into the local ZIP. This preserves the app export without inventing a run or modifying its contents.

## Published index: before / after

Read-only command:

```powershell
& 'C:/Users/Agent/android-sdk/platform-tools/adb.exe' shell su --mount-master -c 'cat /data/adb/cyclone_cloak/state-v1/index.json'
```

**8 → 8 entries**. All eight retain **publisher 0**. Before/after projections of entry key, publisher, Cyclone profile reference and Cloak profile reference match. Existing entries: three for user 13, one for user 14, four for user 16. **No user-17 entry exists**, so there is no new publisher value to report. This compares binding references, not a claimed byte-for-byte whole-tree hash.

No reboot, data clearing, permission changes, Main binding edits, publication-script edits or source-code changes were performed. Files added in this run are this handoff and the evidence artifacts. No automated build/tests were required for this device-only run.
