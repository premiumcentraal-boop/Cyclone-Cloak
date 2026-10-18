# Diagnostics binding reversibility

Result: FAIL
Duration: 23.29 seconds
Errors: ["PROBE_CRASHED"]

| Phase | User | Fixture | PASS | FAIL | OBSERVED | BLOCKED | MISSING | Reused |
|---|---:|---|---:|---:|---:|---:|---:|---|
| user-0-bound-cold | 0 | a | 82 | 0 | 7 | 0 | 0 | False |
| user-0-post-removal-warm-repeat-1 | 0 | a | 82 | 0 | 7 | 0 | 0 | True |
| user-0-post-removal-warm-repeat-2 | 0 | a | 82 | 0 | 7 | 0 | 0 | True |
| user-0-post-removal-warm-repeat-3 | 0 | a | 82 | 0 | 7 | 0 | 0 | True |

Unbound/warm-removal fixture FAIL rows are expected mismatches against synthetic fixtures, not restoration failures. Cold restoration compares actual-value hashes with the same user baseline. Dynamic external-state rows remain observations and denied rows remain blocked.

Restoration: null
Production unchanged: True
Permissions/grant flags/app-op modes unchanged: True (expected diagnostics read-record additions: [["READ_DEVICE_IDENTIFIERS", "deny"], ["USE_ICC_AUTH_WITH_DEVICE_IDENTIFIER", "default"]])

Limitations: only existing users 0 and 17; normal Android switching, not Cyclone bootstrap. Warm in-memory effects are observations. Locked-user private stores are unaudited. Permission app-op modes exclude volatile access times/counters. Installed module file hash is not in-process binary proof. No platform verdict.
