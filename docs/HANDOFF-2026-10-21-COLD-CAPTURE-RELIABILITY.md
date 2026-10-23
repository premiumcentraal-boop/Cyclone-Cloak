# Consecutive-capture reliability fix and full standalone validation

The SIM-state fix is validated: user 0 capture 1 completed, the filesystem
section passed schema conformance, and both SIM rows returned OBSERVED with no
expectation. The remaining blocker is the second consecutive capture:
`PROBE_REPORT_TIMEOUT_USER_0_CAPTURE_2` with no report and no enriched failure
envelope. The most likely cause is launch semantics: a second `am start` on an
already-running probe activity brings it to the foreground without recreating
it, so no capture runs and no report is produced. This handoff authorizes the
fix and the previously skipped captures.

## Part 1 - fixes (local, probe + runner only)

1. Cold-start between captures: in the validation runner flow, force-stop the
   probe package before every capture launch so each capture is a fresh
   process and a guaranteed activity recreation. This matches the eventual
   lifecycle semantics (cold start captures) and removes the foreground-only
   relaunch case.
2. Start/end markers: have the probe write a capture-start marker and a
   capture-end marker (report section or file, your choice) at capture begin
   and end. On a capture timeout, the runner must report which markers it saw,
   distinguishing "capture never started" from "started but no report". This
   turns any future timeout into an immediate diagnosis.
3. Unit/host tests: cover the force-stop-before-launch flow and the marker
   reporting on timeout.
4. Keep changes inside `android/probe/` and the runner/validation tooling
   only. Commit policy unchanged: nothing staged or committed; include a
   run-only patch in the evidence archive alongside the preexisting
   dirty-prerequisite provenance copies.

## Part 2 - full standalone validation on device

Rebuild the diagnostics APK and complete the full procedure:

1. Install; verify installed SHA-256 for users 0 and 17.
2. User 0 capture 1 (cold start) - valid full report.
3. User 0 capture 2 (cold start after force-stop) - valid full report.
4. User 17 captures (same cold-start policy) - valid full reports.
5. Digest determinism across the repeat captures. The two known
   PERMISSION_DENIED rows (build.prop, proc/version) stay BLOCKED and are
   explicitly not measurable for determinism; record them as such.
6. Schema conformance for all reports.
7. Uninstall, evidence archive in a new dated directory with summary.md.

## Gates

- One fix-and-retest cycle only. If a capture still times out, the marker
  diagnosis (started vs never started) is the deliverable. Stop and report.
- Only the launch flow and marker additions may be touched. Other failures are
  reported, not fixed.
- OBSERVED rows stay OBSERVED; BLOCKED rows stay BLOCKED; nothing is promoted
  to VERIFIED.

## Limits

No module install or reboot. No production APK install or launch. No binding,
permission, AppOps or user-switch changes. No release, tag or version bump. No
platform verdict, parity verdict or coverage claim. Preserve all prior
evidence.
