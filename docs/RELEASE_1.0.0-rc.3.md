# Cyclone Cloak 1.0.0-rc.3

Prerelease with matching companion/module version **1.0.0-rc.3 / code 62**.

## Scope since RC2

- Cyclone calm-glass visual language: static blue gradient, dark grouped surfaces,
  larger titles, profile faces, capsule actions and status dots.
- Home has a horizontal profile overview and a clear next setup action.
- Profiles uses a three-column gallery and scrollable detail sheets for existing
  readiness checks, binding actions, evidence review and run history.
- Binding confirmation and navigation remain visible below scrolling content.
  Existing validation, warning acknowledgement and confirmation-stamp rules remain.
- Long binding status messages no longer squeeze app package names.
- Phones, Identities, Health, the phone editor and existing dialogs share the palette.

The five existing destinations remain. No Android-user manager, new permissions,
networking, connector changes, binding schema changes or storage migrations were
added. Existing saved profiles and bindings remain compatible. Native module,
publication and runtime behavior are unchanged; module metadata advances solely
to match the companion release.

This source also includes the post-RC2 diagnostics-only WebView UA main-looper
reader change (`3fc15a0`). Its local checks are not physical proof: the recorded
T1 device run still returned `RUNTIME_EXCEPTION:AndroidRuntimeException` on both
UA rows. This remains unresolved and is not described as fixed by this release.

## Changed files

The UI implementation, tests and documentation are enumerated in
[UI-CALM-GLASS.md](UI-CALM-GLASS.md). Release packaging also updates
`android/app/build.gradle` and `android/module/module.prop`.

## Verification

The local RC3 release gate passed, including seven new UI tests:

| Suite | Passed | Failed / errors / skipped |
| --- | ---: | --- |
| App | 791 | 0 / 0 / 0 |
| Module | 80 | 0 / 0 / 0 |
| Play diagnostics | 30 | 0 / 0 / 0 |
| Standard probe | 151 | 0 / 0 / 0 |
| Diagnostics probe | 151 | 0 / 0 / 0 |
| Forge | 1,139 | 0 |
| Schema | 192 | 0 |
| Probe host tests | 34 | 0 |

Play diagnostics lint, debug/release builds, both probe builds, module packaging,
release artifact metadata/embedded-ZIP checks and diff whitespace checks pass.
A preceding final visual check also passed 14 UI tests. No new compiler warnings.

Exact commands from the repository root (PowerShell):

```powershell
$env:JAVA_HOME='C:/Program Files/Eclipse Adoptium/jdk-17.0.20.101-hotspot'
$env:ANDROID_HOME='C:/Users/Agent/android-sdk'
$env:CLOAK_TEST_WSL='1'
& "$env:TEMP/cloak-build/gradle-8.11.1/bin/gradle.bat" -p android :app:testDebugUnitTest :module:testDebugUnitTest :playEvidence:testDebugUnitTest :playEvidence:lintDebug :probe:testStandardDebugUnitTest :probe:testDiagnosticsDebugUnitTest :probe:assembleStandardDebug :probe:assembleDiagnosticsDebug :app:assembleDebug :app:assembleRelease :module:packageModule :playEvidence:assembleRelease --offline --no-daemon --console=plain
uv run --with pytest --with jsonschema --with cryptography -- python -X utf8 -m pytest forge/tests -q
uv run --with pytest --with jsonschema -- python -X utf8 -m pytest forge/schema_tests -q
uv run -- python -X utf8 -m unittest discover -s android/probe/tests -v
uv run -- python -X utf8 tools/verify_release.py --tag v1.0.0-rc.3 --artifacts
git diff --check
git diff --cached --check
```

Signed validation must pass before publication. The published APK must verify
with the existing signing identity and embed the exact published module ZIP.

## Upgrade and evidence limits

Install the signed APK as an update using the existing signing identity. Retain
saved app data; no reset or migration is required. The module ZIP has matching
RC3 packaging metadata but introduces no new runtime behavior. This release run
does not install either artifact or reboot the device.

UI renders use synthetic local test state. Physical visual, large-font and
TalkBack review remain **UNVERIFIED**. RC2 lifecycle and self-visibility evidence
belongs to its measured artifacts and is not automatically evidence for RC3.
Known unavailable/blocked runtime surfaces remain limitations.

No platform verdict, certification, hardware equivalence, permanent trust or
broader device compatibility is claimed.
