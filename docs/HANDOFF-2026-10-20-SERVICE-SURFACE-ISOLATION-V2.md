# Service surface isolation v2 — handoff

Date: 2026-10-20. Branch: main. Base: bed5535.
Implementation is uncommitted; no commit, push, tag or release was requested.
Earlier uncommitted cohort, instance-realism and runtime-envelope work, plus
unrelated handoffs/evidence, were preserved.

## Result

Added a pure binding-scoped service isolation evaluator with ISOLATED,
SHARED_CONFIRMED, CONFLICT, STALE, UNKNOWN and NEEDS_REVIEW. It checks current
assignment/inventory, allowed and forbidden packages, exact-group sharing consent,
freshness and profile/app/user/artifact binding. Consent and its inventory are
read under one existing store lock. Sticky review flags cannot clear themselves.

Existing explicit diagnostics import saves only measured package observations
for enabled apps bound to the selected identity. Its confirmation stamp covers
that scope. The existing readiness card displays per-app states and reasons and
gates its overall verdict. Another identity's evidence cannot be borrowed.
Passing isolation never supplies missing Play evidence or a platform verdict.

## Changed files in this sprint

New:

- `android/app/src/main/java/dev/cyclone/cloak/play/ServiceSurfaceIsolation.kt`
- `android/app/src/main/java/dev/cyclone/cloak/play/ServiceSurfaceStore.kt`
- `android/app/src/test/java/dev/cyclone/cloak/play/ServiceSurfaceIsolationTest.kt`
- `android/app/src/test/java/dev/cyclone/cloak/play/ServiceSurfaceStoreTest.kt`
- `docs/SERVICE-SURFACE-ISOLATION-V2.md`
- this handoff

Updated:

- `android/app/src/main/java/dev/cyclone/cloak/play/GoogleSurfaceStore.kt`
- `android/app/src/main/java/dev/cyclone/cloak/play/PlayEvidenceImport.kt`
- `android/app/src/main/java/dev/cyclone/cloak/ui/CloakViewModel.kt`
- `android/app/src/main/java/dev/cyclone/cloak/ui/PlayReadinessCard.kt`
- `android/app/src/test/java/dev/cyclone/cloak/play/PlayEvidenceImportTest.kt`
- `android/app/src/test/java/dev/cyclone/cloak/ui/PlayReadinessCardTest.kt`
- `docs/ARCHITECTURE.md`
- `docs/CURRENT-STATE.md`

## Verification

Final counts: **781 app**, **80 module**, **114 standard probe**, **114 diagnostics
probe**, **1,245 forge/schema** tests passed; zero failures/errors. App total
includes **27 new tests**. Debug APK build succeeded. No compiler warnings.
Whitespace checks passed; Git only reported pre-existing LF-to-CRLF notices for
DeviceProfileEngine.kt, module/build.gradle and ProbeReportPolicy.java.

Exact commands, run from `C:/Users/Agent/Cyclone/cloak-repo`:

```powershell
$env:JAVA_HOME='C:/Program Files/Eclipse Adoptium/jdk-17.0.20.101-hotspot'
$env:ANDROID_HOME='C:/Users/Agent/android-sdk'
$env:CLOAK_TEST_WSL='1'

# Focused run: 54 tests passed before the final two UI/binding safeguards.
& "$env:TEMP/cloak-build/gradle-8.11.1/bin/gradle.bat" -p android :app:testDebugUnitTest --tests '*ServiceSurface*' --tests '*PlayEvidenceImportTest' --tests '*PlayReadinessCardTest' --offline --no-daemon --console=plain

# Full Android regression gate and debug build.
& "$env:TEMP/cloak-build/gradle-8.11.1/bin/gradle.bat" -p android :app:testDebugUnitTest :module:testDebugUnitTest :probe:testStandardDebugUnitTest :probe:testDiagnosticsDebugUnitTest :app:assembleDebug --offline --no-daemon --console=plain

uv run --offline --with pytest --with jsonschema --with cryptography -- python -X utf8 -m pytest forge/tests forge/schema_tests -q

# Final affected-target rerun after the different-identity guard and recovery test.
& "$env:TEMP/cloak-build/gradle-8.11.1/bin/gradle.bat" -p android :app:testDebugUnitTest :module:testDebugUnitTest :app:assembleDebug --offline --no-daemon --console=plain

git diff --check
git diff --cached --check
```

Build/test console logs: `%TEMP%/cloak-service-focused.log`,
`%TEMP%/cloak-service-android.log`, `%TEMP%/cloak-service-python.log`,
`%TEMP%/cloak-service-final.log`. An initial focused compilation found a test-only
Robolectric helper invocation error; it was corrected before the successful runs.

## Storage and limits

Bounded format-1 binding sidecars use additional hashed `binding:` keys in the
existing private Google surface preferences. Existing assignment library format,
profile files and binding storage are unchanged. Corrupt records are hidden and
preserved; writes refuse to overwrite them. Other app records remain readable;
restoring a valid record restores evaluation. Details and public APIs are in
[SERVICE-SURFACE-ISOLATION-V2.md](SERVICE-SURFACE-ISOLATION-V2.md).

The existing diagnostics harness does not prove account-slot ownership, backup
policy or private-data isolation. Imports therefore leave these facts absent and
cannot alone reach ISOLATED or READY. A future authorized provider must supply
those observations; no facts are fabricated. Local metadata is not Android
enforcement, platform certification or a service verdict. Separate evidence saves
can partially succeed; the UI reports this and retains the current readiness gate.

No device actions, account operations, permission changes, runtime/module edits,
connector or publication changes were made in this sprint. No release or tag.
