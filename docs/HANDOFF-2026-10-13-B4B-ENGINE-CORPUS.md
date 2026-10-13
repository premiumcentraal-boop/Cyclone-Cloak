# B4B engine facade and device corpus — 2026-10-13

## Completed scope

Added Python and Kotlin pure engine facades, a Draft 2020-12 engine-bundle schema,
optional catalog-fact schema and conservative metadata enrichment of all 15
existing phones. Both languages retain seeded generation values and old forge
signatures. No new phones were added (the handoff made them optional).

Repo main started at `1212882`, containing latest published prerelease
v0.11.0-alpha.2. No runtime module, probe, connector, UI, storage or publishing
source changed. No device actions, release or tag were performed. This run's
changes are local; it does not reuse the previous B3B push authorization.

## Facades and exact signatures

[Engine reference](IDENTITY-ENGINE.md) contains the complete API and confidence table.

Python: `forge/cloak_forge/engine.py`

```python
generate(request: Mapping[str, Any]) -> dict[str, Any]
validate(profile: Mapping[str, Any]) -> list[Finding]
derive_posture(profile: Mapping[str, Any]) -> dict[str, str]
check_uniqueness(profile: Mapping[str, Any], library: Iterable[Mapping[str, Any]]) -> list[Finding]
score_coverage(profile: Mapping[str, Any], device_report: Mapping[str, str]) -> dict[str, Any]
```

Kotlin: `android/app/src/main/java/dev/cyclone/cloak/forge/IdentityEngine.kt`

```kotlin
generate(request: GenerationRequest): JSONObject
validate(profile: JSONObject): List<ProfileValidator.Finding>
derive_posture(profile: JSONObject): JSONObject
check_uniqueness(profile: JSONObject, library: List<JSONObject>): List<ProfileValidator.Finding>
score_coverage(profile: JSONObject, device_report: Map<String, String>): JSONObject
```

Kotlin GenerationRequest has name/seed/template and optional schema_version (0.3
by default). Python takes the same keys with a template ID string. Both accept
0.2 and 0.3. Bundles include profile/posture/findings/uniqueness/metadata.

Bundle schema `$id`:
`https://github.com/premiumcentraal-boop/Cyclone-Cloak/schema/engine-bundle.schema.json`.
Posture calls existing Python build_pif and Kotlin CloakForge.buildPif ->
CloakPif.fromProfile. No duplicated posture calculation or attestation claim.

Python uniqueness minimally mirrors the existing Kotlin derived index. Coverage
uses the existing field ledger (mirrored and pinned by engine vectors in Kotlin).
Missing proof is "not verified"; unknown fields are PENDING; raw PASS stays PASS.
No automatic device inspection or proof fabrication occurs inside a facade.

## Corpus and confidence

Three records remain **needs verification**: Pixel 4, Pixel 7, Galaxy S23.
Twelve remain **approximated**: Pixel 6, 6a, 7a, 8, 6 Pro, 7 Pro, 8 Pro, 8a,
9, 9 Pro, 9 Pro XL, 9a. No complete record was promoted to public reference.
Public specification links support the new coarse camera/capability/sensor facts,
while exact build/firmware/Camera2 fields retain their existing confidence.
The per-record source URLs and full table are in IDENTITY-ENGINE.md.

New facts stay catalog-only: source_confidence, camera_class,
expected_capability_set, sensor_class_list, sources. Optional board and
DRM-security fields are schema-supported but omitted without adequate evidence.
Every existing hardware field was already present. Coarse sensor classes do not
invent sensor models. All 15 raw records and their generated profiles validate
without findings. Existing profile blocks were compared against HEAD's catalog;
all generated profiles are unchanged. Both languages also test byte-identical
serialization against their existing generation entry point.

## Validation additions and corrections

- Kotlin now applies the camera rules Python already had; shared malformed-camera
  vectors assert exact severities, fields, codes and messages.
- Python now rejects boolean camera facings rather than equating True with 1.
- Catalog metadata rejects unsupported confidence, malformed optional text/list
  values, duplicate list entries and invalid DRM-level strings.
- Catalog/phone-template validation rejects aspect above 4:1. Existing profile
  aspect warnings and generation behavior remain unchanged.
- SDK/release consistency and invalid patch dates were already checked.
- The requested rejection of patches earlier than build date is incorrect:
  normal Android builds are produced after their security-patch date. That
  behavior is deliberately preserved and tested. Later-than-build patches retain
  the existing warning. This correction avoids invalidating all existing phones.
- Profile schema now accepts existing generated UUIDv5 IDs and null integrity
  timestamps/tags. UUIDv4 identifier rules remain unchanged. A shadowed duplicate
  camera property was removed without changing its effective schema definition.
  These schema-document corrections do not migrate or rewrite stored data.

Thus generation, posture, identity derivation and existing integration behavior
are unchanged. The explicit validation additions above are the only stricter
behavior, as requested; existing valid profiles continue to pass.

## Exact test/build commands and results

```powershell
$env:JAVA_HOME='C:/Program Files/Eclipse Adoptium/jdk-17.0.20.101-hotspot'
& "$env:TEMP/cloak-build/gradle-8.11.1/bin/gradle.bat" -p android :app:testDebugUnitTest :module:testDebugUnitTest --offline --no-daemon
uv run --with pytest -- python -m pytest forge/tests -q
uv run --with pytest --with jsonschema -- python -m pytest forge/schema_tests -q
uv run -- python -m unittest discover -s android/probe/tests -v
```

- Forge: **217 passed**, including original parity/coverage tests and new facade,
  optional metadata and rejection tests.
- Supplemental actual Draft 2020-12 schema validation: **32 passed** (both versions
  of every catalog bundle, catalog compatibility and required/unknown-field cases).
- Runner regression suite: **6 passed**; no device run.
- Android app: **293 tests**, zero failures/errors, **270 passed / 23 skipped**.
- Android module: **12 passed**, zero failures/errors/skips.
- Gradle compilation/test build succeeded. The existing app test dependency graph
  packages the unchanged runtime module locally; it was not installed.
- No compiler warnings were emitted. Git prints configured LF-to-CRLF notices;
  whitespace checks pass.

The supplemental schema suite uses uv's test-only jsonschema package, isolated
from the standard forge suite and engine runtime. No production dependency added.

## Changed files

- `forge/cloak_forge/engine.py`
- `forge/cloak_forge/validate.py`
- `forge/tests/test_engine.py`
- `forge/tests/test_catalog.py`
- `forge/tests/vectors/engine.json`
- `forge/tests/vectors/validation.json`
- `forge/schema_tests/test_engine_schema.py`
- `android/app/src/main/java/dev/cyclone/cloak/forge/IdentityEngine.kt`
- `android/app/src/main/java/dev/cyclone/cloak/forge/PhoneTemplate.kt`
- `android/app/src/main/java/dev/cyclone/cloak/forge/ProfileValidator.kt`
- `android/app/src/test/java/dev/cyclone/cloak/forge/IdentityEngineTest.kt`
- `catalog/phones.json`
- `schema/engine-bundle.schema.json`
- `schema/phone-catalog.schema.json`
- `schema/cloak-profile.schema.json`
- `docs/IDENTITY-ENGINE.md`
- `docs/ARCHITECTURE.md`
- This per-run handoff.

## Remaining limitations

Corpus metadata is not physical-device verification or a complete hardware
inventory. Board/DRM specifics and exact camera/sensor APIs need documented
device evidence. Existing approximate firmware values are preserved to protect
forge parity. Source-ledger APPLIED does not mean measured VERIFIED. The facade
is not wired into UI/storage workflows in this sprint. Existing unrelated stale
release headers in architecture/README remain outside this scoped change.
