# Restoration follow-up

Result: RESTORED_WITH_EXPECTED_LIMITATIONS; focused cycle 1 and all 3 lifecycle cycles completed.

| Cycle | User | Restoration PASS | OBSERVED | BLOCKED | FAIL | Package delta |
|---|---:|---:|---:|---:|---:|---|
| 1 | 0 | 102 | 7 | 8 | 0 | +0/-0 |
| 1 | 17 | 100 | 4 | 13 | 0 | +0/-0 |
| 2 | 0 | 102 | 7 | 8 | 0 | +0/-0 |
| 2 | 17 | 101 | 4 | 12 | 0 | +0/-0 |
| 3 | 0 | 102 | 7 | 8 | 0 | +0/-0 |
| 3 | 17 | 101 | 4 | 12 | 0 | +0/-0 |

Production semantics and raw index bytes unchanged in all final cycles. Permission/grant/AppOps snapshots unchanged; cleanup passed after every cycle and at boundaries. Zero installations, permission resets or reboots.

Historical package deltas cannot be reconstructed: old evidence retained hashes only. New full inventories: user 0 = 132 packages; user 17 = 126; no added/removed packages in the final proof.

Historical unrelated control hashes correspond to 0 -> 1. Fresh reproduction independently confirmed host state; guarded EXTERNAL_STATE observation, never PASS. The host-setting writer remains unattributed.

Historical production delta was index JSON serialization only; semantic entries, body/share/module/store hashes matched. Old byte sizes were not retained. New file audits record size/hash and gate semantic fields plus all other file changes.

Factory external fixtures had lost existing shared EXTERNAL_STATE Wi-Fi policies. Harness now validates those observations against checked-in rules with fingerprints. Names remain hashed, malformed values fail, denials stay blocked.

Tests: Android app 781; module 80; Play diagnostics 30; standard probe 114; diagnostics probe 114. Forge/schema 1284. Host probe 7. Builds, generator checks, offline backend self-test and whitespace checks passed. No compiler warnings.

Limitations: expected permission/API blocks and external-state observations; normal Android switching rather than Cyclone bootstrap; accessible stores only; module file hash is not in-process binary proof. Candidate alpha.27/code51 metadata does not identify the published alpha.27 binary.

No release, tag, version bump, production code change or broad compatibility/platform claim. Exact commands and changed files: ../../HANDOFF-2026-10-21-RESTORATION-HARNESS-FOLLOW-UP.md.
