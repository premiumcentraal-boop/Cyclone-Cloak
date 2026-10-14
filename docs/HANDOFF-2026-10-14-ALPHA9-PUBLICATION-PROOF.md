# Alpha.9 state publication proof

Status: prerelease published after green CI; normal Main-app publication and
two-profile binding preservation passed. Independent profile-side publication
remains unverified because Cyclone could not open the receiving profile.

[Release](https://github.com/premiumcentraal-boop/Cyclone-Cloak/releases/tag/v0.11.0-alpha.9)
· [green CI](https://github.com/premiumcentraal-boop/Cyclone-Cloak/actions/runs/38573519136)

## Scope and commits

- `1bd4d45`: preserved diagnostics backend and Android publication-lock repair.
- `87da2c6`: alpha.9 release metadata, app/module code 33 and release documentation.
- Native module behavior, reader/storage layout, identity semantics and connector
  protocol are unchanged. No permissions or networking added.

## Cause and correction

Android mksh does not inherit descriptor 9 into external Toybox flock. The previous
sequence therefore reported `Bad file descriptor` as false publisher contention.
The repaired script holds the unchanged lock inode through inherited descriptor 0.
Path-based flock is unsupported by this device's Toybox and is not used.

## Local checks

428 app tests, 12 module tests, 227 forge tests, 33 schema tests and 3 diagnostics
helper tests passed. App tests used WSL for actual POSIX publication scripts, with
zero skipped tests. Debug APK and module ZIP built; no compiler warnings.

```powershell
$env:JAVA_HOME='C:/Program Files/Eclipse Adoptium/jdk-17.0.20.101-hotspot'
$env:CLOAK_TEST_WSL='1'
& "$env:TEMP/cloak-build/gradle-8.11.1/bin/gradle.bat" -p android :app:testDebugUnitTest :module:testDebugUnitTest :app:assembleDebug :module:packageModule --offline --no-daemon
uv run --with pytest -- python -m pytest forge/tests tools/tests -q
uv run --with pytest --with jsonschema -- python -m pytest forge/schema_tests -q
uv run -- python tools/verify_release.py --tag v0.11.0-alpha.9
git diff --check
git push origin main
git push origin v0.11.0-alpha.9
```

## Device evidence

The attached device reports Pixel 8 (`shiba`), rather than the requested Pixel 7 Pro.
Initial app/module are alpha.8/code 32. The read-only baseline published index has
three entries, recorded without identity contents in
`runtime-evidence/publication-alpha9-baseline-2026-10-14.json`.

The released signed APK installed with `install -r`, preserving app data. Its
SHA-256 agrees with GitHub, APK signature verification passed, and its embedded
module ZIP exactly matches the released ZIP. Normal Health → Check & repair queued
the bundled module. The app explicitly reported **Reboot required**; exactly one
reboot activated alpha.9/code 33. No uninstall, data clear or second reboot occurred.

Post-reboot Check & repair reported **COMPLETED**. Diagnostics include completed
precheck, lock open/acquire, recovery, staging, merge, atomic swap and completion.
No LOCKED, LOCK_FAILED, PUBLISH_FAILED or false publisher error occurred in the
three successful publication transactions retained by the exported bundle.

| Normal binding flow | Saved identity | UI result | Index count |
| --- | --- | --- | --- |
| Profile B trdt, user 14, `com.android.chrome` | Pixel 7 profile 2 | Binding ready | 7 |
| alpha 8 pixel 7 pro, user 16, `com.android.chrome` | Existing alpha 6 test pixel 7 pro | Binding ready | 8 |

The second flow acknowledged the existing identity-sharing warning; no identity
was generated, edited or replaced. All six old local bindings remained present.
Every entry present after the first bind survived the second with unchanged key,
profile ID and publisher. Health reported **Ready: 49 pass, 0 warning, 0 fail**.
Both binds were issued from Main and have publisher 0.

The app's **Save diagnostics bundle** action reported success. Exported evidence:

- `docs/runtime-evidence/cloak-alpha9-device-diagnostics-2026-10-14.zip`
  (3,653 bytes; 256 events; redacted; flush complete; dropped 0; persistence errors 0).
- SHA-256: `f71574f8e916854f92e71948be156ff6876c4315f5b87f4d6681d94a95f9a09a`.
- `runtime-evidence/publication-alpha9-proof-2026-10-14.json` records results and limits.
- Baseline/first-bind/second-bind JSON files retain only index keys, profile IDs,
  publisher IDs and binding directory hashes, without profile bodies or summaries.
- `runtime-evidence/cloak-diagnostics-20261014-154950-284.zip` is supplemental
  read-only helper collection made before the device repair; it is not the app export.

## Release artifacts and commands

| Asset | Bytes | SHA-256 |
| --- | --- | --- |
| app-release.apk | 43,162,770 | `3ec6b3be20c7b1ec75378be982bd440af4fa64b29389350c88507c6550cb9311` |
| cyclone-cloak-0.11.0-alpha.9.zip | 1,677,628 | `f689481565ba63607248c4bd96b8438961d63ace99f35b1eef32e30f17911327` |

CI ran the same forge/schema and Android test tasks, then the existing signed gate:

```text
gradle -p android :app:assembleRelease :module:packageModule --no-daemon
python3 tools/verify_release.py --tag v0.11.0-alpha.9 --artifacts
```

Additional local commands (paths resolve to the exact tools used):

```powershell
$adb='C:/Users/Agent/android-sdk/platform-tools/adb.exe'
$releaseDir=Join-Path $env:TEMP 'cloak-alpha9-release'
gh release download v0.11.0-alpha.9 --dir $releaseDir --pattern app-release.apk --pattern cyclone-cloak-0.11.0-alpha.9.zip
Get-FileHash -Algorithm SHA256 (Join-Path $releaseDir 'app-release.apk'),(Join-Path $releaseDir 'cyclone-cloak-0.11.0-alpha.9.zip')
& 'C:/Users/Agent/android-sdk/build-tools/35.0.0/apksigner.bat' verify --verbose (Join-Path $releaseDir 'app-release.apk')
& $adb -s 3B171FDJH0061G install -r (Join-Path $releaseDir 'app-release.apk')
& $adb -s 3B171FDJH0061G reboot
& $adb -s 3B171FDJH0061G shell getprop sys.boot_completed
& $adb -s 3B171FDJH0061G shell su --mount-master -c 'cat /data/adb/modules/cyclone_cloak/module.prop'
& $adb -s 3B171FDJH0061G shell am start -n dev.cyclone.cloak/.MainActivity
& $adb -s 3B171FDJH0061G shell uiautomator dump /sdcard/cloak-alpha9-ui.xml
& $adb -s 3B171FDJH0061G exec-out cat /sdcard/cloak-alpha9-ui.xml
& $adb -s 3B171FDJH0061G pull /sdcard/Download/cloak-diagnostics-1791987112694.zip docs/runtime-evidence/cloak-alpha9-device-diagnostics-2026-10-14.zip
powershell -NoProfile -ExecutionPolicy Bypass -File tools/diagnose.ps1 -Serial 3B171FDJH0061G -OutputDirectory docs/runtime-evidence
```

Phone UI actions used current observed controls. When the semantic controller failed,
standard `adb shell input tap` and `input swipe` exercised those same app controls.
Representative exact inputs: `input tap 540 645` (first repair),
`input tap 574 350` (post-reboot repair), `input tap 235 1226` and
`input tap 235 1700` (reviewed binding confirmations), `input tap 273 1806`
(export), `input tap 927 2274` (document save). Positions are screen-specific.
Scroll commands used `(540,1900)→(540,400), 250 ms`,
`(540,2200)→(540,250), 100 ms`, and reverse scrolling after inspecting each page.
Temporary host UI parsing helper: `$env:TEMP/cloak-alpha9-ui.py` (not product source).
Published index was read with `su --mount-master -c
'cat /data/adb/cyclone_cloak/state-v1/index.json'`; captured output was reduced locally
before writing the checked-in evidence.

## Failures and limits

- Actual hardware is Pixel 8, not Pixel 7 Pro. No Pixel 7 Pro proof is claimed.
- Semantic control initially needed an explicit device ID, then failed to activate
  the repair button. Cyclone's human confirmation gate was confirmed by the user.
  Post-reboot action capability remained unavailable; normal ADB UI input was used.
- An additional profile-side writer check could not complete. Direct launch with
  `am start --user 16 -n dev.cyclone.cloak/.MainActivity` returned **Activity does not
  exist** while that user was stopped. Cyclone's normal profile-switch UI then
  reported **couldn't verify the receiving profile** and retained Main (user 0).
  Only publisher 0 exists on this device. Separate-writer mutual exclusion/recovery
  has local shell-test evidence, not a successful second-writer device result.
- A combined final app-launch/evidence-write command was rejected as **blocked by
  policy**; it did not execute. The local evidence document was written separately
  with the file editor. No subsequent device state is inferred from that command.
- Two temporary evidence-parser commands hit Windows output decoding errors; UTF-8
  reruns succeeded. These were host collection errors, not app/build failures.
- Git emitted LF/CRLF normalization notices. Local and CI builds had no compiler
  warnings. No platform verdict, broad hook coverage, power-loss atomicity or
  coordination with old/external publishers is claimed.

Changed product files are listed in the diagnostics and publication-reliability
handoffs. This release run changed only version metadata, release/status docs and
redacted evidence. The two unrelated untracked handoffs were preserved unstaged.
