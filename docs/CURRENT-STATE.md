# Current state

Unreleased: Runtime envelope phase 2 adds scoped read-only callbacks and 15
digest-only probe rows. Seventeen contract paths are PARTIAL; none is newly
APPLIED. Local tests are not physical evidence. Two-profile cold-start, user
isolation and cold unbound restoration remain UNVERIFIED for this module.
Nominal capacity and physical cores remain descriptive where API meanings differ.
No release, permission, binding or publication change. See
[runtime envelope boundaries](RUNTIME-ENVELOPE-PHASE-2.md).

Unreleased: Instance realism v1 adds opt-in deterministic, provenance-labelled
generation from approved complete tuples in Python Device Factory and the
Python/Kotlin identity engine. Reviewed SKU/configuration alternatives and matched
catalog firmware bundles vary together; unsupported hardware/carrier/package facts
stay fixed or unknown. Existing generation and schema 0.2/0.3/0.4 imports remain
compatible. No runtime or device changes; physical verification is UNVERIFIED.
See [instance realism policies and limits](INSTANCE-REALISM-V1.md).

Unreleased: Cohort separation v1 adds pure Python/Kotlin read-only analysis over
saved profile bodies. Seven category scores, shared groups, duplicate/near-duplicate
identifier flags, missing Reality facts and conservative recommendations contain
only IDs/labels and structural metadata. Unknown facts reduce confidence. No UI,
storage, binding, connector, publication or runtime changes; no platform verdict.
See [cohort separation contract and limitations](COHORT-SEPARATION-V1.md).

Alpha.27 integrates `codex/play-readiness-integration-v1` with explicit local
Play/Google diagnostics import and per-profile readiness cards in Profiles.
Envelope validation binds exact profile bytes, Android user, current APK/bundled
module artifact set and freshness. Missing required observations remain UNKNOWN;
stale/mismatched evidence requires review. Account-slot conflicts require explicit
inventory-bound sharing confirmation. Physical capture/import is PENDING: no ADB
device was attached. No runtime, binding or permissions changes.
The owner explicitly approved release with missing device evidence and inherited
legacy-probe lint errors (9 per flavor) documented, subject to signed CI passing.
No probe permissions or source were changed. See
[integration flow and limitations](PLAY-READINESS-INTEGRATION-V1.md).

Alpha.27 includes the `codex/play-evidence-harness-v1` separate diagnostics APK
and local evidence export runner. All collection requires a user tap; optional
public Integrity requests require separate confirmation. Sixteen strict rows cover
caller-visible Play/Google surfaces, with unknowns preserved and no production
mutation. Play Protect, actual settings access and decoded Integrity verdicts
remain unmeasured; physical collection is UNVERIFIED. No permissions added.
See [Play evidence harness](PLAY-EVIDENCE-HARNESS-V1.md).

Alpha.27 includes the `codex/play-readiness-evaluation-v1` strict local Play
readiness report parsing, conservative evidence-bound evaluation and a separate
app-private readiness store. Missing evidence remains UNKNOWN; stale or mismatched
evidence requires review. Integrity metadata stays optional evidence, not a
platform verdict. The evaluator itself does not collect observations or enforce
runtime behavior; the integrated UI is described above.
See [local readiness evaluation](PLAY-READINESS-EVALUATION-V1.md).

Alpha.27 includes the `codex/google-surface-isolation-v1` local Google surface
assignment store and pure isolation/review model over Play Surface Contract v1.
It records approved configuration, requires explicit current-inventory review for
shared slots, and checks supplied profile/user/hash-bound observations. Missing
evidence stays UNKNOWN. This is a state layer, with no new collector, UI flow,
runtime enforcement or platform verdict. Existing profiles and bindings are
unchanged; physical verification is UNVERIFIED. See
[Google Surface Isolation v1](GOOGLE-SURFACE-ISOLATION-V1.md).

Updated 2026-10-19. Current source **v0.11.0-alpha.27 / versionCode 51**
adds local Play readiness integration over Play Surface Contract v1 and the existing Reality Studio, missing-only repair
and Fleet Diversity workflows. Optional schema 0.4 sections describe compute, memory,
panel, camera summaries, sensors, power, DRM level, radio labels, carrier, package
versions, WebView and environment/input metadata. Existing 0.2/0.3 profiles are
not migrated. New-profile and workshop flows explicitly request 0.4; direct
forge APIs keep their 0.3 default for compatibility.

Kotlin/Python rules share a strict schema and parity vectors. Catalog data is
conservative and provenance-labelled; unavailable/unknown fields stay empty.
Reality coverage in Identities separates local coherence from physical evidence.
A read-only optional probe extension hashes new observations without changing the
legacy 89 rows. Alpha.24 physical proof covers the recorded display/linked parity
and capture-lifetime behavior only. Three cycles completed 24 captures across
users 0 and 17; all six bound captures had 13 linked PASS / 0 FAIL. Twelve focused
warm repetitions completed in one process through compacting GC. Both users
restored cold; permissions/AppOps, 327 production entries, published bodies,
publisher shares and accessible stores were unchanged. Cleanup passed each cycle.
User 17 retains four bound permission blocks. See
[measured evidence](runtime-evidence/2026-10-18-222138-alpha24-integrated-stabilization/summary.md) and
[release notes](RELEASE_0.11.0-alpha.24.md). No platform verdict.

Alpha.24 also integrates the opt-in Python Device Factory (12 frozen canonical
references, deterministic identifiers, strict quality/uniqueness reports and
all-or-nothing in-memory batches) and the app Profile Operations Console (explicit
creation/assignment/replacement review, per-profile readiness/history and document
picker exports). Existing generators and profile/binding JSON remain compatible.
Factory firmware/HAL approximations, UI interactions on a phone, and broader
Reality runtime coverage remain **UNVERIFIED**. See [factory](DEVICE-FACTORY-V1.md)
and [console](PROFILE-OPERATIONS-CONSOLE.md) scope and limitations.

The existing Batch Identity Workshop remains available:

Identities now offers multi-template planning (1–20 per template, up to 100 total),
deterministic per-draft seed derivation with uniqueness retries, an in-memory
preview, regeneration/removal, private details and explicit save confirmation.
Each selected item is revalidated against the current library, atomically added
without replacement and reported as saved, failed or skipped. Earlier successes
survive later failures. No binding is created and no connector/publication path
is called. Existing engine metadata remains in its separate hash-bound sidecar.

Versioned local JSON plans preserve seeds, labels, counts, revisions and template
hashes for reproduction, without profile bodies or raw identifiers. Seeds are
private: export requires an explicit warning/confirmation. Import always previews
and recomputes validation; incompatible templates or derivations fail safely.
No profile migration, storage-layout change, new permissions, networking,
connector or module runtime change. Workshop/SAF device verification: UNVERIFIED.
No platform verdict or broader compatibility is claimed.

Separately, the alpha.18 diagnostics lifecycle harness completed three cycles
(24 captures) with zero cold-restoration failures. Production and permission audits
and cleanup passed each cycle, with zero APK installations. Four user-17 bound
permission blocks remain; advertising.id was BLOCKED/IOException in cycle 3's
cold post-removal capture and remains a limitation, not a pass.
[Evidence](runtime-evidence/2026-10-18-015343-alpha18-lifecycle-install-once-3cycles/EXECUTION-REPORT.md)
does not verify the workshop or newly packaged alpha.23 binaries.

## Alpha.26 Play Surface Contract

Optional `play_surface` expectations and separate local readiness records share a
schema, Kotlin/Python parsers and parity vectors. Account-slot sharing requires
consent on every active assignment. Known package/service conflicts remain
explicit, including partial observations. Missing or mismatched evidence remains
UNKNOWN; binding never promotes state or relabels foreign reports. Existing
0.2/0.3/0.4 profiles stay compatible without migration. This is contract/validation
only: no account setup, service isolation, observation provider, persistence or UI.
No runtime, connector, publication, binding storage, permission or platform-verdict
change. See [contract](PLAY-SURFACE-CONTRACT-V1.md) and
[alpha.26 release notes](RELEASE_0.11.0-alpha.26.md).

## Alpha.25 Catalog Import Bridge

The opt-in offline Python pipeline creates provenance-aware drafts from a bounded
hardware XML subset, attaches exactly matching build properties and requires
explicit review/licensing before candidate promotion. Conflicts block acceptance;
missing launch API remains unknown. Approved candidates reuse Device Factory
derivation, validation and supplied-library uniqueness without changing the frozen
canonical catalog or saved profiles. No Android import UI or device test is added.
The complete example is synthetic; approved upstream references still need actual
artifact/license/model/firmware review. No runtime, connector, publication, binding,
profile schema, storage-layout or permission change. Imported platform trust stays
unknown. See [pipeline](CATALOG-IMPORT-BRIDGE-V1.md) and
[alpha.25 release notes](RELEASE_0.11.0-alpha.25.md).

## Reality Studio and Fleet Diversity

Selected schema 0.4 identities expose twelve section editors with provenance,
availability, validation and temporary preservation locks. Edits are draft-only
until an explicit before/after confirmation; stale drafts or invalid/duplicate
values cannot save. Repair requires an exact compatible template, preserves
unavailable facts, and fills only missing optional values. Regeneration reproduces
catalog/seed facts, not new physical hardware. Old profiles remain unchanged.

The library diversity disclosure shows repeated paths, labels, counts and local
recommendations without raw identifiers. Balanced batch rounds prefer distinct
selected combinations and show unavoidable repetition. Version 1 plans without a
diversity policy remain readable. Saves do not invoke binding/publication flows;
existing published state is not refreshed automatically. Hash-bound evidence and
engine sidecars may become stale after a confirmed edit. Locks are editor-local.
See [workflow and limitations](REALITY-STUDIO.md). Physical UI checks **UNVERIFIED**.

## Versions and major components

| Component | Current source version / responsibility |
| --- | --- |
| App | `dev.cyclone.cloak`, `0.11.0-alpha.26`, code 50; minimum API 29, target/compile API 36 |
| Module | `cyclone_cloak`, `v0.11.0-alpha.26`, code 50; ARM64/ARMv7, minimum API 26; scoped display-mode parity callbacks added in alpha.23 |
| Profile | Schema `0.4`, legacy `0.2`/`0.3` accepted; existing JSON storage unchanged |
| Binding / reader | Binding schema 2; module index `schemaVersion: 2`; existing key lookup unchanged |
| Connector | `cyclone.connector/1.3`; Cyclone 5.0.0-alpha.122 or newer |

- Phones/Identities: catalog-based generation, coherent validation, deterministic
  uniqueness retries, engine bundle import and separate local engine metadata.
- Profiles: multi-app/All apps binding review from either entry point, sharing
  the existing per-app record and bulk-binding path.
- Health: per-profile Companion copies checks, Readiness Doctor, local duplicate diagnostics, the read-only
  **Library quality** summary, and the compatibility coverage matrix. Library
  counts use readable profiles and existing validation, binding and local-record
  facts; unknown inventory is not presented as an unreadable count.
- Local compatibility reports: preview/save, provenance, profile/artifact matches,
  freshness and guarded rotation/reset actions. Existing diagnostics classification
  distinguishes missing/blocked/incomplete proof. All states are local evidence,
  not platform verdicts; the library summary does not change posture decisions.
- Data/root/connector: existing atomic profile storage and backup behavior,
  conservative reconciliation, publisher shares and recovery. This release
  preserves storage layout and connector behavior. Binding work reuses one root
  session after preflight; publication scripts retain their inherited lock descriptor and phase diagnostics.
- Module/probe: unchanged runtime implementation and separate diagnostics app.
  The real advertising-ID SDK and its permissions are diagnostics-only. Optional
  observations are counted separately, not promoted to verified coverage. Evidence
  remains artifact-specific; see [RUNTIME-COVERAGE.md](RUNTIME-COVERAGE.md).

## Local verification and release gate

Alpha.26 local release checks: app **608 tests**, module **40 tests**, forge
**876 passed**, schema **131 passed**, probe **109 per flavor** and host probe
checks **7 passed**: **1,880 total**, zero failures/skips.
WSL enabled the actual publication shell tests on Windows. Debug assembly and module
packaging succeeded without compiler warnings. The standard release CI gate signs the APK, packages the module and verifies
that the APK embeds the exact released module ZIP before publishing.

```powershell
$env:JAVA_HOME='C:/Program Files/Eclipse Adoptium/jdk-17.0.20.101-hotspot'
$env:CLOAK_TEST_WSL='1'
& "$env:TEMP/cloak-build/gradle-8.11.1/bin/gradle.bat" -p android :app:testDebugUnitTest :module:testDebugUnitTest :probe:testStandardDebugUnitTest :probe:testDiagnosticsDebugUnitTest :app:assembleDebug :app:assembleRelease :module:packageModule :probe:assembleStandardDebug :probe:assembleDiagnosticsDebug --offline --no-daemon --console=plain
uv run --with pytest -- python -X utf8 -m pytest forge/tests -q
uv run --with pytest --with jsonschema -- python -X utf8 -m pytest forge/schema_tests -q
uv run -- python -X utf8 -m unittest discover -s android/probe/tests -v
uv run -- python -X utf8 tools/verify_release.py --tag v0.11.0-alpha.26 --artifacts
git diff --check
```

The release workflow reruns tests, signs the APK using existing CI secrets, packages
the module and checks exact embedded-module agreement before uploading the prerelease.
Local debug output: `android/app/build/outputs/apk/debug/app-debug.apk`.
Release assets: `app-release.apk`, `cyclone-cloak-0.11.0-alpha.26.zip`,
`play-surface.schema.json`, `play-readiness.schema.json`,
`PLAY-SURFACE-CONTRACT-V1.md` and the Play Surface Contract handoff. No old physical
evidence is attached as new alpha.26 proof.

## Known limits and separately verified device evidence

- Local quality, readiness and PASS records do not prove interception or a
  platform verdict. Runtime coverage is partial. Probe fixture results concern
  their measured artifact; they do not verify arbitrary saved identities or the
  currently installed module. Alpha.9 publication proof covers normal Main-app
  publication on Pixel 8 and two Cyclone profiles; it does not verify runtime hooks
  or an independent profile-side publisher. Cyclone could not open the receiving
  profile for that additional check.
- Existing physical-device checks remain separate, including reboot/activation,
  multi-user behavior beyond the recorded diagnostics transition, publication/recovery and backup/import UI. See
  [DEFERRED-DEVICE-CHECKS.md](DEFERRED-DEVICE-CHECKS.md) and the runtime coverage ledger.
  Earlier artifact measurements are not physical verification of alpha.21.
- Publisher swaps retain the existing two-rename reader gap and do not guarantee
  power-loss safety or lock coordination with older/external writers.
- Profile batch commits coordinate cooperating in-process callers. Downloads
  and private backup commits are separate; private data is lost on uninstall.
  A full-state backup is not implemented.
- Catalog confidence indicates source quality, not firmware capture or device
  verification. Automatic resets/reseeding and network-egress control are absent.

Architecture and contracts: [ARCHITECTURE.md](ARCHITECTURE.md),
[STATE_LAYOUT.md](STATE_LAYOUT.md), [CYCLONE_PROFILES_REPORT.md](CYCLONE_PROFILES_REPORT.md).
Historical handoffs preserve their dated evidence; current library behavior is
in [Run E completion](HANDOFF-2026-10-13-RUN-E-LIBRARY-QUALITY-COMPLETED.md).


## Reality Runtime Bridge (formats 1 and 2)

Alpha.23 / code 47 extends the pure Kotlin/Python contract, generated strict schema,
read-only probe extension, coverage readiness counts and prioritized backlog.
All 97 schema leaf/array paths are represented: **10 applied (when matched), 0 partial, 5 pending,
42 diagnostic-only, 37 host-only, 3 unsupported**. These are capability counts,
not physical measurements. Reality is not consumed by the current module.

Legacy profiles report `not present`; future sections are ignored safely without
rewriting stored data. New physical verification is **UNVERIFIED**. No platform
verdict, new permissions or networking. Scoped display-mode hooks are the only
new runtime behavior in this sprint.
See [Device Reality](DEVICE-REALITY.md#reality-runtime-bridge-formats-1-and-2) and
[runtime backlog](REALITY-RUNTIME-BACKLOG.md).


## Runtime Linked Parity Phase 1

`RealityRuntimeLink` (Kotlin) and `runtime_link.evaluate` (Python) evaluate ten
reviewed aliases from the shared runtime table. Base profile fields remain the
only runtime source. Exact matches are MATCHED; conflicts are MISMATCHED;
missing Reality is NOT_PRESENT and missing base is BASE_MISSING. Findings expose
paths/state only, never values, and do not mutate or publish profiles.
The density link targets the existing `display.density`, not a new density_dpi
base field. No profile schema or storage migration is introduced.

Contract format 2 records linked_base_path, EXACT/NONE parity_requirement,
runtime_covered, present, parity_state and nullable parity_passed. The ten linked
paths are APPLIED only with a present exact match; otherwise PARTIAL. The remaining
87 statuses stay unchanged. Format 1 contracts and optional report format 1 remain
readable. APPLIED describes implemented local capability plus required parity,
not verified execution on any phone. All ten matched: 10 applied, 0 partial,
5 pending, 42 diagnostic-only, 37 host-only, 3 unsupported.

Scoped Java callbacks construct profile-backed Display.Mode return objects and
include the same mode in getSupportedModes without changing host mode objects or
physical display configuration. Missing/invalid base overrides are no-ops or retain
the missing component. Existing metrics/refresh, locale/timezone and telephony
surfaces remain authoritative. Framework-object tests cover API 34/35; older
constructor fallback and ART installation still require physical verification.

The optional `linked_parity` probe extension has 13 digest-only rows: ten linked
observations plus SIM operator, active modem count and supported-mode membership.
The latter records a digest of the full aggregate and separately compares whether
it contains the complete base mode tuple. No base expectation means no PASS.
Permission/API failures remain explicit. Legacy 89 rows are unchanged. The coverage
card shows matched/mismatched links, field paths and applied/partial/pending counts.
Local parity is not a platform verdict. Physical verification: **PENDING**; the
connected device still has alpha.18/code 42, so it cannot validate these callbacks.
