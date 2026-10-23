# Bind, lifecycle and reference-digest comparison - post-boot validation

After the candidate module install gate passes (see the install-gate handoff),
run the standard restoration lifecycle and collect a reference-vs-visible
digest comparison for the four filesystem surfaces. Both are standard
validation steps on this device, using the existing harness and policy. No
runtime edits, no release, no verdict promotion.

Device: `3B171FDJH0061G`, USB attached, unlocked. Preconditions: candidate
module installed and boot gate green; diagnostics probe APK available.

## Part A - Single-cycle lifecycle

1. Using the existing reversibility harness and the standard policy, run one
   complete cycle: baseline-cold (unbound) -> bound-cold -> post-removal-warm
   -> post-removal-cold -> restoration comparison, user 0 first, then user 17.
2. The four filesystem observation rows record digests/counts as OBSERVED. No
   row is promoted to VERIFIED in this part.
3. Confirm session audit: production unchanged, permissions/AppOps snapshots
   identical, cleanup checks pass.

Gate: cycle completes with restoration not worse than the 2026-10-21
alpha.28 baseline and zero unexplained production delta.

## Part B - Reference digest comparison

1. Via su (standard device administration), compute reference SHA-256 of
   `/system/build.prop`, `/proc/version`, `/proc/cpuinfo`, and a bounded
   `/proc/self/maps` snapshot from a plain shell context.
2. Compare with the Part A process-visible digests per user and capture.
3. Produce a three-state table: MATCH (equal digests), DIFFERS (both digests
   plus differing line count), UNAVAILABLE (reason). This is a consistency
   measurement only: do not edit code to force a match, do not mark any row
   VERIFIED.

## Evidence

One directory `docs/runtime-evidence/<timestamp>-lifecycle-and-digests/`:
lifecycle artifacts, digest table, `summary.md` with limits (one cycle,
users 0 and 17, digests are caller-visible views, maps volatile, no platform
verdict; multi-cycle soak and release remain follow-ups).

## Failure script

Any harness failure: stop, restore state using the documented backup, report
the failing phase. No further device actions.
