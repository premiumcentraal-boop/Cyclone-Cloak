# Integration Handoff for the Next Agent — 2026-10-12

## Context

Cyclone Cloak RC2 is published and CI green. It adds stronger profile validation, one-click generation, uniqueness checks, guided binding, backup/import and Readiness Doctor v3.

Your work should focus on the safe application/integration layer. A separate compatibility-engine workstream will handle lower-level runtime and posture internals. Do not modify those internals.

## Primary objective

Prepare Cyclone Cloak to consume a standalone Device Profile Engine through a clean internal interface, while preserving all existing behavior.

The user-facing concept is:

```text
Device Profile Engine -> validated compatibility profile -> Cyclone binding -> readiness diagnostics
```

## Important boundary

Do not modify:

- the native runtime module;
- root publication;
- connector contract;
- attestation or posture internals;
- binding identity rules;
- existing storage schemas.

You may add an internal adapter, but existing behavior must remain the default.

## Work package 1 — Engine adapter seam

Add an internal interface such as:

```kotlin
interface DeviceProfileEngine {
    fun generate(request: EngineRequest): EngineResult
    fun validate(profile: JSONObject): List<EngineFinding>
    fun checkUniqueness(profile: JSONObject, library: List<JSONObject>): List<EngineFinding>
}
```

Add stable DTOs for:

- `EngineRequest`;
- `EngineResult`;
- `EngineFinding`;
- `ProfileArtifact`;
- `PostureReference`.

Rules:

- The current in-app forge becomes the default implementation.
- UI must depend on the interface, not directly on forge internals.
- No behavior change is required in this package.
- Add tests proving the default implementation preserves current output.

## Work package 2 — Engine bundle import

Add a local import path for an engine-produced bundle.

The bundle may contain:

```json
{
  "profile": {},
  "posture": {},
  "findings": [],
  "uniqueness": [],
  "metadata": {}
}
```

Requirements:

1. Validate the profile before import.
2. Show findings before saving.
3. Reject invalid bundles.
4. Preserve existing backup privacy rules.
5. Do not include posture content in copied diagnostics.
6. Store only what the existing storage layer already supports unless explicitly approved.

## Work package 3 — Diagnostics wording

Improve readiness reporting so the app distinguishes three states:

1. **Generated** — a valid profile exists.
2. **Applied** — the profile is bound and published for the app.
3. **Verified** — a device probe proved the expected behavior.

Rules:

- Never show “verified” unless device evidence exists.
- Keep the copied diagnostics allowlist unchanged.
- Show engine version and profile schema where available.
- Make unknown states explicit.

## Work package 4 — Catalog metadata UI

Expose catalog confidence without changing the schema.

For each phone template, show whether its data is:

1. Public reference.
2. Approximated.
3. Needs verification.

Requirements:

- Do not imply firmware capture or physical verification.
- Keep confidence metadata read-only.
- Add tests for display and filtering.

## Work package 5 — Documentation and release notes

Update only user-facing and integration documentation:

1. Explain that profiles are generated and validated locally.
2. Explain the difference between generated, applied and verified.
3. Document the new engine adapter seam.
4. Keep known limitations explicit.

Do not claim full device compatibility or platform-verdict success.

## Work package 6 — Release candidate preparation

Only after the prior work packages are complete:

1. Run all existing tests.
2. Confirm app and module versions agree.
3. Verify APK and module ZIP artifacts.
4. Update release notes.
5. Mark unverified device checks clearly.
6. Do not publish without explicit authorization.

## Test commands

Use the repository’s existing commands:

```powershell
uv run --with pytest -- python -m pytest forge/tests -q
gradle -p android :app:testDebugUnitTest :module:testDebugUnitTest --no-daemon
gradle -p android :app:assembleDebug :module:packageModule --no-daemon
```

If local Robolectric dependencies are unavailable, report that as a local environment limitation. Do not remove or bypass tests.

## Required completion report

Each work package must report:

1. Summary.
2. Changed files.
3. Tests run.
4. Build results.
5. Assumptions.
6. Known limitations.
7. Whether release metadata changed.

## Safe language guidance

Use neutral technical terms:

- device-configuration profile;
- compatibility profile;
- coherence validation;
- readiness diagnostics;
- posture artifact;
- runtime evidence;
- engine adapter.

Avoid broader product narratives. Focus only on the interface, storage, diagnostics and release-quality work.
