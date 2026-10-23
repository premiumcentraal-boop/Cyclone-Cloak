# Standalone runner: adopt the proven lifecycle launch path for user 17

The last two standalone attempts localized one launch blocker and each closed
with a single, well-bounded stop gate. The evidence is consistent: a cold
`am start --user 17` against a background user exits 0 but the process never
appears, while the existing lifecycle runner's switch-based launch path
demonstrably works for user 17 on this device. Your next task ports that
proven path into the standalone validation flow. Nothing in this sprint is
new invention; it is a port of an already-proven mechanism.

## Reference implementation

`tools/run_probe_reversibility.py` contains the proven sequence:
`am switch-user 17`, a bounded settle loop reading `am get-current-user`,
`am force-stop --user 17 <package>`, and the exact `am start --user 17`
invocation with its extras (external_profile, no_permission_prompt,
expected_user, fixture, request_id). Read that file before changing anything.

## Part 1 - local runner change (runner code and tests only)

1. In `tools/run_standalone_probe_validation.py`, use the existing
   `activate_user` helper for every user-17 capture (it already mirrors the
   lifecycle settle loop). Before launching a user-17 capture, the runner
   must be switched to user 17 via that helper; after the user-17 captures
   it must switch back to user 0 the same way. Record each switch round
   trip in the launch evidence.
2. Match the lifecycle runner's per-user fixture selection exactly: fixture
   `a` for user 0, fixture `b` for user 17. Update
   `prepare_external_fixture` and the launch command so user-17 captures use
   fixture `b` and the matching synthetic asset. Keep the no-binding rule:
   fixtures are synthetic assets only, never published bodies.
3. Keep the existing marker and launch-evidence systems active so a timeout
   still yields the started/never-started diagnosis.
4. Add host tests covering: switch-wait-launch-read for user 17, the
   switch-back step, and the per-user fixture selection.
5. Scope: standalone runner and probe code only. Commit policy unchanged:
   nothing staged, run-only patch archived in the evidence bundle.

## Part 2 - device validation

1. Rebuild both probe flavors, install, and verify the installed SHA-256 for
   users 0 and 17.
2. User 0 smoke capture (cold), as before.
3. User 17 capture 1 via the switch-user path - must complete with visible
   STARTED and COMPLETED markers.
4. User 17 capture 2 via the same path - must complete cold.
5. Digest determinism for caller-observable rows across the two user-17
   captures. The two permission-denied rows stay BLOCKED/NOT_COMPARABLE.
6. Schema conformance for all reports; switch back to user 0; uninstall the
   diagnostics APK for both users; write the evidence archive and a
   summary.md into a new dated directory under docs/runtime-evidence.

## Gates

- One fix-and-retest cycle only. If a user-17 capture still times out with
  the switch path active, the launch evidence bundle is the deliverable;
  stop and report. Do not iterate.
- No package enable commands, no speculative launch variants, no user
  creation or removal, and no second launch mechanism: the lifecycle
  runner's sequence is the only authorized path for user 17.
- OBSERVED stays OBSERVED, BLOCKED stays BLOCKED; nothing is promoted to
  VERIFIED, and no coverage or parity claim is made.

## Limits

No production APK install or launch, no module install, no reboot, no
binding, permission or AppOps changes, no release or version bump. The only
user-switch activity is the standard round trip: switch to 17 for captures,
switch back to 0 afterward. Preserve all prior evidence.
