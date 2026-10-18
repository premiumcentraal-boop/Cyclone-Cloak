# Device Reality v1 handoff — 2026-10-18

Release candidate: **v0.11.0-alpha.20 / code 44**. Optional schema 0.4 metadata,
pure deterministic generation and cross-field validation, extended local
uniqueness, conservative catalog facts, redacted read-only observations and a
compact Reality coverage disclosure. No saved files are migrated. Profile storage,
binding, publication, connector and module implementation are unchanged. Only
module packaging metadata changes. No new dependencies or permissions.

## Public entry points

- Kotlin `DeviceReality.generate(template: JSONObject, seed: String, ua: String)`,
  `validate(profile: JSONObject)`, `token(seed, field)` and `uniquePaths`.
- `CloakForge.forgeProfile(..., schemaVersion: String = "0.3")` accepts 0.2/0.3/0.4.
  UI generation and workshop requests explicitly use 0.4. Existing direct API
  defaults remain 0.3. `BatchIdentityPlanner.Request.schemaVersion` defaults 0.4;
  plan import preserves its explicit schema version.
- Python `forge_profile(..., schema_version="0.3")` and engine request
  `schema_version` accept 0.4. New metadata is deterministic from seed/catalog;
  missing facts remain explicit rather than being synthesized.
- `RealityCoverage.summarize(profile, report?)` validates optional hashed probe
  extensions and reports local coherence/gaps. `RuntimeEvidence.toScoreInput`
  conservatively merges extension rows; no match is promoted to verified.
- `RealityCoverageCard(profile, evidence)` appears inside the selected identity's
  existing Diagnostics & evidence disclosure. No new screen or tab.

## Format and behavior

See [Device Reality](DEVICE-REALITY.md) for the twelve availability/provenance
sections, source table, cross-field rules, 43-field matrix and limitations.
Reality is an optional profile-body field, not a new file/store. Existing engine
metadata sidecars remain separate. Backups accept schema 0.2/0.3/0.4 and redact new
inventory markers/configured labels by default. Explicit identity backups retain
them. Importing a profile as a template retains descriptive facts, omits local
identity labels, and derives fresh distinguishing metadata on generation.

Probe reports retain format 1 and the original 89 rows. An optional
`device_reality: {format:1,rows:[...]}` contains only IDs, digests, states, fixed
reasons and duration. Observed is not pass; matching hashes are match-only;
permission/API failures remain explicit. Old evidence is not reclassified.

## Verification

Final local target counts: **546 app / 12 module / 96 standard probe /
96 diagnostics probe / 556 forge / 60 schema / 7 host probe**. Zero skipped
Android tests. Debug/release APK assembly and module packaging use the unchanged
normal lane. Local release APK may be unsigned; signed validation and release CI
use the existing release signer before publication.

Exact final commands (PowerShell, repository root):

```powershell
$env:JAVA_HOME='C:/Program Files/Eclipse Adoptium/jdk-17.0.20.101-hotspot'
$env:CLOAK_TEST_WSL='1'
& "$env:TEMP/cloak-build/gradle-8.11.1/bin/gradle.bat" -p android :app:testDebugUnitTest :module:testDebugUnitTest :probe:testStandardDebugUnitTest :probe:testDiagnosticsDebugUnitTest :app:assembleDebug :app:assembleRelease :module:packageModule :probe:assembleStandardDebug :probe:assembleDiagnosticsDebug --offline --no-daemon --console=plain
uv run --with pytest -- python -X utf8 -m pytest forge/tests -q
uv run --with pytest --with jsonschema -- python -X utf8 -m pytest forge/schema_tests -q
uv run -- python -X utf8 -m unittest discover -s android/probe/tests -v
uv run -- python -X utf8 tools/generate_reality_rules.py --check
uv run -- python -X utf8 tools/verify_release.py --tag v0.11.0-alpha.20 --artifacts
git diff --check
git diff --cached --check
```

Earlier checks caught and corrected: strict package regex rejected Android's
`android` package, manufacturer comparison needed case normalization, Unicode
messages needed preserved UTF-8, and generation/backup tests needed explicit 0.4
expectations. The warning-only display test now changes both correlated fields;
a contradictory panel claim is correctly blocking. The full gate is rerun after
corrections; tests are not bypassed.

Release gate: push main, run `signed-validation.yml` with tag input
`v0.11.0-alpha.20`, wait green, tag the tested commit and allow `release.yml` to
publish the signed prerelease. Verify downloaded APK signature, version/code,
asset SHA-256 and embedded module ZIP byte equality.

## Limitations

All new physical-device checks are **UNVERIFIED**; no device actions in this run.
No physical hardware, runtime hook or platform verdict is provided by schema
metadata or coherence scores. Live device state and exact inventories can differ
from a configured draft. Unmapped fields and caller-visible package limitations
are listed in DEVICE-REALITY.md. Historical alpha.18 attachments in the existing
release workflow are explicitly historical, not evidence for these new fields.
No new signer, key material, attestation, account, SMS or evasion functionality.
Unrelated older handoffs and evidence remain unstaged and unchanged.

## Changed files

- `README.md`
- `android/app/build.gradle`
- `android/app/src/main/java/dev/cyclone/cloak/forge/BatchIdentityPlanner.kt`
- `android/app/src/main/java/dev/cyclone/cloak/forge/CloakForge.kt`
- `android/app/src/main/java/dev/cyclone/cloak/forge/DeviceReality.kt`
- `android/app/src/main/java/dev/cyclone/cloak/forge/EngineBundleCodec.kt`
- `android/app/src/main/java/dev/cyclone/cloak/forge/IdentityEngine.kt`
- `android/app/src/main/java/dev/cyclone/cloak/forge/PhoneTemplate.kt`
- `android/app/src/main/java/dev/cyclone/cloak/forge/ProfileBackup.kt`
- `android/app/src/main/java/dev/cyclone/cloak/forge/ProfileUniqueness.kt`
- `android/app/src/main/java/dev/cyclone/cloak/forge/ProfileValidator.kt`
- `android/app/src/main/java/dev/cyclone/cloak/forge/RealityCoverage.kt`
- `android/app/src/main/java/dev/cyclone/cloak/forge/RealityRules.kt`
- `android/app/src/main/java/dev/cyclone/cloak/forge/RuntimeEvidence.kt`
- `android/app/src/main/java/dev/cyclone/cloak/ui/CloakViewModel.kt`
- `android/app/src/main/java/dev/cyclone/cloak/ui/IdentitiesScreen.kt`
- `android/app/src/main/java/dev/cyclone/cloak/ui/ProfileGeneration.kt`
- `android/app/src/main/java/dev/cyclone/cloak/ui/RealityCoverageCard.kt`
- `android/app/src/test/java/dev/cyclone/cloak/forge/DeviceRealityTest.kt`
- `android/app/src/test/java/dev/cyclone/cloak/forge/ProfileBackupTest.kt`
- `android/app/src/test/java/dev/cyclone/cloak/ui/ProfileGenerationTest.kt`
- `android/app/src/test/java/dev/cyclone/cloak/ui/RealityCoverageCardTest.kt`
- `android/module/module.prop`
- `android/probe/src/main/assets/reality-fields.json`
- `android/probe/src/main/java/dev/cyclone/cloak/probe/ProbeActivity.java`
- `android/probe/src/main/java/dev/cyclone/cloak/probe/RealityProbe.java`
- `android/probe/src/main/java/dev/cyclone/cloak/probe/RealityReport.java`
- `android/probe/src/test/java/dev/cyclone/cloak/probe/RealityReportTest.java`
- `catalog/phones.json`
- `catalog/reality-coverage.json`
- `docs/ARCHITECTURE.md`
- `docs/CURRENT-STATE.md`
- `docs/DEVICE-REALITY.md`
- `docs/HANDOFF-2026-10-18-DEVICE-REALITY.md`
- `docs/RELEASE_0.11.0-alpha.20.md`
- `forge/cloak_forge/engine.py`
- `forge/cloak_forge/forge.py`
- `forge/cloak_forge/reality.py`
- `forge/cloak_forge/templates.py`
- `forge/cloak_forge/validate.py`
- `forge/schema_tests/test_engine_schema.py`
- `forge/tests/test_catalog.py`
- `forge/tests/test_reality.py`
- `forge/tests/vectors/engine-unique.json`
- `forge/tests/vectors/engine.json`
- `forge/tests/vectors/reality.json`
- `forge/tests/vectors/validation.json`
- `schema/cloak-profile.schema.json`
- `schema/device-reality.schema.json`
- `schema/engine-bundle.schema.json`
- `schema/phone-catalog.schema.json`
- `tools/generate_reality_rules.py`
