# Diagnostics binding reversibility

Result: FAIL
Duration: 81.89 seconds
Errors: ["COLD_RESTORATION_FAILED"]

| Phase | User | Fixture | PASS | FAIL | OBSERVED | BLOCKED | MISSING | Reused |
|---|---:|---|---:|---:|---:|---:|---:|---|
| user-0-baseline-cold | 0 | external | 32 | 37 | 12 | 8 | 0 | False |
| user-17-baseline-cold | 17 | external | 22 | 46 | 9 | 12 | 0 | False |
| user-0-bound-cold | 0 | external | 76 | 0 | 11 | 2 | 0 | False |
| user-17-bound-cold | 17 | external | 75 | 0 | 8 | 6 | 0 | False |
| user-0-post-removal-warm | 0 | external | 76 | 0 | 11 | 2 | 0 | True |
| user-17-post-removal-warm | 17 | external | 75 | 0 | 8 | 6 | 0 | True |
| user-0-post-removal-cold | 0 | external | 32 | 37 | 12 | 8 | 0 | False |
| user-17-post-removal-cold | 17 | external | 22 | 46 | 9 | 12 | 0 | False |

Unbound/warm-removal fixture FAIL rows are expected mismatches against synthetic fixtures, not restoration failures. Cold restoration compares actual-value hashes with the same user baseline. Dynamic external-state rows remain observations and denied rows remain blocked.

Restoration: {"0": "FAIL", "17": "FAIL"}
Production unchanged: True
Permissions/grant flags/app-op modes unchanged: True (expected diagnostics read-record additions: [["READ_DEVICE_IDENTIFIERS", "deny"], ["USE_ICC_AUTH_WITH_DEVICE_IDENTIFIER", "default"]])

Limitations: only existing users 0 and 17; normal Android switching, not Cyclone bootstrap. Warm in-memory effects are observations. Locked-user private stores are unaudited. Permission app-op modes exclude volatile access times/counters. Installed module file hash is not in-process binary proof. No platform verdict.
