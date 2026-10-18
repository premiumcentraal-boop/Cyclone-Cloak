# Diagnostics lifecycle regression

Result: RESTORED_WITH_EXPECTED_LIMITATIONS
Cycles passed: 3/3

| Cycle | Result | User 0 restoration | Secondary restoration | Cleanup | Permission policy | Production |
|---|---|---|---|---|---|---|
| 1 | PASS | RESTORED_WITH_LIMITATIONS | RESTORED_WITH_LIMITATIONS | True | True | True |
| 2 | PASS | RESTORED_WITH_LIMITATIONS | RESTORED_WITH_LIMITATIONS | True | True | True |
| 3 | PASS | RESTORED_WITH_LIMITATIONS | RESTORED_WITH_LIMITATIONS | True | True | True |

Permission snapshot changed: False
Production snapshot changed: False
APK install policy: reuse_matching_install_missing_once
APK installation skipped: True
APK install attempts: 0
Dynamic or blocked rows remain limitations. No platform verdict.
