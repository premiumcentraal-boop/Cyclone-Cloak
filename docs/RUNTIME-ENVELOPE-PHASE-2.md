# Runtime envelope phase 2 — unreleased

Selected read-only callbacks use saved facts inside a bound process. No release
or device run was performed. Physical verification is **UNVERIFIED**; this is no
platform verdict, hardware change, provider installation or DRM provisioning.

## Scope and failure policy

The unchanged native user/package lookup selects the profile. Only a bound
process receives a bounded, ASCII-escaped in-memory projection through JNI and
the existing pinned hook loader. No new storage, reader format, publication,
connector contract or JSON system property is introduced. Snapshots exclude
credentials and are never logged. Each callback captures an immutable snapshot.
A warm process can retain it after binding removal; cold unbound restoration
requires a new process. Android user/package lookup is unchanged.

Valid legacy 0.2/0.3 profiles and profiles without Reality opt out of new hooks.
Inside an active envelope, missing/invalid/unknown/unavailable required facts
produce `PROFILE_SURFACE_UNAVAILABLE`. Real exceptions/permission denials are
preserved. Unsupported default sensors return null; invalid SIM slots return
UNKNOWN. Unexpected implementation errors preserve the original result and log
only a fixed message. Hook/API installation failures are explicit limitations.
Incomplete 0.4 profiles can therefore make formerly successful reads unavailable;
callers must handle this. It has not been measured on device.

## Surface matrix

Every new/changed callback is **physically UNVERIFIED**. None is newly APPLIED.

| Group | Implemented path | Boundary |
| --- | --- | --- |
| Camera | Existing camera2 level/pixel-array readers reject unmapped data in an active envelope. Sorted real IDs/facing replace query-order assignment; synchronized weak object keys replace identity-hash lookup. | Real IDs/facing and operation stay unchanged. Profile pixel dimensions cannot exceed real dimensions; hardware level cannot be promoted. No invented IDs, stream configurations or capabilities. |
| Sensors | Filter actual `getSensorList/getDefaultSensor` objects by unique declared type. Name, delay, range and resolution getters use declared descriptors. | No sensor objects/types/events invented; no sampling changes. Descriptors cannot claim better range, resolution or delay limits than the real sensor. Repeated types are rejected. Dynamic sensor APIs are not covered. |
| RAM | Digest-only `MemoryInfo.totalMem` observation. | Nominal SKU RAM is not kernel-visible total memory; no byte count is fabricated. |
| Storage | Explicit `free_storage_gib` drives `StatFs.getAvailableBytes/getAvailableBlocksLong`, bounded by actual filesystem total and requiring block alignment. Constructor/restat tracking restricts it to canonical private-data paths. | Other users/apps, external/system paths and total capacity stay host-owned. Unknown tracking fails closed. Deprecated 32-bit APIs are not covered. Advertised storage is not filesystem size. |
| Compute | `GLES20.glGetString(GL_RENDERER)` uses declared metadata after a successful real GL read. | GL vendor/version/extensions, ABI, rendering and scheduler/physical-core counts are not rewritten. Existing SoC observation remains read-only. |
| Packages | Copy visible `getPackageInfo/getInstalledPackages` results for the fixed allowlist; attempt int and API-33 flags overloads. | Preserve membership/names and denials. No visibility expansion. Allowlist: android, Play Store, Play Services, GSF, Google WebView, Chrome. |
| WebView | Real provider must match declared provider/package version. Current-package metadata, default UA and concrete settings UA are checked consistently. | No engine switch, invented installation or abstract-method hook. Base and Reality UA must agree. |
| Display | `Display.getRealSize` additionally uses authoritative base dimensions. | Existing mode/metrics/refresh paths remain; no logical-window or physical display reconfiguration. |
| SIM | Both `getSimState` overloads use declared state and base slot-count bounds. | Missing state is unavailable, invalid slot UNKNOWN; existing operator/count paths remain. |
| DRM | Real `securityLevel` is match-only gated against declared metadata. | Mismatch is unavailable, never substituted/elevated. No keys, provisioning or other properties touched. |

Capacity distinctions follow Android's [MemoryInfo](https://developer.android.com/reference/android/app/ActivityManager.MemoryInfo)
and [StatFs](https://developer.android.com/reference/android/os/StatFs) semantics.
Sensor paths preserve actual [SensorManager](https://developer.android.com/reference/android/hardware/SensorManager)
objects and WebView preserves the actual
[provider identity](https://developer.android.com/reference/android/webkit/WebView#getCurrentWebViewPackage()).

## Contract and diagnostics

The 97-path shared table now has 10 APPLIED linked paths, 17 PARTIAL, 3 PENDING,
27 DIAGNOSTIC_ONLY, 37 HOST_ONLY and 3 UNSUPPORTED. The partial counts include
containers and preserved membership/type paths, not independent physical APIs.
Generated Kotlin, probe table, contract schema and vectors match the source.
DRM stays descriptive/match-only. Existing alpha.24 evidence does not cover this
module or the new rows.

Optional `runtime_envelope` format 1 adds 15 stable digest-only rows, fixed reasons
and durations; `verification` is always `NOT_VERIFIED`, including hash matches.
Nested numbers and inventory order normalize before comparison; ordered display
tuples keep their order. Existing legacy/linked sections keep their format.
See `schema/runtime-envelope-report.schema.json`. No raw descriptor values are
exported by this extension. RAM, total storage, scheduler cores and camera
aggregates deliberately have no invented exact expectation.

GPU observation uses a private 1x1 EGL pbuffer, destroys its own surface/context
and restores the thread's prior context. It never terminates the shared display.
API/context failures remain explicit. Permissions and package visibility stay as-is.

## Required device evidence

`android/module/src/test/resources/envelope-a.json` and `envelope-b.json` are
synthetic unit fixtures, not hardware definitions or device evidence. Kotlin
Robolectric tests reconstruct snapshots repeatedly on APIs 34/35, exercise actual
callback bodies and check independence, visibility preservation and failures.
They cannot prove ART hook installation, actual cross-user isolation or physical
cold restoration.

For each new surface, a later device pass still needs two source-supported
fixtures, repeated cold bound captures, a cold unbound control, explicit API and
permission limitations, production audits and cleanup proof. Keep unsupported
catalog facts unknown; never invent hardware to obtain a passing row.
