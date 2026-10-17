# Three-cycle diagnostics lifecycle validation

Result: RESTORED_WITH_EXPECTED_LIMITATIONS. All three cycles passed; 24 captures completed.
Runner commit: `7d088418de43b3d3a7d9d4314b998fcc0cd08e5f`. Pinned APK/base harness: alpha.18 / fb1e3c6.
APK installation was skipped for the entire invocation (zero attempts). Both user package paths and hashes matched before every cycle.

| Cycle | Seconds | User 0 restoration P/O/B | User 17 restoration P/O/B | Restoration failures |
|---|---:|---|---|---:|
| 1 | 44.14 | 73/8/8 | 72/5/12 | 0 |
| 2 | 41.42 | 73/8/8 | 72/5/12 | 0 |
| 3 | 42.57 | 73/8/8 | 71/5/13 | 0 |

Cycle durations total 128.13 seconds (excluding wrapper overhead). Production and permission/AppOps snapshots were identical, including between-cycle boundaries. All temporary entry, profile body, private report/expected-profile file and scratch cleanup checks passed. Fixture/user/profile/published-body metadata checks passed in all captures.

Four user-17 bound permission blocks remain. Additionally, advertising.id returned BLOCKED / IOException in the third user-17 cold post-removal capture. It remains a blocked restoration limitation, never a PASS. Other baseline permission/API limits and external observations remain explicit. Raw unbound fixture FAIL rows are synthetic-fixture mismatches, not cold-restoration failures.

Original cycle/lifecycle evidence is preserved. This complete ZIP adds the execution report, validation provenance and console log. The previous failed evidence is unchanged. Provenance records both executed file SHA-256 and Git-normalized source SHA-256 because Git normalizes Windows newlines.

Local checks: 65 focused, 503 forge and 33 schema tests passed; git diff --check passed. No new Android build: reused clean-tag APK verified. No production APK/module installs, permission resets, new users or reboot. Audit covers accessible binding stores and module files; AppOps access timestamps/counters are excluded. Android switching does not verify Cyclone bootstrap. No platform verdict.
