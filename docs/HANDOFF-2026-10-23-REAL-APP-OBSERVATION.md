# Real-application runtime observation, one binding

The module boot gate is closed: the instrumented alpha.27 build installs,
loads (durable companion record), captures cleanly for users 0 and 17, and
removes without leaving any trace. The remaining gap before broader claims
is that every prior capture used the diagnostics probe. This run points the
same proven machinery at one real, neutral application and answers a single
question: does a real app's own Java view of the device match the published
profile body it is bound to?

## Reference state

The prior cycle's evidence, hashes and runner conventions are in
`docs/runtime-evidence/2026-10-23-020207-alpha27-root-companion-cycle/`.
Read that summary and the runner script before changing anything. The
instrumented ZIP, its hashes and the companion-record mechanism are the
validated artifacts; reuse them unchanged.

## Part 1 - preparation

1. Confirm device, users (0 and 17) and ADB as before. Snapshot production
   APK, permissions, AppOps and binding stores first, identical to prior
   runs.
2. Install the exact instrumented ZIP (hashes recorded previously) via the
   established Magisk install flow. One reboot, verify installed files
   match.
3. Choose one neutral, already-installed application that displays device
   information through ordinary public APIs (for example a system info or
   diagnostics app, or Android Settings itself). Do not use any app that
   requires login or network during this run.
4. Create one temporary binding for that application under user 17 through
   the existing publication flow, exactly as the diagnostics runs did.
   Record the canonical published body hash.

## Part 2 - observation

1. Switch to user 17 via the proven switch-user path.
2. Cold-launch the bound application (force-stop, then the standard am
   start invocation used for the probe, adapted to the chosen activity).
3. Capture the application's own rendered identity values through its UI
   (screenshot or UI dump, whichever the runner already supports) plus a
   bounded target-PID logcat of the launch.
4. Compare the captured values field-by-field against the published body:
   Build fields, telephony fields, display density. Each field is recorded
   as MATCH, MISMATCH, or NOT_DISPLAYED. No field is promoted to VERIFIED
   by this run alone.
5. Repeat once more (second cold start, different PID) to confirm
   determinism of the displayed values across launches.

## Part 3 - teardown

1. Remove the temporary binding through the existing removal flow.
2. Remove the module via the standard remove marker and reboot. Verify
   module directories absent and no hook mappings in fresh processes.
3. Final production/permission/AppOps/binding-store equality audit and
   cleanup checks, exactly as prior cycles.
4. Archive everything (launch evidence, UI dumps, comparison table, command
   log) in a new dated directory under docs/runtime-evidence with a
   summary.md.

## Gates

- One fix-and-retest cycle only. If the bound application fails to launch
  or crashes before any field can be captured, archive the launch evidence
  and stop. If fields mismatch, that is a valid result, not a failure;
  record it and continue the cycle.
- The module and its companion-record mechanism are not modified in this
  sprint. OBSERVED stays OBSERVED, BLOCKED stays BLOCKED, no VERIFIED
  promotion, no platform verdict, no coverage claim.

## Limits

No production APK install/launch, no permission or AppOps writes, no user
creation/removal, no release/tag/version bump. The only authorized device
actions are the instrumented module reinstall, two reboots (boot stage and
removal stage), the two real-app captures, the binding create/remove pair,
and the standard user round trip. Preserve all prior evidence.
