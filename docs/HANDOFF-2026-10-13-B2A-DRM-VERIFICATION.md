# B2A DRM verification handoff — 2026-10-13

## Outcome

Installed the locally patched module through Magisk, rebooted the test phone, and verified the existing DRM fix `9a6a1e7`. Pixel 8 (arm64), API 36, Android user 0. `drm.deviceUniqueId` is VERIFIED: distinct profiles A/B pass, repeated A is stable, and unbound before/after values match. Three bound cold starts log `widevine identity served`; no vault-empty log was found.

Overall: **54 VERIFIED, 2 FAIL, 8 MATCH_ONLY, 3 BLOCKED, 3 UNMAPPED**, out of 70 reads. Raw bound results: A and repeated A each 62 PASS/0 FAIL; B 64 PASS/0 FAIL. Unbound fixture mismatches are expected controls, not runtime failures.

Installed module: v0.10.0-rc.3/code 24, arm64 SHA-256 `f87b218e5763a2084093b817c42eaee632390e7d1cb72a686851a469e4e3c4b9`. This is a patched local build, not a newly published release. Main HEAD was `f244089`, containing the fix and latest published tag v0.10.0-rc.3. No source, schema, connector, version, or publication changes were made.

## Changed files

- `docs/HANDOFF-B2A-VERIFY-DRM.md`: completed status, results and corrected callback/Pine claims.
- `docs/RUNTIME-COVERAGE.md`: current binary, DRM proof, counts and limitations.
- `docs/RUNTIME-MEASUREMENTS.md`: regenerated 70-read matrix.
- `docs/RUNTIME-MATRIX-B2A-2026-10-13.md`: dated matrix snapshot.
- `docs/DEFERRED-DEVICE-CHECKS.md`: authorized partial resumption and remaining checks.
- `docs/runtime-evidence/b2a-drm.json`: redacted five-phase device evidence.
- `docs/runtime-evidence/b2a-drm-log.txt`: filtered serving logs.
- This handoff: exact commands, results and limitations.

Earlier evidence files remain intact. Host identifier observations are hashed; fixture expectations are synthetic.

## Exact test and build commands

PowerShell setup:

```powershell
$env:JAVA_HOME='C:/Program Files/Eclipse Adoptium/jdk-17.0.20.101-hotspot'
```

```powershell
& "$env:TEMP/cloak-build/gradle-8.11.1/bin/gradle.bat" -p android :module:testDebugUnitTest --offline --no-daemon --rerun-tasks
& "$env:TEMP/cloak-build/gradle-8.11.1/bin/gradle.bat" -p android :app:assembleRelease :module:packageModule --offline --no-daemon
& "$env:TEMP/cloak-build/gradle-8.11.1/bin/gradle.bat" -p android :probe:assembleDebug -PprobeAdvertisingSdk=true --offline --no-daemon
uv run -- python tools/run_runtime_probe.py --adb C:/Users/Agent/android-sdk/platform-tools/adb.exe --serial 3B171FDJH0061G --user 0 --output docs/runtime-evidence/b2a-drm.json
uv run -- python tools/render_runtime_matrix.py docs/runtime-evidence/b2a-drm.json docs/RUNTIME-MEASUREMENTS.md
uv run -- python tools/render_runtime_matrix.py docs/runtime-evidence/b2a-drm.json docs/RUNTIME-MATRIX-B2A-2026-10-13.md
```

Module suite: **10 tests, zero failures/errors/skips**, including `widevineVaultServesDeviceUniqueIdProperty`. All three Gradle invocations succeeded. App release APK and module ZIP built successfully; probe built with the real advertising-ID SDK. No compiler warnings were emitted. App/Python suites were not rerun in this verification-only run.

## Installation and reboot

Commands used (ADB executable as above):

```powershell
adb -s 3B171FDJH0061G push android/module/build/module/cyclone-cloak-0.10.0-rc.3.zip /data/local/tmp/cloak-b2a-drm.zip
adb -s 3B171FDJH0061G shell 'su -c "magisk --install-module /data/local/tmp/cloak-b2a-drm.zip"'
adb -s 3B171FDJH0061G reboot
adb -s 3B171FDJH0061G wait-for-device
adb -s 3B171FDJH0061G shell getprop sys.boot_completed
adb -s 3B171FDJH0061G shell 'su -c "sha256sum /data/adb/modules/cyclone_cloak/zygisk/arm64-v8a.so"'
```

Magisk installation succeeded; boot completed and the loaded module file hash matched the new artifact. The freshly built companion release APK is unsigned, so the existing companion and its data were retained. The DRM change is entirely in the module. The debug probe was installed by the runner; its temporary fixtures/binding were cleaned up after the five phases.

Initial attempts encountered an app-start error after boot and an installer timeout. Android's compatibility warning was acknowledged; Play Protect's optional send-for-check prompt was declined. The final runner invocation exited successfully with all five phases recorded. An obsolete pending installer client was stopped after successful completion.

## Warnings and remaining work

- Android displayed a 16 KB alignment compatibility warning for the debug probe's native library. This is a device install warning, not a Gradle compiler warning.
- Two FAIL matrix rows remain fixture gaps for profile A (`build.BOOTLOADER`, `build.getRadioVersion`).
- Three permission-blocked Wi-Fi/Bluetooth reads, camera/sensor expectations and other unmapped reads remain unresolved.
- This proves the measured MediaDrm unique-ID read on this one device/user, not all DRM properties/security levels, Android versions, ARM32 or secondary users.
- Signed companion upgrades, broader Root Doctor/publication workflows and other deferred checks remain unverified.
- No GitHub release, tag or push was performed.
