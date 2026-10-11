# Health Doctor v2 (historical RC1 design)

Superseded by Readiness Doctor v3 in the current development candidate. See
[ARCHITECTURE.md](ARCHITECTURE.md#readiness-doctor-v3) and
[CURRENT-STATE.md](CURRENT-STATE.md) for current checks and redaction.
The description below records RC1 behavior.

The Health tab projects existing facts into a single **Ready / Degraded / Failed**
verdict. It does not change synchronization, reconciliation, root publication,
module reads, profile schema or identity storage. No checks run automatically
as a result of opening or copying diagnostics.

## Checks and verdict

- Root/Zygisk/publication uses the combined `RootDoctorResult`. A combined
  result does not provide enough evidence to invent separate successful checks.
- Installed release names are compared after removing the module's `v` prefix.
  App and module version codes have separate numbering and are not compared.
  An absent/unrecognized version warns; a mismatch fails. A queued module repair
  leaves the installed/loaded version unconfirmed, even if its queued release
  matches the app. Matching names do not prove ART hook installation.
- Connector approval, binding scopes and startup registration are checked from
  the existing sync report and provider state. Refresh/check-in-progress warns.
- Each enabled binding is checked against the available Cyclone snapshot:
  profile existence/readiness, Android-user correspondence, app-list membership,
  locally available configuration, reported issues and existing health state.
  Mirrored configurations remain owned by Main; the UI does not require a local
  identity file for them or introduce a new mirror/storage policy.
- Failed applicable checks make the verdict Failed; otherwise warnings make it
  Degraded; otherwise it is Ready. Disabled bindings have a warning row but are
  excluded from the verdict. No enabled bindings adds a warning and prevents Ready.

These are last-reported setup/binding facts, not proof that every inspection hook
works. The [coverage matrix](HEALTH-COVERAGE.md) remains separate. Snapshot
absence means profile availability is unknown, not that every profile was deleted.

Existing Check & repair, Open Magisk, module-download and reconnect actions keep
their existing handlers. Check & repair may queue a module repair and publish
state; copying only writes a report to the local clipboard. Reboot-dependent
tests remain [deferred](DEFERRED-DEVICE-CHECKS.md).

## Redacted copy contract

`HealthDiagnostics.redactedReport()` builds `cyclone-cloak.health-doctor/2` JSON
from a positive allowlist. The copied report includes only:

- Verdict, scope, enabled/disabled counts.
- Validated project release names, or null for unconfirmed values.
- Enumerated check kind, pass/warn/fail state, anonymous binding ordinal,
  whether the check affects readiness, and fixed reason codes/messages.

It never serializes a state object, identity JSON, private display context,
Cyclone/profile/identity IDs or labels, package names, Android user IDs, seeds,
device fingerprints, serials, Android/advertising IDs, MAC addresses, raw root
output, exception messages, connector messages or provider error text. Unknown
input strings map to fixed explanations rather than being copied verbatim.
The clipboard label is fixed and copying requires pressing Copy diagnostics.
The report is transient diagnostic output, not a new persisted storage format.

## Local verification

- Ten `HealthDiagnosticsTest` JVM tests pass against actual compiled app root/
  connector types. Tests cover every root code, release matching and unknown/
  queued versions, disabled/missing/unknown bindings, scopes/provider/refresh,
  failure precedence, and adversarial private-string injection plus exact export
  key allowlists.
- Three `HealthDoctorCardScreenTest` cases check a single verdict, copy-button
  output, reasons and unconfirmed queued versions. `ScreensTest.health` now
  expects the v2 screen. These rendering tests require Robolectric; the normal
  `:app:testDebugUnitTest` target remains blocked by its uncached framework JAR.
- App/module builds and 138 Python tests pass. No phone test, module install or
  reboot was performed in this UI sprint. See [CURRENT-STATE.md](CURRENT-STATE.md)
  for the latest build details and dependency limitation.
