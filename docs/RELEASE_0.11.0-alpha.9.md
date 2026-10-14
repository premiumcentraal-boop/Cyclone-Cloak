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

Local suites, CI tests and signed release artifact verification passed before
publication. The released APK and bundled module were installed on the connected
Pixel 8, with one restart explicitly required by Root Doctor. Normal Health repair
reported publication `COMPLETED`; two Chrome bindings in Android users 14 and 16
reached ready, and all earlier index entries survived unchanged. Diagnostics show
every publication phase completed, with no publication failure or false lock error.
See the [dated proof handoff](https://github.com/premiumcentraal-boop/Cyclone-Cloak/blob/main/docs/HANDOFF-2026-10-14-ALPHA9-PUBLICATION-PROOF.md).

Both bindings were published from Main. A separate profile-side writer remains
unverified because Cyclone could not verify/open the receiving profile. This is
not Pixel 7 Pro verification. The state swap uses rename and rollback;
uninterrupted power-loss atomicity is not claimed.
