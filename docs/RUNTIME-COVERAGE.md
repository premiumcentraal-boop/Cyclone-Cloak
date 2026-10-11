# Runtime coverage and device proof

Reboot-dependent checks are deferred at the owner's request (2026-10-11, away
from home). Do not install or reboot into the local runtime fixes yet. See
[DEFERRED-DEVICE-CHECKS.md](DEFERRED-DEVICE-CHECKS.md) for the resume checklist.

Measured 2026-10-11 on a rooted Pixel 8, Android API 36, Android user 11.
The installed module identifies itself as `v0.10.0-alpha.1`, versionCode 18;
its arm64 library SHA-256 is
`137c9e084fd6825c2e506b0f4b4ae8078bdc742184bd886ea2d0c19610847f75`.
This identifies the measured binary; its version label alone does not prove
byte-for-byte correspondence with a published build.

The independent `dev.cyclone.cloak.probe` app reads Android APIs and native
properties. Its fixtures supply expected values only. It never loads the
Cloak engine, calls hook callbacks itself, or substitutes expected values for
observations. The installed release was tested with two distinct valid schema
0.2 profiles, a repeated cold start, and unbound controls before and after.
Both fixtures have distinct Build/identifier/locale values; A has two SIM slots
and 5G, B has one slot and LTE. The runner removes its own temporary bindings
and profile files, preserving other app/user bindings. No module replacement,
reboot, permission grant or production app restart occurred in these runs.

## Visible matrix

The companion Health tab also displays a local 13-area summary from the checked-in
`android/app/src/main/assets/compatibility-coverage.json`. The generated
[Health matrix](HEALTH-COVERAGE.md) uses the same source. Build and serial have
selected baseline reads with device proof; no whole area or local runtime patch
is claimed verified. Dates in that summary use UTC (the documented 2026-10-11
Berlin run started on 2026-10-10 UTC). It does not run a live check or infer
coverage from Root Doctor. Reboot-dependent verification remains deferred.

The test app displays a row per read. The complete, generated **70-read matrix**
is [RUNTIME-MEASUREMENTS.md](RUNTIME-MEASUREMENTS.md), backed by
[redacted JSON evidence](runtime-evidence/release-api36-advertising.json).
Observed host values are hashed; fixture expectations are synthetic test data.

**13 reads VERIFIED, 39 FAIL, 7 BLOCKED, 8 MATCH_ONLY, 3 UNMAPPED.** A PASS in
the raw app report means equality with that fixture only. VERIFIED additionally
requires differing expectations for A/B, repeat stability and restoration of
the unbound control. Matching host display dimensions do not prove a hook.
BLOCKED and UNMAPPED never count as passed coverage.

| Requested surface | Installed binary: device result | Local runtime changes / remaining work |
| --- | --- | --- |
| Build | 10 field reads verified; bootloader/radio not fully matched | Map bootloader/radio when supplied; empty fixture fields remain unspecified |
| Build.VERSION | SDK/release/patch verified; incremental fails | Write INCREMENTAL on Build.VERSION instead of Build |
| System properties | Java and native identity reads fail; density equality is coincidental | Skip non-native wrappers during JNI registration, register each method independently; native libc and handle-based reads remain uncovered |
| Telephony manager | IMEI/subscriber/SIM reads fail; modem counts throw ClassCastException; network type is permission blocked | Integer count/type results; existing identifier callbacks depend on corrected property registration |
| Settings provider reads | Secure provider query fails | Guarded single Android-ID query callback; generic provider call/bundle paths remain unverified |
| Android ID | Settings.Secure getter fails | Restrict getter to android_id; unrelated keys must retain provider behavior |
| Serial number | Build.SERIAL verified; Build.getSerial fails | Existing getter consumes corrected property surface |
| Advertising ID | Real Google SDK read fails | Defer SDK hook installation until app class loader is available |
| Wi-Fi info | Object MAC getter fails; service/SSID reads permission blocked | MAC callback consumes corrected property surface; no SSID/BSSID profile fields |
| Bluetooth identity | Address getter fails; name read permission blocked | Address callback consumes corrected property surface; no name profile field |
| DRM/MediaDrm | Widevine deviceUniqueId fails | Existing byte-array callback consumes corrected property surface; other DRM properties/attestation are not covered |
| Display metrics | Profile B height differs from host and fails; density matches only; refresh fails | Geometry/refresh callbacks remain unimplemented; screen/resource/window consistency needs a separate implementation and device tests |
| Camera info | Camera characteristics enumerated, UNMAPPED | No camera expectation in current schema; observed host capabilities are not profile coverage |
| Sensor list | Sensor type/name/vendor enumerated, UNMAPPED | No sensor expectation in current schema; do not invent capabilities or mark enumeration as a hook |
| Network operator | Numeric/name/country reads fail | Existing callbacks consume corrected property surface |
| SIM/operator fields | Numeric/name/country/serial fail; secondary slot visibility checked | Integer counts and network types corrected; slot absence returns null as intended |
| Country/locale/timezone | Country/default/resource locale and timezone do not consistently match both profiles | Apply locale at application startup and resource configuration reads; default timezone callback |

The local patches build and callback tests pass, but **have not been loaded
into the phone's Zygisk runtime**. The matrix above remains the installed
binary's measured result. Installing the module and rebooting is required
before measuring the patches; preserve this baseline and save a separate
post-change evidence file. A callback unit test is not proof of ART installation.

## Reproduce

Build (this checkout has no Gradle wrapper; use the installed Gradle executable):

```powershell
gradle -p android :probe:assembleDebug -PprobeAdvertisingSdk=true
gradle -p android :module:testDebugUnitTest :app:assembleDebug :module:packageModule
uv run --with pytest -- python -m pytest forge/tests -q
uv run -- python tools/run_runtime_probe.py --adb C:/Users/Agent/android-sdk/platform-tools/adb.exe --serial SERIAL --user 11 --output docs/runtime-evidence/new-run.json
uv run -- python tools/render_runtime_matrix.py docs/runtime-evidence/new-run.json docs/RUNTIME-MEASUREMENTS.md
```

Select an existing running test Android user explicitly. The runner installs the
probe only in that user, refuses to replace a pre-existing probe binding,
temporarily edits only its own index tuple, and cleans it up in `finally`.
Do not run concurrent Cloak publication while measuring: the index is atomically
replaced but does not have a shared cross-process lock. Read-only metadata
records the module binary hash, version and real host API before starting.

Without `-PprobeAdvertisingSdk=true`, the minimal APK records SDK absence as
BLOCKED. The optional dependency is Google's real advertising-ID SDK 18.3.0,
not a test class with the same name. Its call runs off the UI thread, as
[Google documents](https://developers.google.com/android/reference/com/google/android/gms/ads/identifier/AdvertisingIdClient).
The probe adds no Android permissions or network code. Restricted service reads
remain blocked; those require a separately authorized test environment to prove.

## Known limits and exit criteria

- Device proof currently covers one arm64/API36 device, one Android user and one
  process. Other APIs/ARM32, additional processes, other users, subscription
  overloads, delayed class loaders and early SDK reads need their own runs.
- Direct native `__system_property_get`, property handles, full provider reads,
  display geometry/refresh, camera and sensor identity are not complete.
  Camera/sensor field expectations need an agreed representation compatible
  with the existing profile schema before a meaningful pass can be claimed.
- Do not fabricate camera/sensor capabilities or alter actual hardware operation
  to make a test green. Do not treat denied permissions as a working hook.
- Every claimed hook must match two distinct profiles, remain stable on a fresh
  process, restore the unbound control, and leave other app/user bindings intact.
  Generic log lines such as "callbacks installed" are insufficient proof.
- No profile schema, companion storage, connector contract or release versions
  were changed. The probe's private report is diagnostic output only.

The JNI registration defect is consistent with [AOSP SystemProperties](https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/master/core/java/android/os/SystemProperties.java):
`native_get(String)` is a Java forwarding method on modern Android. Registering
it as a native method rejected the release's registration batch on this device.
