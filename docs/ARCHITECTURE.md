# Cyclone Cloak Architecture

Current source: v0.11.0-alpha.26, app/module code 50. Device Reality v1
adds optional profile schema 0.4 descriptions and read-only probe observations.
Existing schema 0.2/0.3 files are accepted as-is. Physical evidence is artifact-specific; see the runtime coverage ledger.

## Play Surface Contract v1

An optional `play_surface` describes local account-slot and package/service
expectations without account operations or runtime enforcement. A shared JSON
Schema generates Kotlin rules and is consumed directly by Python. Both pure
validators use common vectors; absent sections preserve existing profiles.
Separate readiness records bind local evidence to profile ID/body, artifact and
report digests. Mismatches or missing evidence yield UNKNOWN, never promotion.
Active slot reuse requires explicit sharing consent on every assignment. No
provider collection, persistence, UI or Google/platform verdict is implemented.
See [contract and APIs](PLAY-SURFACE-CONTRACT-V1.md).

## Catalog Import Bridge v1

The offline Python bridge parses an allowlisted hardware XML subset into a separate
provenance manifest with known/unknown/unavailable facts. Matching build properties
and explicit reviewed corrections complete a draft; disagreement cannot be hidden
by precedence. Per-source hashes, license review and field evidence/confidence are
validated before promotion. Missing launch API remains unknown. A digest-bound
candidate is replayed through completion before
`DeviceFactory.from_imported_candidates()` admits it to existing deterministic
derivation, coherence, uniqueness and batch acceptance. Canonical templates and
saved-profile schemas remain unchanged. No Android import UI or runtime path uses
the bridge. Reference pages are not licensed hardware artifacts; the promoted
example is synthetic and imported platform trust stays unknown.
See [pipeline and promotion rules](CATALOG-IMPORT-BRIDGE-V1.md).

## Alpha.24 integration and hook lifetime

The embedded Java/Pine DEX is loaded with an InMemoryDexClassLoader. Native
trampolines hold code addresses rather than Java GC roots, so the module retains
one strong JNI global reference to that loader for the scoped process lifetime,
including partial installation. Failure to allocate the root aborts Java hook
installation; local-reference cleanup does not release the global root. No GC
suppression or forced cold-capture workaround is used. Pine receives the actual
host API/debug mode before profile Build values can affect its configuration.
Display mode construction tolerates optional metadata failure and falls back to
the simple constructor while preserving the same active/supported profile tuple.

Device Factory v1 is a separate opt-in Python API with a frozen twelve-template
catalog; it does not replace existing Android or Python generators. The Profile
Operations Console composes existing workshop, validation, binding review,
reconciliation and journal components. Replacement requires an explicit guarded
review; it preserves disabled records and rejects stale target snapshots.
Run journal format 1 accepts optional redacted previous-device references.
No connector protocol, binding JSON, profile schema or permission change.
See [factory](DEVICE-FACTORY-V1.md) and [console](PROFILE-OPERATIONS-CONSOLE.md).

## Reality Studio and Fleet Diversity

`RealityStudio.Session` owns a defensive copy and ephemeral field locks. Only
schema-listed Reality value paths are editable; identifiers and the base profile
are outside its editing capability. Validation runs before accepting each draft
operation. User edits use the existing `needs verification` source and an explicit
`User-set fields:` reason marker, without changing the profile schema. Mixed
provenance is conservative rather than labelling retained edits as public facts.

Repair adds missing values only from an exact model/firmware/API/hardware-compatible
template. Regeneration restores catalog/seed values in one section, preserving locks
and unavailable envelopes. Neither operation fabricates unknown hardware. A preview
lists every changed leaf path, with sensitive labels hidden. Explicit save compares
the original profile hash and rechecks coherence and library uniqueness. The
ViewModel calls the existing atomic CloakStore writer directly; it does not call
saveIdentity, publication, reconciliation, the connector or binding storage.
Hash-bound engine metadata/evidence can become stale, and publication is not
refreshed automatically. Locks and unsaved drafts end when the editor closes.
Older schemas are read-only here, without implicit migration.

`FleetDiversity` groups readable profiles by model, firmware, camera, display,
recorded storage and SoC characteristics, plus a correlated core group. It hashes
comparison values internally and returns only paths, labels, counts and section
recommendations. Score is the fraction of distinct known characteristics over all
profile pairs and six characteristics; missing facts earn no points, and no
comparable pairs produces an unrecorded score. This is similarity of local data,
not runtime coverage or a platform verdict.

The pure batch planner defaults to balanced fair rounds across selected template
clusters before repeats. Per-template counts and seed/uniqueness derivations are
unchanged. Format 1 adds optional `request.diversity_policy` (`balanced` or `legacy`).
Absent policy means legacy ordering; bodies are reproduced from the recorded
seeds, current matching template digests and derivation metadata. Imported summaries
are never trusted. Repeated clusters and diversity score are recomputed for review.
No invented firmware, camera, SoC or storage variants are generated to raise a score.

## Device Reality v1

`DeviceReality` is pure: no storage, Android APIs, hooks or connector calls.
`schema/device-reality.schema.json` defines bounded sections and availability;
Python validation and generated Kotlin `RealityRules` share those definitions.
Explicit cross-field rules bind panel, carrier, environment and WebView values to
the base profile, and check SoC/API/hardware, storage, camera inventory and power.
New-profile UI and batch planning request 0.4; direct engine/forge defaults stay
0.3 for API compatibility. Plans retain an explicit schema version. No stored
profile is rewritten. Existing storage/import paths carry the additive body.

Catalog facts are copied deterministically; unknown hardware stays unknown.
A seed-derived inventory marker and configured radio labels participate in local
uniqueness checks. Neither a marker nor a label claims a physical hardware ID.

The probe retains its 89 legacy rows and adds optional `device_reality` format 1
with hashed observations. Capture-session ownership and final generation gating
also cover this extension. `RealityCoverage` validates extension hashes and IDs;
`RuntimeEvidence` merges fixture results conservatively into existing in-memory
coverage state. Match-only never promotes to verified. Identity coverage shows
local coherence separately from missing facts and probe fixture evidence.
See [Device Reality](DEVICE-REALITY.md) for sources, format and limitations.

## Batch Identity Workshop

`BatchIdentityPlanner` is a pure planning layer over `DeviceProfileEngine`, the
existing coherence validator and `ProfileUniqueness`. A request selects distinct
template IDs, 1–20 drafts per template (100 total maximum), a batch seed and an
optional label prefix. Stable SHA-256 derivation separates each template, ordinal
and regeneration revision. The engine retries identifier collisions against the
library snapshot and already planned drafts. Every draft retains requested and
chosen seeds, attempt count, template digest, revision, label and findings.

The Identities entry opens a Material 3 preview, with selection, regeneration,
removal, private details, an aggregate summary and explicit save confirmation.
`BatchIdentitySave` revalidates each approved item against the current library and
earlier successes. It reports SAVED/FAILED/SKIPPED per item and continues after a
failed write. The ViewModel uses the existing atomic additive `CloakStore.importBatch`
for one profile at a time and the existing hash-bound `EngineMetadataStore` for
engine metadata. It never calls the identity save path that can republish an
existing binding, reconciliation, publication or the connector. There is no
existing-identity replacement option. Removing a preview cannot delete saved data.

Batch-plan JSON version 1 contains planner/engine/schema versions, template IDs
and hashes, counts, labels, seeds, revisions, retries and a descriptive validation
summary. Strict parsing rejects duplicate keys, oversized/deep data, unsupported
versions, missing/drifted templates and inconsistent seed derivations. Import
reproduces bodies from the current matching templates and recomputes validation
and uniqueness; imported summaries cannot authorize saving. No profile bodies or
raw identifier values are exported, but reproduction seeds are private and an
explicit warning precedes local document export. Plans remain separate from the
profile schema, sidecar format and binding storage. Closing discards only drafts.

## Existing diagnostics evidence

Each probe capture owns its request metadata, profile, expectations and rows.
A generation gate prevents superseded workers from publishing or updating UI;
only complete sets of 89 unique known rows may be successful reports.
The harness retains bounded redacted failure evidence before cleanup.
Five Pixel 8 captures at diagnostics commit `8e7ab14`, against installed
alpha.17/code 41, proved user 0/A and user 17/B selection across warm/cold
transitions: 408 PASS, 29 OBSERVED, zero FAIL/MISSING. Four user-17 permission
limitations account for eight BLOCKED occurrences. Production audits and
permissions matched before/after. This does not physically verify the newly
packaged alpha.18 binaries or establish a platform verdict.
Diagnostics fixture sidecars retain the typed/enum, external-state and paired
stability policies described in [policy documentation](DIAGNOSTIC-EXPECTATION-POLICIES.md).
Normal-app publication and preservation across two Cyclone profiles passed in
older evidence; independent profile-side publication remains unverified.
See [CURRENT-STATE.md](CURRENT-STATE.md) for checks and publication status.
Dated release reports describe their historical releases.

## System view

```text
Cyclone app in the same Android user
        | approved cyclone.connector/1.3 provider/Binder calls and events
        v
Cloak companion: UI -> Cyclone sync -> root publishing
        | su --mount-master
        v
/data/adb/cyclone_cloak/publishers/<androidUser>/
        | merge shares, Main takes precedence
        v
/data/adb/cyclone_cloak/state-v1/
        | root Zygisk companion reads the user/package profile
        v
Zygisk module in the scoped app -> Build/properties + Pine Java callbacks
```

The connector requires Cyclone 5.0.0-alpha.122 or newer. Cloak talks to Cyclone
in its own Android user. The previously proposed loopback HTTPS API is not implemented.

## Pure identity engine

The Python `cloak_forge.engine` module and Kotlin `IdentityEngine` object wrap
existing generation, validation, posture and uniqueness logic. They return
bundles without storage or connector actions. [Engine/corpus documentation](IDENTITY-ENGINE.md)
records signatures, optional catalog facts and proof limits. Runtime hooks and
publishing remain separate components.

The app's `DeviceProfileEngine` adapter delegates to that pure facade. Normal
new-profile actions perform deterministic uniqueness retries before saving;
Import also accepts validated engine bundles. `EngineMetadataStore` retains
metadata in app-private preferences keyed by profile ID and body digest, leaving
the existing profiles directory and all published bodies unchanged. The identity
screen exposes descriptive engine labels and optional probe-fixture field
coverage. [B5 handoff](HANDOFF-2026-10-13-B5-ENGINE-INTEGRATION.md) documents recovery
and evidence limits; runtime and connector paths do not consume this sidecar.

## Local compatibility posture

`PostureState`, `PostureRecord` and `PostureStore` provide separate app-private
records keyed by saved profile ID. They do not infer a platform result from
integrity fields, engine metadata or runtime evidence. The default
`LocalCompatibilityPostureProvider` returns UNKNOWN and does not save anything.
Missing or corrupt records display as "not recorded" beneath the identity card.
These are neutral local compatibility states, not platform verdicts and not inputs
to binding health or publication. Pure rotation helpers require explicit caller
persistence and a completed local reset before clearing a rotation flag. See
[B6A handoff](HANDOFF-2026-10-13-B6A-POSTURE-STATE.md).

## App layout

`android/probe` is an independent diagnostic APK, not part of the companion or
module payload. It reads framework/native APIs against two synthetic fixtures
and displays per-read results. [RUNTIME-COVERAGE.md](RUNTIME-COVERAGE.md) records
device evidence separately from source implementation and local callback tests.

`MainActivity` hosts `CloakTheme` and `CloakApp` using `CloakViewModel`. The view
model exposes observable state and serializes changes with a mutex on the IO
dispatcher. `ConnectorService` handles connector registration and wakes.

Packages under `android/app/src/main/java/dev/cyclone/cloak/`:

| Package | Responsibility |
| --- | --- |
| `forge/` | Shared phone catalog, phone drafts, dump/JSON imports, coherence validation, seeded identities and fleets, PIF data |
| `data/` | Identities, custom phones, bindings, install placement and atomic JSON writes |
| `root/` | Root Doctor, module repair, binding resolution, staging and root publication |
| `cyclone/` | Connector bridge, approval/scope gates, events, reconciliation, Main mirror, health, bulk binding, open requests and startup callback |
| `ui/` | Four tabs, phone editor, cards, theme and view model |

`com/cyclone/connector/client/` contains the connector client and startup provider
base. `ArchitectureTest` checks that `forge` and `data` import no other Cloak
layers, `root` may import those two, `cyclone` may also import `root`, and `ui`
may import all lower layers.

| Tab | Current behavior |
| --- | --- |
| Phones | Built-in/custom templates; one-click validated profile generation; build, import, clone, edit and share |
| Identities | Seeded generation, fleet creation, values, rename, guarded deletion, binding and import/export |
| Profiles | Cyclone profiles, guided multi-app binding, filtered bindings, health pills, bulk binding and open requests |
| Health | Readiness Doctor v3 readiness/checks, redacted clipboard diagnostics, existing repair/reconnect actions and bundled compatibility coverage |

Editing a phone does not change the snapshot already stored in an identity.

`ui/ProfileGeneration` orchestrates the Phones generation action: choose a unique
default name, allocate a seed with `CloakForge.newSeed`, forge the full existing
payload, validate it and call the existing atomic `CloakStore.save` writer only
when no errors remain. `CloakViewModel` serializes the read/generate/save operation
with its existing work mutex and retains running/saved/rejected/failed UI state.
Duplicate IDs or names are rejected rather than replacing existing identities.
This flow does not publish bindings or change the connector/runtime contracts.

## Data and synchronization

The profile contract is `schema/cloak-profile.schema.json`, `schema_version: "0.4"` (0.2/0.3 accepted unchanged).
Python `forge/cloak_forge/` and Kotlin `forge/ProfileValidator.kt` implement
coherence checks. They consume `catalog/phones.json` and cases in `forge/tests/vectors/`.

Identities use app-private `files/profiles/`; custom phones use `files/phones/`.
`JsonDirectory` uses temporary files and atomic renames and skips unreadable
entries. Bindings use `no_backup/cloak-bindings-v1.json` (binding-store schema
version 2, separate from the profile schema and module index versions).

A binding's stable identity is Cyclone profile id plus package. Reconciliation
updates its Android user number from Cyclone's registry. Main binds apps across
ready profiles; a profile's Cloak binds only its own apps. Main is not a binding
target. Sync checks approval/scopes, writes changed connector tuples, clears stale
tuples and reports health. Snapshot absence, trashed/unready profiles, missing
app lists/packages and duplicate ties retain local choices and warn. Deferred
bindings are excluded from remote cleanup; deletion requires a removal event
confirmed by the current snapshot. See [RECONCILIATION-TESTS.md](RECONCILIATION-TESTS.md).
Event sequence numbers deduplicate wakes. Open
requests are gated and are not retried blindly. `CloakStartupProvider` resolves
enabled bindings for the pre-launch callback.

Each install publishes its own root-owned share. The assembled tree merges all
shares with Main taking precedence. Publication now holds a process-safe kernel
lock across migration, share replacement, pruning and merge; interrupted swaps
restore the previous complete tree before another merge. Each invocation owns
its private staging snapshot. Secondary installs mirror Main's bindings
through root reads. [STATE_LAYOUT.md](STATE_LAYOUT.md) defines permissions, keys,
index metadata, migration and publication.

## Module and diagnostics

`android/module` builds a Magisk/Zygisk module for `arm64-v8a` and `armeabi-v7a`.
`cloak.cpp` requests the profile from the root companion before specialization,
then applies Build fields, property interception and locale/timezone after
specialization. The embedded Pine library and
`src/hook/java/dev/cyclone/cloak/hook/HookBridge.java` implement Java callbacks for
telephony, Android ID, Wi-Fi/Bluetooth addresses, serial, WebView user agent,
MediaDRM device id, advertising ID and guarded GSF queries. Stored identifiers
also appear under `cloak.*` properties; storage does not prove every consumer
API is intercepted. Callbacks remain available during the app process lifetime;
the code does not implement the old claimed unload posture. Unbound apps do not
receive a profile.

Root Doctor checks root access, Magisk/Zygisk, module version/architecture and
publication. The app embeds the matching module ZIP for explicit offline repair.
An installed module update requires a reboot and another check.

Publishing `pif.json` does not establish a Play Integrity verdict. Automatic
attestation checks, keybox management, burned-fingerprint rotation, egress/account
tracking and automatic reseeding on app-data clear are not implemented in this
release. Device verification and remaining compatibility surfaces are recorded
in [CURRENT-STATE.md](CURRENT-STATE.md) and [COMPAT_SURFACE.md](COMPAT_SURFACE.md).

## Local compatibility posture

Posture is a local evidence layer, not a platform verdict. `PostureStore` keeps one
app-private record per profile. B6B adds a versioned local report parser and a
conservative decision engine. A report must name its provider, check time, profile
ID, profile SHA-256, artifact SHA-256 and explicit check rows; an optional raw
verdict is retained but never overrides check rows.

The decision maps actual rows to PASS, DEGRADED, FAILED or UNKNOWN. A profile-body
mismatch, artifact mismatch, future timestamp or report older than seven days maps
to NEEDS_ROTATION instead of reusing the evidence. If the current artifact digest is
unavailable, PASS is not possible. The Identities screen can load one report for a
selected identity, then shows state, source, timestamp, reason and an artifact-mismatch
warning. Calling providers and loading reports never save records; an explicit
preview/save action does.
The subsequent diagnostics adapter accepts the existing runtime probe JSON without
changing its wire format. It reuses five-phase proof classification for every read,
including reads with no profile-schema mapping. Only VERIFIED becomes a PASS row.
Each row also carries a machine-readable cause. Missing phases, blocked reads,
unmapped or ambiguous results map to UNKNOWN, while raw PASS, partial or control
instability remain WARNING; failures remain FAILED. Missing saved-profile scope
adds a warning, and conflicting artifact/metadata digests are discarded.
A load binds the in-memory report to the selected ID and canonical body SHA-256.
Optional source profile claims are checked, never overwritten silently. The identity
card offers **Load diagnostics for posture**, a preview with every row and freshness
warnings, then **Save local posture**. Save reloads the profile and rechecks context;
changes require another review. Mismatched profiles cannot be saved. Only the existing
posture summary record is persisted; raw diagnostics, expected values and check values
are not stored. Artifact comparison concerns the bundled module payload, not the
installed module. Older profile files, binding and publication behavior are unchanged.

## Local profile uniqueness

`forge/ProfileUniqueness` derives an in-memory index from readable saved profile JSON. It is rebuilt before each proposed write and for Health diagnostics, so there is no cache file, migration, or new storage format to corrupt. It indexes Android ID, advertising/app-set IDs, serial, Widevine ID, Wi-Fi/Bluetooth MAC addresses, both IMEIs, SIM serial, and GSF ID within the current Android user's library. Comparisons are case-insensitive; empty and non-string entries are skipped. Coherence validation separately reports malformed values.

Create, generate, import (including fleets), duplicate, rename, and explicit identifier review check the library before saving. Duplicate findings use `warning`, `DUPLICATE_IDENTIFIER`, and the identifier's field path. A review offers per-field regeneration or explicit duplicate acceptance. Library imports preview the whole validated batch and commit together after explicit confirmation; cancellation saves none of the proposed import. A reviewed save checks the latest library again. The existing atomic store writes only after review/validation, and existing publication is invoked after explicit saves of bound profiles.

Regeneration derives one replacement from a fresh random seed and retries collisions up to 32 times. It changes only that draft field; profile ID, original seed, other identifiers, and saved source records remain unchanged. The replacement is an explicit override, so replaying the original seed will reconstruct the original value rather than the override. Existing duplicates are never automatically repaired. Health lists duplicate groups and affected profile names, hides raw identifier values, and keeps this view separate from copied diagnostics. Checks do not compare profiles across Android users or against devices outside this library.

## Readiness Doctor v3

The Health screen derives read-only facts from the last sync/root results and the
local library snapshot. It separately reports publication, synchronization,
version agreement, binding enabled state, Cyclone profile/user/app references,
identity readability/coherence, duplicate binding targets, identifier conflicts,
and identity ID/reference mismatches. Unreadable files are counted during the
existing local-library refresh; they are never repaired, removed or rewritten.

The worst applicable check determines ready/degraded/failed. Disabled bindings
retain visible checks but do not affect the verdict, and no enabled bindings
produces degraded. Missing/corrupt referenced identities fail; unconfirmed facts
warn. Main-mirrored identity files are not locally readable and are explicitly
unverified here. Staleness means mismatched references/user numbers or pending
last-reported health, not an arbitrary age threshold. Readiness is a snapshot,
not physical-device hook verification.

Clipboard format `cyclone-cloak.readiness-doctor/3` exports only check enums,
fixed reason text, binding ordinals/counts, applicability and grammar-checked
release versions. Profile/app names, JSON, seeds, identifiers, raw errors, keys,
tokens and user content are never serialized. Existing sync, health-state
mapping, publication actions, runtime and connector behavior are unchanged.

## Guided binding workflow (multi-app selection in development after RC2)

`ui/BindingFlow` derives step validation from the latest synchronized Cyclone
snapshot, readable saved identities, the existing binding records, coherence
validation and local uniqueness index. `BindingFlowScreen` walks through profile,
app, compatibility profile, review and confirmation result. It is available from
Profiles â†’ Bind a device profile and from an identity's Bind to profile apps
action. App selection is a set with individual checkboxes and an All apps toggle.
Both entry paths show the identity picker before review; a preselected identity
is shown as a selected summary. Review lists all packages, and the confirmation
stamp includes the sorted selected set. The existing fleet bulk planner remains
unchanged and shares the view model's per-app store-update helper with this flow.

Unready profiles, missing users/apps, invalid identities, duplicate target
records and Main-managed mirrors block confirmation with corrective guidance.
Sharing an identity, duplicate distinguishing values and replacing a target's
current identity require explicit warning acknowledgement. A changed reviewed
snapshot/library invalidates confirmation until the summary is reviewed again.

The view model rechecks local data under its existing work mutex and writes every
selected app together through one `CloakBindingStore.update/upsertIn` operation. New profiles use the
existing catalog, forge, validated generation and atomic profile store. Cancelling
after generation retains the saved profile. Success requires the existing root
publication and reconciliation/health models to confirm readiness. Retry runs
existing publication/synchronization for the retained target without rewriting
its bindings; a changed or removed selected target returns to review. Connector, sync
engine, runtime module and storage formats are unchanged by this UI workflow.

## Connection and binding-run evidence

The process-owned `ConnectionLifecycle` continues to use one
`ReconnectCoordinator` and `CycloneBridge`. Its connection-only refresh opts out
of root reads and publication, including on resume and manual reconnect. This
keeps a previously approved connector from waiting on a separate Magisk prompt.
Explicit repair and binding operations retain their existing privileged-session
path. Connection status has one operator state: connected, retrying, waiting for
user action, or failed after bounded retries. The status dialog shows one next
step and hides Retry while an attempt is active. Cached registry rows remain
visible during retries; cached data never grants authorization.

Health → Runs uses the existing `binding-runs` journal and atomic `pending.json`.
An active row is visible from the start, with approval-wait guidance when the
session reports a wait. Checkpoints retain the selected targets and captured
evidence after local writes, publication, and synchronization. A process restart
converts an unfinished checkpoint into a needs-attention record with
`INTERRUPTED`; its end time is the recovery observation time, not a claimed
exact process-death time. Completed records remain append-only and bounded.

Each record includes start/end time, duration, targets, final state, first failure
code, publication phase markers, historical handshake facts, and reconciliation
counts when available. New fields are optional in the existing schema-version-1
envelope; earlier journal entries remain readable. Retry of an unchanged saved
target runs publication and synchronization without recreating bindings. An
unsaved bind returns to review with its selection retained; changed or ambiguous
records require review. A still-valid in-memory review acknowledgement is reused.
Retrying a saved run from history links the new attempt with `retry_of`.

Readiness requires publication and reconciliation evidence. The bridge observes
later publication attempts during sync, so a failed final publication cannot be
masked by an earlier successful one. Expired root/connector approval windows have
fixed retry guidance. Missing evidence never implies success.

Per-run and 24-hour ZIPs contain seven JSON files: `manifest.json`, `runs.json`,
`diagnostics.json`, `bindings-index.json`, `module-state.json`,
`connector-handshake.json`, and `reconciliation.json`. The last file contains
historical counts and fixed failure codes, never free-text issue values.
Diagnostics are limited to the requested time window; a still-active run can be
included through export time. Bindings and top-level module/connector snapshots
describe export time, while run observations describe the recorded attempt.
Module-state SHA-256 describes observed state, not the installed binary. Exports
exclude profile bodies, seeds, credentials, and raw shell/connector output.
No platform verdict or device-verification claim follows from a run record.

## Local profile backups

`forge/ProfileBackup` defines a profile-only portable envelope with a schema/app
version, creation time, profile count and optional label. The Identities screen
exports one profile or the readable library, and previews/explicitly confirms
single-profile, legacy fleet and backup imports. Coherence errors block the whole
batch; warnings and duplicate distinguishing values require acknowledgement.
ID/name collisions receive new local IDs/names in the preview rather than
replacing existing records. Binding, Cyclone and publication records are excluded.

Default export projects only compatibility settings and profile names. Seeds,
profile IDs and distinguishing identifiers are included only by explicit checkbox
selection. Arbitrary metadata, health, network hints and integrity/keybox data
are always omitted. Restoring a default backup generates fresh identity values.
These are profile-only backups; an advanced full-state backup is not implemented.

`JsonDirectory.writeBatch` stages a copy of the existing library plus every new
validated profile. A shared in-process lock excludes cooperating readers/writers
during the two directory renames. Failed commits restore the previous tree; a
failed rollback leaves `.profiles.previous` for recovery on the next store access.
The existing one-JSON-file-per-profile storage format is retained. Temporary
staging/backup folders are implementation artifacts, not reader schemas.

Export saves an atomic app-private backup and a new local Downloads document.
`LocalDocumentExport` uses MediaStore's pending-file visibility: complete bytes
are written and synced before publishing the document. Failed copies are
discarded; the already complete in-app backup remains available with a warning.
Downloads copies survive app-data removal, while in-app backups do not. No new
permissions or networking are used; `allowBackup=false` remains unchanged.

## Compatibility report workflow

The selected identity's local report card shows a 12-character report digest
prefix when provenance is available, source, checked time, explicit saved-profile
and bundled-artifact matches, and freshness. Missing metadata is shown as not
recorded; matching a bundled payload does not verify the installed module.
**Load compatibility report** validates and previews the existing report envelope
before an explicit save. Replay/newer-report acceptance rules remain in force.
**Review report details** opens the saved summary; individual check rows are only
available in the import preview because the existing store retains a summary.

**Mark needs rotation** requires confirmation and preserves report provenance.
**Clear after explicit local reset** is enabled only for Needs rotation and requires
acknowledgement that a reset or reseed already completed. It records Unknown and
clears the current evidence association without performing an identity reset.
Replay history is retained. Both actions recheck the reviewed record before writing;
a changed record requires another review. Errors remain visible on the selected
identity (and in an open import preview), with retry/cancel paths. Profile files,
connector behavior, binding and module publication are unchanged.


## Reality Runtime Bridge (formats 1 and 2)

`catalog/reality-runtime-fields.json` is the reviewed shared source for 97 schema
paths. The generator emits the pure Kotlin table, probe asset and strict JSON
contract schema. Python and Kotlin produce the same ordered capability contract
without copying profile values. Semantic validation requires exact reviewed-table
agreement; unknown future Reality sections are ignored without changing profiles.

The capture session adds an optional digest-only `reality_runtime` extension by
reusing the existing comparison evidence. No readers outside the contract are
selected. Nested inventory matches are not inferred from aggregate hashes.
RealityCoverage validates the extension and displays runtime readiness separately
from local coherence. Ten linked fields are conditionally applied only with exact base parity; see Phase 1 below.
See [format and limitations](DEVICE-REALITY.md#reality-runtime-bridge-formats-1-and-2)
and [authorized future work](REALITY-RUNTIME-BACKLOG.md).


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
