# Internal Dirty-Core Plan — 2026-10-12

## Read of RC2

RC2 is a strong management-layer release, but it is not yet the full engine.

What is now solid:

- Coherence validation.
- One-click profile generation.
- Uniqueness checks.
- Catalog expansion.
- Guided binding.
- Backup/import.
- Readiness diagnostics.
- CI, signed APK and module ZIP release gates.

What remains open:

- RC2 has no physical-device verification.
- The older measured runtime baseline had only 13 verified reads out of 70.
- Display, camera, sensor and several identifier surfaces are incomplete.
- `pif.json` exists, but there is no complete attestation/verdict adapter.
- The engine is still coupled to the app rather than being a clean reusable core.

Conclusion: the next work should stop being UI-led and become engine/runtime-led.

## Operating principle

Split the project into two lanes:

1. **Dirty core lane** — local compatibility engine, runtime adapter, posture adapter, evidence harness.
2. **Normal integration lane** — Cyclone Cloak UI, storage, diagnostics, import/export and release packaging.

Do not do both in the same branch. The dirty core should live on `codex/dirty-core-rc3` or in a separate engine workspace until it is stable.

## Phase A — RC2 device baseline

Before changing runtime behavior, establish what RC2 actually does on the test phone.

1. Install the signed RC2 APK and matching module ZIP.
2. Reboot once.
3. Confirm Root Doctor sees matching app/module versions, Zygisk state and publication state.
4. Record the loaded module hash.
5. Run the independent probe in five phases:
   - unbound before;
   - profile A;
   - repeated profile A;
   - profile B;
   - unbound after.
6. Save a fresh redacted evidence JSON.
7. Generate a new 70-read matrix.

Exit criteria:

- RC2 has its own device evidence.
- Every read is pass, fail, blocked or unmapped with a reason.
- No compatibility claim is inherited from older binaries.

## Phase B — Runtime repair loop

Repair runtime coverage in small, evidence-driven batches.

Priority order:

1. Build and `Build.VERSION` consistency.
2. Native and Java system-property paths.
3. Telephony counts, types, operator and SIM fields.
4. Settings provider and Android ID reads.
5. Advertising ID class-loading and SDK read path.
6. Wi-Fi, Bluetooth, serial and MediaDRM identity callbacks.
7. Locale, country, resource configuration and timezone.
8. Display geometry and refresh rate.
9. Camera and sensor metadata, but only after schema support exists.

For each batch:

1. Build the module and probe.
2. Run module callback tests.
3. Install/reboot only when the owner approves the device action.
4. Re-run the five-phase probe.
5. Save separate evidence.
6. Update the coverage matrix.
7. Commit only after the evidence result is known.

Exit criteria:

- Every mapped surface has either verified device evidence or an explicit blocked/unmapped reason.
- Two distinct profiles remain stable across cold starts.
- Unbound control apps return to host values.
- Production bindings remain intact.

## Phase C — Extract the identity engine

Create a clean engine boundary, separate from the Cloak UI.

Public operations:

```text
generate(request) -> engine bundle
validate(profile) -> findings
derive_posture(profile) -> posture artifact
check_uniqueness(profile, library) -> findings
score_coverage(profile, device_report) -> coverage
```

Engine bundle:

```json
{
  "profile": {},
  "posture": {},
  "findings": [],
  "uniqueness": [],
  "metadata": {}
}
```

Rules:

- No UI, Cyclone connector, storage or root publishing imports.
- Keep schema 0.2 as the default compatibility output.
- Add schema 0.3 only behind an explicit engine flag.
- Preserve deterministic seeded generation.
- Keep Kotlin and Python golden vectors aligned.

## Phase D — Device fact corpus

Move from representative templates to a structured device corpus.

Each device record should include:

- manufacturer, brand, model, product and device;
- hardware and board data;
- Android release, SDK level, security patch and build fingerprint;
- bootloader and baseband shape;
- display dimensions, density and refresh class;
- camera class and expected capability set;
- sensor class list;
- DRM/security level where known;
- carrier/telephony defaults;
- locale/timezone defaults;
- source confidence.

Rules:

- Do not invent hardware capabilities.
- Mark values as public reference, approximated or needs verification.
- Validation must reject impossible combinations.

## Phase E — Posture and attestation adapter

Create one isolated adapter for platform-compatibility posture.

Responsibilities:

1. Generate the PIF-style posture artifact.
2. Run local posture consistency checks.
3. Provide verdict self-test plumbing.
4. Track fingerprint health and rotation state.
5. Trigger reseeding after reset or data clear.
6. Expose an optional keybox-provider abstraction.

Rules:

- Do not store key material in the repository.
- Do not claim a platform verdict unless the local self-test records one.
- Report states as unknown, pass, degraded, failed or needs rotation.
- Keep this separate from UI, Cyclone sync and profile storage.

## Phase F — Cyclone Cloak integration seam

Cyclone Cloak should consume the engine without becoming the engine.

Integration contract:

```text
Engine -> validated compatibility profile -> Cloak binding/publication -> readiness diagnostics
```

Implementation:

1. Define a `DeviceProfileEngine` interface in the app.
2. Keep the current in-app forge as the default implementation.
3. Add an import path for engine-produced bundles.
4. Validate every imported bundle before save.
5. Show engine version, profile schema and confidence metadata.
6. Keep publication and binding behavior unchanged.

## Phase G — RC3 candidate

RC3 is ready only when:

1. RC2 device evidence exists.
2. Runtime coverage measurably improves.
3. Engine extraction passes all golden-vector tests.
4. Posture adapter has explicit state reporting.
5. Full CI is green.
6. Release notes distinguish generated, applied and verified states.

## Guardrails

Keep the dirty core limited to local compatibility engineering and diagnostics.

Do not build:

- account-creation automation;
- platform-policy evasion logic;
- credential handling;
- third-party service abuse tooling.

The objective is narrow: coherent local device profiles, correct app-visible rendering, honest readiness reporting and safe release packaging.
