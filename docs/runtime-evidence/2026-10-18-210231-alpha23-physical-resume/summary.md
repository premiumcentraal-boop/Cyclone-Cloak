# Alpha.23 physical validation continuation

Result: FAIL — stopped in cycle 1, user-0-bound-cold.
Companion/module: v0.11.0-alpha.23 / code 47; source 0d028a947b37013e0ad89a98d1455a79c33bfd3d.
Both users were RUNNING_UNLOCKED at the fresh baseline. No additional installation or reboot.

| Bound user 0 section | PASS | FAIL | BLOCKED | OBSERVED |
|---|---:|---:|---:|---:|
| linked_parity | 11 | 2 | 0 | 0 |
| legacy rows | 82 | 0 | 0 | 7 |

Exact failures:
- reality.panel.values.refresh_hz / Display.getMode refresh: expected 90 Hz, actual 60 Hz (values confirmed against recorded SHA-256 hashes).
- linked.supportedModes: expected true (mode tuple present), actual false. Aggregate modes digest is preserved in the failed-linked report.

Fixture A/profile hash and temporary published-body hash both: e476b02fd308c62cf207f4fe60e97085bbd14ca6293b405c2cedb996c9555f53
Other 11 linked rows passed: width, height, density, MCC, MNC, slots, language, country, timezone, SIM operator and active modem count.
Permission/AppOps delta: none. Production delta: none.
Cleanup passed for users 0 and 17: probe entries, body directories, expected-profile/report files and scratch removed.
Cycles 2 and 3, user-17 bound capture, cold restoration comparisons: NOT RUN after first bound failure.
Unbound baseline fixture mismatches are control observations; they are not classified as runtime regressions.
No production source, connector logic, bindings, permissions or release metadata edited. No commit, push or new release.
Local parity is an implemented capability, not a platform verdict.

Evidence: result.json; lifecycle/aggregate-summary.json; lifecycle/cycle-001/failed-linked-user-0-bound-cold.json; failed-user-0-bound-cold.json; before/after audit and permission files; cleanup-proof.json; lifecycle-console.log.
APK/module verification and initial install/reboot records remain in the previous stopped evidence directory; alpha23 asset verification and diagnostics APK provenance are copied here.
