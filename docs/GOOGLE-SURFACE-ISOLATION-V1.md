# Google Surface Isolation v1

This is an additive local **configuration and state layer**, built on Play Surface
Contract v1. It records approved expectations and evaluates supplied observations.
It does not isolate Google services by modifying Android, collect accounts, enforce
package access, create accounts, or establish a platform verdict. There are no new
permissions, network calls, credential fields, hooks, or automatic setup actions.

## Records and storage

`GoogleSurfaceRecord` binds a Cyclone profile ID, nullable Android user ID and
compatibility-profile SHA-256 to an immutable Play Surface JSON snapshot, package
rules and an active flag. The hash uses the app's existing canonical JSON SHA-256
helper (`EngineMetadataStore.digest`); no engine metadata is written or read.
Callers must hash the currently selected saved profile using the same convention.
The Play Surface account contains the opaque slot, mode, display hint,
provisioning state, allowed packages, shared description and review timestamp.
Labels must be non-secret display text, not account names, passwords or tokens.

`GoogleSurfaceStore` uses MODE_PRIVATE preferences `google-surface-isolation`,
key `library`. A single synchronous atomic preference commit writes:

```json
{
  "format": 1,
  "records": [
    {
      "format": 1,
      "cyclone_profile_id": "Cyclone_0123456789abcdef",
      "android_user_id": 17,
      "profile_hash": "<canonical profile SHA-256>",
      "play_surface": "<Play Surface Contract v1 object, not a string>",
      "packages": [
        {"package": "com.google.android.gms", "role": "SURFACE", "requirement": "REQUIRED"},
        {"package": "org.example.control", "role": "CONTROL", "requirement": "OPTIONAL"}
      ],
      "active": true
    }
  ],
  "confirmations": ["<digest of explicitly reviewed active assignments>"]
}
```

The illustration uses placeholders, not importable fixture values. Each record
is limited to 192 KiB, 256 distinct package rules, and the existing 128 KiB Play
Surface limit. The library is limited to 512 distinct Cyclone profiles and 2 MiB.
Strict parsing rejects duplicate keys, unknown keys/versions/enums, wrong types,
invalid hashes, malformed Play Surface contracts and duplicate package/profile IDs.
Errors have fixed reason codes and never echo rejected values or unknown keys.

Missing storage is an empty library. Unreadable or corrupt storage is hidden from
`read`, but `snapshot.readable=false` distinguishes it from absence. Evaluation
returns UNKNOWN and writes/clears fail closed; bytes remain intact for recovery.
There is no automatic reset or migration of corrupt assignments.

Synchronization covers store instances in the current app process; Android's
private preferences isolate separate user copies. No multi-process writer is
introduced. Profile JSON, imports, engine metadata, posture records, bindings,
publication and connector stores remain untouched. Android backup is already
disabled in the app; no backup setting changes are made.

## Explicit shared assignment review

1. The caller constructs an approved record and calls `store.review(record)`.
2. The review returns the affected active profile IDs and a digest binding the
   proposal to the current inventory. Display those profiles before confirmation.
3. If the slot is reused, `save(record)` throws `ReviewRequired` without writing.
4. Only after explicit user approval call
   `save(record, reviewedDigest=review.digest, confirmShared=true)`.
5. Save rechecks the review under the store lock. A stale digest cannot approve a
   changed assignment. Confirmation is persisted for the entire exact active
   sharing group, including user IDs, profile hashes, labels and package rules.
6. New peers, changed records, removal and reactivation invalidate old consent.

An imported `account.shared=true` alone is not consent for this store. The separate
confirmation record avoids rewriting any saved compatibility profile. Inactive
assignments do not reserve slots. Explicit clear removes only this local sidecar
assignment, never accounts, profiles or bindings. No confirmation UI is added in
this state-layer run: callers must present the review before invoking confirmation.

## Package policy and observations

`SURFACE` identifies expected Google/Play/provider packages. `CONTROL` identifies
unrelated comparison packages; an unrelated package is never accepted as a Google
service. REQUIRED means enabled; OPTIONAL permits enabled, disabled or missing;
FORBIDDEN means missing (disabled is still present). Known Play Surface package
state and version expectations additionally apply. An unknown state or missing
declared version observation prevents success. Contradictory known requirements
need package review even before observations arrive.

Account `allowed_packages` may contain ordinary apps; it is distinct from the
Google service list. Each account-scoped package must have an explicit rule.
All named Play Services, Play Store and Web provider expectations must have a
SURFACE rule. Missing Web provider identity or unknown expectations remain unknown.
This is enforcement **metadata**, not an Android access-control implementation.

`GoogleSurfaceObservation` accepts only local descriptions: profile/user/hash,
source, checked time, observed opaque account mode/slot, backup/sync mode, package
states/versions and an explicit private-data-boundary result. No account contents,
credentials, process data or Google APIs are read by this layer. Observation
collection and consent UI belong to a future separately reviewed integration.
Timestamp is descriptive; automatic freshness policy is not implemented here.

## Evaluation

Use `store.evaluate(currentProfileId, currentAndroidUserId, currentProfileHash,
observation)`; it derives sharing consent from storage. The pure evaluator also
accepts an explicit inventory and confirmation fact for testing/trusted callers.
It emits sorted check rows with PASS/FAIL/UNKNOWN, stable `GS_*` reason codes and
fixed readable messages. Six checks cover account association, backup/sync,
package visibility, app-data boundary, Android-user boundary and profile hash.
Early binding/configuration failures produce an explicit blocking check.

| State | Meaning |
| --- | --- |
| READY | All six supplied local isolation checks pass for the current assignment |
| NEEDS_ACCOUNT | Local provisioning is incomplete, blocked or needs review |
| NEEDS_PACKAGE_REVIEW | Package scope, requirements or supplied observations disagree |
| SHARED_ACCOUNT_CONFIRMED | All local checks pass and the exact reused slot group has approval |
| CONFLICT | User/profile/hash/account/data-boundary/backup mismatch, or unconfirmed reuse |
| UNKNOWN | Missing configuration, user assignment, evidence, inactive record or unreadable inventory |

Missing evidence cannot become READY. Shared confirmation alone cannot become
SHARED_ACCOUNT_CONFIRMED. Hash or Android-user changes require configuration
review, and foreign evidence is never relabelled. `none` mode requires an explicit
observation of no associated slot; it is not inferred from missing evidence.
Play Protect, settings access and install-origin descriptions remain in the
contract but are not service verdicts or additional isolation checks.

This store can detect reuse only across its supplied local inventory. It does not
read other Android users' private stores or assert complete cross-user visibility.
Android sandbox behavior on a phone, account association collection and package
visibility collection remain **UNVERIFIED**. No platform verdict is claimed.

## Public entry points

- `GoogleSurfaceRecord.fromProfile(id, user, profile, packages)` returns null for
  older profiles without `play_surface`, with no mutation.
- `GoogleSurfaceCodec.validate / encode / parse` validate the separate format-1 record.
- `GoogleSurfaceStore.snapshot / read / review / save / clear / evaluate` persist
  and review local assignments; observations are never persisted automatically.
- `GoogleSurfaceIsolation.evaluate` is a pure state calculation; it neither
  changes configuration nor performs observation collection.

Focused tests: `:app:testDebugUnitTest --tests 'dev.cyclone.cloak.play.*'`.
Full validation commands and results are recorded in the sprint handoff.
