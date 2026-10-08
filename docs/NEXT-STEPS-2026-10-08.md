# Cyclone Cloak - current checkpoint and next build plan (2026-10-08)

This document supersedes the older ad-hoc publish debugging notes. The current
branch is `main`, tag `v0.5.0-alpha.4`, commit `fb69e80`.

## 1. Current checkpoint

### Working and implemented
- Cyclone connector shell is live and the companion app is the connector side.
- Profile Forge (`forge/`) generates coherent v0.2 device profiles, with
  deterministic identifier derivation and pytest coverage.
- Companion app supports import, binding, enable/disable, and removal of
  per-profile app bindings.
- The app already writes the `cloakProfileId` through the Cyclone config
  contract exactly as the Cyclone-side handoff expects.
- Root Doctor now checks Magisk access, module identity/version/ABI, Zygisk,
  and profile-state publishing in one flow. It can schedule the bundled module
  and explains when a reboot is needed.
- Publish uses `su --mount-master`, a validated staging tree, and a root-only
  atomic swap to `/data/adb/cyclone_cloak/state-v1`.
- The Zygisk module reads the published state through the root companion and
  applies the device profile to the scoped app.
- Release workflow builds and signs the APK and attaches the module ZIP.

### Not yet proven on device
- The exact new alpha.4 publish path has not been re-tested on the Pixel 8
  after the older handoff was written.
- The Zygisk module has not yet been proven loading into a scoped app with
  `CloakModule: cloaking` in logcat.
- The Cyclone UI has not yet been re-checked after the latest Cyclone release
  to confirm the `Rooted` pill appears from the config written by Cloak.
- Multi-user behavior is still only code-tested, not physically exercised.
- Identifier coverage is still partial: Build/props/telephony/display are
  applied, while many identifiers and locale fields remain pending.

## 2. Next build plan

### Sprint 6 - prove the rooted route
Goal: turn the current alpha.4 from code-complete into device-proven.

1. Install the current alpha.4 APK and let Root Doctor run.
2. Confirm Magisk shows the matching module as installed and enabled.
3. Publish a binding and verify `/data/adb/cyclone_cloak/state-v1/index.json`
   exists with the expected key and profile JSON.
4. Force-stop and relaunch a bound app.
5. Confirm `CloakModule: cloaking` appears and the app sees the bound device.
6. Confirm Cyclone sees the same binding through the config contract and shows
   the expected `Rooted` state.

Exit criteria: a bound app starts, reads the profile, and Cyclone agrees that
the profile is rooted. Any failure should produce a precise Root Doctor reason.

### Sprint 7 - identifier and locale coverage
Goal: close the pending identifier surface without breaking the profile.

1. Extend the module to apply the pending identifier fields.
2. Extend the same module to apply locale and timezone.
3. Keep the coverage ledger truthful: move fields from `PENDING` to `APPLIED`
   only when the module actually serves them.
4. Add unit vectors for each new field and keep the schema tests green.

### Sprint 8 - multi-profile and integrity posture
Goal: make the fleet path real and safer.

1. Exercise at least two Cyclone profiles and two bound apps on the same phone.
2. Verify that profiles cannot leak values into each other.
3. Add the Play Integrity / pif-style posture work in its own workstream.
4. Keep the current honest distinction: device-level integrity is the target,
   strong integrity depends on real keybox supply.

### Sprint 9 - release readiness
1. Stabilize the UI around Root Doctor and binding health.
2. Add explicit onboarding and failure diagnostics.
3. Run the full Forge, Android, and module test matrix.
4. Tag `v1.0.0` only after the physical test matrix passes.

## 3. Next immediate action
Run the exact Sprint 6 test protocol on the Pixel 8 with the current alpha.4
build. The older handoff is useful history, but the current Root Doctor path
should be retested before further code changes.
