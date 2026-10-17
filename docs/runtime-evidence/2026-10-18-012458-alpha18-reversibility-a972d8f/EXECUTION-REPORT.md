# Diagnostics reversibility evidence

Result: RESTORED_WITH_EXPECTED_LIMITATIONS; eight captures in 44.51 seconds. No runner errors.
Runner: `a972d8f2fdef623685ad3f1c02ca0c033d140f85`. Source/APK pinned to `v0.11.0-alpha.18` / `fb1e3c6ae2e4caca56778a54d8025dba696b53b8`.

Production audits and permissions/AppOps snapshots are exactly identical before and after. No permission additions, removals, mode changes or resets. All temporary binding/body/private-file/scratch cleanup checks passed for both users. Fixture/user/hash validation passed in all captures. Both warm captures reused their previous process.

Cold restoration counts: user 0 {"PASS": 73, "OBSERVED": 8, "BLOCKED": 8}; user 17 {"BLOCKED": 13, "PASS": 71, "OBSERVED": 5}. No restoration failures. Raw unbound fixture FAIL rows are synthetic-fixture mismatches, not restoration failures. Blocked/unavailable/external rows are never promoted to restoration PASS.

38 focused runner tests passed. No source changes, production APK/module installs, reboot or new users. Original runner summary, bundle, snapshots and provenance remain intact; supplementary provenance corrects the original hard-coded build-command checkout path.

Limitations: only users 0 and 17; normal Android switching, not Cyclone bootstrap. Four user-17 bound rows remain permission-blocked. Other baseline permission/API blocks and external observations remain explicit. Audit covers accessible stores and module files, not in-process binary proof; AppOps volatile timestamps/counters are excluded. No platform verdict.
