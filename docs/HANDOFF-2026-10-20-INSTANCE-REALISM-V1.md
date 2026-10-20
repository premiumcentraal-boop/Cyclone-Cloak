# Instance realism v1 handoff — 2026-10-20

Implemented locally on main based on `bed5535e3292545c94fa5607381963e350e3c002`.
No release, tag, device command, installation or version bump. The pre-existing
uncommitted Cohort Separation work and unrelated handoffs/evidence are preserved.

## Changes

- New `forge/cloak_forge/instance_realism.py`: deterministic approved tuple
  selection, application, provenance validation and cross-slot uniqueness checks.
- New `catalog/instance-realism-v1.json` and `tools/generate_instance_realism.py`:
  134 tuples / 24 baseline sources / 12 model families. Generated Kotlin rules
  are pinned to the exact checked-in source. Twenty-two sources offer matching
  counterpart-catalog firmware alternatives; no firmware strings are invented.
- Updated Python `device_factory.py`, `engine.py`, `validate.py`: opt-in generation,
  complete-tuple coherence, deterministic collision retries through the engine,
  and optional provenance validation. Existing generation defaults remain intact.
- New Kotlin `InstanceRealism.kt`, generated `InstanceRealismRules.kt`; updated
  `IdentityEngine.kt`, `DeviceProfileEngine.kt`, `ProfileValidator.kt` and
  `EngineBundleCodec.kt` for matching generation/replay/bundle validation.
- Updated `ProfileBackup.kt` to round-trip allowlisted realism provenance and
  existing network hint on full identity export. Default redacted exports omit
  the derivation claim. No storage layout or format version changed.
- Added `schema/instance-realism.schema.json`; additive optional fields in
  `cloak-profile.schema.json` and `engine-bundle.schema.json`.
- Added `forge/tests/test_instance_realism.py` (58 cases),
  `forge/schema_tests/test_instance_realism_schema.py` (10 cases), and
  Android `InstanceRealismTest.kt` (8 tests, including Python/Kotlin parity).
- Added `INSTANCE-REALISM-V1.md`, this handoff, and updated `ARCHITECTURE.md` /
  `CURRENT-STATE.md` while retaining the previous cohort documentation.

## Public API and metadata

Python `InstanceRequest` adds defaulted `instance_realism=False` and
`realism_tuple=None`; Factory batch and engine requests accept the same options.
Kotlin `IdentityEngine.GenerationRequest` adds the equivalent defaulted fields.
`InstanceRealism.plan(source, seed, tuple)`, `apply`, `validate` and `conflicts`
are pure helpers. Unsupported opt-in templates fail explicitly.

`meta.instance_realism` format 1 records catalog digest, source/tuple, per-field
source/confidence/availability and axis policies. It contains no raw identifiers
or seeds. Engine metadata also keeps the private `realism_request_seed`; pin the
returned tuple and use that input to reproduce selection. `chosen_seed` remains
the final profile seed, compatible with the existing bundle invariant.

## Verification

Full gate: **1,073 forge + 163 schema = 1,236 Python tests passed**;
**754 app + 40 module = 794 Android tests passed**, zero failures/errors.
Module tasks were up-to-date; existing XML results confirmed all 40 tests pass.
Debug APK built successfully at `android/app/build/outputs/apk/debug/app-debug.apk`.
Eight new Kotlin tests and 68 new Python/schema cases are included in those totals.
No compiler warnings appeared. Git reports its existing LF-to-CRLF normalization
notice for `DeviceProfileEngine.kt`; both whitespace checks pass.

Exact final commands (repository root, PowerShell):

```powershell
$env:JAVA_HOME='C:/Program Files/Eclipse Adoptium/jdk-17.0.20.101-hotspot'
$env:ANDROID_HOME='C:/Users/Agent/android-sdk'
$env:CLOAK_TEST_WSL='1'
& "$env:TEMP/cloak-build/gradle-8.11.1/bin/gradle.bat" -p android :app:testDebugUnitTest :module:testDebugUnitTest :app:assembleDebug --offline --no-daemon --console=plain
uv run --offline --with pytest --with jsonschema --with cryptography -- python -X utf8 -m pytest forge/tests forge/schema_tests -q
uv run --offline -- python -X utf8 tools/generate_instance_realism.py --check
uv run --offline --with ruff -- ruff check forge/cloak_forge/instance_realism.py forge/cloak_forge/device_factory.py forge/cloak_forge/engine.py forge/tests/test_instance_realism.py forge/schema_tests/test_instance_realism_schema.py tools/generate_instance_realism.py --select E9,F
git diff --check
git diff --cached --check
```

Additional focused/regression checks during implementation:

```powershell
uv run --offline --with pytest --with jsonschema --with cryptography -- python -X utf8 -m pytest forge/tests/test_instance_realism.py forge/schema_tests/test_instance_realism_schema.py -q
uv run --offline --with pytest --with jsonschema --with cryptography -- python -X utf8 -m pytest forge/tests/test_device_factory.py forge/tests/test_reality.py forge/tests/test_engine.py -q
& "$env:TEMP/cloak-build/gradle-8.11.1/bin/gradle.bat" -p android :app:testDebugUnitTest --tests '*InstanceRealismTest' --offline --no-daemon --console=plain
```

The focused backup test exposed an omitted existing network hint; preserving its
allowlisted value fixed the round-trip. The final full Android gate includes
that fix. Production compilation also caught an unavailable Android JSONObject
comparison method; canonical digest comparison replaced it. Final logs:
`%TEMP%/cloak-realism-android.log`, `%TEMP%/cloak-realism-python.log`.

## Limits and next steps

No UI toggle was added; generation is opt-in through the existing pure APIs.
Only reviewed SKU pairs and existing matching firmware bundles vary. Carrier
issuers, camera/sensor inventories, DRM, and package/provider versions stay fixed
or unknown/unavailable without supporting alternatives. US timezone choices are
configuration preferences, not measured location. Inherited firmware remains
approximated, not device-verified. No credentials, attestation, issued identifier
claims, key material or platform verdicts are generated.

Callers must supply the complete library snapshot for collision checks. Imported
catalog templates without an approved realism baseline remain usable through
legacy generation, but cannot opt into guessed variations. Future registry
versions must retain v1 resolution for saved provenance. Physical compatibility
is UNVERIFIED; this run intentionally performed no device or runtime work.
