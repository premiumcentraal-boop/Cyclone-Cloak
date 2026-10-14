# Cyclone Cloak Architecture

Current prerelease: [v0.11.0-alpha.9](https://github.com/premiumcentraal-boop/Cyclone-Cloak/releases/tag/v0.11.0-alpha.9), app/module code 33 (bounded local diagnostics and Android publication locking repair).
Normal-app publication and preservation of bindings across two Cyclone profiles
passed on Pixel 8; an independent profile-side publisher is not device-verified.
The existing main implementation adds the native property callback hook and
probe row; device proof remains pending. Earlier evidence does not verify this
new row or the new artifact. No platform verdict is claimed.
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

The profile contract is `schema/cloak-profile.schema.json`, `schema_version: "0.2"`.
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
