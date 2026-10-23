# User 17 capture launch reliability - final standalone-probe blocker

The cold-capture run closed the user-0 track: captures 1 and 2 completed cold
with different PIDs, visible STARTED/COMPLETED markers, 89 unique legacy rows
and valid filesystem schema. The marker system also produced an immediate
diagnosis on the first failure: user 17 capture 1 is
CAPTURE_NEVER_STARTED, with no process and no visible markers. Your own
handoff correctly preserves the ambiguity that markers may be unreadable to
the caller rather than absent. This run resolves that ambiguity and finishes
the user-17 track.

## Part 1 - launch diagnostics and visibility (local, probe + runner only)

1. Marker/report visibility: ensure the start/end markers and the completed
   report are published where the non-root caller can read them for EVERY
   user (user-scoped external storage path per user). If the current path is
   user-0-only, make it per-user. Distinguish "marker absent" from "marker
   present but unreadable" in the timeout diagnosis output.
2. Launch evidence bundle: the runner must record, for every launch:
   - the verbatim `am start` stdout/stderr and exit code,
   - `pidof` before and after the launch,
   - `pm path --user 17` and package enabled/stopped state for the target user,
   - a bounded logcat window around the launch filtered to the package.
3. Host/unit tests for the per-user path logic and the diagnosis output.
4. Scope unchanged: `android/probe/` and the validation runner only. Commit
   policy unchanged: nothing staged; run-only patch in the evidence archive.

## Part 2 - device validation

1. Rebuild, install, verify hashes for users 0 and 17.
2. One user-0 smoke capture only (already proven; do not re-litigate it).
3. User 17 capture 1 with the full launch evidence bundle. If the evidence
   identifies a launch-path defect fixable inside probe/runner scope (wrong
   user flag, install-existing gap, disabled package, user-scoped path),
   apply the one fix and retry once.
4. On success: user 17 capture 2 and the digest determinism comparison for
   caller-observable rows. The two PERMISSION_DENIED rows stay BLOCKED and
   NOT_COMPARABLE.
5. Schema conformance for all reports; uninstall; evidence archive in a new
   dated directory with summary.md.

## Gates

- One fix-and-retest cycle for user 17 maximum. If it still fails, the launch
  evidence bundle (am start output, process state, logcat window, path
  visibility check) is the deliverable. Stop and report.
- Do not touch user-0 code paths beyond the shared per-user path fix.
- OBSERVED stays OBSERVED, BLOCKED stays BLOCKED, nothing promoted to
  VERIFIED.

## Limits

No module install or reboot. No production APK install or launch. No binding,
permission, AppOps or user-switch changes. No release, tag or version bump. No
platform verdict, parity verdict or coverage claim. Preserve all prior
evidence.
