# B5 engine integration — 2026-10-13

## Completion

B4B was tested before B5 implementation: 217 forge tests passed; Android app
270 passed / 23 skipped, module 12 passed. B4B's 18 files were committed as
`a5e703023349cdb3b7f88f8ebbe10e225b9946ef` with the requested message
`feat(engine): add identity engine facades and corpus metadata`, then pushed to
`origin/main`. Tags were fetched. The latest published prerelease remains
`v0.11.0-alpha.2` (commit `cd4e2c62d2e54904b69457d10283b87d5133c903`), already
contained in main. No version, release or tag was created in B5.

B5 introduces an app engine boundary, strict bundle import, separate local
metadata, deterministic uniqueness retries and fixture-scoped coverage. The
existing profile body, profiles directory, direct forge APIs, fleet generation,
binding records, synchronization, publication and connector contract remain
unchanged. No module sources, permissions, dependencies or device actions changed.

## Public signatures

App package `dev.cyclone.cloak.forge`:

```kotlin
interface DeviceProfileEngine {
    fun generate(request: IdentityEngine.GenerationRequest): JSONObject
    fun generate_unique(request: IdentityEngine.GenerationRequest, existingProfiles: List<JSONObject>): JSONObject
    fun validate(profile: JSONObject): List<ProfileValidator.Finding>
    fun check_uniqueness(profile: JSONObject, existingProfiles: List<JSONObject>): List<ProfileValidator.Finding>
    fun derive_posture(profile: JSONObject): JSONObject
    fun score_coverage(profile: JSONObject, deviceReport: Map<String, String>): JSONObject
}
class DefaultDeviceProfileEngine : DeviceProfileEngine
DefaultDeviceProfileEngine.retrySeed(seed: String, attempt: Int): String
EngineBundleCodec.decode(text: String): EngineBundleCodec.Bundle
EngineBundleCodec.encode(bundle: EngineBundleCodec.Bundle): String
EngineBundleCodec.validateMetadata(metadata: JSONObject, profile: JSONObject)
RuntimeEvidence.toScoreInput(evidence: JSONObject): Map<String, String>
RuntimeEvidence.rows(profile: JSONObject, input: Map<String, String> = emptyMap()): List<RuntimeEvidence.FieldCoverage>
```

The default delegates to existing `IdentityEngine` operations. The request is
still `GenerationRequest(name, seed, template, schema_version = "0.3")`.
`Bundle(profile, metadata, findings, extras = JSONObject())` preserves all optional
bundle fields for round-trip serialization. Its `findings` property is freshly
validated; imported findings, uniqueness claims and posture never authorize a save.
`FieldCoverage(field, state, testStatus)` contains no identifier values.

Python `cloak_forge.engine` adds matching operations:

```python
generate_unique(request: Mapping[str, Any], existingProfiles: Iterable[Mapping[str, Any]]) -> dict[str, Any]
retry_seed(seed: str, attempt: int) -> str
```

Data package:

```kotlin
EngineMetadataStore(context: Context, validateMetadata: (JSONObject, JSONObject) -> Unit)
read(profile: JSONObject): JSONObject?
save(profile: JSONObject, metadata: JSONObject, saveBody: (JSONObject) -> String): String
EngineMetadataStore.digest(profile: JSONObject): String
```

The validation callback keeps data independent of forge, preserving the existing
architecture rule. UI supplies `EngineBundleCodec::validateMetadata`.

## Generation and import

The initial seed is allocated once. Attempt 1 uses it unchanged. Attempts 2–128
use the lowercase SHA-256 of UTF-8
`cloak-engine-unique-v1:<initial seed>:<attempt number>`.
Every candidate is validated and checked against all eleven existing uniqueness
fields, case-insensitively, plus saved profile IDs. Generation does not apply the
edit-oriented own-ID exclusion. Library order does not affect the result.
Exhaustion fails without saving. The chosen seed remains in the original profile
seed field; engine metadata additionally records chosen_seed and attempt_count.
Shared Kotlin/Python vectors pin a two-conflict retry ending at attempt 3.

Phones generation, New identity and generation within the binding sheet use this
path. Existing direct `CloakForge.forgeProfile`, `forgeNewProfile`, Python
`forge_profile`, and direct `IdentityEngine.generate` remain available. An empty
library produces exactly the old direct profile body.

Identities → Import accepts an engine bundle in addition to the existing profile,
phone, dump, fleet and backup formats. Bundle parsing limits input to 2,000,000
characters, nesting to 32 levels and each findings array to 1,000 entries. It
rejects duplicate keys, non-JSON syntax, trailing data, unknown bundle/metadata
properties, unsupported engine/schema versions, malformed diagnostic/posture
shapes, inconsistent template/schema/seed metadata and coherence errors.
Warnings and current library conflicts use the existing review flow. Metadata
survives warning review; explicit identifier regeneration drops provenance for
that edited draft. Engine imports refuse to replace an existing profile ID,
including an unreadable existing file. Old bare profile/backup imports retain
their existing behavior.

## Sidecar format and recovery

Existing profile files stay at `<filesDir>/profiles/<id>.json`, written with the
existing atomic CloakStore. No fields are added to their bodies. JSON values are
preserved; serialization uses the existing store's formatting, not source-file
byte formatting.

Engine records live in app-private SharedPreferences named
`device-profile-engine` (Android's existing shared_prefs area), keyed by profile
ID. This is an additive metadata record, not a relocation or migration of the
profile library:

```json
{
  "format": 1,
  "profile_sha256": "<canonical body digest>",
  "metadata": {
    "engine_version": "1",
    "schema_version": "0.3",
    "template": "pixel_7",
    "source_confidence": "needs verification",
    "chosen_seed": "<private chosen seed>",
    "attempt_count": 1
  }
}
```

Older bundles need only engine_version, schema_version and template.
source_confidence is optional; chosen_seed and attempt_count must appear together.
The codec checks chosen_seed against the unchanged profile seed. No original retry
seed, keys, tokens or runtime reports are persisted in metadata.

The metadata commit precedes the existing atomic body write. On body-write failure
the previous sidecar is restored. A process interruption before a new body exists
may leave an orphan, which library loading does not expose. A mismatched body
digest or corrupt/missing sidecar produces no metadata and does not break reading
the profile. Canonical hashing sorts object keys, retains array order, and uses
JSON number formatting so `90.0`/`90` survive an unchanged storage round trip.
Subsequent body edits invalidate old provenance rather than rewriting it. This is
not a general multi-file transaction or a power-loss guarantee.

## Identity UI and coverage semantics

Expanded identity cards show `Engine version`, `Profile schema version`,
`Template ID`, and `Source confidence`. Missing metadata says `not recorded`;
legacy template IDs can still come from the existing profile meta block.
`Field coverage` expands the field list. `Load probe evidence` picks a local JSON
document without new permissions or networking. Seeds and identifier values are
not interpolated into the new descriptive labels or coverage rows.

Coverage uses exactly `verified`, `applied`, `pending`, `not_verified`, `failed`.
Each row also shows its underlying test status. Implementation APPLIED without
proof shows applied; PENDING shows pending; internal bookkeeping has no device
claim. A raw PASS is never promoted by display logic. BLOCKED, partial, observed,
unmapped and coincidental matches are not_verified. A recorded failure remains
failed even in a partial run.

The helper maps Build, properties, telephony/operator, identifiers, display,
camera and locale/timezone probe IDs to existing score_coverage field paths.
Multiple reads of one field aggregate conservatively: one failure fails the field;
all required surfaces must have complete proof before it becomes VERIFIED.
Complete proof requires all five phases, stable A repeat, restored unbound control,
distinct A/B expectations and hashes, hashes on every phase, and a matching
artifact/module hash. Duplicate phases/rows, unsupported states and mixed probe
packages are rejected. Unmapped reads such as Bluetooth name, SSID and sensors
are not invented as profile fields or expectations.

This is evidence about synthetic probe fixtures on the artifact measured in the
loaded file. It does not prove this saved identity or the currently installed
module. The UI explicitly states that scope. Loading evidence is session-only;
raw report values, identifiers and host metadata are not retained in UI state.
Local files are supplied evidence, not authenticated measurements.

The checked-in B2A DRM report maps widevine_id to VERIFIED. The B3B partial
diagnostics report maps camera.cameras to FAIL and Wi-Fi identity to INCOMPLETE;
none of its fields becomes VERIFIED. The old installed RC3 module predates camera
coverage; its failure remains evidence, not a module defect chased in B5.

## Changed files (B5)

- App forge: new `DeviceProfileEngine.kt`, `EngineBundleCodec.kt`, `RuntimeEvidence.kt`; updated `Imports.kt`.
- App data: new `EngineMetadataStore.kt`; existing stores untouched.
- App UI: new `EngineIdentitySummary.kt`; updated `CloakViewModel.kt`, `IdentitiesScreen.kt`, `ProfileGeneration.kt`.
- Android tests: new `DeviceProfileEngineTest.kt`, `RuntimeEvidenceTest.kt`, `EngineMetadataStoreTest.kt`, `EngineIdentitySummaryTest.kt`; updated `ProfileGenerationTest.kt`.
- Python: updated `forge/cloak_forge/engine.py`; new `forge/tests/test_engine_integration.py`, `forge/tests/vectors/engine-unique.json`; updated `forge/schema_tests/test_engine_schema.py`.
- Schema documentation: additive optional metadata in `schema/engine-bundle.schema.json`; profile and catalog schemas unchanged in B5.
- Docs: `docs/ARCHITECTURE.md`, `docs/IDENTITY-ENGINE.md`, this handoff.

## Exact test/build commands

All commands run from `C:\Users\Agent\Cyclone\cloak-repo`:

```powershell
$env:JAVA_HOME='C:/Program Files/Eclipse Adoptium/jdk-17.0.20.101-hotspot'
uv run --with pytest -- python -m pytest forge/tests -q
& "$env:TEMP/cloak-build/gradle-8.11.1/bin/gradle.bat" -p android :app:testDebugUnitTest :module:testDebugUnitTest --offline --no-daemon
uv run --with pytest --with jsonschema -- python -m pytest forge/schema_tests -q
uv run -- python -m unittest discover -s android/probe/tests -v
& "$env:TEMP/cloak-build/gradle-8.11.1/bin/gradle.bat" -p android :app:testDebugUnitTest :module:testDebugUnitTest :app:assembleDebug --offline --no-daemon
git diff --check
```

Final B5: forge **227 passed**, supplemental schemas **33 passed**, probe runner
**6 passed**. Android app **293 passed / 23 skipped** (316 total), module
**12 passed**, no failures/errors. The 23 existing skips are POSIX publication
shell tests on Windows without the optional WSL test switch; they were not
removed or bypassed. B5 adds 23 Android tests, 10 forge cases and one schema test.
Coverage includes full/partial real-artifact ingestion, malformed bundles,
0.2/0.3 compatibility, full bundle round-trip, shared retry parity, case-insensitive
conflicts, retry exhaustion, metadata rollback/corruption, integral-number storage
round trips, label privacy and the normal generation sidecar callback.

Debug build succeeds at `android/app/build/outputs/apk/debug/app-debug.apk`.
The existing preBuild dependency also packages the unchanged module at
`android/module/build/module/cyclone-cloak-0.11.0-alpha.2.zip`.
No compiler/build warnings in the successful final run. Git reports ordinary
LF-to-CRLF normalization notices. Initial B5 iterations caught and corrected
Android JSON API availability, a data-to-forge architecture dependency and test
use of a JVM-only JSON comparison API. The architecture rule and all tests pass.

## Intentional follow-ups

- No phone install, reboot or new physical-device verification. Camera verification
  still awaits the coordinated pass with the newer module installed.
- Engine sidecars are not exported by existing profile backups; restoring such a
  backup preserves existing backup behavior and shows unrecorded engine metadata.
  Existing stored profiles/fleets are not backfilled with inferred provenance.
- No stale/orphan metadata cleanup or body-edit metadata migration; mismatches are
  safely hidden. Evidence is not persisted or associated with saved identities.
- Existing README/architecture release headers still mention historical RC2 despite
  the newer published alpha.2. They were not broadly rewritten in this engine sprint.
- B4B was pushed as requested. B5 is a local commit on main; its hash is in the
  completion report and `git log`. No additional GitHub release was published.
