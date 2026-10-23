# Real-application observation with the proven durable load record

The module boot gate is closed: the instrumented alpha.27 build installs,
loads (durable companion record, matched artifact digests), captures
cleanly for users 0 and 17 through the probe, and removes without leaving
any trace. The remaining open question before broader claims is whether a
real, neutral application's own view of the device matches the published
profile body it is bound to. This sprint answers that question using only
the already-validated machinery. It is not a build sprint; it is an
observation sprint against one bound application.

## Reference state

Prior cycle evidence, hashes and runner conventions live in
`docs/runtime-evidence/2026-10-23-020207-alpha27-root-companion-cycle/`.
The instrumented ZIP (SHA-256
`b86db15b3a02598cac22985a4e93a2195f61b102403ab132d95f6a2228cb894b`) and
its companion-record mechanism are validated artifacts. Reuse them
unchanged. Read that summary and the runner script before changing
anything.

## Part 1 - preparation

1. Confirm device, users (0 and 17), and ADB as before. Snapshot
   production APK, permissions, AppOps and binding stores first, identical
   to prior runs.
2. Install the exact instrumented ZIP via the established Magisk install
   flow. One reboot, verify installed immutable files match the ZIP and
   the durable record mechanism is intact.
3. Choose one neutral, already-installed application that displays device
   information through ordinary public APIs (a system-info or diagnostics
   app, or Android Settings itself). Do not use any app that requires
   login or network access during this run.
4. Create one temporary binding for that application under user 17
   through the existing publication flow, exactly as the diagnostics runs
   did. Record the canonical published body hash.

## Part 2 - observation

1. Switch to user 17 via the proven switch-user path.
2. Cold-launch the bound application (force-stop, then the standard
   `am start` invocation used for the probe, adapted to the chosen
   activity).
3. Capture the application's own rendered identity values through its UI
   (screenshot or UI dump, whichever the existing runner already
   supports), plus a bounded target-PID logcat of the launch.
4. A durable load record must exist for this capture, matching the
   installed artifact digest and the bound package/user. That record is
   the load proof; absence of the record is a stop-gate condition.
5. Compare the captured values field-by-field against the published
   body: Build fields, telephony fields, display density. Each field is
   recorded as MATCH, MISMATCH, or NOT_DISPLAYED. No field is promoted
   to VERIFIED by this run alone.
6. Repeat once more (second cold start, different PID) to confirm
   determinism of the displayed values across launches.

## Part 3 - teardown

1. Remove the temporary binding through the existing removal flow.
2. Remove the module via the standard remove marker and reboot. Verify
   module directories absent and no hook mappings in fresh processes,
   exactly as the prior cycle proved.
3. Final production/permission/AppOps/binding-store equality audit and
   cleanup checks, identical to prior cycles.
4. Archive everything (launch evidence, UI dumps, comparison table,
   durable records, command log) in a new dated directory under
   `docs/runtime-evidence` with a `summary.md`.

## Gates

- One fix-and-retest cycle only. If the bound application fails to
  launch or crashes before any field can be captured, archive the launch
  evidence and stop. If fields mismatch, that is a valid result, not a
  failure; record it and continue the cycle.
- The module, its companion-record mechanism and the runner's validated
  code paths are not modified in this sprint. OBSERVED stays OBSERVED,
  BLOCKED stays BLOCKED, no VERIFIED promotion, no platform verdict, no
  coverage claim.

## Limits

No production APK install/launch, no permission or AppOps writes, no user
creation/removal, no release/tag/version bump. The only authorized device
actions are the instrumented module reinstall, two reboots (boot stage
and removal stage), the two real-application captures through the proven
switch-user path, the binding create/remove pair, and the standard user
round trip. Preserve all prior evidence.
