# Cyclone Cloak Hook Surface

Every surface an anti-fraud SDK can read, how Cloak rewrites it, and the phase in which the hook
lands. The source of every value is the profile's identity vault entry (see
`docs/ARCHITECTURE.md`); hooks never generate values on the fly.

| # | Surface | Typical read | Hook approach | Phase |
| --- | --- | --- | --- | --- |
| 1 | Build fields | `Build.FINGERPRINT`, `MODEL`, `BRAND`, `MANUFACTURER`, `DEVICE`, `PRODUCT`, `HARDWARE`, `SDK_INT`, `TIME`, `TYPE`, `TAGS`, `ID`, `INCREMENTAL`, `BOOTLOADER`, `RADIO` | Zygisk: static field rewrite in the scoped process | 1 |
| 2 | System properties | `SystemProperties.get`, native `__system_property_get` (`ro.product.*`, `ro.build.*`) | Native copy-on-write prop table, scoped per app | 1 |
| 3 | Device serial | `Build.getSerial()`, `Os.gethostname`-adjacent reads | Rewrite above `getserial` | 1 |
| 4 | IMEI / MEID | `TelephonyManager.getImei()`, `getDeviceId()` | Framework hook, per-slot values from vault | 2 |
| 5 | SIM / carrier | `getSimSerialNumber()`, `getSubscriberId()`, `getNetworkOperator()`, `getNetworkOperatorName()` | Framework hook, per-slot carrier block | 2 |
| 6 | Wi-Fi MAC | `WifiInfo.getMacAddress()` | Interface-agnostic hook; BSSID/SSID randomized only if the profile asks | 2 |
| 7 | Bluetooth MAC | `BluetoothAdapter.getAddress()` | Adapter hook | 2 |
| 8 | ANDROID_ID | `Settings.Secure.ANDROID_ID` | Per-(profile, app) value; stable per binding, never the host value | 2 |
| 9 | Advertising ID / App Set ID | Play services SDK calls | Play services hook scoped to the app's reads | 2 |
| 10 | Widevine / MediaDRM | `MediaDrm.getPropertyByteArray DEVICE_ID`, security level | DRM plugin read hook; L1/L2/L3 reported per profile | 2 |
| 11 | GSF ID | `com.google.android.gsf` reads | Provider-level hook | 2 |
| 12 | Locale / timezone | `Locale.getDefault()`, `TimeZone.getDefault()` | In-process configuration rewrite; must agree with egress IP region | 2 |
| 13 | WebView user agent | `WebSettings.getUserAgentString()` | UA rewrite from a named UA profile | 2 |
| 14 | Uptime | `SystemClock.elapsedRealtime()`, native `clock_gettime(CLOCK_BOOTTIME)` | Offset shift, persistent per profile | 3 |
| 15 | Display | `DisplayMetrics`, refresh rate modes | Optional; off by default (layout breakage risk) | 3 |
| 16 | Sensors | `SensorManager` name/vendor enumeration | Optional rename pass only; never invent or drop sensors | 3 |
| 17 | Root / attestation | Play Integrity verdict, key attestation | PIF-style pif.json per profile + Tricky Store keybox | 2 |

## Rules

- Hooks run only for scoped apps. The host device keeps its real identity for everything else.
- Hooks apply once at process start, then unload. Nothing stays resident.
- Values come from the vault; two apps bound to the same profile see identical values, two
  profiles never share a value.
- A hook that cannot be made coherent with the rest of the profile (e.g. a spoofed screen size
  that disagrees with the density) is disabled by default and requires an explicit opt-in.
