# Service surface isolation v2

This is a local configuration and evidence state machine, not Android enforcement
or a Google/platform verdict. It performs no account, package, permission,
connector, publication or runtime operation.

## Assignment and evidence

`ServiceSurfaceBinding` combines an existing validated `GoogleSurfaceRecord` with
an app package and the current app/bundled-module artifact digest. The assignment
contains the service expectations, account-slot metadata, active state, allowed
account package set and required/optional/forbidden surface/control package rules.
The key remains Cyclone profile ID plus app package; user/body/artifact changes
invalidate evidence rather than change binding identity.

`ServiceSurfaceEvidence` adds a supplied `GoogleSurfaceObservation`: timestamp,
source, package inventory/versions, account-slot association, backup posture and
private-boundary observations. Missing values mean not observed, never success.
The evaluator compares this against current assignments, current context and
current inventory. It does not infer account ownership from caller visibility.

## States and readiness gate

| State | Meaning |
| --- | --- |
| ISOLATED | Fresh, fully matching local evidence; no active shared slot |
| SHARED_CONFIRMED | Same evidence requirements, plus explicit consent for the exact active sharing group |
| CONFLICT | Unconfirmed slot reuse, contradictory package scope/rules or failed isolation observations |
| STALE | Evidence exceeds the context freshness window (24 hours by default) |
| UNKNOWN | No evidence, incomplete observations, inactive assignment or unreadable inventory/store |
| NEEDS_REVIEW | Requested review, changed assignment/binding, future evidence or incomplete account setup |

Configuration conflicts are checked even when observations are absent. The
assignment's `shared` flag is never consent. Existing inventory-bound confirmation
is read together with its inventory under the Google surface store lock.
Profile ID, canonical profile hash, Android user, app package and artifact must all
match. Source, types, inventory and record formats are strictly validated.

The existing readiness card shows per-app isolation states and reasons. The gate
checks every enabled app binding: conflict/stale/review requires NEEDS REVIEW,
unknown or absent scope prevents READY, and existing BLOCKED is preserved. Passing
isolation cannot turn UNKNOWN Play evidence into READY. The UI repeats this gate
so a supplied READY label cannot bypass it.

## Storage and review

`ServiceSurfaceStore` stores bounded format-1 binding sidecars in the existing
app-private `google-surface-isolation` preferences. Keys are
`binding:<SHA-256 of canonical {profile,package}>`; the existing `library` record
and its format are unchanged. Each sidecar contains:

- `format`, `assignment` (existing Google surface codec object);
- `package`, `artifact_hash`, `review_requested`;
- `observation`: `source`, `checked_at`, nullable `account_mode`, `account_slot`,
  `backup_sync`, `private_boundary`, plus `packages` and `versions` maps.

A `:review` boolean can preserve a review request before any evidence exists.
Writes use synchronous atomic preference commits; a failed/corrupt read hides
the record and refuses overwrite. Healthy records remain readable. There is no
automatic deletion, migration or profile rewrite. Freshness/binding failures set
a sticky review flag; saving new evidence clears it only with explicit review
confirmation. Corrupt records require restoration/review outside automatic import.

Public entry points are `ServiceSurfaceIsolation.evaluate(...)`, `gate(...)`, and
`ServiceSurfaceStore.read(...)`, `save(..., reviewConfirmed)`, `requestReview(...)`
and `evaluate(...)`. The pure evaluator accepts a `PlayReadinessContext` with
configurable freshness. Stored results are never trusted as verdicts: reads
re-evaluate against current context/inventory.

## Existing diagnostics import

An explicit successful report save records package observations for the enabled
app scope supplied by the current binding list, restricted to the selected saved
identity. Apps bound to another identity show a binding mismatch and cannot borrow
the selected identity's evidence. The review stamp includes the import scope;
changing it requires a new review. Parent import validation still binds
the exact saved profile-file hash and artifact/user. Only after that validation
is evidence projected onto the existing canonical assignment hash.

Account-slot ownership, backup policy and private-data isolation are not proven
by the current Play harness and remain absent. Its package inventory can therefore
produce UNKNOWN or conflicts, but cannot by itself produce ISOLATED or READY.
No values are collected or fabricated to fill these gaps. A future explicitly
authorized evidence provider may supply the missing observations through the same
local model. Account slots and package metadata contain no account names, tokens,
credentials or profile bodies. No device verification was performed in this run.

Readiness, assignment and isolation saves are separate commits. If a later save
fails, the UI reports the partial save explicitly; earlier evidence is retained
and the current readiness gate still applies.
