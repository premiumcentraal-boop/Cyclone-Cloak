# Cyclone Cloak 0.11.0-alpha.15 — local profile evidence harness

App and module packaging version code: **39**.

## Changes

The harness adds local evidence collection and transition comparison. A phased
PowerShell runner collects structured diagnostic-probe rows, compares before
switch / after switch / after probe cold restart, and writes a compact summary
and redacted evidence ZIP. Individual phases can be repeated with dependency
and artifact checks. Temporary probe overlays preserve production records and
the existing publication lock inode.

The default probe APK path and permission-free manifest remain available.
Diagnostics rows retain stable IDs; missing, blocked and device-dependent
observations do not silently become PASS. The documentation explains the four
unmapped changes and user-specific permission limits.

## Evidence and boundaries

- Small run: completed with zero critical failures (10.3 seconds).
- Full transition: 327 targets audited, zero critical failures (40.2 seconds).
- 70 expected field changes were observed during Android user 17 → 0 transition.
- Blocked rows are diagnostics limitations, not compatibility verdicts.
- The small run has 13 warning IDs / one blocked ID. The full run has 16 warning
  IDs / five blocked IDs. Both are **DEGRADED**, not complete coverage claims.
- The optional Advertising ID SDK was absent. User 17 lacked existing location
  and Bluetooth grants. No grants or production permissions were added.

The harness audits published targets and temporarily scopes the independent
probe. It does not measure normal app publication performance, observations
inside all 327 apps, or Cyclone bootstrap acknowledgement. Existing evidence
measured the installed alpha.14 module file, not this newly packaged artifact;
it does not automatically attest to alpha.15. No new device run is claimed.

A new debug-only invocation seam for the real companion flow is **deferred**:
the live reviewed ViewModel has no existing non-UI endpoint. Adding a mutation
endpoint or bypassing confirmation would exceed neutral observability. Existing
bounded run-journal events and per-run bundles remain available. Full per-phase
debug invocation and interruption/retry timing proof remain future work.

The harness does not claim external service compatibility. It does not add
production permissions. It does not change public APIs, storage schemas,
connector behavior, binding behavior, publication scripts, or runtime behavior.
Local compatibility evidence is not a platform verdict.

## Validation and upgrade

Local suites: **522 app, 12 module, 277 forge (48 harness cases), 33 schema,
six existing probe-runner tests**. Debug, probe and module packaging checks are
required before tagging; signed CI validates the release pair before publishing.
See the dated follow-up handoff for final gate results and asset verification.

Install the signed APK through the normal update flow, retaining app data.
Matching module packaging metadata is alpha.15/code 39; module behavior is
unchanged. Follow the module manager's own requirements if updating its package.
This release preparation performs no device installation or restart.

Assets: `app-release.apk`, `cyclone-cloak-0.11.0-alpha.15.zip`.
