# Catalog Import Bridge v1 — handoff

Date: 2026-10-18. Branch: `codex/catalog-import-bridge-v1`.
Base: `0ba1168`, latest main after the signed alpha.24 release.

Implemented an offline, provenance-aware XML/build-property/review pipeline and
an opt-in admission path into the existing Device Factory. Hardware-only drafts
remain incomplete. Conflicts block promotion unless an explicit reviewed
correction names the disagreeing evidence. Missing launch API remains unknown.
Imported trust, certification and attestation stay unknown.

## Changed files

- `forge/cloak_forge/hardware_xml.py`: bounded AOSP-subset XML parsing, matching
  build properties, explicit review attachment, resolution, completion,
  promotion and candidate replay validation.
- `forge/cloak_forge/catalog_provenance.py`: source/manifest validation, exact
  byte hashes, availability/confidence, stable fact IDs, bounded strict JSON,
  fixed redacted rejection codes and resolution-reference validation.
- `forge/cloak_forge/device_factory.py`: additive
  `DeviceFactory.from_imported_candidates(candidates)`; existing canonical
  catalog and model-specific checks unchanged.
- `catalog/import-fields-v1.json`: versioned canonical fact allowlist and
  required complete Factory shape. No fallback templates.
- `catalog/import-sources-v1.json`: eight approved reference pages, snapshot
  hashes, AOSP pinned revision, license/notice context, date and confidence scope.
- `schema/catalog-provenance.v1.schema.json`: new import interchange schema;
  existing profile schemas are unchanged.
- `forge/tests/test_hardware_xml.py`: 75 focused importer/factory tests.
- `forge/schema_tests/test_catalog_import_schema.py`: 13 new schema and legacy
  profile compatibility tests.
- `forge/tests/fixtures/catalog-import/`: self-authored XML, matching synthetic
  build properties, explicit completion facts, sources, complete synthetic
  candidate and LF attributes preserving exact hashes across platforms.
- `docs/CATALOG-IMPORT-BRIDGE-V1.md`: API, examples, supported subset,
  provenance/source table, promotion rules, limits and confidence boundaries.
- `docs/DEVICE-FACTORY-V1.md`: links the optional import path.
- This handoff.

## Public entry points and interchange

```python
parse_hardware_xml(text, source, *, device_id=None)
attach_build_properties(manifest, text, source)
attach_review(manifest, text, source)
resolve(manifest)
completion(manifest)
promote(manifest, *, reviewer)
validate_candidate(candidate)
validate_source(source)
validate_manifest(manifest, allowed_paths)
DeviceFactory.from_imported_candidates(candidates)
```

Inputs accept local bytes; XML/build-property input is limited to 256,000 bytes,
JSON to 2 MB. New source records include `license_reviewed`; contributing sources
cannot promote until it is true. Sources bind exact bytes with SHA-256 and may
also carry a revision. `known` facts have a value; `unknown` and `unavailable`
have null values. Field evidence and confidence remain in the separate format-1
provenance manifest. Candidate digest binds the template, manifest and explicit
review assertion. This is tamper detection, not cryptographic authorization.

Factory generation reuses deterministic HMAC identifiers, stable derivation,
existing coherence validation, cross-instance/library uniqueness and atomic
in-memory batch acceptance. The caller supplies the existing library and decides
whether to persist profiles. No new storage layout or automatic binding occurs.

## Verification

| Gate | Passed | Failures / skips |
|---|---:|---|
| Forge | 810 | 0 / 0 |
| Schema | 94 | 0 / 0 |
| Android app | 598 | 0 / 0 |
| Android module | 40 | 0 / 0 |
| Standard probe JVM | 109 | 0 / 0 |
| Diagnostics probe JVM | 109 | 0 / 0 |
| Probe host Python | 7 | 0 / 0 |
| **Total** | **1,767** | **0 / 0** |

Debug build succeeded. No compiler warnings were emitted. Ruff checks/format
checks and staged/unstaged diff whitespace checks passed. Initial new-test
failures were corrected (legacy test used a non-hex seed, synthetic source labels
were compared as if identical, and a test assumed SDK 34 instead of the fixture's
33); no existing behavior was changed to make these pass.

Exact commands, from the repository root:

```powershell
$env:JAVA_HOME='C:/Program Files/Eclipse Adoptium/jdk-17.0.20.101-hotspot'
$env:ANDROID_HOME='C:/Users/Agent/android-sdk'
$env:CLOAK_TEST_WSL='1'
& "$env:TEMP/cloak-build/gradle-8.11.1/bin/gradle.bat" -p android :app:testDebugUnitTest :module:testDebugUnitTest :probe:testStandardDebugUnitTest :probe:testDiagnosticsDebugUnitTest :app:assembleDebug --offline --no-daemon --console=plain
uv run --with pytest -- python -X utf8 -m pytest forge/tests/test_hardware_xml.py -q --tb=short
uv run --with pytest -- python -X utf8 -m pytest forge/tests -q --tb=short
uv run --with pytest --with jsonschema -- python -X utf8 -m pytest forge/schema_tests -q --tb=short
uv run -- python -X utf8 -m unittest discover -s android/probe/tests -v
uv run --with ruff -- ruff check forge/cloak_forge/catalog_provenance.py forge/cloak_forge/hardware_xml.py forge/tests/test_hardware_xml.py forge/schema_tests/test_catalog_import_schema.py
uv run --with ruff -- ruff format --check forge/cloak_forge/catalog_provenance.py forge/cloak_forge/hardware_xml.py forge/tests/test_hardware_xml.py forge/schema_tests/test_catalog_import_schema.py
git diff --check
git diff --cached --check
```

Debug APK: `android/app/build/outputs/apk/debug/app-debug.apk`.
No release build, tag, publication, device operation or production install.
Android runtime/module/connector/publication code, versions, existing profile
schemas, catalog records and saved-profile storage were not modified. The Android
build already packages `catalog/` as assets, so its normal asset task sees the new
inert reference/allowlist files; no Android reader or behavior uses them.
Older unrelated untracked handoffs and evidence were left untouched.

## Limits and next steps

- Only XML, build-property and explicit review JSON adapters exist. Other
  approved sources are reference/cross-check sources, not automated data feeds.
- Reference-page hashes identify what was consulted, not downloadable artifacts.
  Per-file licensing, exact model/SKU, firmware binding and source confidence
  require review before real records can be promoted.
- V1 intentionally requires the current complete Factory capability shape.
  Partial hardware definitions remain useful drafts, not generation-ready phones.
- Unsupported XML extensions fail closed; curate and hash supported inputs or
  add a separately tested subset extension. No makefiles/scripts are executed.
- The complete example is synthetic and retains explicit compatibility
  approximations; no new real hardware definition was approved or physically
  verified in this sprint.
- No Android import UI or catalog installation was added. A future local review
  surface may consume candidates without changing profile storage.
- No platform verdict, attestation, credentials or key material is generated.
