# Cyclone Cloak v0.11.0-alpha.9

This prerelease adds bounded local developer diagnostics and repairs publication
locking on Android. App and module versions agree at `0.11.0-alpha.9`, code **33**.

- Health now offers a plain developer diagnostics list, filters, redacted copy and
  local ZIP export. Events describe actions, transitions, reason codes and timing;
  profile contents and secret values are excluded.
- Android mksh does not inherit the previous high-numbered lock descriptor into
  Toybox flock. Publication now locks an inherited descriptor for the entire
  publisher-share merge and state swap, without unlinking the lock file.
- Publication reports fixed precheck, staging, lock, merge, swap, recovery and
  completion phases. Genuine contention is distinguished from lock setup failure.
- Existing rollback and interrupted-publication recovery preserve complete trees.

Install the signed APK as an update. Health → Check & repair can queue the matching
bundled module; restart only when it reports that a restart is required, then check
again. Existing profiles and bindings remain compatible; no data reset is needed.

No profile-schema, identity-semantics, connector-protocol or storage-layout change.
No new permissions, networking or analytics. Native runtime behavior is unchanged;
the module package has matching version metadata. This is local compatibility
evidence, not a platform verdict. Older runtime evidence does not automatically
verify this artifact or the native property callback probe row.

Local suites and signed release artifact verification gate publication. Normal-app
device publication and two-profile isolation proof are pending installation of the
released artifacts and will be recorded in a separate dated handoff. The state swap
uses rename and rollback; uninterrupted power-loss atomicity is not claimed.
