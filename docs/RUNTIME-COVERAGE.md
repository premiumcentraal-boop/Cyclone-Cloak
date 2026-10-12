# Runtime coverage and device proof

Reboot-dependent checks for future local fixes remain deferred; see
[DEFERRED-DEVICE-CHECKS.md](DEFERRED-DEVICE-CHECKS.md) for the resume checklist.
RC2 device baseline measured 2026-10-12 on a rooted Pixel 8, Android API 36,
Android user 13. The installed module identifies itself as `v0.10.0-rc.2`,
versionCode 23; its arm64 library SHA-256 is
`2ec607f9563e9f2ac47ab4245041ca10420d2d36d530e040f862a9c231c5a528`.
This identifies the measured binary; its version label alone does not prove
byte-for-byte correspondence with a published build. The earlier alpha.1
baseline (v0.10.0-alpha.1, code 18, user 11, 13 verified reads) remains in
git history under the 2026-10-11 measurement of this file.

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
[redacted JSON evidence](runtime-evidence/rc2-api36-baseline.json)
(dated copy: [RUNTIME-MATRIX-RC2-2026-10-12.md](RUNTIME-MATRIX-RC2-2026-10-12.md)).
Observed host values are hashed; fixture expectations are synthetic test data.

**45 reads VERIFIED, 11 FAIL, 8 MATCH_ONLY, 3 BLOCKED, 3 UNMAPPED.** A PASS in
the raw app report means equality with that fixture only. VERIFIED additionally
requires differing expectations for A/B, repeat stability and restoration of
the unbound control. Bound runs measure PASS=55/70 per profile with identical
bound-a and bound-a-repeat reports; unbound-before and unbound-after reports
match exactly. Remaining FAILs concentrate on native libc property reads
(ro.product.model, ro.build.version.sdk, ro.build.fingerprint, ro.serialno,
persist.sys.timezone), DRM deviceUniqueId and display refresh/geometry; the
three BLOCKED rows (Wi-Fi service MAC/SSID, Bluetooth name) are permission
gates on the probe identity, not module failures. Matching host display dimensions do not prove a hook.
BLOCKED and UNMAPPED never count as passed coverage.

| Requested surface | RC2 installed binary: device result | Remaining work |
| --- | --- | --- |
| Build | Manufacturer/brand/model/device/product/hardware/fingerprint/ID/serial/getSerial/time all VERIFIED; TYPE/TAGS match-only; bootloader/radio PASS on B, unmapped on A | Map bootloader/radio in profile A fixture or catalog |
| Build.VERSION | SDK, release, incremental and security patch all VERIFIED | None for measured surfaces |
| System properties | Java-side property reads VERIFIED; native libc reads (model, sdk, fingerprint, serialno, timezone) still FAIL | Hook or intercept native __system_property_get paths |
| Telephony manager | IMEI (both slots), deviceId, subscriber, SIM serial and network types VERIFIED on both profiles | Keep stable across cold starts; add secondary-slot profiles |
| Settings provider reads | Secure provider Android-ID query VERIFIED | Generic provider call/bundle paths remain unverified |
| Android ID | Settings.Secure android_id VERIFIED | None measured |
| Serial number | Build.SERIAL and Build.getSerial VERIFIED | None measured |
| Advertising ID | Real Google SDK read VERIFIED when bound | Re-verify after app updates of the SDK |
| Wi-Fi info | Object MAC getter VERIFIED | Service MAC/SSID blocked by probe permissions; measure from a privileged probe identity |
| Bluetooth identity | Address getter VERIFIED | Name read blocked by probe permissions |
| DRM/MediaDrm | Widevine deviceUniqueId still FAIL | Implement MediaDrm byte-array identity callback |
| Display metrics | Density VERIFIED; A heights match host (match-only); B height/refresh FAIL | Implement display geometry/refresh rewrites for profiles that differ from host |
| Camera info | Enumerated, UNMAPPED | No camera expectation in schema yet (Phase D corpus) |
| Sensor list | Enumerated, UNMAPPED | No sensor expectation in schema yet; do not invent capabilities |
| Network operator | Numeric/name/country VERIFIED on both profiles | None measured |
| SIM/operator fields | Numeric/name/country/serial VERIFIED; secondary slot checked | None measured |
| Country/locale/timezone | Country, resource locale and default timezone VERIFIED on both profiles | None measured |

The RC2 release has been installed, rebooted and measured on the device; the
matrix above is the installed RC2 binary's own result. Any future local runtime
change must follow the same path: build, install with the owner's approval,
reboot, then re-run the probe and save a separate evidence file. A callback
unit test alone is not proof of ART installation.

## Reproduce

Build (this checkout has no Gradle wrapper; use the installed Gradle executable):

```powershell
gradle -p android :probe:assembleDebug -PprobeAdvertisingSdk=true
gradle -p android :module:testDebugUnitTest :app:assembleDebug :module:packageModule
uv run --with pytest -- python -m pytest forge/tests -q
uv run -- python tools/run_runtime_probe.py --adb C:/Users/Agent/android-sdk/platform-tools/adb.exe --serial SERIAL --user 13 --output docs/runtime-evidence/new-run.json
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
