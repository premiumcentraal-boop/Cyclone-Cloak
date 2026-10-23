# Runtime coverage and device proof

## Unreleased runtime envelope phase 2

[Surface matrix and limits](RUNTIME-ENVELOPE-PHASE-2.md) records local callback
tests and 15 digest-only probe rows. All new/changed surfaces remain physically
**UNVERIFIED**: two-fixture cold starts, actual cross-user isolation, cold unbound
restoration and permission/API evidence are pending. No row is silently verified
by existing alpha.24 evidence. New contract counts: 10 APPLIED linked-base paths,
17 PARTIAL, 3 PENDING, 27 DIAGNOSTIC_ONLY, 37 HOST_ONLY, 3 UNSUPPORTED. DRM is
match-only; nominal RAM/storage and physical CPU counts are not fabricated.

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


## 2026-10-21 candidate restoration follow-up (packaged in alpha.28)

The focused cycle and three lifecycle cycles completed for users 0 and 17.
Cold restoration had zero FAIL rows: user 0 each cycle 102 PASS / 7 OBSERVED /
8 BLOCKED; user 17 cycle 1 100 PASS / 4 OBSERVED / 13 BLOCKED and cycles 2/3
101 PASS / 4 OBSERVED / 12 BLOCKED. These are restoration comparisons, not
counts of runtime hooks verified. Package inventories and production/permission/
AppOps snapshots matched; all cleanup proofs passed. Warm removal retained
in-memory profile values until cold restart, as explicitly observed.

The artifact was the measured candidate with alpha.27/code51 metadata. Exact
hashes are recorded in the evidence; it is not the published alpha.27 binary or
a physical alpha.28 release validation. Blocked and external-state rows remain
limitations. No contract status is promoted by this harness-only correction.
[Summary](runtime-evidence/2026-10-20-235457-restoration-follow-up/summary.md) and
[diagnosis/commands](HANDOFF-2026-10-21-RESTORATION-HARNESS-FOLLOW-UP.md).


## Rung 1a preflight — 2026-10-23

[Evidence](runtime-evidence/2026-10-23-025139-rung1a-preflight/summary.md): **STOPPED** before captures.
The device had no installed or pending Cloak module after the prior authorized
removal cycle; module installation is outside this measurement sprint. All 17
PARTIAL paths remain PARTIAL. New physical measurements: **0**; verified promotions:
**0**. Production before/after snapshots matched; no device state changed.


## Rung 1a revised preflight — 2026-10-23

[Evidence](runtime-evidence/2026-10-23-072412-rung1a-revised-preflight/summary.md): candidate alpha.28 ZIP and
installed immutable libraries match the supplied hashes; diagnostics build passed.
**STOPPED** before installation/capture: required schema-0.4 fixture C is absent
from the checkout and rebuilt APK. All **17 PARTIAL** paths remain PARTIAL; new
measured rows **0**, promotions **0**. Production before/after snapshots match.


## Rung 1a fixture C measurement — 2026-10-23

[Evidence](runtime-evidence/2026-10-23-080419-rung1a-fixture-c/summary.md) identifies candidate ZIP
`8fd58b4ad0ea60ec06460b0ecaa0b9cea8e56467437975103d04392958a68795`
and matching installed ARM64 file
`d7dd105446a229f981e5cdd1aed862a13e3c7d04220f04fb08eadde175bb15c1`.
User0/A smoke: **81 PASS / 4 OBSERVED / 4 BLOCKED** legacy rows. User17/C
cold captures: **75/7/7**, then **76/10/3** after normal diagnostics UI grants;
all legacy FAIL counts zero. Full legacy equality is not claimed across grants.

Of the 17 PARTIAL contract paths, **1 measured APPLIED / 16 remain PARTIAL**
in this evidence ledger: `reality.carrier.values.sim_state` matched ready=5
in both getSimState readers on two distinct cold user17 processes. No generated
contract status changed and **zero VERIFIED promotions**. C envelope counts each:
**3 PASS / 3 OBSERVED / 10 MISSING / 0 FAIL**. Missing GPU/free-space/package/
sensor/provider/version facts stay unavailable. Both C WebView UA reads returned
AndroidRuntimeException; user-agent remains PARTIAL. External report label is
`external`, with fixture C source/hash explicitly recorded. Production snapshots
matched; temporary entries/bodies/scratch and both users' probe state removed.
No module changes or platform verdict; manager-installed candidate remains loaded.


### 2026-10-23 Rung 1b enriched fixture C — no status promotion

Four cold captures completed under the unchanged alpha28 candidate. User-17 settled repeats each measured legacy 76 PASS / 11 OBSERVED / 2 BLOCKED and envelope 7 PASS / 3 OBSERVED / 6 MISSING. Hashes/markers and production cleanup audit matched; settled retained PASS digests were deterministic. The enriched fixture passes the relaxed schema but fails full coherence (Android package version 16 versus base release 13; WebView 155 versus base UA Chrome 122). Therefore this run is NOT_VALIDATED and makes no new APPLIED/VERIFIED promotion. UA underlying cause remains unavailable. See [evidence summary](runtime-evidence/2026-10-23-181911-rung1b-enriched-c-captures/summary.md). No module change, reboot, release or platform verdict.


### 2026-10-23 Rung 1b coherent v2 — authoring contract gate

An unbound v2 host export completed with a genuine default WebView UA and base facts. Full profile validation still rejects the exact linked WebView UA as UA_FORMAT under the browser-only base grammar. No proposed fixture applied; comparison captures NOT RUN, no APPLIED/VERIFIED promotion. Diagnostics uninstalled and production audit equal; module unchanged. See [stop evidence](runtime-evidence/2026-10-23-183957-rung1b-coherent-v2/summary.md). Existing measured counts remain historical, not relabelled.

### 2026-10-23 Rung 1b coherent v3 — capture gate stop

Shared Python/Kotlin/schema validation now accepts genuine Android WebView UA
syntax without changing existing Chrome profile support. Host-authored fixture C
passes full schema/coherence validation (one real-date PATCH_AFTER_BUILD warning).
The initial permission-setup capture failed while deriving a subscriber expectation
from an absent optional SIM serial. The completed optional-expectation fix passes
both probe unit suites. Its one device retest timed out with the normal location
permission dialog pending; only older-request markers were readable. The three
measured captures and post-grants determinism comparison were NOT RUN.

**NOT_VALIDATED; zero new APPLIED/VERIFIED promotions.** Instance-UA ledger state
is UNVERIFIED; no new instance-UA stack was reached and historical raw reports are
unchanged. Production snapshots match exactly, temporary overlay/probe files were
removed, and user 0 restored. Loaded module immutable files unchanged; mutable
load-log observations are recorded separately. No module action/reboot/release or
platform verdict. See [v3 stop evidence](runtime-evidence/2026-10-23-185155-rung1b-coherent-v3/summary.md).

### 2026-10-23 Rung 1b coherent v3 final — permission UI blocker

The explicitly authorized resume separated UI setup from the report timeout.
User 0 normal location/nearby grants resolved and its cold A smoke completed:
**82 PASS / 7 OBSERVED / 0 FAIL / 0 BLOCKED** legacy; **13 linked PASS**.
User 17's location hierarchy remained after one observed While-using-app tap
and subsequent waits. WindowManager named its permission activity focused, while
InputDispatcher reported **FocusedWindows: none / NO_WINDOW**. No alternate taps,
force-close, shell grants, new code fix or module action was attempted.

**NOT_VALIDATED**: the coherent-C triplet and determinism comparison were NOT RUN;
zero new APPLIED/VERIFIED promotions. User-0 instance-UA remains raw MISSING with
reason `RuntimeException`; no full RUNTIME_EXCEPTION stack was retained. The ledger
keeps that surface UNVERIFIED. Production before/after snapshots match, temporary
overlays and diagnostics state were removed, and user 0 restored. This is not a
platform verdict. See [final-resume evidence](runtime-evidence/2026-10-23-191424-rung1b-coherent-v3-final/summary.md).

### 2026-10-23 Rung 1b coherent v3 — completed measured triplet

The next explicitly authorized UI attempt cleared location in 2.71 seconds;
nearby Allow also resolved normally. No second location attempt or shell grant
was needed. Three coherent-C cold captures completed under the unchanged loaded
alpha.28 candidate (PIDs 28316, 28760, 29223). Each recorded:

- Legacy: **62 PASS / 25 OBSERVED / 2 BLOCKED / 0 FAIL**.
- Runtime envelope: **8 PASS / 3 OBSERVED / 5 MISSING / 0 FAIL**.
- Device Reality: **14 PASS / 17 OBSERVED / 4 MISSING / 8 UNMAPPED**.
- Linked parity: **13 PASS / 0 FAIL**.

Retained PASS digests were identical across all three captures: 8 envelope,
14 Reality and 13 linked rows. All 62 legacy PASS rows used exact MATCH against
the same fixture expectations; raw legacy actuals are redacted, so independent
raw-value hash retention is not claimed. **User-17 legacy determinism: CONFIRMED
post-grants.** No report row was relabelled. The larger OBSERVED count reflects
the coherent fixture deliberately omitting unreadable identifiers and unsupported
expectations, rather than filling them with synthetic host claims.

In this artifact-scoped evidence ledger, **8 of the original 17 PARTIAL paths are
measured APPLIED (7 new, SIM state reaffirmed); 9 remain PARTIAL**:

| Paths | Evidence-ledger state | Measured evidence |
| --- | --- | --- |
| `reality.carrier.values.sim_state` | APPLIED, reaffirmed | both SIM-state envelope readers PASS x3 |
| `reality.compute.values.gpu` | APPLIED | concrete EGL/GLES renderer envelope PASS x3; generic Reality row stays UNMAPPED |
| `reality.packages.values.installed` and `[].package`, `[].version_code`, `[].version_name` | APPLIED | full canonical package/version aggregate PASS x3 |
| `reality.webview.values.provider`, `.version` | APPLIED | corresponding envelope and Reality readers PASS x3 |
| `reality.sensors.values.inventory` and its six child paths | PARTIAL | PROFILE_SURFACE_UNAVAILABLE; no promotion |
| `reality.memory.values.free_storage_gib` | PARTIAL | PROFILE_SURFACE_UNAVAILABLE; no promotion |
| `reality.webview.values.user_agent` | PARTIAL; instance reader UNVERIFIED | default-UA PASS x3, instance-UA MISSING/RuntimeException x3 |

This ledger does not change the generated contract table or prove distinct-value
override/isolation from host-authored equality alone. **Zero VERIFIED promotions**.
All fixture/report/published-body checks passed with correct user 17 and external
fixture C provenance. Three durable records match PID/package/user/installed ABI
digest. Production snapshots match exactly; temporary entries/bodies/probe files
removed, user 0 restored. Module immutable files unchanged; load-log bytes recorded
as mutable evidence. No release, reboot, module action or platform verdict.
See [completed triplet evidence](runtime-evidence/2026-10-23-192355-rung1b-two-ui-attempts/summary.md).
