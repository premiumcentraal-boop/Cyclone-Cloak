# Commands run

```powershell
adb -s 3B171FDJH0061G install --user 0 -r <published>/playEvidence-release.apk
adb -s 3B171FDJH0061G shell cmd package install-existing --user 17 dev.cyclone.cloak.playevidence
uv run --offline --with jsonschema -- python $env:TEMP/cloak-play-device.py prepare
uv run --offline --with jsonschema -- python $env:TEMP/cloak-play-device.py collect
uv run --offline --with jsonschema -- python $env:TEMP/cloak-play-device.py cleanup
uv run --offline --with jsonschema -- python $env:TEMP/cloak-play-device.py audit
$env:JAVA_HOME='C:/Program Files/Eclipse Adoptium/jdk-17.0.20.101-hotspot'
$env:CLOAK_TEST_WSL='1'
& "$env:TEMP/cloak-build/gradle-8.11.1/bin/gradle.bat" -p android :app:testDebugUnitTest :module:testDebugUnitTest :playEvidence:testDebugUnitTest :probe:testStandardDebugUnitTest :probe:testDiagnosticsDebugUnitTest --offline --no-daemon --console=plain
uv run --offline --with pytest --with jsonschema -- python -X utf8 -m pytest forge/tests forge/schema_tests -q
uv run --offline --with jsonschema -- python -X utf8 -m unittest discover -s android/probe/tests -v
git diff --check
```

The temporary operator helper used the existing tools/run_play_evidence.py strict validator. It read the saved profile in memory, staged it directly between app-private directories, and exported no profile body. The raw report was collected via scoped root reads because release run-as is disabled. Production import/review/save and four negative imports used the normal document picker and UI. No Integrity request was initiated.
