# B3B diagnostics probe / partial runner â€” 2026-10-13

## Result

Added diagnostics flavor and launcher (`dev.cyclone.cloak.probe.diag`, version `1.0-diag`), delayed reads until permission results arrive, and added explicit permission-denial reasons. The standard manifest remains unchanged and its APK declares no permissions. Added `--phases all` (default) or a comma-separated subset; canonical order, package-aware temporary bindings, package/report checks, cleanup and redaction remain intact. Partial matrices show missing phases as `not run` and never claim VERIFIED proof.

The requested bound-a device run completed without reboot or module installation:

| Read | Earlier default result | Diagnostics result | Reason |
| --- | --- | --- | --- |
| wifi.serviceMac | BLOCKED | PASS | MATCH |
| wifi.ssid | BLOCKED | OBSERVED | NO_PROFILE_EXPECTATION |
| bluetooth.name | BLOCKED | OBSERVED | NO_PROFILE_EXPECTATION |

**Final raw counts: 63 PASS, 1 FAIL, 0 BLOCKED, 6 OBSERVED (70 total).** All 70 proof classifications are INCOMPLETE because only bound-a ran. Historical five-phase B2A evidence remains **54 VERIFIED, 2 FAIL, 8 MATCH_ONLY, 3 BLOCKED, 3 UNMAPPED**; this single phase does not replace or increase that proof.

The FAIL is `camera.characteristics`: current fixtures include newer schema 0.3 camera expectations, while the device still runs the earlier RC3 DRM module. This is an expected version mismatch, not a defect in this sprint. Camera rows will be verified during the coordinated device pass after installing the v0.11.0-alpha.1 module. SSID/Bluetooth name are observable and await profile-backed expectations in a future sprint. Installed arm64 hash: `f87b218e5763a2084093b817c42eaee632390e7d1cb72a686851a469e4e3c4b9`. The runner metadata records the installed binary, grants, selected phases, model and API. Both probes remain installed side by side; the temporary diagnostics binding/profile was cleaned up.

## Corrections to handoff assumptions

- `ProbeActivity` was final; it needed to become extensible for the requested subclass.
- SSID and Bluetooth name have no fixture expectations. Readable values therefore yield OBSERVED, not PASS/FAIL; expectations were not invented.
- The requested three runtime permissions alone do not unlock Wi-Fi service reads. The device confirms the normal `ACCESS_WIFI_STATE` prerequisite is missing. The owner explicitly authorized adding ACCESS_WIFI_STATE and ACCESS_COARSE_LOCATION to diagnostics only. With those prerequisites and runtime grants, Wi-Fi MAC passes and SSID becomes observable. The initial blocked run remains in `runtime-evidence/b3b-diagnostics-2026-10-13-before-prerequisites.json`.
- Android 12+ requires requesting ACCESS_COARSE_LOCATION alongside ACCESS_FINE_LOCATION for the location dialog. Diagnostics now declares coarse location and requests the coarse/fine pair together. The final measurement granted all runtime permissions through adb; the interactive allow/deny dialog itself was not device-tested in this run. See [Android location guidance](https://developer.android.com/develop/sensors-and-location/location/permissions/runtime) and [WifiManager API](https://developer.android.com/reference/android/net/wifi/WifiManager).
- Adding flavors moves Android's native output paths. The standard flavor is copied back to the historical default path after assembly. `:probe:assembleDebug` builds both flavors; `:probe:assembleStandardDebug` builds just the standard variant and updates that copy.
- The checkout already contains newer camera work and the published v0.11.0-alpha.1 tag. The installed phone module remains v0.10.0-rc.3/code 24. Source state is not evidence of installed runtime behavior.

## APK paths

- Default: `android/probe/build/outputs/apk/debug/probe-debug.apk`.
- Diagnostics: `android/probe/build/outputs/apk/diagnostics/debug/probe-diagnostics-debug.apk`.

The optional advertising-ID SDK flag uses the pre-existing dependency; no new dependency was added. Default APK permission inventory was checked with aapt and is empty.

## Exact local test/build commands

```powershell
$env:JAVA_HOME='C:/Program Files/Eclipse Adoptium/jdk-17.0.20.101-hotspot'
& "$env:TEMP/cloak-build/gradle-8.11.1/bin/gradle.bat" -p android :probe:assembleStandardDebug :probe:assembleDiagnosticsDebug -PprobeAdvertisingSdk=true :module:testDebugUnitTest :app:testDebugUnitTest --offline --no-daemon
& "$env:TEMP/cloak-build/gradle-8.11.1/bin/gradle.bat" -p android :probe:assembleDebug -PprobeAdvertisingSdk=true --offline --no-daemon
& "$env:TEMP/cloak-build/gradle-8.11.1/bin/gradle.bat" -p android :probe:assembleDiagnosticsDebug -PprobeAdvertisingSdk=true --offline --no-daemon
uv run -- python -m unittest discover -s android/probe/tests -v
uv run --with pytest -- python -m pytest forge/tests -q
```

Results: module 11 tests/zero failures, app 285 tests/zero failures (262 passed, 23 skipped), runner 6 tests passed, forge 186 passed. Both variants built successfully; the approved diagnostics-only follow-up build also passed. Initial diagnostics manifest merge failed on the application label; adding the explicit label override fixed it, and the same build/test command then succeeded. No compiler warnings were emitted. Git reports its configured LF-to-CRLF normalization notice; whitespace checks pass. The app test dependency graph packages a module ZIP locally, but nothing installed that module on the phone.

## Exact device/evidence commands

```powershell
& 'C:/Users/Agent/android-sdk/build-tools/35.0.0/aapt.exe' dump permissions android/probe/build/outputs/apk/debug/probe-debug.apk
& 'C:/Users/Agent/android-sdk/build-tools/35.0.0/aapt.exe' dump badging android/probe/build/outputs/apk/diagnostics/debug/probe-diagnostics-debug.apk
& 'C:/Users/Agent/android-sdk/platform-tools/adb.exe' -s 3B171FDJH0061G install --user 0 -r android/probe/build/outputs/apk/diagnostics/debug/probe-diagnostics-debug.apk
& 'C:/Users/Agent/android-sdk/platform-tools/adb.exe' -s 3B171FDJH0061G shell pm grant --user 0 dev.cyclone.cloak.probe.diag android.permission.ACCESS_COARSE_LOCATION
& 'C:/Users/Agent/android-sdk/platform-tools/adb.exe' -s 3B171FDJH0061G shell pm grant --user 0 dev.cyclone.cloak.probe.diag android.permission.ACCESS_FINE_LOCATION
& 'C:/Users/Agent/android-sdk/platform-tools/adb.exe' -s 3B171FDJH0061G shell pm grant --user 0 dev.cyclone.cloak.probe.diag android.permission.NEARBY_WIFI_DEVICES
& 'C:/Users/Agent/android-sdk/platform-tools/adb.exe' -s 3B171FDJH0061G shell pm grant --user 0 dev.cyclone.cloak.probe.diag android.permission.BLUETOOTH_CONNECT
uv run -- python tools/run_runtime_probe.py --adb C:/Users/Agent/android-sdk/platform-tools/adb.exe --serial 3B171FDJH0061G --user 0 --apk android/probe/build/outputs/apk/diagnostics/debug/probe-diagnostics-debug.apk --phases bound-a --output docs/runtime-evidence/b3b-diagnostics-2026-10-13.json
uv run -- python tools/render_runtime_matrix.py docs/runtime-evidence/b3b-diagnostics-2026-10-13.json docs/RUNTIME-MATRIX-B3B-2026-10-13.md
```

The single phase was repeated after the approved prerequisite permission update. The final JSON records the successful rerun; the earlier blocked results are retained separately. ACCESS_WIFI_STATE is granted at install time and needs no runtime prompt. A full five-phase run was deliberately not performed. Example subsets: `--phases bound-a` or `--phases bound-a,bound-b`; unknown/duplicate/empty phase names are rejected before device actions.

## Changed files and boundaries

- `android/probe/build.gradle`: flavors and default output copy.
- `android/probe/src/main/java/dev/cyclone/cloak/probe/ProbeActivity.java`: delayed-read extension points and redacted permission reasons.
- `android/probe/src/diagnostics/AndroidManifest.xml`: diagnostics-only permission and launcher entries.
- `android/probe/src/diagnostics/java/dev/cyclone/cloak/probe/DiagnosticsProbeActivity.java`: runtime permission gate and denial handling.
- `android/probe/tests/test_runner.py`: host regression tests.
- `tools/run_runtime_probe.py`: phases, APK package/launcher resolution, scoped binding and permission metadata.
- `tools/render_runtime_matrix.py`: missing phase display.
- `docs/RUNTIME-COVERAGE.md`: separate partial measurement and honest counts.
- `docs/RUNTIME-MATRIX-B3B-2026-10-13.md`: generated partial matrix.
- `docs/runtime-evidence/b3b-diagnostics-2026-10-13.json`: final redacted evidence.
- `docs/runtime-evidence/b3b-diagnostics-2026-10-13-before-prerequisites.json`: initial missing-permission evidence.
- This dated handoff.

No app/module/forge/schema/contract source, release metadata or dependencies changed. No reboot, module installation, release or tag. The owner authorized committing and pushing this sprint to main. Full five-phase proof of newly readable rows is still pending; this partial measurement does not claim it.
