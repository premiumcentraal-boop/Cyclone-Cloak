# Module filesystem parity surfaces - 2026-10-21

## Outcome

Commit `a4417c6` on main adds read-only filesystem parity surfaces to the
module's compatibility envelope. Module builds and packages for both ABIs;
module unit tests pass. No release, no device actions, no other files changed.

## What landed in `android/module/src/main/cpp/cloak.cpp` (+275 lines)

1. **Parity read surfaces.** After a profile binds, three identity files are
   served from the profile envelope through a memfd view so that file-backed
   reads agree with the existing Java and property reads:
   - `/system/build.prop` and `/vendor/build.prop` render the profile's device
     keys (manufacturer, model, fingerprint, SDK, security patch, serial, etc).
   - `/proc/version` renders a kernel string consistent with the profile's
     claimed SDK.
   - `/proc/cpuinfo` renders an 8-core ARM layout naming the profile hardware.
2. **Read consistency.** `/proc/self/maps`, `/proc/self/mountinfo` and
   `/proc/mounts` snapshots exclude build-environment bookkeeping lines so the
   process view stays coherent with the published envelope.
3. All new hooks install through the existing ShadowHook pattern after the
   property hooks, and respect the `disable_native_hook` kill switch.

## Local verification (already run)

```powershell
$env:JAVA_HOME='C:/Program Files/Eclipse Adoptium/jdk-17.0.20.101-hotspot'
& "$env:TEMP/cloak-build/gradle-8.11.1/bin/gradle.bat" -p android :module:packageModule :module:testDebugUnitTest --offline --no-daemon --console=plain
```

Result: BUILD SUCCESSFUL, module zip `cyclone-cloak-0.11.0-alpha.28.zip`,
tests up to date with no failures. App/forge/schema suites were not rerun; the
change is confined to module native code.

## Requested next steps (no device actions required for 1-3)

1. **Ledger entries.** Add the new parity surfaces to
   `android/probe/src/main/assets/reality-runtime-fields.json` following the
   existing row shape, with status `PARTIAL`, `runtime_covered: false`, and
   reason: native read surfaces implemented and locally compiled; physical
   evidence pending. Suggested field paths:
   - `reality.filesystem.values.build_prop`
   - `reality.filesystem.values.proc_version`
   - `reality.filesystem.values.proc_cpuinfo`
   - `reality.envelope.values.maps_view`
   - `reality.envelope.values.mounts_view`
2. **Focused unit test.** Extend the existing ledger test so the new rows parse
   and keep `runtime_covered == false` until device evidence lands. One test
   class, same style as the current coverage tests.
3. **Release alpha.29** through the standard release workflow after 1-2 land.
   Version code 53. Notes: filesystem parity read surfaces added; device
   evidence pending; no platform verdict.
4. **Next device session (separately scheduled).** Extend the probe with read
   rows for `/system/build.prop`, `/proc/version`, `/proc/cpuinfo` and the
   maps/mounts views, then run the standard cold/restore lifecycle on users 0
   and 17 and classify results under the existing policy. Rows stay
   non-verified until that run.

## Limits

Read-only synthetic content for the listed paths; writes are not intercepted.
Substring-based snapshot filtering is intentionally conservative. No platform
verdict is claimed. Historical evidence is unchanged.
