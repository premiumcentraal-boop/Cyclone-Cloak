# Diagnostics observation reader stabilization and re-validation

You previously ran the standalone probe validation (see
docs/HANDOFF-2026-10-21-PROBE-ONLY-VALIDATION.md) and reported INCOMPLETE with
five failed captures: NullPointerException twice on user 0, then
PROBE_REPORT_TIMEOUT on the consecutive user 0 attempt and on user 17. The
failure envelope carried UID/package/request/profile digest but no stack and no
failing row ID. You asked for a separately authorized diagnostics-code
investigation: this is that authorization.

## Scope

Probe-side only. Every change stays inside `android/probe/` sources and tests
plus the new evidence directory. The module, production app, connector, binding
storage, release metadata and all pre-existing uncommitted files are untouched
by you (leave them exactly as they are; commit only your own changes).

## Part 1 - reproduce and harden (local, no device)

1. Add unit tests covering every filesystem-observation reader added by the
   observation work for null, empty and absent inputs. The NPE must become a
   failing test first, then a fixed test.
2. Make each reader null-safe. No reader may throw on missing or malformed
   data; it records an explicit row-level failure reason instead.
3. Add a per-reader timeout (a few seconds each, well under the 60-second
   report budget). A slow or blocked read produces a timed-out row and the
   report still completes. A hang in one reader can never again prevent the
   whole report from arriving.
4. Enrich the structured failure envelope: on any capture failure include
   exception class, message, stack trace and the failing row ID. Keep the
   existing fields unchanged.
5. Run the standard local suites for the probe flavors and fix only probe code
   until green.

## Part 2 - re-run the standalone validation on device

Repeat the identical procedure from
docs/HANDOFF-2026-10-21-PROBE-ONLY-VALIDATION.md, using your freshly built
probe APK: install, user 0 capture, user 17 capture, schema conformance,
filesystem counts, digest determinism check, uninstall, evidence archive in a
new dated directory with summary.md. Reuse the exact commands from that
handoff; do not improvise new ones.

## Gates

- If any capture still fails, the new enriched envelope (stack + row ID) is the
  deliverable. Stop, report, and make no further fixes in this run.
- A single green run is sufficient for this sprint; repeated/cold runs are a
  later sprint.
- OBSERVED rows stay OBSERVED. Nothing is promoted to VERIFIED.

## Limits

No module install or reboot. No production APK install or launch. No binding or
permission changes. No release, tag or version bump. No platform verdict or
coverage claim. Preserve all prior evidence; today's five failed attempts
remain the baseline this sprint is measured against.
