# Play Readiness Evaluation v1

Local evidence evaluation and private storage only. READY means the six required
local observations are positive, current and bound to the selected profile, user
and artifact. It is **not a Play Integrity, Google, certification or platform
verdict**. This layer performs no collection, account operations, Integrity API
requests, networking, runtime changes or destructive reset.

## Report format 1

`PlayReadinessReport.parse` accepts strict JSON, at most 64 KiB UTF-8 and depth 32.
All fields below are required except `integrity_response`. Unknown keys, duplicate
keys, wrong types, trailing input, unsupported versions, non-finite numbers,
invalid calendar dates and unknown vocabularies are rejected. Errors are fixed
codes/messages and do not echo input. Explicit `unknown` values are valid missing
knowledge; missing required fields are malformed/incomplete structure.

| Field | Format |
| --- | --- |
| `format` | integer 1 |
| `profile_id` | bounded opaque local ID, 1–96 ASCII letters/digits/`._:-`, starting alphanumeric |
| `profile_hash` | lowercase SHA-256 of the selected saved profile using the caller's agreed hash convention |
| `android_user_id` | integer 0–21474; never silently defaults to owner |
| `artifact_hash` | lowercase SHA-256 identifying the measured app/module artifact set |
| `timestamp` | valid UTC `YYYY-MM-DDTHH:MM:SSZ` |
| `play_services` | package object, package name exactly `com.google.android.gms` |
| `play_store` | package object, package name exactly `com.android.vending` |
| `web_provider` | package object, declared provider package; null name allowed only if not enabled |
| `play_protect` | enabled / disabled / unavailable / blocked / permission_denied / unknown |
| `google_account` | associated / none / blocked / permission_denied / unknown; no account names or contents |
| `google_settings_access` | accessible / blocked / unavailable / permission_denied / unknown |
| `integrity_api` | available / unavailable / blocked / permission_denied / unknown |
| `warnings` | unique list of PERMISSION_LIMITATION / PACKAGE_LIMITATION / OPTIONAL_OBSERVATION / USER_REPORTED_WARNING |
| `failures` | unique list of PACKAGE_BLOCKED / ACCOUNT_BLOCKED / SETTINGS_BLOCKED / LOCAL_CHECK_FAILED |
| `integrity_response` | optional object: `response_digest`, `received_at`, `provider_id` only |

Package objects require `package_name` and `state`. States are enabled / disabled /
missing / blocked / permission_denied / unknown. Optional `version_name` is bounded
printable display metadata. Version strings are recorded, not interpreted as
proof of Google compatibility. The current evaluator uses package state, not
version-policy comparisons.

The context must supply the same profile-hash convention and measured artifact
digest as the report. The evaluator does not inspect installed binaries. For an
app/module pair, a collector should hash a stable manifest containing both exact
artifact digests, rather than use whichever binary happens to be available. No
artifact digest or profile hash is inferred, substituted or normalized by this
layer. Android user is bound separately, in addition to the shared contract fields.

Integrity response metadata may describe only a real response supplied by the
caller: SHA-256 of those response bytes, UTC receive time no later than report
time, and bounded provider ID. API state must be `available`. Raw tokens,
credentials, signed responses and verdict fields are rejected. The parser cannot
authenticate this metadata or prove its origin: it remains supplied evidence,
never a generated or verified platform verdict.

## Decision order

1. Missing report: UNKNOWN. Malformed/incomplete report: UNKNOWN + NEEDS_REVIEW.
2. Profile ID/hash, Android user or artifact mismatch: UNKNOWN + NEEDS_REVIEW.
   The report is rejected for saving; its original bindings remain in the result.
3. Future timestamp or age greater than the configured freshness window: UNKNOWN
   + NEEDS_REVIEW. Default window is 24 hours, configurable from greater than zero
   through 365 days. The exact freshness boundary is inclusive.
4. User-requested review: UNKNOWN + NEEDS_REVIEW until explicit review of current
   evidence. No profile reset/reseed or account action occurs.
5. Any of the six required observations unknown: UNKNOWN, even if a different row
   reports failure. Known failures/limitations remain in the findings.
6. Required package disabled/missing/blocked, account/settings/Play Protect blocked,
   or an explicit reported failure: BLOCKED.
7. Permission denial, disabled/unavailable Play Protect, unavailable settings,
   no associated account, or an explicit reported warning: DEGRADED. Optional
   Integrity API block/denial also remains an explicit limitation.
8. Otherwise: READY for the required local observations only.

The required areas are Play Services, Play Store, Web provider, Play Protect,
account association and Google settings access. No account association is a
limitation in this baseline policy; it does not initiate account creation.

Integrity API availability and optional response metadata are **observations**,
never PASS checks. Unknown/unavailable optional Integrity evidence does not turn
a clean local run into failure, and available/returned metadata cannot promote
unknown required evidence. Findings are sorted deterministically by field/code.

## Providers and shared contract

`PlayReadinessProvider.evaluate(context, report?, requestReview)` returns
`PlayReadinessResult(state, review, findings, contractRecordJson, accepted,
optionalObservationFields)`.

- `DefaultPlayReadinessProvider` always returns UNKNOWN and ignores supplied data.
- `LocalPlayReadinessProvider` evaluates only supplied reports; it performs no IO.
- Future providers implement the same interface; they must not fabricate evidence.

`PlayReadinessContext` supplies current profile ID/hash, Android user, artifact
digest, evaluation clock and freshness window. A `PlayReadinessFinding` has a
fixed field, stable `PR_*` code and readable message. `accepted` means a report
is structurally valid, current and bound; it does not mean READY. Explicit
unknown observations can be stored as UNKNOWN.

The generated format-1 readiness record passes the existing
`PlaySurfaceContract.parseReadiness` / `play-readiness.schema.json`. It preserves
the exact input report's SHA-256, timestamp and original profile/artifact bindings.
Detailed `PR_*` findings map to existing `PS_*` reason categories without changing
the shared profile schema, Python parser or generated Kotlin rules. The four
readiness states are separate from CURRENT / NEEDS_REVIEW review policy.

## Private persistence

`PlayReadinessStore` uses MODE_PRIVATE preferences `play-readiness-evaluation`,
one key per Cyclone profile ID. Each atomically committed envelope contains:

```text
format: 1
profile_id: selected Cyclone profile ID
report: exact original strict JSON string, or null
readiness: last evaluated shared-contract record, or null before evidence exists
review_requested: boolean
```

Snapshots are validated but never trusted to promote evidence: `read(context)`
always re-evaluates the original report against current hashes/user/time. A stale
or changed binding sets a persistent review flag, so reverting the clock/profile
does not silently restore READY. `requestReview(id)` also sets that flag and
downgrades a saved snapshot to UNKNOWN. To resolve it, explicitly supply a fresh
matching report via `save(context, report, reviewConfirmed=true)`.

`save` rejects malformed, stale, future or foreign reports with `RejectedReport`
and the UNKNOWN result; older evidence is not overwritten. `read` hides corrupt
envelopes as UNKNOWN + NEEDS_REVIEW without erasing them; normal save cannot
silently overwrite corrupt storage. Explicit `clear(id)` removes only this
readiness sidecar. Private records are bounded to 128 KiB each. Store instances
are serialized in the existing single-process application model.

No report is automatically collected or saved by startup, health, binding or
publication paths. No UI is added in this local evaluation/storage sprint. Existing
profile files, schemas 0.2/0.3/0.4, Google isolation assignments, posture/engine
metadata, connectors, bindings and module state remain unchanged. Physical
collection and platform-response provenance authentication remain UNVERIFIED.
