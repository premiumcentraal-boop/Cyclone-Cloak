# Runtime coverage and device proof

## Current measured alpha.24 stabilization — 2026-10-18

[Evidence](runtime-evidence/2026-10-18-222138-alpha24-integrated-stabilization/summary.md) measures code `49564ce` (app/module
0.11.0-alpha.24 / code 48) on the connected API 36 device. The signed-validation
artifact's native arm64 SHA-256 is
`4a3e5099c7ebde60728b19306b7412f8576679ebb4163c2dc05272b1ed2e8900`.
Twelve same-process warm repetitions completed through compacting GC after the
hook-loader lifetime fix. Three full lifecycle cycles completed 24 captures:

| Bound user / fixture | Legacy PASS | OBSERVED | BLOCKED | Linked PASS / FAIL | Active tuple |
|---|---:|---:|---:|---|---|
| 0 / A | 82 | 7 | 0 | 13 / 0 | 1080x2400 @ 90 Hz |
| 17 / B | 81 | 4 | 4 | 13 / 0 | 1080x2340 @ 120 Hz |

These counts held in each cycle. Supported modes contained the exact active tuple;
fixture labels, user IDs and report/published hashes agreed. Warm removal captures
completed without crash; both users restored cold with zero comparison FAIL rows.
Unbound captures retain expected synthetic-fixture mismatches and permission/API
limits. Four user-17 bound rows (`wifi.bssid`, `wifi.ssid`, `bluetooth.address`,
`bluetooth.name`) remain BLOCKED; their permission state was not changed.
Production entries stayed 327; per-cycle and boundary permission/AppOps, published
body/share/store/module audits were unchanged. Every cleanup proof passed.

The raw module/APK hashes, signed provenance, prior failed tombstone (redacted),
phase reports, snapshots and limitations are retained in the evidence. This is
implemented linked/display parity on the measured artifact, not a platform verdict,
complete Reality enforcement, factory hardware proof or physical console UI proof.
The following sections preserve earlier measurements and do not describe the
currently installed module unless explicitly dated as such.


The owner authorized module installation and reboot for B2A DRM verification on
2026-10-13. This run used a rooted Pixel 8/API 36, Android user 0. The installed
module retains version `v0.10.0-rc.3`, versionCode 24, with the local DRM fix
`9a6a1e7`; its arm64 SHA-256 is
`f87b218e5763a2084093b817c42eaee632390e7d1cb72a686851a469e4e3c4b9`.
The version label alone does not identify a published binary. The earlier RC3
baseline remains in [its evidence file](runtime-evidence/rc3-api36-final.json).
Other pending device checks are listed in
[DEFERRED-DEVICE-CHECKS.md](DEFERRED-DEVICE-CHECKS.md).

The independent `dev.cyclone.cloak.probe` app reads Android APIs and native
properties. Its fixtures supply expected values only. It never loads the
Cloak engine, calls hook callbacks itself, or substitutes expected values for
observations. The installed patched module was tested with two distinct valid schema
0.2 profiles, a repeated cold start, and unbound controls before and after.
Both fixtures have distinct Build/identifier/locale values; A has two SIM slots
and 5G, B has one slot and LTE. The runner removes its own temporary bindings
and profile files, preserving other app/user bindings. The module was installed and the device rebooted before these runs. The existing
companion app was retained because the freshly built release APK was unsigned.
The runner itself did not reboot or grant permissions.

## B3B diagnostics partial measurement â€” 2026-10-13

A side-by-side `dev.cyclone.cloak.probe.diag` APK can request runtime permissions
before reads start. The default APK still declares no permissions, including
with the optional advertising-ID SDK, and retains
`android/probe/build/outputs/apk/debug/probe-debug.apk`. Diagnostics output is
`android/probe/build/outputs/apk/diagnostics/debug/probe-diagnostics-debug.apk`.

[Partial evidence](runtime-evidence/b3b-diagnostics-2026-10-13.json) and
[the B3B matrix](RUNTIME-MATRIX-B3B-2026-10-13.md) cover **bound-a only**:
**63 PASS, 1 FAIL, 0 BLOCKED, 6 OBSERVED**, 70 reads. All 70 proof classifications
are INCOMPLETE; a single phase cannot increase the historical 54 VERIFIED count.
The installed module hash remains the B2A hash above. No module installation or
reboot occurred. Current source contains schema 0.3 camera fixtures, while the
installed module is still the RC3 DRM patch; camera metadata therefore fails
its new fixture expectation. This is an expected version mismatch, not a
defect assigned to this sprint. Camera verification belongs to the coordinated
device pass after the v0.11.0-alpha.1 module is installed.

After the owner authorized both prerequisite permissions, `wifi.serviceMac`
moved from BLOCKED to PASS (`MATCH`). `wifi.ssid` and `bluetooth.name` moved
from BLOCKED to OBSERVED (`NO_PROFILE_EXPECTATION`). Diagnostics alone declares
`ACCESS_WIFI_STATE` and `ACCESS_COARSE_LOCATION` in addition to the original
three permissions; runtime location requests include the coarse/fine pair.
The [initial evidence](runtime-evidence/b3b-diagnostics-2026-10-13-before-prerequisites.json)
retains the two missing-Wi-Fi-permission results.
SSID and Bluetooth name have no fixture expectations, so readable values are
OBSERVED rather than PASS/FAIL. Denial remains explicit and never counts as
engine coverage. The default permission-free probe intentionally preserves
permission-blocked behavior.

Use `--phases all` (default) for the original five-phase proof, or
`--phases bound-a` / `--phases bound-a,bound-b` for a subset. Subsets are executed
in the canonical five-phase order, carry `metadata.phases`, and still clean up
only their own temporary binding/fixtures. Missing phases render as `not run`;
partial equality alone is not VERIFIED proof. SSID/Bluetooth name remain
pending profile-backed expectations in a future sprint.

## Visible matrix

The companion Health tab also displays a local 13-area summary from the checked-in
`android/app/src/main/assets/compatibility-coverage.json`. The generated
[Health matrix](HEALTH-COVERAGE.md) uses the same source. Build and serial have
selected baseline reads with device proof; no whole area or local runtime patch
is claimed verified. Dates in that summary use UTC (the documented 2026-10-11
Berlin run started on 2026-10-10 UTC). It does not run a live check or infer
coverage from Root Doctor. This summary must not be interpreted as proof of every API in an area.

The test app displays a row per read. The complete, generated **70-read matrix**
is [RUNTIME-MEASUREMENTS.md](RUNTIME-MEASUREMENTS.md), backed by
[redacted JSON evidence](runtime-evidence/b2a-drm.json)
(dated copy: [RUNTIME-MATRIX-B2A-2026-10-13.md](RUNTIME-MATRIX-B2A-2026-10-13.md)).
(dated copy: [RUNTIME-MATRIX-RC2-2026-10-12.md](RUNTIME-MATRIX-RC2-2026-10-12.md)).
Observed host values are hashed; fixture expectations are synthetic test data.

**54 reads VERIFIED, 2 FAIL, 8 MATCH_ONLY, 3 BLOCKED, 3 UNMAPPED.** A PASS in
the raw report means equality with that fixture only. VERIFIED additionally
requires differing expectations for A/B, repeat stability and restoration of
the unbound control. Bound runs measure PASS=62/70 (A) and 64/70 (B), with
identical bound-a and bound-a-repeat reports; unbound-before and unbound-after
reports match exactly. `drm.deviceUniqueId` is now VERIFIED. The previous
property guard accepted `deviceId` but omitted Android's `deviceUniqueId` name;
absence of a log after that guard did not prove that the callback never fired.
The patched callback serves both bound fixtures, and three bound cold starts
emit the serving log. The two FAILs are fixture gaps: profile A carries no
bootloader/radio expectations.
The three BLOCKED rows (Wi-Fi service MAC/SSID, Bluetooth name) remain
permission gates on the probe identity, not module failures. Matching host display dimensions do not prove a hook.
BLOCKED and UNMAPPED never count as passed coverage.

| Requested surface | Installed patched module: device result | Remaining work |
| --- | --- | --- |
| Build | Manufacturer/brand/model/device/product/hardware/fingerprint/ID/serial/getSerial/time all VERIFIED; TYPE/TAGS match-only; bootloader/radio PASS on B and now fixture A has expectations | Next device run must prove fixture A |
| Build.VERSION | SDK, release, incremental and security patch all VERIFIED | None measured |
| System properties | Java-side AND native libc `__system_property_get` reads all VERIFIED (ShadowHook inline hook, bind-time install) | `__system_property_read_callback` hook is implemented and probe-exercised; device proof is pending |
| Telephony manager | IMEI (both slots), deviceId, subscriber, SIM serial and network types VERIFIED on both profiles | Keep stable across cold starts; add secondary-slot profiles |
| Settings provider reads | Secure provider Android-ID query VERIFIED | Generic provider call/bundle paths remain unverified |
| Android ID | Settings.Secure android_id VERIFIED | None measured |
| Serial number | Build.SERIAL and Build.getSerial VERIFIED | None measured |
| Advertising ID | Real Google SDK read VERIFIED when bound | Re-verify after app updates of the SDK |
| Wi-Fi info | Object MAC getter VERIFIED | Service MAC/SSID blocked by probe permissions; measure from a privileged probe identity |
| Bluetooth identity | Address getter VERIFIED | Name read blocked by probe permissions |
| DRM/MediaDrm | `deviceUniqueId` VERIFIED for two distinct profiles, repeated A and restored unbound control | Other DRM properties, UUIDs and security levels remain unverified |
| Display metrics | Density, width, real metrics and refresh VERIFIED via Resources/Display rewrites (out-parameter handling fixed this release) | `display.resources.*` for B verified; screen/resource/window consistency beyond probe scope |
| Camera info | Enumerated, UNMAPPED | No camera expectation in schema yet (Phase D corpus) |
| Sensor list | Enumerated, UNMAPPED | No sensor expectation in schema yet; do not invent capabilities |
| Network operator | Numeric/name/country VERIFIED on both profiles | None measured |
| SIM/operator fields | Numeric/name/country/serial VERIFIED; secondary slot checked | None measured |
| Country/locale/timezone | Country, resource locale and default timezone VERIFIED on both profiles | None measured |

The local DRM patch has been installed, rebooted and measured; the matrix above
is the identified patched binary's result. Any future local runtime
change must follow the same path: build, install with the owner's approval,
reboot, then re-run the probe and save a separate evidence file. A callback
unit test alone is not proof of ART installation.

Known hook-framework note: Pine cannot hook the abstract
`WebSettings.getUserAgentString` (logcat: "Cannot hook abstract methods"); the
UA surface needs a concrete-class hook point. Recorded for batch 2.

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
- The new direct `__system_property_read_callback` probe row has local build
  coverage only; the checked-in device matrix predates it. Full provider reads,
  broader display consistency, camera and sensor identity are not complete. The
  measured direct native `__system_property_get` and display geometry/refresh
  reads have device proof.
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


## Reality Runtime Bridge (formats 1 and 2)

The contract table maps 97 schema leaf/array paths, including host-only provenance.
Runtime readiness: **10 applied (when matched), 0 partial, 5 pending, 42 diagnostic-only,
37 host-only, 3 unsupported**. Partial refers only to existing linked base fields;
the module does not consume Reality. These counts do not replace measured rows.

Optional `reality_runtime` observations reuse digest-only comparisons. PASS means
match-only, not verified. Nested inventory leaves stay unmapped until individually
measured; no aggregate hash silently verifies a leaf. Historical device evidence
is unchanged. The new extension is **UNVERIFIED on physical devices**.
See [format](DEVICE-REALITY.md#reality-runtime-bridge-format-1) and
[prioritized future surfaces](REALITY-RUNTIME-BACKLOG.md). No platform verdict.


## Runtime Linked Parity Phase 1

`RealityRuntimeLink` (Kotlin) and `runtime_link.evaluate` (Python) evaluate ten
reviewed aliases from the shared runtime table. Base profile fields remain the
only runtime source. Exact matches are MATCHED; conflicts are MISMATCHED;
missing Reality is NOT_PRESENT and missing base is BASE_MISSING. Findings expose
paths/state only, never values, and do not mutate or publish profiles.
The density link targets the existing `display.density`, not a new density_dpi
base field. No profile schema or storage migration is introduced.

Contract format 2 records linked_base_path, EXACT/NONE parity_requirement,
runtime_covered, present, parity_state and nullable parity_passed. The ten linked
paths are APPLIED only with a present exact match; otherwise PARTIAL. The remaining
87 statuses stay unchanged. Format 1 contracts and optional report format 1 remain
readable. APPLIED describes implemented local capability plus required parity,
not verified execution on any phone. All ten matched: 10 applied, 0 partial,
5 pending, 42 diagnostic-only, 37 host-only, 3 unsupported.

Scoped Java callbacks construct profile-backed Display.Mode return objects and
include the same mode in getSupportedModes without changing host mode objects or
physical display configuration. Missing/invalid base overrides are no-ops or retain
the missing component. Existing metrics/refresh, locale/timezone and telephony
surfaces remain authoritative. Framework-object tests cover API 34/35; older
constructor fallback and ART installation still require physical verification.

The optional `linked_parity` probe extension has 13 digest-only rows: ten linked
observations plus SIM operator, active modem count and supported-mode membership.
The latter records a digest of the full aggregate and separately compares whether
it contains the complete base mode tuple. No base expectation means no PASS.
Permission/API failures remain explicit. Legacy 89 rows are unchanged. The coverage
card shows matched/mismatched links, field paths and applied/partial/pending counts.
Local parity is not a platform verdict. Physical verification: **PENDING**; the
connected device still has alpha.18/code 42, so it cannot validate these callbacks.
