# Alpha.4 release and diagnostics posture adapter

## Release boundary

Published prerelease: [v0.11.0-alpha.4](https://github.com/premiumcentraal-boop/Cyclone-Cloak/releases/tag/v0.11.0-alpha.4).
Release commit: `2f0da6b31859fa1930590e0e66fc82391f6a5f52`.
[Release CI](https://github.com/premiumcentraal-boop/Cyclone-Cloak/actions/runs/38425205507) passed all four suites, signed packaging and artifact checks.

Assets: `app-release.apk` (42,930,390 bytes) and
`cyclone-cloak-0.11.0-alpha.4.zip` (1,674,609 bytes).
App/module metadata agrees at alpha.4, version code 28. Release notes describe
the posture decision engine as local evidence only, never a platform verdict.
The adapter below is subsequent main development, not included in those release assets.

## Behavior

An expanded selected identity offers **Load diagnostics for posture**. Loading
opens **Preview local diagnostics posture**, showing the local state, each read,
measurement time, selected profile ID/body SHA-256, artifact and warnings.
**Save local posture** explicitly writes the existing private posture summary;
Cancel discards the preview. No raw JSON, expected values, seeds or identifiers
are persisted by this path. Profile files and engine metadata are unchanged.

The adapter consumes existing format-1 runtime diagnostics (`runs`, `metadata`,
optional `artifact`). It reuses five-phase proof classification; all raw reads
are retained, even if no engine field maps to them. VERIFIED becomes PASS and
FAIL becomes FAIL. Partial, blocked, observed, incomplete, unmapped and match-only
proof becomes WARNING. Raw probe PASS is not VERIFIED. Malformed types/statuses,
duplicate phases/reads/JSON keys, non-JSON syntax, trailing data, excessive nesting
and oversized files are rejected.

Only agreement between root `artifact` and metadata `module_arm64_sha256`
retains the artifact digest. Absence/disagreement produces UNKNOWN, with a visible
warning and no saved artifact digest. Comparison is against the bundled module
payload, not the installed module. This adapter does not perform device verification.

Every converted report is bound to the selected saved profile ID and canonical
current SHA-256. Optional source `profile_id`/`profile_sha256` claims may appear
at root or in metadata; conflicting claims fail parsing. A claimed other profile
or body mismatch blocks saving. Existing fixture reports lack this binding, so
they receive a scope WARNING rather than certifying an arbitrary saved identity.
Reports older than seven days, future timestamps and different current artifacts
show explicit warnings and cannot pass. Save reloads the profile and evaluates
context again; changed previews require another review. No automatic rotation,
reseed, deletion or module action occurs.

## Public entry points

- `DiagnosticsPostureAdapter.convert(text, selectedProfile): AdaptedDiagnostics`
- `DiagnosticsPostureAdapter.mapStatus(status): PostureCheckStatus`
- `AdaptedDiagnostics.preview(currentProfile, currentArtifactDigest, now): DiagnosticsPosturePreview`
- `DiagnosticsReview.save(currentProfile, currentArtifactDigest, now, saveRecord): SaveResult`
  (`Saved`, `Blocked`, or `ReviewChanged`).
- `RuntimeEvidence.readStatuses(evidence): Map<String, String>`; the existing
  `toScoreInput` API retains its field-mapping behavior.
- `PostureSummary.freshnessWarnings(record, profileId, profileDigest, artifactDigest, now)`.
- View-model `loadDiagnosticsForPosture`, `saveDiagnosticsPosture`, and
  `dismissDiagnosticsPosture`.

The in-memory report now accepts a nullable artifact digest. The existing posture
JSON import still requires its artifact string, and the persisted format-1
`compatibility-posture` preferences already support null artifact digests. No
profile/schema/connector/publication migration is required.

## Commands and validation

Windows environment: `JAVA_HOME=C:/Program Files/Eclipse Adoptium/jdk-17.0.20.101-hotspot`.

```powershell
& "$env:TEMP/cloak-build/gradle-8.11.1/bin/gradle.bat" -p android :app:testDebugUnitTest :module:testDebugUnitTest --offline --no-daemon
& "$env:TEMP/cloak-build/gradle-8.11.1/bin/gradle.bat" -p android :app:testDebugUnitTest :module:testDebugUnitTest :app:assembleDebug --offline --no-daemon
uv run --with pytest -- python -m pytest forge/tests -q
uv run --with pytest --with jsonschema -- python -m pytest forge/schema_tests -q
```

Release baseline: app 341 tests (318 passed, 23 Windows POSIX-shell skips),
module 12 passed, forge 227 passed, schema 33 passed.
Adapter local results: app 357 tests (334 passed, 23 Windows POSIX-shell skips),
module 12 passed, forge 227 passed, schema 33 passed; debug build passed.
Signed validation outcome is recorded in the completion report.

Signed CI uses the existing release secrets, with no new credentials or app
permissions. `.github/workflows/signed-validation.yml` runs all suites and:

```sh
gradle -p android :app:testDebugUnitTest :module:testDebugUnitTest --no-daemon
gradle -p android :app:assembleRelease :module:packageModule --no-daemon
python3 tools/verify_release.py --tag v0.11.0-alpha.4 --artifacts
```

It uploads signed validation artifacts without creating a release or changing
alpha.4 assets. Physical-device behavior remains unverified in this sprint.
The checked-in partial B3B report retains all 70 reads; its expected camera failure
and incomplete proof are not converted into a pass. Full saved-profile evidence
generation and installed-artifact verification remain follow-up work.

## Changed files

Release: app version metadata, module packaging metadata, release workflow,
`docs/RELEASE_0.11.0-alpha.4.md`.

Adapter: `data/CompatibilityPostureReport.kt`, `forge/RuntimeEvidence.kt`,
shared strict JSON parser extracted from `forge/EngineBundleCodec.kt` to
`forge/StrictJson.kt`, `ui/DiagnosticsPostureAdapter.kt`,
`ui/DiagnosticsPostureDialog.kt`, `ui/CloakViewModel.kt`,
`ui/IdentitiesScreen.kt`, `ui/PostureSummary.kt`, adapter/dialog/freshness/storage
tests, architecture documentation, this handoff and signed-validation workflow.
Module/runtime/connector/binding/publication implementation is unchanged.
