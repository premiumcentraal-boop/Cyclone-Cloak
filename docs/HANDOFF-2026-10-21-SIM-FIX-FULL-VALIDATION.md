# SIM-state capture fix and full standalone validation

The hardening run localized the capture NullPointerException to the optional
SIM-state expectation expression in `RuntimeEnvelopeReport.collect` (line 142,
nested integer/null conditionals), before `envelope.telephony.simState` is
constructed. You deliberately stopped without fixing it. This authorizes the
fix and the captures the stop gate skipped.

## Part 1 - fix (local, probe-only)

1. Add a unit test that constructs a runtime envelope with a null/absent
   SIM-state expectation and reproduces the NPE against the current line-142
   expression.
2. Make the optional SIM-state mapping null-safe: absent expectation means the
   field is omitted or explicitly null per the existing schema - never an
   unboxing NPE.
3. Run the probe test suites (standard + diagnostics flavors) and the probe
   host tests until green.
4. Keep changes inside `android/probe/` sources and tests only. Leave
   everything uncommitted exactly as the previous run did; the commit policy
   from the hardening handoff (nothing staged, hardening-style patch in the
   evidence archive) applies unchanged.

## Part 2 - full standalone validation on device

Rebuild the diagnostics APK and repeat the full procedure from
docs/HANDOFF-2026-10-21-PROBE-ONLY-VALIDATION.md, now including the stages the
stop gate previously skipped:

1. Install; verify installed SHA-256 for users 0 and 17.
2. User 0 capture - must complete with a valid full report.
3. User 17 capture - must complete with a valid full report.
4. Digest determinism check (repeat capture, compare deterministic digests).
5. Schema conformance for both users' reports.
6. Uninstall, evidence archive in a new dated directory with summary.md.

The two filesystem rows that report PERMISSION_DENIED on /system/build.prop
and /proc/version are expected for a non-root probe. Record them as BLOCKED;
do not attempt to elevate, grant, or work around them, and never promote any
row to VERIFIED.

## Gates

- Any capture failure: the enriched envelope is the deliverable. Stop and
  report; no iteration beyond one fix-and-retest cycle for the SIM expression.
- Only the SIM-state expression may be touched in this run. Other failures are
  reported, not fixed.

## Limits

No module install or reboot. No production APK install or launch. No binding,
permission, AppOps or user-switch changes. No release, tag or version bump. No
platform verdict or coverage claim. Preserve all prior evidence.
