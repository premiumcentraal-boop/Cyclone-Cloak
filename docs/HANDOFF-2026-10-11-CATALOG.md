# Phone catalog expansion handoff — 2026-10-11

## Summary

Added exactly eight templates, growing the shared catalog from seven to fifteen:
Pixel 6 Pro, Pixel 7 Pro, Pixel 8 Pro, Pixel 8a, Pixel 9, Pixel 9 Pro,
Pixel 9 Pro XL and Pixel 9a. Classes cover compact, mainstream, large-display,
midrange, older supported targets and newer supported targets. Every entry has
manufacturer/model, release/API, display, telephony, network and locale defaults.
New catalog-only classification metadata supplies hardware feature and camera
groups without extending generated profile fields.

Added optional `confidence` metadata to all catalog entries. New entries are
`approximated`: public model/display/build references plus explicit synthetic
configuration defaults. Legacy fixtures are `needs verification`. The source
confidence table and definitions are in [CATALOG-SOURCES.md](CATALOG-SOURCES.md).

Kotlin phone import/export retains optional annotations. Python raw loading does
the same. Both profile forges continue copying only the existing schema blocks.
Tests verify legacy annotation-free phone JSON still loads and new annotations
never enter generated profiles. All seven existing device/telephony/network/
display/locale payloads were compared against HEAD and remain unchanged.

## Changed files in this run

- `catalog/phones.json`: eight additions and confidence/classification metadata.
- `android/app/src/main/java/dev/cyclone/cloak/forge/PhoneTemplate.kt`: optional catalog metadata round-trip support.
- `android/app/src/test/java/dev/cyclone/cloak/forge/PhoneCatalogTest.kt`: full catalog and metadata/legacy-reader compatibility checks.
- `forge/cloak_forge/templates.py`: document catalog metadata preservation and profile-block boundary.
- `forge/tests/test_catalog.py`: full catalog validation, all required device classes, metadata and defaults assertions.
- `docs/CATALOG-SOURCES.md`: current source-confidence table and approximations.
- `README.md`: fifteen-template catalog description (preserves earlier generation changes).
- `docs/HANDOFF-2026-10-11-CATALOG.md`: this run's report.

Prior uncommitted coherence, generation and uniqueness changes and user-owned
brief/mission/report files were preserved. This list covers this run only.

## Exact test/build commands run

From `C:\Users\Agent\Cyclone\cloak-repo`:

```powershell
uv run --with pytest -- python -m pytest forge/tests -q
& ./tools/run-local-profile-tests.ps1
$env:JAVA_HOME='C:/Program Files/Eclipse Adoptium/jdk-17.0.20.101-hotspot'
& "$env:TEMP/cloak-build/gradle-8.11.1/bin/gradle.bat" -p android :app:testDebugUnitTest --no-daemon
& "$env:TEMP/cloak-build/gradle-8.11.1/bin/gradle.bat" -p android :app:assembleDebug :module:packageModule --offline --no-daemon
& "$env:TEMP/cloak-build/gradle-8.11.1/bin/gradle.bat" -p android :app:testDebugUnitTest --offline --no-daemon
git -c core.safecrlf=false diff --check
```

## Results

- Python: **185 passed**, including a parameterized zero-findings check for all 15 entries.
- Supplemental direct JVM: **70 passed**, including Kotlin phone/profile validation for every entry and metadata import/export/legacy compatibility. This does not replace the complete Android/Robolectric suite.
- App APK and module ZIP build: **passed**, 35 seconds, 74 tasks (15 executed, 59 up-to-date). Packaged APK `assets/phones.json` matches the checked-in 15-entry catalog exactly. Build warning: `Detected multiple Kotlin daemon sessions at` after the interrupted online test run; no source compiler warnings.
- Full Android suite remains **blocked** by missing Robolectric runtime dependencies. The online run stalled downloading the Android 14 instrumented runtime (temporary download remained at 335,404 bytes for several minutes); its own daemon was stopped. A direct download check returned HTTP 200 but timed out after 45 seconds with only 2,862,174 / 151,142,227 bytes received. No tests or dependency checks were removed or disabled. The offline retry failed at `:app:copyRobolectricRuntime` after 26 seconds: uncached `org.robolectric:android-all-instrumented:14-robolectric-10818077-i7` / `android-all-instrumented-14-robolectric-10818077-i7.jar`.
- Whitespace check passed.

## Assumptions, limitations and release metadata

Compact and older/newer describe classes relative to the catalog, not Android
resource qualifiers or a guarantee of current manufacturer update eligibility.
All additions use Google models because one primary specification source and
AOSP build tables provide consistent conservative references across generations.
Camera groups and NFC/UWB feature groups are descriptions, not runtime-hook
claims. Density, carrier, locale and synthetic firmware properties are explicit
approximations. No factory dumps or physical-device checks were performed.
Reboot/device checks remain deferred per the existing user request.

Runtime module, connector contract, profile storage schema and generated profile
shape were not modified. Catalog schemaVersion remains 1. App/module release
metadata remains **0.10.0-rc.1**; HEAD includes published tag `v0.10.0-rc.1`.
No commit, push, release or tag was made. The definition-of-done item requiring
the full Android test suite to pass remains outstanding until runtime dependencies
can be downloaded and that target rerun.
