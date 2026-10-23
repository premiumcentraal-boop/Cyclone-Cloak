# Rung 1b: fixture C enrichment, WebView UA diagnostic closure, settle capture

## Completion protocol

When the run reaches its stop-gate or completes, send exactly one message
to the manager thread (01a1661e-f7c1-7862-a4fc-a251f2658a03) containing
only:
RUN DONE: Rung1b | result: <COMPLETE|COMPLETE_WITH_LIMITATIONS|NOT_VALIDATED|STOPPED> | evidence: <path to summary.md>
Do not include any other content, questions, or thread references in that
ping. Put any open question under an "Open questions" heading in
summary.md instead. Never read any other chat.

## Manager pre-work (already complete)

1. The probe now records full reader-failure causes for non-permission
   exceptions and classifies AndroidRuntimeException as
   RUNTIME_EXCEPTION:<SimpleName>, logging the stack to the retained
   diagnostic buffer. Both WebSettings UA readers were already present.
2. The instrumented alpha.28 candidate ZIP
   (SHA-256 8fd58b4ad0ea60ec06460b0ecaa0b9cea8e56467437975103d04392958a68795)
   is loaded and unchanged on the device from Rung 1a.
3. The rebuilt diagnostics APK with the reader-failure fix is built
   locally; install it as part of this sprint.

## Part 1 - fixture C enrichment

Fixture C currently carries only carrier.sim_state expectations.
Author the missing Reality expectations into fixture C so that Rung 1b
can close every remaining PARTIAL path that the device can honestly
measure:

- reality.compute.values.gpu: use the real GPU renderer of the host
  device (query via the existing reader once, then pin that value in
  the fixture; this is expected-value authoring, not host spoofing).
- reality.memory.values.free_storage_gib: use the host's real
  available-bytes reading converted to GiB, rounded to one decimal.
- reality.packages.values.installed: use the real host package list
  with real versions as captured by the existing reader at authoring
  time.
- reality.sensors.values.inventory: use the real host sensor inventory
  (type/name/delay/range/resolution) as captured at authoring time.
- reality.webview.values.provider, .version, .user_agent: use the
  real host WebView provider and its version, and the concrete instance
  UA string read at authoring time.

Record the source of every authored value (reader id, capture time,
host state) in a provenance section inside fixture C itself. No value
may be invented; every authored expectation must originate from a
real read of the authoring host at authoring time.

## Part 2 - user-17 measurement with enriched fixture C

1. Install the rebuilt diagnostics APK for users 0 and 17; verify
   SHA-256 for both.
2. Execute the existing probe runner as in Rung 1a: user 0 smoke with
   fixture A, then two cold user-17 captures with the enriched fixture
   C.
3. Every previously PARTIAL row must either measure PASS against its
   authored expectation or be recorded as NOT_DISPLAYED/MISSING with
   the exact reason. No row is promoted on a MISSING; a PROMOTION
   requires PASS.
4. Confirm determinism across the two cold user-17 captures for every
   row that measured PASS.

## Part 3 - WebView UA diagnostic closure

The manager's reader-failure fix now retains the AndroidRuntimeException
cause in the target-PID log buffer and classifies it as
RUNTIME_EXCEPTION:<SimpleName> in the report. Read that evidence from
the Rung 1a rerun captures and record the exception cause verbatim in
summary.md under "Open questions". Do not attempt a code fix in this
sprint; the goal is a diagnosed cause, not a workaround.

## Part 4 - settle capture

After the user-17 permission grants settle (all grant dialogs closed,
no pending dialog), perform one additional user-17 cold capture with
the enriched fixture C. Compare its legacy counts against capture 2 of
this run. If they match exactly, record "user-17 legacy determinism:
CONFIRMED post-grants" in summary.md. If they differ, record the
delta.

## Gates

- One fix-and-retest cycle maximum per defect class (build, fixture
  authoring, capture). Stop on the first gate failure of each class.
- OBSERVED stays OBSERVED, BLOCKED stays BLOCKED; a PARTIAL row is
  promoted to APPLIED only via a measured PASS under the loaded
  candidate. No VERIFIED promotion.
- No release, tag, version bump, commit or push. All prior evidence
  preserved.

## Limits

The loaded candidate module is unchanged from Rung 1a. Authorized
device actions: diagnostics probe install/capture/uninstall, standard
user round trip, and the existing UI permission grants already given.
No production APK install/launch, no shell permission or AppOps
writes, no user creation/removal.
