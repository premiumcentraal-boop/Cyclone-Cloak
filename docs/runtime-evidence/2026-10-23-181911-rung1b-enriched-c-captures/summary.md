# Rung 1b — enriched fixture C capture sequence

Result: **NOT_VALIDATED**. All four requested captures completed, hashes/markers were consistent, and settled-permission repeats were deterministic. Final full profile coherence validation found two contradictions introduced by combining real Android-16 host facts with the existing Android-13 synthetic fixture. These are preserved as findings, not repaired by inventing facts. **No PARTIAL-to-APPLIED promotion** was made from this inconsistent fixture. No VERIFIED promotion or platform verdict.

## Provenance and build

- Repository commit: d4f0a5cb5f4d70bacaeb0b8ecdff184eff154150.
- Existing authoring export reused exactly from the prior stopped run; no authoring relaunch. captured_at_ms **1792771610372**, captured_sdk_int **36**, unbound Android user **17**. Exact raw source retained as authoring.json; per-value provenance is in fixture C's existing open meta.authoring_provenance field.
- Source fixture C SHA-256: **5de89420c21c195defe179a14603c8655d0a2bcc79e4047ca7b39cba795aaeb6**.
- New diagnostics APK SHA-256: **087fcfa3afaa4d415ab327d314ab348afd81214761afa0b5034b1d41008fd259**. Users 0 and 17 each had this exact installed APK hash; see diagnostics-installed.json. APK contains the exact enriched asset bytes.
- Loaded candidate ARM64 remains **d7dd105446a229f981e5cdd1aed862a13e3c7d04220f04fb08eadde175bb15c1**. All installed immutable files match the previous validated candidate manifest associated with ZIP **8fd58b4ad0ea60ec06460b0ecaa0b9cea8e56467437975103d04392958a68795**. Four PID/package/user-matching durable records are retained.
- The current local ZIP hash is **0a69abcfb7943cd2d43b5e6e4ececc8b8c356e99768dda431b83d9bcf1a43d18**, which differs from the original candidate ZIP. It was not installed or used; the previously validated installed files were checked directly. Module code and packaging were not changed by this run. The mutable durable log changed through ordinary recording; immutable file hashes did not.

Fixture enrichment uses real Mali-G715, 66.9 GiB rounded from 66.90276718139648, five caller-visible package/version entries, all 38 sensor descriptors, and WebView provider com.google.android.webview version 155.0.8059.43. The reality WebView UA field is absent; no fabricated UA. Existing carrier.sim_state='ready' remains unchanged. Source/proposed assets and schemas are preserved. The relaxed sensor schema passes with no errors.

## Captures

| User / fixture | Capture | PID | Legacy PASS / OBSERVED / BLOCKED | Envelope PASS / OBSERVED / MISSING |
| --- | --- | --- | --- | --- |
| 0 / A | smoke cold | 2376 | 81 / 4 / 4 | 1 / 14 / 1 |
| 17 / C (external label) | cold 1, grants settling | 4123 | 75 / 8 / 6 | 7 / 3 / 6 |
| 17 / C (external label) | cold 2 | 7420 | 76 / 11 / 2 | 7 / 3 / 6 |
| 17 / C (external label) | settled cold 3 | 7978 | 76 / 11 / 2 | 7 / 3 / 6 |

No legacy or envelope FAIL rows. Every report has the expected Android user, unique cold PID, request-bound STARTED/COMPLETED markers, 89 unique legacy rows and valid filesystem-observation schema. Fixture C is honestly labelled external, never A/B. Its report hash is the raw packaged asset hash above; the canonical published body hash is **dcab867f2a35c2d4729fb70ddc1c9ea97b3bf96ffb557a904b4f63fbbf91c9dd** in all three C captures. A's report/source hash and canonical published hash also match independently. Raw asset bytes and canonical publication bytes intentionally have different digests; neither is conflated with the other.

## Measured outcomes, not promotions

| Reality path | Envelope outcome | Reason |
| --- | --- | --- |
| reality.compute.values.gpu | PASS | HASH_MATCH_ONLY |
| reality.memory.values.free_storage_gib | MISSING | PROFILE_SURFACE_UNAVAILABLE |
| reality.packages.values.installed | PASS | HASH_MATCH_ONLY |
| reality.sensors.values.inventory | MISSING | PROFILE_SURFACE_UNAVAILABLE |
| reality.webview.values.provider | PASS | HASH_MATCH_ONLY |
| reality.webview.values.version | PASS | HASH_MATCH_ONLY |
| reality.webview.values.user_agent | MISSING | RuntimeException |
| reality.carrier.values.sim_state | PASS | HASH_MATCH_ONLY |

The seven envelope PASS rows (unchanged in all three C captures) are GPU renderer, package versions, WebView provider, WebView version, real display size, SIM state, and slot-0 SIM state. Every new observed match remains an evidence-level hash comparison. Sensor/free-storage expectations are present in C, but the unchanged loaded module exposes PROFILE_SURFACE_UNAVAILABLE; no replacement, clamping or runtime fix was attempted. Other envelope missing rows are camera characteristics, default UA and DRM security level; exact outcomes remain in the reports. Settled legacy BLOCKED rows are retained with exact per-row reasons.

**user-17 legacy determinism: CONFIRMED post-grants** — counts for captures 2 and 3 match exactly, The two captures have 110 common PASS row states, including 34 with retained comparable actual digests; all 34 digests match. Legacy raw values/digests are not retained by redaction, so no direct raw-value comparison is claimed for them. Capture 1 versus 2 has 108 common PASS row states, with 33 comparable actual digests and no differing digest; two additional rows pass after grants settle. The first capture is not presented as a same-permission-state comparison. OBSERVED/BLOCKED rows were never promoted.

## Unresolved fixture coherence gate

The relaxed schema passes, but the existing forge coherence validator returns:

1. **REALITY_LINK — reality.packages**: host android package version_name **16** conflicts with fixture C device.version_release **13**.
2. **REALITY_WEBVIEW — reality.webview**: observed WebView version **155.0.8059.43** does not match the preserved base UA Chrome/**122.0.5640.47**. The reality UA is intentionally absent because its real authoring read failed.

See fixture-coherence-findings.json. This was detected by the final full coherence check after the captures. Neither the base profile nor the real authoring facts were silently rewritten. A fixture correction requires an explicit decision about coherent base values and an actually readable UA, not bypassing validation. Runtime-readiness source tables remain unchanged and no new global APPLIED status is claimed.

## Cleanup and preservation

Temporary diagnostics overlay entries and bodies removed through the existing kernel-lock/raw-byte-CAS cleanup path. Production before/after snapshots match exactly, covering production APK SHA-256, audited install/runtime permission grants, stable AppOps, published production files/shares and accessible binding/index stores. No production binding mutation persists.

Diagnostics APK uninstalled globally; package paths absent for users 0/17 and CE/DE/external diagnostic directories absent. Scratch/body cleanup and restored publication bytes are preserved in overlay-remove.json, cleanup.json and audit-comparison.json. Current user returned to **0**. Loaded module remains installed and unchanged; no module install/removal/reboot occurred. Normal location/nearby Android UI grant actions were diagnostics-only; screenshots and permission states are retained. No shell grants/AppOps writes, production APK launch/install, user creation/removal, commit, push, tag, release or version bump.

## Checks and changed files

- Diagnostics probe tests: **145 passed**, 0 failures/errors/skips. Diagnostics APK build passed; no compiler warning/error lines were reported.
- Updated fixture Reality JSON schema validation: passed.
- Full profile coherence validation: **2 errors**, above. This blocks validation/promotion claims.
- git diff --check: pre-existing ProbeReportPolicy.java:90 new blank line at EOF; exit 2, preserved. Cached diff check passed. Existing LF/CRLF warnings retained.
- Full app/module/forge suites were not rerun in this scoped device measurement run.

This run changes **android/probe/src/main/assets/profile-c.json**, this new evidence directory, and an evidence pointer in docs/RUNTIME-COVERAGE.md. Existing manager changes (sensor schema, AndroidRuntimeException logging, authoring collector/export) are preserved. No module source changes. Exact build/capture/cleanup commands and outputs are in commands.jsonl, run_ops.py and diagnostics-build.log.

## Open questions

**Concrete instance UA unavailable; authoring capture failed with NullPointerException.** That is the exact retained authoring error class, not a diagnosed cause. The current C instance-UA row reports **RuntimeException**. No RUNTIME_EXCEPTION-tagged stack/cause was found in this run's retained target-PID logs, even with bounded root reads of all log buffers. There is therefore **no genuine exception-cause text to quote verbatim**; manufacturing one would violate the evidence rule. Target-PID logs for every capture are retained. No WebView UA fix was attempted.

The two fixture coherence contradictions remain open. Sensor/free-storage runtime availability is also unresolved under this unchanged measured candidate. No platform verdict, certification, permanent trust or broader compatibility claim is made.
