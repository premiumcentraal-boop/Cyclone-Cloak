# B2A verify sheet: DRM unique-id reads

Status: fix `9a6a1e7` verified on a Pixel 8/API 36, Android user 0, on 2026-10-13 after authorized module installation and reboot. Coverage is now 54/70 verified reads.

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
5. In logcat, confirm the callback evidence line `widevine identity served` appears in the probe
   process while bound (not `widevine hook fired but vault empty`).
6. Update `docs/RUNTIME-COVERAGE.md`: the DRM row moves from FAIL to PASS and the
   verified-read count goes from 53/70 to 54/70 (or note any new discovery honestly).

## Notes for the next sprint decision

- The earlier claim that Pine cannot intercept native Java methods was not supported by class-name inspection. This device run confirms interception of the measured `MediaDrm.getPropertyByteArray` read; other native surfaces still require their own evidence.
- After this verify passes, the next coverage targets per the runtime matrix are the two
  OBSERVED fixture gaps (`build.BOOTLOADER`, `build.getRadioVersion`) which only need
  values added to the test profiles, not new hook code.
## Completed verification

[Redacted evidence](runtime-evidence/b2a-drm.json) contains all five cold-start phases: unbound before, A, repeated A, B, unbound after. DRM passes for both distinct bound fixtures and repeated A; the original unbound value is restored. [Filtered callback logs](runtime-evidence/b2a-drm-log.txt) show three `widevine identity served` lines and no vault-empty lines.

The installed arm64 module SHA-256 is `f87b218e5763a2084093b817c42eaee632390e7d1cb72a686851a469e4e3c4b9`. Its version label remains v0.10.0-rc.3/code 24; this is a locally patched binary, not a newly published release. Both release artifacts built, but the companion APK was unsigned, so the existing companion and its data were retained. The module and debug probe were installed. See the [completion report](HANDOFF-2026-10-13-B2A-DRM-VERIFICATION.md) for commands and limitations.
