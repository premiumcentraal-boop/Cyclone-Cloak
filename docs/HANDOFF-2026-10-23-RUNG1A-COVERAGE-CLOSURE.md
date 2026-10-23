# Rung 1a: coverage-gap closure on the physical device

## Completion protocol

When this run reaches its stop gate or completes, send exactly one message
to the manager thread containing only:
RUN DONE: <sprint name> | result: <COMPLETE|COMPLETE_WITH_LIMITATIONS|NOT_VALIDATED|STOPPED> | evidence: <path to summary.md>
No other content, no thread references, no questions in that ping. Any
open question goes into the summary under "Open questions".

## Objective

Convert the remaining PARTIAL contract paths in RUNTIME-COVERAGE.md and
the user-17 SIM/operator rows that are currently NOT_COMPARABLE into
either measured expectations or documented permanent blocks, using only
the existing diagnostics probe and existing permission flows on the
connected device. No production code changes in this sprint. This is a
measurement and documentation sprint.

## Part 1 - PARTIAL path closure

1. Read `docs/RUNTIME-COVERAGE.md` and list every row marked PARTIAL.
2. For each PARTIAL row, verify whether the alpha.28 candidate code path
   for that row is already exercised by the existing probe. If it is,
   capture one physical measurement per row using the existing probe
   runner on users 0 and 17, exactly as previous cycles did.
3. If a PARTIAL row's code path is not yet exercised by the probe, leave
   it PARTIAL and record the reason under "Open questions" in the
   summary. Do not write new probe code in this sprint.
4. Update `docs/RUNTIME-COVERAGE.md` with the new measured counts and a
   dated pointer to the evidence directory. Nothing else in the doc
   changes.

## Part 2 - user-17 permission and SIM closure

1. Use the existing diagnostics flow to request the minimum runtime
   permissions needed for the current BLOCKED or NOT_COMPARABLE rows on
   user 17. Grant them through the normal UI flow only, never via shell
   or AppOps shell commands.
2. Re-run the probe's bound capture for user 17 (fixture B). Each
   previously blocked row must either now return a real measured value
   with an expectation, or be documented as structurally impossible with
   the exact reason.
3. Confirm determinism across the two cold captures for user 17 as
   before.

## Part 3 - archive and audit

1. Snapshot production APK, permissions, AppOps, binding stores before
   and after, and verify byte-equality as in prior cycles.
2. Remove the diagnostics APK globally after capture. Verify user-0 and
   user-17 private probe state cleanup.
3. Archive launch evidence, reports, digests and command logs in a new
   dated directory under `docs/runtime-evidence/` with a `summary.md`
   and an "Open questions" section.

## Gates

- One fix-and-retest cycle maximum. If a measurement fails, archive the
  evidence and stop.
- No release, tag, version bump, or code change. OBSERVED stays
  OBSERVED, BLOCKED stays BLOCKED; no VERIFIED promotion of any row that
  was previously PARTIAL or BLOCKED without the manager's explicit
  approval.
- Nothing is pushed to main.

## Limits

No production APK install/launch, no new permissions beyond the existing
diagnostics app's own manifest, no user creation/removal, no module
changes. The only authorized device actions are the standard probe
install/capture/uninstall cycle, the user round trip, and the explicit
UI permission grants listed above. Preserve all prior evidence.
