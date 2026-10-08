# Cyclone Cloak 0.7.0-alpha.1

This release closes the deep-identity surface: every identifier the Forge
emits now reaches the scoped app through the Java callback layer, and each
binding publishes its own Play Integrity posture artifact.

- **MediaDRM identity.** `MediaDrm.getPropertyByteArray` answers device-identity
  reads from the profile's Widevine vault value, so DRM-level device checks see
  the bound device, not the host.
- **Advertising identity.** The Play services advertising-id read path is
  answered from the profile vault, keeping ad-SDK identities consistent with
  the Settings provider value.
- **WebView user agent.** Each profile carries a Chrome-on-Android UA string
  composed from its own Build fields; `WebSettings.getUserAgentString()` serves
  it. The UA is validated for model/release coherence at forge time.
- **GSF read path.** The classic gservices `android_id` query is answered from
  the profile's GSF vault value through a guarded query rewrite; anything
  unusual falls through to the real provider response.
- **Per-binding pif.json.** Publishing now writes a PIF-style key/value file
  next to each binding's profile.json, derived from the same device block, for
  Tricky Store-style consumers.
- **Coverage ledger honesty.** `telephony.sim_slot_count` and the UA block are
  now marked APPLIED and enforced by tests against the module source.

Identifiers rendered before (IMEI, SIM, carrier, MACs, serial, ANDROID_ID,
locale/timezone) are unchanged. Display geometry, uptime and sensor renaming
remain phase-3 work; Play Integrity verdicts need on-device validation.

## First setup or app update

1. Flash the bundled module ZIP in Magisk and reboot.
2. Install or update the companion APK; grant root when Root Doctor asks.
3. Bind profiles to apps; publishing now stages both profile.json and pif.json.
4. Force-stop and relaunch a bound app, then check `logcat -s CloakModule` for
   the `[cloak-hook] java callbacks installed` line.
