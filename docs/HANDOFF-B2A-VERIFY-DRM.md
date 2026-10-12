# B2A verify sheet: DRM unique-id reads

Status: fix committed on main (`9a6a1e7`), not yet verified on a device.

## Root cause (already fixed)

The RC3 device matrix showed `drm.deviceUniqueId VALUE_MISMATCH`. The Java hook for
`MediaDrm.getPropertyByteArray` only matched the property name `deviceId`, but apps read
`MediaDrm.PROPERTY_DEVICE_UNIQUE_ID`, whose string value is `deviceUniqueId`. The stored
identity value was therefore never served for that read.

The fix (one condition + one test) makes the hook answer both property names from the
stored widevine value. Local result: full module unit suite green, including the new
`widevineVaultServesDeviceUniqueIdProperty` test.

## What to verify on device (one pass)

1. Build from current `main`: `:app:assembleRelease` and `:module:packageModule`.
2. Install both artifacts on the test phone (module zip via Magisk, APK via adb), reboot once.
3. Bind the probe app to profile A, then run:
   `uv run python tools/run_runtime_probe.py --serial <serial> --user 0 --output docs/runtime-evidence/b2a-drm.json`
4. Check in the JSON: `drm.deviceUniqueId` is `PASS` for both bound profiles and the
   unbound run still shows the host's real value.
5. In logcat, confirm the one-shot line `widevine identity served` appears in the probe
   process while bound (not `widevine hook fired but vault empty`).
6. Update `docs/RUNTIME-COVERAGE.md`: the DRM row moves from FAIL to PASS and the
   verified-read count goes from 53/70 to 54/70 (or note any new discovery honestly).

## Notes for the next sprint decision

- Pine `core:0.3.0` has no native-method hook API (34 classes, none Native). All
  native-level reads must keep using the existing `cloak.cpp` + ShadowHook path; do not
  spend time trying to route native reads through Pine.
- After this verify passes, the next coverage targets per the runtime matrix are the two
  OBSERVED fixture gaps (`build.BOOTLOADER`, `build.getRadioVersion`) which only need
  values added to the test profiles, not new hook code.