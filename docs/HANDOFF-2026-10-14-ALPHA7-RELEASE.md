# Alpha.7 release handoff

## Scope

Starting main: `27d3a11caf2d69fd78e5bb50b97cf1b657be1ed1`, including the native
property callback hook and probe row. Release: `v0.11.0-alpha.7`, code **31**.
This run changes only packaging metadata and documentation. No device tests,
storage/schema, connector, binding, publication or verification-logic changes.

Changed files: `android/app/build.gradle`, `android/module/module.prop`,
`README.md`, `docs/ARCHITECTURE.md`, `docs/CURRENT-STATE.md`,
`docs/RELEASE_0.11.0-alpha.7.md`, and this handoff.

## Exact local commands and results

```powershell
$env:JAVA_HOME='C:/Program Files/Eclipse Adoptium/jdk-17.0.20.101-hotspot'
& "$env:TEMP/cloak-build/gradle-8.11.1/bin/gradle.bat" -p android :app:testDebugUnitTest :module:testDebugUnitTest :app:assembleDebug --offline --no-daemon
& "$env:TEMP/cloak-build/gradle-8.11.1/bin/gradle.bat" -p android :probe:assembleDebug --offline --no-daemon
uv run --with pytest -- python -m pytest forge/tests -q
uv run --with pytest --with jsonschema -- python -m pytest forge/schema_tests -q
uv run -- python tools/verify_release.py --tag v0.11.0-alpha.7
git diff --check
```

App: **398 total, 375 passed, 23 existing Windows POSIX-shell skips**, no
failures/errors. Module: **12 passed**. Forge: **227 passed**. Schema: **33
passed**. Debug app, module packaging, both probe variants, metadata agreement
and whitespace checks passed.

App/module build produced no new compiler warnings. Diagnostics probe manifest
warned that its removal marker for `com.google.android.gms.permission.AD_ID`
had no other matching declaration; build succeeded. This marker is unchanged.

## Publication gate

The unchanged release workflow reruns the suites, builds/signs using existing CI
credentials, and verifies tag/version/code, both module ABIs and exact embedded
module ZIP agreement before upload. Release source: `a9bb38d761f10666628dd1c2a51711792d2aee5b`.
[Release CI](https://github.com/premiumcentraal-boop/Cyclone-Cloak/actions/runs/38479788715) completed successfully.
[Published prerelease](https://github.com/premiumcentraal-boop/Cyclone-Cloak/releases/tag/v0.11.0-alpha.7). Uploaded assets: `app-release.apk` and
`cyclone-cloak-0.11.0-alpha.7.zip`.

Asset sizes: APK **43,048,058 bytes**, module ZIP **1,677,627 bytes**. CI verified
alpha.7/code 31, module ABIs and exact embedded module ZIP agreement. CI warnings
were existing setup-java/Node deprecations and an unmatched uv cache glob; no
compiler warnings. Downloaded assets matched the published SHA-256 digests;
the APK embedded the exact ZIP, with alpha.7/code 31 module metadata.
`apksigner verify --verbose` confirmed APK Signature Scheme v2 with one signer.
Publication headers are finalized in a documentation-only
follow-up commit; the release tag is unchanged.

CI build and artifact commands:

```sh
gradle -p android :app:testDebugUnitTest :module:testDebugUnitTest --no-daemon
gradle -p android :app:assembleRelease :module:packageModule --no-daemon
python3 tools/verify_release.py --tag v0.11.0-alpha.7 --artifacts
```

## Evidence limits

`properties.nativeCallback.ro.product.model` is exercised by probe source and
builds locally. Physical-device proof is **pending**; older evidence does not
cover that row, updated fixture A bootloader/baseband expectations, or this new
artifact automatically. No platform verdict is claimed. Activation/reboot and
device coverage remain a separately authorized follow-up.
