# Runtime envelope phase 2 handoff — 2026-10-20

Main base: `bed5535e3292545c94fa5607381963e350e3c002`; latest tag alpha.27.
No release/tag, device actions, version bump, permission changes or profile
migration. Existing local Cohort Separation and Instance Realism work, old
handoffs and evidence are preserved. This work remains uncommitted.

## Implemented

The existing user/package binding lookup passes a bounded in-memory profile
projection to the existing pinned Java loader. `RuntimeEnvelope` validates an
immutable process snapshot; new callbacks preserve API denials, fail closed on
missing/invalid facts and fail open only on implementation errors. No new reader
format, publisher path, profile storage or connector change.

Callbacks cover visible allowlisted package versions, real sensor inventories and
descriptors, explicit private-directory free-space reads, GPU renderer metadata,
provider/package/UA consistency, real display size, SIM state/slot edges and
match-only DRM security-level reads. Existing camera mapping is deterministic by
real ID/facing and object identity, with physical pixel/capability ceilings.
Sensor descriptors likewise cannot claim better physical capabilities.

Nominal RAM/storage, physical CPU count, GPU vendor, SoC rewriting, dynamic sensor
enumeration and unsupported hardware capabilities are not fabricated or hooked.
See [the exact surface matrix and boundaries](RUNTIME-ENVELOPE-PHASE-2.md).
This is a partial runtime expansion, not proof that every descriptive Reality
fact can be enforced or that incomplete profiles will work with every app.

## Changed files in this run

- `android/module/build.gradle`: enable Kotlin runtime tests (JVM 17); embedded
  Java hook compilation remains Java 11.
- `android/module/src/main/cpp/cloak.cpp`: bounded per-process JNI snapshot from
  the existing saved profile; existing binding/user lookup is untouched.
- `android/module/src/hook/java/dev/cyclone/cloak/hook/HookBridge.java`:
  configure snapshot, integrate callbacks and harden camera object/ordinal mapping.
- New module `RuntimeEnvelope.java`, `RuntimeEnvelopeHooks.java` in that package.
- New module test `RuntimeEnvelopeTest.kt` and synthetic resources
  `envelope-a.json`, `envelope-b.json`. They are test-only, not device definitions.
- New probe `RuntimeEnvelopeReport.java` and `RuntimeEnvelopeReportTest.java`;
  updated `ProbeActivity.java` and `ProbeReportPolicy.java`.
- Updated `catalog/reality-runtime-fields.json` and generated
  `android/app/src/main/java/dev/cyclone/cloak/forge/RealityRuntimeTable.kt`,
  probe `assets/reality-runtime-fields.json`,
  `forge/tests/vectors/reality-runtime-contract.json`,
  `schema/reality-runtime.contract.json`.
- New `schema/runtime-envelope-report.schema.json` and
  `forge/schema_tests/test_runtime_envelope_report_schema.py`.
- Updated `ARCHITECTURE.md`, `CURRENT-STATE.md`, `RUNTIME-COVERAGE.md`,
  `REALITY-RUNTIME-BACKLOG.md`; added the surface guide and this handoff.

The optional format-1 `runtime_envelope` report has 15 digest-only rows and always
states `NOT_VERIFIED`. No raw names/identifiers are added to reports. Legacy and
linked-parity sections keep their format. Contract counts: 10 APPLIED linked-base
paths, 17 PARTIAL, 3 PENDING, 27 DIAGNOSTIC_ONLY, 37 HOST_ONLY, 3 UNSUPPORTED.
No new surface is APPLIED or verified. Existing evidence does not cover this
changed module.

## Verification commands

Repository root, PowerShell:

```powershell
$env:JAVA_HOME='C:/Program Files/Eclipse Adoptium/jdk-17.0.20.101-hotspot'
$env:ANDROID_HOME='C:/Users/Agent/android-sdk'
$env:CLOAK_TEST_WSL='1'
& "$env:TEMP/cloak-build/gradle-8.11.1/bin/gradle.bat" -p android :app:testDebugUnitTest :module:testDebugUnitTest :probe:testStandardDebugUnitTest :probe:testDiagnosticsDebugUnitTest :app:assembleDebug :module:packageModule :probe:assembleStandardDebug :probe:assembleDiagnosticsDebug --offline --no-daemon --console=plain
uv run --offline --with pytest --with jsonschema --with cryptography -- python -X utf8 -m pytest forge/tests forge/schema_tests -q
uv run --offline -- python -X utf8 -m unittest discover -s android/probe/tests -v
uv run --offline -- python -X utf8 tools/generate_runtime_contract.py --check
uv run --offline --with pytest --with jsonschema --with cryptography -- python -X utf8 -m pytest forge/schema_tests/test_runtime_envelope_report_schema.py -q
uv run --offline --with ruff -- ruff check forge/schema_tests/test_runtime_envelope_report_schema.py --select E9,F
git diff --check
git diff --cached --check
```

Full Python/schema: **1,245 passed** (1,073 forge, 172 schema). Probe Python:
**7 passed**. Focused new schema checks: **9 passed**. Generated consumer and
static checks pass. Android: **754 app, 80 module, 114 standard probe and 114
diagnostics probe tests passed**, zero failures/errors. The 20 new Kotlin methods
run on both APIs 34/35 (40 added executions); each probe flavor includes five new
report tests. App debug, module package (both ABIs), standard probe and diagnostics
probe builds pass. No compiler warnings were emitted. Git emits only LF-to-CRLF
normalization notices for existing working-copy settings; whitespace checks pass.

After narrowing the SIM count check to the existing schema's 1–2 slots, the
affected runtime tests and packaging were rerun successfully with:

```powershell
& "$env:TEMP/cloak-build/gradle-8.11.1/bin/gradle.bat" -p android :module:testDebugUnitTest :app:assembleDebug :module:packageModule :probe:testStandardDebugUnitTest :probe:testDiagnosticsDebugUnitTest --offline --no-daemon --console=plain
```

This also confirms the filesystem restat regression guard: a failed new path
validation cannot retain the old private-path scope. Block-alignment, SIM parity,
real camera/sensor capability ceilings and unbound callback controls pass.

Initial schema collection caught a missing new schema `$id`; adding its local
registry identifier fixed collection and the full Python/schema suite passed.
No tests were removed or bypassed.

Logs: `%TEMP%/cloak-envelope-verified.log`,
`%TEMP%/cloak-envelope-final-bounds.log`, `%TEMP%/cloak-envelope-python.log`,
`%TEMP%/cloak-envelope-probe-python.log`.
Local builds retain existing release metadata; they are development artifacts,
not a newly published alpha or evidence for the signed alpha.27 binary.

## Remaining verification

Kotlin tests use two explicitly synthetic fixtures and repeat reconstructed cold
snapshots/unbound controls on Robolectric APIs 34/35. They test actual callback
bodies, not ART installation, physical cold starts or cross-user behavior.
Physical two-fixture, repeated-cold, unbound-restoration and isolation evidence is
**UNVERIFIED for every new surface**. It requires a separate matching-module
device pass with source-supported facts, permission/API limits, production audits
and cleanup proof. Do not invent catalog facts to make those captures pass.
The existing legacy A/B probe fixtures are schema 0.3 and opt out of new Reality
callbacks; their old evidence cannot exercise or verify this expansion.

Missing data in an active 0.4 envelope can now produce an explicit unavailable
exception; callers must handle it. Out-of-scope reads remain host-owned. Hook
availability varies by Android/provider implementation. No claims about platform
certification, attestation, DRM provisioning or physical hardware operation.
