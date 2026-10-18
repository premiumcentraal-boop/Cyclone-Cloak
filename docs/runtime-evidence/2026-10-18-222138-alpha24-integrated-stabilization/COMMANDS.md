# Exact verification commands

```powershell
$env:JAVA_HOME='C:/Program Files/Eclipse Adoptium/jdk-17.0.20.101-hotspot'
$env:ANDROID_HOME='C:/Users/Agent/android-sdk'
$env:CLOAK_TEST_WSL='1'
& "$env:TEMP/cloak-build/gradle-8.11.1/bin/gradle.bat" -p android :app:testDebugUnitTest :module:testDebugUnitTest :probe:testStandardDebugUnitTest :probe:testDiagnosticsDebugUnitTest :app:assembleDebug :app:assembleRelease :module:packageModule :probe:assembleStandardDebug :probe:assembleDiagnosticsDebug --offline --no-daemon --console=plain
uv run --with pytest -- python -X utf8 -m pytest forge/tests -q
uv run --with pytest --with jsonschema -- python -X utf8 -m pytest forge/schema_tests -q
uv run -- python -X utf8 -m unittest discover -s android/probe/tests -v
uv run -- python -X utf8 tools/verify_release.py --tag v0.11.0-alpha.24 --artifacts
git diff --check
git diff --cached --check
```

Clean detached diagnostics source/build: see build-provenance.json. Installation,
verification and read-only audits: install_candidate.py and verify_signed.py;
matching artifact hashes are in installation-artifacts.json and signed-artifact-verification.json.
The module required one reboot for this candidate. User 17 was started normally,
not force-unlocked. No permissions, AppOps or production bindings were changed.

```powershell
$env:PYTHONPATH="$PWD/tools"
uv run -- python -X utf8 "$p/focused_warm_runner.py" --source-checkout "$env:TEMP/cloak-alpha24-integrated-clean" --adb C:/Users/Agent/android-sdk/platform-tools/adb.exe --serial 3B171FDJH0061G --secondary 17 --output "$p/focused-warm"
uv run -- python -X utf8 "$p/alpha24_lifecycle_runner.py" --source-checkout "$env:TEMP/cloak-alpha24-integrated-clean" --adb C:/Users/Agent/android-sdk/platform-tools/adb.exe --serial 3B171FDJH0061G --secondary 17 --cycles 3 --output "$p/lifecycle"
```

`$p` is this evidence directory. The source commit is 49564ce; the Android gate
ran on code-identical 625e691 before a Python schema-test import-only correction.
No repeat installation was performed by either harness invocation.
