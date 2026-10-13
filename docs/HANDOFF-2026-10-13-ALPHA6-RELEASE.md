# Alpha.6 release handoff

## Scope and starting point

Release tag: `v0.11.0-alpha.6`, app/module code 30.
Release source: `f4ff3992759ef1652a25aceda8d07fb9c3dd0daa`.

Starting main was `933f0e6`, containing the read-only Library quality card and
15 focused summary/Compose tests. There were three commits above alpha.5:
`6dfa287` (existing diagnostics cause classification), `cdeb3a9` (handoff docs)
and `933f0e6` (library quality). All were included as the existing main state.

This run changed only app/module packaging version metadata and documentation.
No runtime code, connector behavior, storage layout, binding logic, posture
decisions, diagnostics behavior, networking or permissions were changed.
No device actions were performed.

## Changed files

- `android/app/build.gradle`: alpha.6 / version code 30.
- `android/module/module.prop`: matching packaging version/code; runtime unchanged.
- `README.md`: replaces stale RC2 status and release links.
- `docs/ARCHITECTURE.md`: reconciles the prerelease header.
- `docs/CURRENT-STATE.md`: current component versions and local results; removes
  obsolete Robolectric blockers and distinguishes device evidence from local tests.
- `docs/RELEASE_0.11.0-alpha.6.md`: concise release, upgrade and scope notes.
- This publication handoff.

## Exact local checks

```powershell
$env:JAVA_HOME='C:/Program Files/Eclipse Adoptium/jdk-17.0.20.101-hotspot'
& "$env:TEMP/cloak-build/gradle-8.11.1/bin/gradle.bat" -p android :app:testDebugUnitTest :module:testDebugUnitTest :app:assembleDebug --offline --no-daemon
uv run --with pytest -- python -m pytest forge/tests -q
uv run --with pytest --with jsonschema -- python -m pytest forge/schema_tests -q
uv run -- python tools/verify_release.py --tag v0.11.0-alpha.6
git diff --check
```

The Android/debug command was run before and after the version update. Final
results: app 398 tests (375 passed, 23 existing Windows POSIX-shell skips),
module 12 passed, forge 227 passed, schema 33 passed; debug build, metadata
agreement and whitespace checks passed. No new compiler/build warnings.

## CI and publication

[Release CI](https://github.com/premiumcentraal-boop/Cyclone-Cloak/actions/runs/38472829633)
passed all four suites on Linux, signed builds, matching-artifact checks and
prerelease publication. The CI commands included:

```sh
gradle -p android :app:testDebugUnitTest :module:testDebugUnitTest --no-daemon
gradle -p android :app:assembleRelease :module:packageModule --no-daemon
python3 tools/verify_release.py --tag v0.11.0-alpha.6 --artifacts
```

Existing CI credentials sign the release APK. Artifact checks verify matching
version/code, both module ABIs and exact embedded module ZIP bytes before upload.
Release URL: [v0.11.0-alpha.6](https://github.com/premiumcentraal-boop/Cyclone-Cloak/releases/tag/v0.11.0-alpha.6).
Assets: `app-release.apk`, `cyclone-cloak-0.11.0-alpha.6.zip`.
Published prerelease confirmed. `app-release.apk`: 43,045,082 bytes;
`cyclone-cloak-0.11.0-alpha.6.zip`: 1,674,608 bytes. CI warnings were limited to
existing action-tooling/Node API deprecations; no new compiler warnings.
Publication status headers were finalized in a subsequent documentation-only
commit without modifying or moving the release tag.

The Library quality card and local posture states are not platform verdicts.
Device behavior remains separately evidenced against its measured artifact;
existing runtime results do not constitute alpha.6 device verification.
