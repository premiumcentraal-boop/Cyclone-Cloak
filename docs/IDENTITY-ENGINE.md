# Identity engine boundary and device corpus — 2026-10-13

The engine is a pure facade over the existing forges. It has no UI, connector,
storage or publishing dependencies. Existing callers and function signatures
are unchanged. Generated profiles are not automatically saved, bound or published.

## Public operations

Python module: `forge/cloak_forge/engine.py`.

```python
generate(request: Mapping[str, Any]) -> dict[str, Any]
validate(profile: Mapping[str, Any]) -> list[Finding]
derive_posture(profile: Mapping[str, Any]) -> dict[str, str]
check_uniqueness(profile: Mapping[str, Any], library: Iterable[Mapping[str, Any]]) -> list[Finding]
score_coverage(profile: Mapping[str, Any], device_report: Mapping[str, str]) -> dict[str, Any]
```

Kotlin object: `android/app/src/main/java/dev/cyclone/cloak/forge/IdentityEngine.kt`.

```kotlin
generate(request: GenerationRequest): JSONObject
validate(profile: JSONObject): List<ProfileValidator.Finding>
derive_posture(profile: JSONObject): JSONObject
check_uniqueness(profile: JSONObject, library: List<JSONObject>): List<ProfileValidator.Finding>
score_coverage(profile: JSONObject, device_report: Map<String, String>): JSONObject
```

`IdentityEngine.GenerationRequest(name: String, seed: String, template: PhoneTemplate,
schema_version: String = "0.3")` is the Kotlin request. Python accepts those same
keys, with a catalog template ID string. Both accept schema 0.2 and 0.3; omitted
version defaults to 0.3. Explicit legacy requests change only the version label,
not the seed-derived values or existing optional camera data.

The bundle contains `profile`, `posture`, `findings`, `uniqueness`, `metadata`.
Metadata has engine_version "1", selected schema_version and template ID.
It contains no duplicate seed. Findings serialize severity/code/message/field.
The bundle is a return value, not a new persisted storage format.

Schema: `schema/engine-bundle.schema.json`, Draft 2020-12.
`$id`: `https://github.com/premiumcentraal-boop/Cyclone-Cloak/schema/engine-bundle.schema.json`.
It requires profile and metadata and rejects unknown bundle properties.
Tests resolve its profile schema reference locally, without fetching schemas.

Posture delegates to Python `build_pif` and Kotlin `CloakForge.buildPif`, which
already delegates to `CloakPif.fromProfile`. No posture logic is duplicated and
no attestation result is inferred. Python uniqueness mirrors the existing Kotlin
`ProfileUniqueness` derived index: case-insensitive values, same eleven identifier
fields, own-ID exclusion, one warning per conflicting field, no raw values in
messages and no mutation of library records.

Coverage accepts a mapping from profile field path to recorded test status.
It reuses Python's APPLIED/PENDING/INTERNAL ledger; Kotlin mirrors it with shared
parity vectors. Arrays are ledger leaves (e.g. camera.cameras). Unknown fields
are PENDING. Missing test status is "not verified". A caller-supplied raw PASS
stays PASS; it does not become VERIFIED. This facade does not read devices,
translate probe IDs or certify supplied reports. The ledger includes historical
PENDING labels (including display geometry) independently of later device proof.

## Optional corpus

`schema/phone-catalog.schema.json` documents schemaVersion 1 with optional
source_confidence, camera_class, expected_capability_set, sensor_class_list,
board, drm_security_level and sources. Existing confidence/classification fields
remain compatible. Kotlin keeps optional corpus claims separately on PhoneTemplate;
Python load_catalog retains them. Generation still copies only the existing
profile blocks. No migration is required.

All 15 existing records were enriched. No new phones were added. Every original
hardware field already exists; board properties and DRM security levels were
omitted because the sources do not establish them confidently for these exact
builds. No specific sensor chip names were invented. Coarse sensor classes are
accelerometer, gyroscope and magnetometer; capability claims are front/rear
camera, Wi-Fi, Bluetooth and NFC. These are documented model classes, not measured
runtime hooks or an exhaustive Android sensor/camera enumeration.

| Phone | Record confidence | Public camera class |
| --- | --- | --- |
| Pixel 4 (`pixel_4`) | needs verification | dual-back + front |
| Pixel 7 (`pixel_7`) | needs verification | dual-back + front |
| Galaxy S23 (`galaxy_s23`) | needs verification | triple-back + front |
| Pixel 6 (`pixel_6`) | approximated | dual-back + front |
| Pixel 6a (`pixel_6a`) | approximated | dual-back + front |
| Pixel 7a (`pixel_7a`) | approximated | dual-back + front |
| Pixel 8 (`pixel_8`) | approximated | dual-back + front |
| Pixel 6 Pro (`pixel_6_pro`) | approximated | triple-back + front |
| Pixel 7 Pro (`pixel_7_pro`) | approximated | triple-back + front |
| Pixel 8 Pro (`pixel_8_pro`) | approximated | triple-back + front |
| Pixel 8a (`pixel_8a`) | approximated | dual-back + front |
| Pixel 9 (`pixel_9`) | approximated | dual-back + front |
| Pixel 9 Pro (`pixel_9_pro`) | approximated | triple-back + front |
| Pixel 9 Pro XL (`pixel_9_pro_xl`) | approximated | triple-back + front |
| Pixel 9a (`pixel_9a`) | approximated | dual-back + front |

Source types: [Google Pixel specifications](https://support.google.com/pixelphone/answer/7158570?hl=en),
[older Pixel specifications](https://support.google.com/pixelphone/answer/16043605?hl=en),
and [Samsung Galaxy S23 specifications](https://www.samsung.com/au/smartphones/galaxy-s/galaxy-s23-phantom-black-128gb-sm-s911bzkaats/).
Each record carries its source URL. Record confidence remains conservative:
public specifications support the coarse new facts, but exact build, firmware
and Camera2 values remain approximated or need verification. Existing camera
blocks are representative logical-camera expectations, not a complete inventory;
Pixel 9a's preserved block has fewer entries than its physical camera class.
Neither camera blocks nor existing synthetic build fields were changed.

## Validation and compatibility notes

SDK/release errors and security-patch date validity already existed. A patch dated
before a build is normal; the handoff's request to reject it was incorrect and
would invalidate existing records. The existing later-than-build warning is
preserved. Invalid calendar dates remain errors.

New corpus checks reject invalid confidence values, malformed optional text/lists,
duplicate list entries and unknown DRM-level strings. Template validation rejects
aspect ratios above 4:1; existing profile-level aspect warnings are unchanged.
Kotlin now mirrors Python camera checks (count, entry type, integral facing 0/1/2,
hardware level, dimensions and unusual sensor aspect). Python now rejects boolean
camera facings rather than accidentally treating True as integer 1. These are
explicit validation additions; generated values and existing valid profiles are
unchanged. Shared camera vectors pin machine codes, severity, paths and messages.

The profile schema previously required UUIDv4 for profile IDs even though both
forges emit UUIDv5, and disallowed null integrity timestamps/tags that both emit.
The documentation schema now accepts those existing values. Identifier UUIDv4
rules are unchanged. A duplicate camera property in the JSON schema was removed
while retaining the existing final camera reference. No stored profile fields
were added, removed or rewritten.

## Verification

```powershell
$env:JAVA_HOME='C:/Program Files/Eclipse Adoptium/jdk-17.0.20.101-hotspot'
& "$env:TEMP/cloak-build/gradle-8.11.1/bin/gradle.bat" -p android :app:testDebugUnitTest :module:testDebugUnitTest --offline --no-daemon
uv run --with pytest -- python -m pytest forge/tests -q
uv run --with pytest --with jsonschema -- python -m pytest forge/schema_tests -q
uv run -- python -m unittest discover -s android/probe/tests -v
```

The supplemental schema suite uses test-only jsonschema via uv; no engine/runtime
dependency was added. It validates both versions of every emitted catalog bundle
and the full catalog. Existing forge/identifier parity tests stay intact; a new
engine vector covers the whole bundle, uniqueness findings and coverage ledger.
