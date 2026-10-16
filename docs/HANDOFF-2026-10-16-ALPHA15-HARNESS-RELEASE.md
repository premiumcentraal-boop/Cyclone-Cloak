# Alpha.15 harness follow-up

The supplied brief's release premise was stale: alpha.14 was already published
at 6743159. With the user's explicit approval, this run preserves that tag and
its assets and uses **v0.11.0-alpha.15 / code 39**.

## Changes and boundaries

- Harness implementation, structured diagnostics probe, 48 harness tests, usage
  documentation, historical results and release notes are committed.
- Six follow-up tests cover the four unmapped IDs and one-sided expectations.
  Existing IDs were meaningful; no renaming or invented expectations was needed.
  Bootloader/baseband expectations were absent in the source profile. Package
  visibility and accessibility settings vary by user. These remain observational.
- The guide contains the source API, expected behavior, and blocking limits for
  those four rows and the five permission/SDK-limited rows.
- A new real-flow debug invocation seam is explicitly **deferred**. The current
  live ViewModel requires reviewed selection and acknowledgement; there is no
  existing non-UI endpoint. This run adds no bypass or new mutation endpoint.
  Existing bounded run journal and per-run diagnostics are preserved; they do
  not prove complete per-phase timing or a new observer. The future seam needs
  a debug instrumentation host attached to the real reviewed ViewModel, with
  explicit test targets and retry/interruption tests.
- No public API, production permissions, profile/binding schema, saved-profile
  migration, connector, publication-script, or runtime behavior changes.
- No new phone test, app installation, permission grant or restart in this run.
  Earlier evidence measured alpha.14 and does not automatically verify alpha.15.
- Unrelated untracked handoffs and raw evidence remain unstaged and unchanged.

Feature commit: `dcf303e10ee6d1205ff3f819e25a7f5f04af985c`.
Release/tag commit: `91e2173ab71edc5dd456a53f8aea01c2a72a4130`.

## Local checks

**522 app, 12 module, 277 forge (48 harness), 33 schema, six probe-runner tests**
passed. Android reports show zero failures/errors/skips. Final Gradle gate:
BUILD SUCCESSFUL in 2m 12s, 218 tasks. No compiler warnings in the captured log.
Unsigned local release packaging passed, including metadata and embedded module
exact-match checks. Signed artifacts are built using the existing CI keystore.

```powershell
$env:JAVA_HOME='C:/Program Files/Eclipse Adoptium/jdk-17.0.20.101-hotspot'
$env:CLOAK_TEST_WSL='1'
& "$env:TEMP/cloak-build/gradle-8.11.1/bin/gradle.bat" -p android :app:testDebugUnitTest :module:testDebugUnitTest :app:assembleDebug :module:packageModule :probe:assembleStandardDebug :probe:assembleDiagnosticsDebug --offline --no-daemon
& "$env:TEMP/cloak-build/gradle-8.11.1/bin/gradle.bat" -p android :app:testDebugUnitTest :module:testDebugUnitTest :app:assembleDebug :app:assembleRelease :module:packageModule :probe:assembleStandardDebug :probe:assembleDiagnosticsDebug --offline --no-daemon
uv run --with pytest -- python -m pytest forge/tests/test_profile_check.py -q
uv run --with pytest -- python -m pytest forge/tests -q
uv run --with pytest --with jsonschema -- python -m pytest forge/schema_tests -q
uv run -- python -m unittest discover -s android/probe/tests -v
uv run -- python tools/verify_release.py --tag v0.11.0-alpha.15
uv run -- python tools/verify_release.py --tag v0.11.0-alpha.15 --artifacts
git diff --check
```

## CI and release

- [Main CI](https://github.com/premiumcentraal-boop/Cyclone-Cloak/actions/runs/38833492308): PASS.
- [Signed validation](https://github.com/premiumcentraal-boop/Cyclone-Cloak/actions/runs/38833491750): PASS before tagging.
- [Release workflow](https://github.com/premiumcentraal-boop/Cyclone-Cloak/actions/runs/38833970911): PASS; signed prerelease published.
- [Prerelease](https://github.com/premiumcentraal-boop/Cyclone-Cloak/releases/tag/v0.11.0-alpha.15).

The signed validation APK verifies with v2 signing and has the same signer
certificate SHA-256 as the published alpha.14 APK:
`17ca3617b0cbaf250d2ef03e945b319e57695de6d977c652ded243ad5656f553`.
APK/module version and code agree and the validation APK embeds the exact ZIP.

Published assets were downloaded and their sizes and SHA-256 checked against
GitHub's asset metadata. `apksigner verify --verbose --print-certs` passed for the
published APK with the unchanged certificate above. `aapt dump badging` reports
`dev.cyclone.cloak`, `0.11.0-alpha.15`, code 39. ZIP integrity checks passed,
module.prop reports `v0.11.0-alpha.15` / 39, and the APK's embedded module bytes
exactly match the downloaded standalone module ZIP. The tag resolves to the
release commit listed above.

| Asset | Bytes | SHA-256 |
| --- | ---: | --- |
| app-release.apk | 43,345,122 | a40c6b1bcd6b299be98296725a6b96afd3d8062927c7f759bf5af59eab2c11e8 |
| cyclone-cloak-0.11.0-alpha.15.zip | 1,678,673 | 4d773af6d3b90679c19c2c89b88857cfa7593dd71c04053d72029b98a4b22b35 |

Downloaded files: `%TEMP%/cloak-harness-release/published/`.

Release operations:

```powershell
git push origin main
gh workflow run signed-validation.yml --ref main -f tag=v0.11.0-alpha.15
# Both main CI and signed validation completed successfully before tagging.
git tag -a v0.11.0-alpha.15 91e2173ab71edc5dd456a53f8aea01c2a72a4130 -m 'Cyclone Cloak 0.11.0-alpha.15: local profile evidence harness'
git push origin v0.11.0-alpha.15
gh release download v0.11.0-alpha.15 --dir "$env:TEMP/cloak-harness-release/published" --clobber
& 'C:/Users/Agent/android-sdk/build-tools/35.0.0/apksigner.bat' verify --verbose --print-certs "$env:TEMP/cloak-harness-release/published/app-release.apk"
& 'C:/Users/Agent/android-sdk/build-tools/35.0.0/aapt.exe' dump badging "$env:TEMP/cloak-harness-release/published/app-release.apk"
```

Raw device evidence remains local. This follow-up does not claim a new device
verification, external compatibility verdict, or implemented debug invocation seam.
