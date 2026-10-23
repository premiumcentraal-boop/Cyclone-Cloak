# User 17 fixture selection — local checks complete

Result: DEVICE_UNAVAILABLE. ADB inventory is empty; no installation, switch, capture, permission change or cleanup action was attempted. Physical validation remains pending.

Changed: standalone runner and test_user_switch_capture.py only. User 0 selects synthetic fixture A; user 17 selects B. Existing switch-wait, cold launch, private report reader, markers and switch-back cleanup remain active. No binding or publication is used.

Checks: both probe builds and both probe unit suites passed; 34 host tests passed. git diff --check and git diff --cached --check passed. See provenance.json for per-flavor test counts and APK SHA-256. Nothing staged or committed. No coverage, parity or platform verdict is claimed.

Commands:
```powershell
$env:JAVA_HOME='C:/Program Files/Eclipse Adoptium/jdk-17.0.20.101-hotspot'
& "$env:TEMP/cloak-build/gradle-8.11.1/bin/gradle.bat" -p android :probe:testStandardDebugUnitTest :probe:testDiagnosticsDebugUnitTest :probe:assembleStandardDebug :probe:assembleDiagnosticsDebug --offline --no-daemon --console=plain
uv run --offline -- python -X utf8 -m unittest discover -s android/probe/tests -v
git diff --check
git diff --cached --check
C:/Users/Agent/android-sdk/platform-tools/adb.exe devices -l
```

Next: reconnect the phone, then perform the single authorized validation attempt. This archive is local build evidence only, not a device run. Prior physical evidence is unchanged.
