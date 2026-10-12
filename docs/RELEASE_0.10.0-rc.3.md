# Cyclone Cloak 0.10.0-rc.3

Published on 2026-10-13 (Berlin) as a prerelease from the phase B1 runtime
repair batch. The release workflow passed the Python suite, Android and module
unit tests, and the signed APK/module artifact checks before upload. Device
verification for this binary is recorded separately in
[RUNTIME-COVERAGE.md](RUNTIME-COVERAGE.md) as soon as the on-device probe run
completes; this note itself makes no device-behavior claims.

## Versions and packaging

- App `dev.cyclone.cloak`: `0.10.0-rc.3`, versionCode **24**, minimum API 29,
  target/compile API 36.
- Module `cyclone_cloak`: `v0.10.0-rc.3`, versionCode **24**, ARM64 and ARMv7,
  minimum API 26.
- Profile schema **0.2**, binding schema **2**, connector contract and module
  reader format unchanged. Profiles already stored on disk remain valid; the
  new property-length rule only rejects over-long values at creation/import.

## Major improvements

- **Native property reads.** The module now stages an embedded inline-hook
  library (bytedance ShadowHook, MIT) and intercepts libc
  `__system_property_get` inside bound app processes, so native readers see
  the same profile values the Java `SystemProperties` surface already served.
  Unbound processes are never hooked. A marker file
  (`disable_native_hook` in the module directory) disables the surface.
- **Display geometry and refresh.** Profile width/height/refresh now flow to
  the app through `cloak.display.*` and are applied to
  `Resources.getDisplayMetrics`, `Display.getMetrics`,
  `Display.getRealMetrics` and `Display.getRefreshRate`.
- **MediaDrm diagnostics.** The device-identity callback logs whether it
  fires and whether the vault was empty, so the on-device probe run can
  distinguish hook-installation failures from empty-profile cases.
- **Property-length validation.** Forge now rejects values longer than 91
  characters in property-fed fields; such values cannot be represented in
  Android's property buffer, so profiles relying on them would silently
  degrade.
- **Multi-app binding flow.** The binding sheet gained an all-apps option and
  a unified identity step; both the profiles and identities entry points now
  complete the same flow (device profile choice included).

## Known limits

- Property-handle reads (`__system_property_read_callback`) are not yet
  intercepted; plain `__system_property_get` is covered.
- Wi-Fi SSID/service-MAC and Bluetooth-name rows stay blocked at the probe's
  permission boundary; camera/sensor surfaces still have no schema fields.
- The MediaDrm fix is diagnostic-gated in this release; a runtime verdict
  lands with the coverage-ledger update, not from this note.
