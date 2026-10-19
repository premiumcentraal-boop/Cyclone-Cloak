# Play/Google Readiness Integration v1

Development branch `codex/play-readiness-integration-v1` consolidates the existing
local isolation/evaluation layers and the separate diagnostics harness. The
published foundation remains alpha.26. Physical evidence is pending.

## User flow

Each Cyclone profile in **Profiles** has a **Local Play/Google readiness** card.
Select a saved device profile if current bindings do not identify exactly one.
This is an evidence-review context; it never changes a binding. Expand binding
details for the exact profile-file hash and current artifact-set digest.

Collect evidence in the separate Play evidence app, using that profile ID, file,
Android user and artifact digest. Collection requires a user tap; the optional
public Integrity request needs a separate confirmation. The importer never
initiates collection or an Integrity request.

Import the original full `report.json`, not the ZIP or readiness-only projection.
Review, then explicitly save local evidence. Unknown required observations remain
UNKNOWN. The card shows READY, DEGRADED, BLOCKED, UNKNOWN or NEEDS REVIEW, source,
timestamp, package/account states and reasons. Returning to the app reevaluates
freshness using the existing evaluator's default 24-hour window.

## Validation and binding

`PlayEvidenceImport.projection(text, context)` validates the complete format-1
envelope with the same pure `EvidenceReport` as diagnostics. Unknown keys,
duplicate/missing rows, inconsistent projections, malformed/oversized input,
foreign profile/user/artifact bindings, stale/future reports and failed capture
binding checks are rejected. Accepted does not mean READY. Saving rechecks the
current selected profile bytes, registry context, artifact, time and assignment
inventory; a changed review stamp requires another review.

`PlayEvidenceRepository.target(profileId, user, identityId)` reads existing profile
bytes without profile writers or migration. No Android-user default is used.
Existing 0.2/0.3/0.4 profile headers remain accepted. Report hashes are SHA-256 of
the **exact saved UTF-8 file bytes**. Google assignment sidecars retain their
existing canonical JSON digest convention; the two are never interchanged.

Artifact binding is the SHA-256 of this UTF-8 text, including its final newline:

```text
play-artifacts/1
app_sha256=<lowercase APK SHA256>
module_zip_sha256=<lowercase bundled ZIP SHA256>
```

The app measures its APK and exact bundled module ZIP. An unreadable artifact
disables import. This does not independently verify the installed root module;
the collector's artifact digest is operator-supplied, not attestation. Its runner
records the installed diagnostics APK hash separately.

## Stores, sharing and privacy

Only existing app-private stores are written: `play-readiness-evaluation` holds
the validated projection and contract-compatible readiness record, while
`google-surface-isolation` holds approved assignment metadata. No profile body,
binding, connector or publisher record changes. No destructive migration runs.

Optional `play_surface` metadata supplies account slot, hint and package scope.
Older profiles without it still import; no account assignment is invented.
Reviewed package rules remain intact for the same profile hash and Android user.
New derived rules are optional expectations, not package enforcement.

Sharing a slot across active local assignments requires a second confirmation
listing the affected Cyclone profiles. Consent is tied to the current inventory;
imported `shared` metadata is not consent. Unresolved conflicts show NEEDS REVIEW.
This checks local assignment records only, not global accounts or other users'
private stores. Caller-visible account presence cannot prove slot ownership or
cross-user isolation. No account write, credential/token capture or authentication
is added.

The existing stores each commit atomically, but there is no cross-store
transaction. Approved assignment metadata can survive a subsequent readiness-save
failure; older readiness evidence is preserved on failed replacement. User errors
must describe partial saves, without deleting previously approved metadata.

## Limits

Synthetic tests cover envelope validation, context/freshness rejection, unchanged
real binding-store bytes, old profiles, sharing consent, artifact hashing and
Compose states/confirmation/error paths. They are not physical evidence.
Play Protect, actual Google settings access and decoded Integrity verdicts remain
unmeasured by the harness. Package visibility/token receipt is not a platform
verdict or permanent proof.

No attached ADB device was available. Installation, user-initiated capture and
on-device import remain **PENDING**. No permissions, module/runtime, connector,
publication, binding layout or release metadata changed. Release is blocked until
physical evidence and all required gates exist and pass.
