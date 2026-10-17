# Successful diagnostics reversibility retry

Result: RESTORED_WITH_EXPECTED_LIMITATIONS. Eight captures completed in 46.91 seconds; no runner errors.
Runner commit: `a972d8f2fdef623685ad3f1c02ca0c033d140f85`. APK source: `v0.11.0-alpha.18` / `fb1e3c6ae2e4caca56778a54d8025dba696b53b8`.

- Bound user 0: 82 PASS, 7 OBSERVED, 0 FAIL, 0 BLOCKED.
- Bound user 17: 81 PASS, 4 OBSERVED, 0 FAIL, 4 expected permission BLOCKED.
- Cold restoration user 0: 73 PASS, 8 OBSERVED, 8 BLOCKED, 0 FAIL.
- Cold restoration user 17: 72 PASS, 5 OBSERVED, 12 BLOCKED, 0 FAIL.
- Both warm captures reused their prior processes. Both cold captures matched their own unbound baselines; insufficient evidence remains observed or blocked.
- Fixture/user/profile/published-body metadata checks passed. Production audits and permission/AppOps snapshots were identical before and after; no allowlisted additions were needed.
- Temporary entries, bodies, reports, expected-profile files and scratch state were removed for both users. Original foreground user 0 was restored by the runner.

Raw unbound fixture FAIL rows are expected synthetic-fixture mismatches and are not restoration failures. The original runner summary and bundle are preserved unchanged. The complete bundle includes supplemental provenance, audit assertions and console/build logs.

Limitations: users 0 and 17 only; Android switching rather than Cyclone bootstrap; denied and external-state rows are not promoted to PASS. Audit covers accessible stores and module files, not in-process binary proof. AppOps timestamps/counters are excluded from mode comparison. No platform verdict. No production APK or module was installed and no permissions were reset.
