# Report: Cyclone Cloak wired to Cyclone's profiles (0.9.0-alpha.1)

Answer to the Cyclone handoff `docs/handoff/CLOAK_CONNECTOR_COMPAT.md` (Cyclone 5.0.0-alpha.122.dev1, contract
`cyclone.connector/1`, minor 3). Only Cloak was changed; nothing in Cyclone was edited.

## What was built

| Handoff | What Cloak does now | Where |
|---|---|---|
| §2.1 manifest | `contract="cyclone.connector/1.3"`, id `cyclone-cloak`, scopes `profiles.read profiles.apps.read profiles.config events.profiles device.root.read profiles.open.request profiles.startup selector.contribute` (dropped the unused `profiles.ext`); marker service and wake receiver as before | `res/xml/cyclone_connector.xml`, `AndroidManifest.xml` |
| §0 kit | Client library synced byte for byte to alpha.122 (`rootStatus`, `requestOpenProfile`) | `com/cyclone/connector/client/CycloneConnector.kt` |
| §2.2 approval | `hello` on start, on every wake, and again after `profile.switched`; not approved → the one approve line, nothing else | `CycloneSyncEngine` |
| §3 bindings | Reconcile: read each tuple, write only when different, report health, clear what Cloak no longer binds (off, removed, moved); first run sweeps stale Cloak tuples. Main writes every profile, a profile's Cloak only its own. Never Main; never a profile without an integer user number | `ReconcilePlanner`, `BindingReconciler` |
| §3.5 | Bindings keyed by profile id + package; user number follows the profile and the module state is republished; `profile.removed` / absent from Main's list → forgotten; trashed → kept | `CloakBindingStore`, engine |
| §4.1 health | Local states mapped to `ready` / `unknown` / `degraded` / `failed` | `CloakHealth` |
| §4.2 Cloak's pill | Rooted ✓ / Rooted ! (reason) / Rooted (unchecked) / Native; nothing for setting up or trashed | `CloakPills`, `CloakUi` |
| §5 open request | "Open in Cyclone" per profile, "Open This phone in Cyclone" from a profile; gated on minor ≥ 3 and the scope; every error code mapped; never retried | `OpenRequests` |
| §6.2 events | Pulled on wake and on start, every page, de-duplicated by `seq`; a journal that started over counts as a reset; unknown types ignored | `CycloneEvents` |
| §6.3 errors | Branches on `code` only. RATE_LIMITED / INTERNAL retried once with backoff; NOT_APPROVED, SCOPE_NOT_GRANTED, UNKNOWN_METHOD stop the run; NO_SUCH_PROFILE / BAD_REQUEST reported per binding. Calls paced at ≤ 14/s | `BindingReconciler` |

### A Cloak-side bug the profiles exposed (fixed)

The module's state tree `/data/adb/cyclone_cloak/state-v1` is one tree for the whole phone, and every publish replaced
it with the publishing install's bindings. Since Cyclone installs Cloak into every profile (alpha.120), opening Cloak or
Root Doctor inside profile B would have wiped every binding Cloak in Main had made for the other profiles. Publishing is
now per install ("shares", docs/STATE_LAYOUT.md), assembled with Main's last; the first publish after the update keeps
the old tree as Main's share.

### How the two Cloaks agree (handoff §3.4, "Cloak's business")

Cloak in Main is the authority and publishes its bindings for every profile into the root state, with each entry's
display summary. Cloak inside profile C reads that published index (read-only, through Root Doctor's su checks; in the
background only after this install has passed Root Doctor), mirrors the entries for its own profile and user as
read-only bindings, and writes them to C's Cyclone. C's Cloak may bind C's own apps too; for an app both bound, Main
wins.

## What passed (no phone)

- `gradle :app:testDebugUnitTest`: **all 91 tests pass** (0 skipped), among them:
  - `CycloneVectorsTest`: Cyclone's own `vectors.json` from alpha.122 (copied into `src/test/resources`). hello says
    minor 3; `root.status.v1` and `profiles.open.request.v1` without their scopes give `SCOPE_NOT_GRANTED`; profiles
    without `profiles.apps.read` give nothing to bind; the events vector; a whole sync against the vectors.
  - `CycloneEnvelopeTest`: what Cloak writes, read by a port of Cyclone's strict reader: a valid version 1, an unknown
    `identityVersion` (bound, no summary), a mismatched tuple and a string `androidUserId` (neither counts), `owner`
    refused; no identifier ever in the summary; field limits.
  - `CycloneSyncTest`: gating on `minor` and `granted`; reconciling after `profile.restored` with a new user number;
    de-duplication by `seq`; the §6.3 error table; no retry on `BUSY`; health to the pill; clears on unbind; the
    approval arriving with a switch; Cloak in a profile mirroring Main.
  - `CloakPublishMergeTest`: runs the real merge-publish shell under `/bin/sh` (dash) against a temporary root.
- `gradle :app:assembleDebug` builds; `aapt2` shows the packaged connector XML and manifest as above.
- Forge tests: 43 passed.

## UNVERIFIED

**Nothing here has been run on a phone.** Rows 5.1–5.9 of Cyclone's `docs/PROFILES_DEVICE_MATRIX.md` (handoff §9.2) are
all unverified:

1. Approval travels Main → B: unverified.
2. A revoke in B stays revoked: unverified.
3. Health reaches the pill (Rooted, then Rooted · check): unverified.
4. Bindings survive a change of B's apps in Cyclone: unverified.
5. `root.status.v1` agrees with the "has" lines: unverified.
6. Open C from B: Not now changes nothing, Open switches, both Cloaks hear `profile.switched`: unverified.
7. Locked phone: only a notification: unverified.
8. `RATE_LIMITED` within 10 s, `ALREADY_OPEN` for the profile in front: unverified (mapped and unit-tested only).
9. `config.set.v1` for `owner` refused: unverified on the phone (Cloak never sends it).
10. Cyclone's debug file free of Cloak's `value` fields: unverified.

Also unverified on the phone: the merge script under Android's `mksh`/toybox (tested under dash only), the legacy
migration of a real 0.8.0 tree, the mirror read from a secondary user's su, and the module (unchanged) reading the
assembled index. The module build (`:module:packageModule`, NDK) was not run in this environment.

## Contract feedback (for the Cyclone agent)

1. **Main's bindings in a profile's Cyclone.** Bindings live only in the Cyclone of the Cloak that wrote them, so Cloak
   in C needs root to learn what Main bound for C (it reads its own published state). A Cyclone-side carry would remove
   that root dependency: on a switch into C, copy the `cyclone-cloak` tuples Main's Cyclone holds *for C* into C's
   Cyclone (read-only for Cloak in C), the same way the approval is carried.
2. **"Which profile am I in?"** Cloak infers it from `uid / 100000` against `profiles[].androidUserId`. A `self` field in
   `hello` (`"owner"` or the caller's own profile id) would make that explicit and survive a stale registry.
3. **A reason with the state.** `config.status.v1` carries only the state, so Cyclone's pill can say "Rooted · check" but
   not why. An optional `reason` (≤ 60 characters, plain text, secrets refused) would let Cyclone show "Zygisk is off"
   or "Restart to finish the module update".
4. **List this install's tuples.** Finding stale bindings Cyclone still holds (after Cloak's data was cleared) costs one
   `config.get.v1` per app per profile. A `config.list.v1` returning this install's tuples (no values) would make it one
   call.
5. **A declined open request.** "Not now" sends nothing, so Cloak can't tell a declined request from one still showing.
   An event such as `profile.open.declined` (ids only) would let Cloak stop showing "asked".
6. No app installed in a profile was refused by `config.set.v1` in testing, but that can only be seen on a phone; Cloak
   reports such a binding as "app not in this profile's list in Cyclone" instead of writing it.
