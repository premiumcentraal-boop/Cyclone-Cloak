# Part 2 preflight — not run

Phone connected. User 0 RUNNING_UNLOCKED; user 17 not started; active user 18. No install, capture, switch, permission/AppOps modification, production action or module action performed. No device-validation claim.

Both probe builds and 288 probe unit tests passed. 34 host tests passed. Both Git whitespace checks passed; existing line-ending notices only.

Commands:
```powershell
$env:JAVA_HOME='C:/Program Files/Eclipse Adoptium/jdk-17.0.20.101-hotspot'
& "$env:TEMP/cloak-build/gradle-8.11.1/bin/gradle.bat" -p android :probe:testStandardDebugUnitTest :probe:testDiagnosticsDebugUnitTest :probe:assembleStandardDebug :probe:assembleDiagnosticsDebug --offline --no-daemon --console=plain
uv run --offline -- python -X utf8 -m unittest discover -s android/probe/tests -q
git diff --check
git diff --cached --check
```

Required next step: open existing Android user 17 normally and unlock it if necessary, then return to user 0 while leaving 17 running. The existing runner rejects stopped users; no alternate launch or package-enable mechanism was attempted. Nothing staged or committed. Prior evidence preserved.
