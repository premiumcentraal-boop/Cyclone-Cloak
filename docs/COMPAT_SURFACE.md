# Cyclone Cloak Compatibility Surface

Compatibility inventory and planned phases, not a claim that every listed read
is intercepted. Current published prerelease: `v0.10.0-rc.1`; see
[ARCHITECTURE.md](ARCHITECTURE.md) and [CURRENT-STATE.md](CURRENT-STATE.md).
Callback values come from the stored identity, not fresh generation at read time.

On-device measurements now supersede the source-only implementation claims
below: see [RUNTIME-COVERAGE.md](RUNTIME-COVERAGE.md) and the
[70-read matrix](RUNTIME-MEASUREMENTS.md). The installed API36 binary fails many
callbacks despite reporting successful installation. Local fixes are built;
their device verification remains pending a module install and reboot.

| # | Surface | Typical read | Callback approach | Phase |
| --- | --- | --- | --- | --- |
| 1 | Build fields | `Build.FINGERPRINT`, `MODEL`, `BRAND`, `MANUFACTURER`, `DEVICE`, `PRODUCT`, `HARDWARE`, `SDK_INT`, `TIME`, `TYPE`, `TAGS`, `ID`, `INCREMENTAL`, `BOOTLOADER`, `RADIO` | platform: static field rewrite in the scoped process | 1 |
| 2 | System properties | `SystemProperties.get`, native `__system_property_get` (`ro.product.*`, `ro.build.*`) | Native copy-on-write prop table, scoped per app | 1 |
| 3 | Device serial | `Build.getSerial()`, `Os.gethostname`-adjacent reads | Rewrite above `getserial` | 1 |
| 4 | IMEI / MEID | `TelephonyManager.getImei()`, `getDeviceId()` | Framework callback, per-slot values from vault | 2 |
| 5 | SIM / carrier | `getSimSerialNumber()`, `getSubscriberId()`, `getNetworkOperator()`, `getNetworkOperatorName()` | Framework callback, per-slot carrier block | 2 |
| 6 | Wi-Fi MAC | `WifiInfo.getMacAddress()` | Interface-agnostic callback; BSSID/SSID randomized only if the profile asks | 2 |
| 7 | Bluetooth MAC | `BluetoothAdapter.getAddress()` | Adapter callback | 2 |
| 8 | ANDROID_ID | `Settings.Secure.ANDROID_ID` | Per-(profile, app) value; stable per binding, never the host value | 2 |
| 9 | Advertising ID / App Set ID | Play services SDK calls | Play services callback scoped to the app's reads | 2 |
| 10 | Widevine / MediaDRM | `MediaDrm.getPropertyByteArray DEVICE_ID`, security level | DRM plugin read callback; L1/L2/L3 reported per profile | 2 |
| 11 | GSF ID | `com.google.android.gsf` reads | Provider-level callback | 2 |
| 12 | Locale / timezone | `Locale.getDefault()`, `TimeZone.getDefault()` | In-process configuration rewrite; must agree with egress IP region | 2 |
| 13 | WebView user agent | `WebSettings.getUserAgentString()` | UA rewrite from a named UA profile | 2 |
| 14 | Uptime | `SystemClock.elapsedRealtime()`, native `clock_gettime(CLOCK_BOOTTIME)` | Offset shift, persistent per profile | 3 |
| 15 | Display | `DisplayMetrics`, refresh rate modes | Optional; off by default (layout breakage risk) | 3 |
| 16 | Sensors | `SensorManager` name/vendor enumeration | Optional rename pass only; never invent or drop sensors | 3 |
| 17 | Root / attestation | Play Integrity verdict, key attestation | PIF-style pif.json per profile + Tricky Store keybox | 2 |

## Release candidate evidence boundary

The RC has **no device verification**. The existing 70-read results describe the
older API36 installed binary by its recorded hash, not this candidate: 13 verified,
39 failed, 7 blocked, 8 coincidental matches and 3 unmapped. Local callback/JVM
success and packaged ARM libraries establish build/test results, not hook
installation or full device compatibility. Camera/sensor metadata and display
geometry are incomplete. Read the [RC notes](RELEASE_0.10.0-rc.1.md) and
[deferred device checklist](DEFERRED-DEVICE-CHECKS.md) before upgrading.

## Rules

- Callbacks run only for scoped apps. The host device keeps its real profile for everything else.
- Build fields are applied at process start; native property and Pine Java callbacks
  remain available for later reads. The module does not implement unloading.
- Values come from the stored identity; intended equality across bindings and
  identifier separation require per-API runtime evidence.
- Planned display/sensor work must preserve coherent geometry and density.
  These pending areas are not evidence of available opt-in hooks.

## Status (0.10.0-rc.1; source implementation, device verification pending)

- Landed through the property surface: Build fields, telephony operator identity, display density, device serial (`ro.serialno`, `ro.boot.serialno`), and the full identifier vault under stable `cloak.*` keys.
- Landed in-process: locale/timezone, the Pine callbacks for TelephonyManager, Settings.Secure ANDROID_ID, WifiInfo, BluetoothAdapter, Build.getSerial, MediaDRM device id, the advertising-id read path, the WebView user agent, and the guarded GSF gservices query rewrite.
- Per binding, the companion now also publishes `pif.json` (PIF-style Play Integrity posture) next to `profile.json`.
- `app_set_id` is stored and exposed as a `cloak.*` property; the Java bridge does not
  implement an App Set SDK callback. Publishing `pif.json` does not verify attestation
  or implement keybox management or automatic fingerprint rotation.
- Still pending (phase 3): display geometry, uptime offset, sensor renaming, and the network egress hint.
