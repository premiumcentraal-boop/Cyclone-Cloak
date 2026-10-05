# Cyclone Cloak - working summary and 0.2 plan

## What this is

A short, current-state summary of the Cyclone Cloak project and the 0.2 plan we have been shaping. This is the build plan we researched and agreed on so far.

## Repos and current state

- Cyclone app: `https://github.com/premiumcentraal-boop/Cyclone`
- Cyclone Cloak: `https://github.com/premiumcentraal-boop/Cyclone-Cloak`
- Cyclone Cloak 0.1 is already released as `v0.1.0`, including the connector app APK and the Zygisk module zip.
- On the Cyclone side, PR #200 adds `androidUserId` to connector profiles. The branch is `codex/cloak-contract`, and the PR is currently open and mergeable.
- A small spec doc update was added to the PR branch to describe `androidUserId` plainly, so connector-side state can stay separate when the same package is installed in multiple Cyclone profiles.
- CI on PR #200 is still running. No further merge action has been taken yet.

## Why `androidUserId` matters

One Cyclone connector app can be installed once per Android user. Each Cyclone profile maps to its own Android user. Exposing `androidUserId` on connector profiles lets a connector tell which user backs a Cyclone profile, so profile-scoped state can stay separate when the same package is installed in multiple profiles. It is a small contract-level addition, not a device-identity feature by itself.

## 0.2 plan

The goal of 0.2 is to turn Cyclone Cloak from a reference build into a real identity layer that can support 20+ Cyclone profiles on one rooted phone.

### Build targets

1. **Profile schema v0.2**
   - Extend the existing cloak profile schema beyond basic device fields.
   - Add structured slots for carrier/SIM identity, telephony IDs, DRM identity, WebView UA, integrity state, profile health, and hook capabilities.
   - Keep the profile schema the single contract between the forge, vault, module, and API.

2. **Identity vault**
   - Give each profile a 256-bit seed.
   - Derive all identifiers from that seed: Android ID, advertising ID, app set ID, serial, IMEIs, SIM serial, MACs, GSF ID, Widevine ID.
   - Support a recycle flow that reseeds the whole profile in one action.
   - Keep 20+ profiles unique without accidental identifier collisions.

3. **Forge v2**
   - Expand dump parsing so profiles can come from real-device captures.
   - Add stronger coherence rules across model, carrier, telephony, DRM security level, locale, timezone, and UA.
   - Reject incoherent profiles instead of generating impossible combinations.

4. **Hook engine v2**
   - Move past the current Build fields and system properties.
   - Add per-process spoofing for serial, telephony identifiers, SIM/carrier reads, Wi-Fi/BT MACs, ANDROID_ID, advertising and app set IDs, Widevine ID, GSF ID, locale, timezone, and WebView UA.
   - Keep hooks scoped to the target app and apply them once at app start.

5. **Integrity layer**
   - Generate per-profile Play Integrity data, ideally compatible with a Tricky Store-style path.
   - Add a self-test that records the last known integrity verdict.
   - Report `device` verdict as the realistic target and `strong` verdict as dependent on a real hardware keybox.

6. **Cyclone connector integration**
   - Keep Cyclone in charge of profiles.
   - Attach identity data to each existing Cyclone profile through the connector contract.
   - Use one selector entry to open the Cloak manager instead of exposing every identity as a separate Cyclone selector entry.

7. **Health panel**
   - Show active device model, fingerprint age, integrity verdict, last recycle, failed checks, and whether bound apps restarted.
   - Mark burned or inconsistent profiles visibly.

8. **Device test harness**
   - Build a probe app that reads every spoofed surface and reports what it sees.
   - Verify the invariants: same app in two profiles sees two devices, same profile across apps stays consistent, unscoped apps remain untouched.

## Out of scope for 0.2

- Virtual app containers
- Filesystem isolation
- Display/sensor spoofing
- Fleet-wide proxy routing
- Absolute banking guarantee
- Any promise of `strong` integrity on every device

## Current working assumption

We are building Cyclone Cloak as a real device-profile engine for Cyclone. Cyclone keeps ownership of profiles. Cyclone Cloak supplies the unseen device underneath each one. Future work can then layer on proxy binding, uptime/display spoofing, and fleet automation once the identity and integrity layer is stable.
