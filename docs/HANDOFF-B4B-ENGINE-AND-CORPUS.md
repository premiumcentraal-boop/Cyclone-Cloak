# B4B — identity engine facade + device fact corpus

Assigned: normal agent. Date: 2026-10-13. No release, no tag — commit to `main` and push. If you believe a release is warranted, ask first.

## Context

- Repo: `C:\Users\Agent\Cyclone\cloak-repo`, branch `main` (repo `AGENTS.md` compass has environment facts; short version: `JAVA_HOME=C:/Program Files/Eclipse Adoptium/jdk-17.0.20.101-hotspot`, Gradle at `$env:TEMP/cloak-build/gradle-8.11.1/bin/gradle.bat` invoked with `-p android`, Python via `uv run`, add `--offline --no-daemon` for Gradle).
- The project has two parallel forges that must never drift: `forge/cloak_forge` (Python reference: `forge.py`, `derive.py`, `validate.py`, `coverage.py`, templates over `catalog/phones.json`) and the Kotlin mirror `android/app/src/main/java/dev/cyclone/cloak/forge` (`CloakForge.kt`, `PhoneTemplate.kt`, `ProfileValidator.kt`), pinned together by parity vectors in `CloakForgeTest` and the coverage ledger tests in `forge/tests/test_coverage.py`.
- The long-standing plan (see `docs/INTERNAL-DIRTY-CORE-PLAN-2026-10-12.md`, Phase C and Phase D) calls for exactly this sprint. This handoff is that plan, scoped to what is safe to build now.

## Part 1 — Engine facade (Phase C)

Create a clean engine boundary over the existing forge logic. **Facade and wiring only — no behavior changes.**

Public operations (both languages, same names):

```text
generate(request) -> engine bundle
validate(profile) -> findings
derive_posture(profile) -> posture artifact
check_uniqueness(profile, library) -> findings
score_coverage(profile, device_report) -> coverage
```

Engine bundle shape:

```json
{
  "profile": {},
  "posture": {},
  "findings": [],
  "uniqueness": [],
  "metadata": {}
}
```

Implementation notes:

- Python facade: `forge/cloak_forge/engine.py` wrapping the existing functions. Kotlin facade: `IdentityEngine.kt` in the app forge package wrapping `CloakForge`/`ProfileValidator`/`CloakPif` (reuse `buildPif`; do not duplicate posture logic).
- `generate(request)`: request carries `name`, `seed`, `template`, optional `schema_version` (accepts `0.2` and `0.3`; default `0.3` — that is the current forge default), returns the full bundle with findings from validating the generated profile and a uniqueness check against an empty library.
- Bundle schema: add `schema/engine-bundle.schema.json` (draft 2020-12, same style as the profile schema) with required `profile` and `metadata`, additionalProperties rules matching the profile schema, and validate emitted bundles in tests.
- `check_uniqueness` and `score_coverage` may be thin first versions if the underlying logic already exists (uniqueness: cross-profile identifier overlap check; coverage: reuse the coverage ledger classification against a device report mapping). If a piece genuinely does not exist yet, implement it minimally and mark it `PENDING` in the ledger honestly.
- Hard rules: **no UI, no Cyclone connector, no storage, no platform-publishing imports inside the engine facades**; deterministic seeded generation must stay byte-identical (parity vectors are the proof); do not change any existing function signature — wrap, do not rewrite.

## Part 2 — Device fact corpus (Phase D)

Turn `catalog/phones.json` entries from representative templates into structured device records. All new fields are **optional** in the schema; validation rules only fire when a field is present, so older catalogs keep validating.

Add per phone (where publicly documented; mark confidence honestly):

- `hardware`/`board` enrichment if missing;
- `camera_class` (e.g. "triple-back + front") and `expected_capability_set`;
- `sensor_class_list` (coarse classes like accelerometer/gyro/magnetometer — never invent specific sensors);
- `drm_security_level` where publicly known;
- `source_confidence` per record: `"public reference"`, `"approximated"`, or `"needs verification"`.

Validation additions (`forge/cloak_forge/validate.py` and mirrored in `ProfileValidator.kt` where cheap):

- reject impossible combinations: `sdk_int` vs `version_release` mismatch, `security_patch` earlier than build date, nonsense display aspect, camera facings outside {0,1,2};
- confidence values outside the three allowed strings are a finding.

Also: you may add up to **5 new well-documented phones** to the catalog with the same structure — public reference data only, every field sourced from public specifications, `source_confidence` set accordingly. Each new phone must pass catalog validation without findings and carry a camera block (see the existing 15 entries for the exact shape).

## Boundaries

- Changes limited to: `forge/**`, `android/app/src/main/java/dev/cyclone/cloak/forge/**`, `android/app/src/test/**`, `catalog/phones.json`, `schema/**`, `docs/**`.
- Do **not** touch `android/module/**` (HookBridge, cloak.cpp), the probe, or connector surfaces — that is a separate workstream with device dependencies.
- All existing suites must stay green: forge pytest (`uv run --with pytest -- python -m pytest forge/tests -q`), module unit tests, app unit tests (`:app:testDebugUnitTest :module:testDebugUnitTest`), runner tests. Add tests for: facade round-trip (generate -> validate -> empty findings for a fresh template), bundle schema validation, parity vectors still passing, new catalog entries validating, and at least one rejection case per new validation rule.

## Reporting

Report back: the facade file paths and exact public signatures, the bundle schema `$id`, how `derive_posture` reuses existing posture logic, the list of enriched/new catalog phones with confidence levels, and any existing behavior you had to change (should be none).