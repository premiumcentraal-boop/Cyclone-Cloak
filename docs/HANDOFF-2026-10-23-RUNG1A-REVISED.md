# Rung 1a revised: measure the alpha.28 candidate with the enriched probe

## Completion protocol

When the run reaches its stop-gate or completes, send exactly one message
to the manager thread containing only:
RUN DONE: <sprint name> | result: <COMPLETE|COMPLETE_WITH_LIMITATIONS|NOT_VALIDATED|STOPPED> | evidence: <path to summary.md>
No other content, questions, or thread references in that ping. Open
questions go under "Open questions" in summary.md.

## Manager pre-work already done (uncensored side)

The following is already complete before you start. Do not repeat it.

1. A fresh alpha.28 candidate module ZIP was built from current main
   (code 52) and installed on the connected device via the standard
   Magisk flow, with one authorized reboot. The installed immutable
   files were verified against the candidate ZIP by SHA-256.
2. The probe gained one new reader:
   `envelope.webview.instanceUa` calls the concrete
   `WebSettings.getUserAgentString` on a real WebView instance, so the
   `reality.webview.values.user_agent` PARTIAL row can now close
   against an expectation. The existing `envelope.webview.defaultUa`
   static reader is unchanged.
3. A schema-0.4 fixture carrying the Reality expectations needed by
   Rung 1a Part 2 (including `carrier.sim_state`) has been prepared
   and is available to the probe as fixture C.

## Your task

## Manager pre-work record (exact artifact hashes)

- Candidate ZIP SHA-256:
  8fd58b4ad0ea60ec06460b0ecaa0b9cea8e56467437975103d04392958a68795
  (2,022,032 bytes)
- Loaded ARM64 library SHA-256:
  d7dd105446a229f981e5cdd1aed862a13e3c7d04220f04fb08eadde175bb15c1
- Both hashes verified on-device after one reboot; user 0 and user 17
  RUNNING_UNLOCKED.

Run the Rung 1a measurement sprint against the loaded candidate:
1. Verify the installed module artifact digest against the candidate
   ZIP hash recorded in the manager's pre-work notes; record it.
2. Install the rebuilt diagnostics APK (with the new reader) for
   users 0 and 17; verify SHA-256 for both.
3. Execute the existing probe runner exactly as in prior cycles, with
   fixture A for user 0 and the new schema-0.4 fixture C for user 17.
   The prior fixture-B files remain as baselines; fixture B itself is
   superseded for this run.
4. For every row previously marked PARTIAL, capture the physical
   measurement with the loaded candidate. A PARTIAL row closes to
   APPLIED only if the measurement matches its expectation.
5. For user-17 rows that require a runtime permission not yet granted,
   use the existing diagnostics UI flow to request the minimum needed.
   Never grant via shell or AppOps commands.
6. Confirm determinism across the two cold captures for each user.
7. Update `docs/RUNTIME-COVERAGE.md` with the new measured counts and
   dated evidence pointer.

## Gates

- One fix-and-retest cycle maximum per defect class (build, permission,
  capture). Stop on the first gate failure of each class and archive.
- OBSERVED stays OBSERVED, BLOCKED stays BLOCKED. A PARTIAL row may be
  promoted to APPLIED only by a measured match under the loaded
  candidate. No VERIFIED promotion beyond what the coverage ledger
  already defines.
- No release, tag, version bump, commit or push. All prior evidence
  preserved.

## Limits

The only authorized device actions are the module install/reboot
already performed by the manager, the diagnostics probe
install/capture/uninstall cycle, the standard user round trip, and the
UI permission grants. No production APK install/launch, no
permission/AppOps shell writes, no user creation/removal, no code
changes in this sprint.
