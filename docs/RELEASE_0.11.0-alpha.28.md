# Cyclone Cloak 0.11.0-alpha.28

Prerelease, matching companion/module version code **52**.

## Included since alpha.27

- Connection/evidence-capture fixes and first-run reconciliation cleanup limited
  to locally managed profiles.
- Authenticated, evidence-bound local Integrity verdict import, explicit opt-in
  token exchange and an owner-operated backend with offline operational checks.
  No provider is trusted by default. A backend signature authenticates its assertion;
  it is not an offline Google signature or platform certification.
- Pure cohort separation analysis and deterministic, provenance-labelled instance
  realism using supported template combinations; unknown facts remain unknown.
- Scoped runtime envelope phase 2, including module/native callbacks and read-only
  diagnostics. Contract paths remain PARTIAL where coverage is incomplete.
- Local per-binding service-surface isolation states and conservative readiness
  gating; no automatic Google account or service changes.
- Restoration harness fixes: complete package/control comparisons, independently
  corroborated host-setting changes, semantic production auditing, stable grants
  and AppOps collection, checked-in external-state policies and cleanup evidence.

The harness fixes themselves do not change runtime, connector, publication,
profile storage or permissions. This release also includes the previously
committed runtime-envelope work above; it is not a diagnostics-only release.

## Measured candidate evidence and limitations

The focused cycle and three complete lifecycle cycles passed with explicit
limitations for existing users 0 and 17. Both users restored cold in every cycle.
Package lists (132 and 126 entries) matched, production state and permissions/
AppOps were unchanged, and cleanup passed after every cycle. Matching installed
diagnostics APKs were reused; no install, reboot or permission reset occurred
in the restoration follow-up.

Cold restoration comparisons (117 rows per user):

| Cycle | User 0 PASS/OBSERVED/BLOCKED/FAIL | User 17 PASS/OBSERVED/BLOCKED/FAIL |
|---|---|---|
| 1 | 102 / 7 / 8 / 0 | 100 / 4 / 13 / 0 |
| 2 | 102 / 7 / 8 / 0 | 101 / 4 / 12 / 0 |
| 3 | 102 / 7 / 8 / 0 | 101 / 4 / 12 / 0 |

These are baseline-restoration comparisons, not a count of verified runtime hooks.
Warm post-removal captures retained in-memory values until cold restart.
Permission/API blocks and dynamic external-state observations remain limitations.
Historical package deltas cannot be reconstructed from hashes alone; their cause
remains unattributed. The historical unrelated control changed with host state;
the writer was not identified. The historical production delta was index JSON
serialization with equal semantic state. Previous failed attempts are preserved.

Evidence was measured on candidate commit
`1c9ce900930b7c52d3d7130b8ca8a741e183b73a` with alpha.27/code51 metadata and exact
artifact hashes. It must not be equated to the published alpha.27 binary or the
new alpha.28 package. Physical validation of the signed alpha.28 artifact remains
pending. Normal Android user switching was tested, not Cyclone bootstrap.

No platform verdict, certification, permanent trust, hardware equivalence or
broader device compatibility is claimed. No authenticated real Integrity verdict
was requested in this follow-up. Legacy probe lint findings remain separately
documented; the standard signed workflow is the release gate.

## Local checks and evidence

1,119 Android tests passed (app 781, module 80, Play diagnostics 30, each probe
flavor 114), plus 1,284 forge/schema tests and 7 host-probe tests. Debug builds,
module packaging, generated-table checks and offline synthetic backend self-test
passed. No compiler warnings were observed. Signed builds and metadata/embedding
checks run in the existing signed-validation and release workflows.

[Handoff and exact commands](HANDOFF-2026-10-21-RESTORATION-HARNESS-FOLLOW-UP.md),
[evidence summary](runtime-evidence/2026-10-20-235457-restoration-follow-up/summary.md).
The attached `restoration-follow-up-evidence.zip` has SHA-256:

`eb4f075976b7235714aaf55eeeb136389c4f3d67f89b2fd886ae51d17f7618f5`

## Upgrade

Update the companion with the signed `app-release.apk` and the module through the
existing module update flow. Existing profiles are not migrated; schema
0.2/0.3/0.4 imports remain supported. No data clearing or permission reset is
required. Runtime module changes require the normal module activation/restart
process; this release run performs no device actions.
