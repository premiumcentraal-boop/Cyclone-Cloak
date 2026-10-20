# Play Integrity verdict bridge v1

This bridge imports authenticated **backend assertions about decoded Google
responses**. It does not decode encrypted tokens locally, call Google, create
verdicts or certify a device. Local Play readiness remains a separate result.
No module, binding store, publisher, connector protocol or permissions change.

## Backend boundary

Google's documented [standard request flow](https://developer.android.com/google/play/integrity/standard#decrypt-verify)
requires a server to call
`https://playintegrity.googleapis.com/v1/PACKAGE_NAME:decodeIntegrityToken`
with service-account authorization. The server validates the actual decoded
`requestDetails`, including package identity, timestamp and `requestHash`, before
using returned [verdict fields](https://developer.android.com/google/play/integrity/verdicts).
An ordinary unsigned JSON decode response is not an authenticated offline file.

`IntegrityBackendAdapter.authenticatedVerdict(request): String?` remains the
production-side adapter interface. The separate
[Integrity verification exchange v1](INTEGRITY-VERIFICATION-EXCHANGE-V1.md) now
provides opt-in private ciphertext retention, explicit document export, and an
owner-operated Python backend. Default collection still discards ciphertext.
Credentials, project configuration and private signing keys are never included;
real decoding requires the owner's separately configured backend and trust pin.

The trusted backend must receive the original token through that separately
implemented secure path, call Google's decode endpoint, validate the expected
requesting package and request binding, then sign only the
safe allowlisted fields below. Do not derive a result from local device facts,
copy historical results, accept client-supplied verdict labels, or invent fields
Google did not evaluate. Device/account claims apply to the requesting app and
measured request, not every app or profile on the phone.

## Runtime trust configuration

`IntegrityTrust.key(provider, keyId)` resolves public verification keys.
The production default reads app-private `files/play-integrity-trust-v1.json`,
at most 16 KiB, format 1, with 1–8 entries in `keys`. Each entry has exactly:
`provider`, `key_id`, `public_key` (base64 DER X.509 RSA public key). Duplicate
provider/key pairs and malformed configuration fail closed. RSA must be at least
2048 bits. No key is bundled and no provider is trusted by default.

Provision this file independently through an approved local runtime configurator
after establishing the backend's identity and decode policy. This sprint adds
no trust-setup UI and performs no provisioning. Importing a verdict cannot add
or replace trust pins. A key supplied in the verdict itself is rejected.
Only public keys belong in this file; backend credentials and signing private
keys stay on the server. Revoking/removing a pin causes stored verdicts to fail
authentication on the next read. Backend signatures authenticate the backend's
assertion, not an independently verifiable Google signature or certification.

## Strict format 1

Schema: `schema/integrity-verdict.schema.json`.
Envelope fields, all required: `format: 1`, `algorithm: "RS256"`, `provider`,
`key_id`, `payload`, `signature`. Payload/signature use canonical padded base64.
RS256 means SHA256withRSA / PKCS#1 v1.5 over the **exact decoded UTF-8 payload
bytes**, not a reserialized JSON object. Total input is capped at 32 KiB.

Decoded payload fields, all required:

| Field | Meaning |
| --- | --- |
| `format` | 1 |
| `provider` | Must equal the pinned envelope provider |
| `request_id` | Exact independently saved diagnostics request ID |
| `request_hash` | Exact request hash returned by Google's decoded requestDetails |
| `profile_id`, `profile_hash` | Cyclone profile ID and exact saved-profile byte SHA-256 |
| `artifact_hash` | Current companion APK + bundled-module artifact-set SHA-256 |
| `android_user_id` | Exact selected Android user; no default |
| `timestamp` | Google's token request timestamp, normalized to UTC seconds |
| `decoded_at` | Backend decode time, UTC seconds |
| `app_recognition` | Actual `appRecognitionVerdict`: PLAY_RECOGNIZED, UNRECOGNIZED_VERSION, UNEVALUATED |
| `app_licensing` | Actual `appLicensingVerdict`: LICENSED, UNLICENSED, UNEVALUATED |
| `device_recognition` | Actual unique deviceRecognitionVerdict labels; empty is valid returned evidence |

Allowed device labels: MEETS_BASIC_INTEGRITY, MEETS_DEVICE_INTEGRITY,
MEETS_STRONG_INTEGRITY, MEETS_VIRTUAL_INTEGRITY. Unknown future fields/labels
require a deliberate contract update. Tokens, credentials, arbitrary logs and
unknown keys are rejected, even inside a signed payload. Duplicate JSON keys,
invalid UTF-8/types/calendar dates, missing fields and unsupported versions fail.
Schema validity alone does not establish authentication or current binding.

The existing request hash is unchanged: lowercase SHA-256 of UTF-8
`request_id + "\n" + profile_id + "\n" + profile_hash + "\n" + android_user_id +
"\n" + artifact_hash`, with no trailing newline. The backend must reconstruct
this from the registered request, never merely trust an imported envelope's
claimed hash. Profile/artifact labels are client request bindings, not claims
that Google attested a selected synthetic profile or independently loaded module.

## Import, storage and evaluation

First explicitly save the original diagnostics report through existing review.
Its validated token row supplies a separate private request receipt, including
request ID, recomputed request hash, bindings, receipt time and token digest.
Old reports stay importable. Older stored projections without request IDs can
show token receipt but must be reimported before a verdict can be bound.

Then use **Import authenticated verdict** on the existing profile readiness card.
The explicit import authenticates, validates all bindings and freshness again,
then saves safe evidence atomically. Rejection leaves prior accepted verdict
bytes intact and displays a fixed reason code. No bindings/accounts change.
No new screen is added. The card shows separate Integrity state, reason,
provider, decode time and the returned app/licensing/device labels.

Store: app-private preferences `play-integrity-bridge-v1`, keyed by profile ID.
Each format-1 entry contains `request`, nullable `envelope` and nullable
`evidence_digest` (validated against the exact signed payload); the request has
`request_id`, `profile_id`, `profile_hash`, `artifact_hash`, `android_user_id`,
nullable `received_at` and nullable `token_digest`. The envelope contains only
the authenticated safe payload/signature. Read re-verifies the signature and
binding every time. `IntegrityVerdict.evidenceDigest` is SHA-256 of exact signed
payload bytes. Existing profile, readiness and assignment formats are unchanged.

Saving a newer request invalidates an earlier request's verdict. Current local
report receipt must match the sidecar receipt. If saving the receipt fails after
existing local readiness was saved, the UI reports that partial save explicitly
and directs reimport; there is no combined multi-store transaction.

Freshness uses the existing configurable context window (default 24 hours), for
both receipt and verdict. Request/decode/receipt times cannot be future-dated;
token issue time must not follow receipt and decode cannot precede receipt.
Malformed/corrupt storage is hidden behind NEEDS_REVIEW with IV_STORAGE.

| Integrity state | Meaning / effect on existing local readiness |
| --- | --- |
| NO_REQUEST | No recorded request; no local-readiness promotion |
| TOKEN_RECEIVED | Receipt only; no decoded claim or promotion |
| DECODED_AVAILABLE | A required app/licensing field is UNEVALUATED; readiness stays/becomes UNKNOWN unless already BLOCKED |
| PASS | Recognized app, licensed, MEETS_DEVICE_INTEGRITY; retain existing local readiness only |
| DEGRADED | Recognized/licensed but basic-only policy result; downgrade otherwise READY |
| FAIL | Explicit nonrecognized/unlicensed or absent required device label; BLOCKED when local evidence is complete |
| NEEDS_REVIEW | Authentication, binding, stale/future or storage problem; UNKNOWN + review |

These pass/degraded/fail labels are a documented **local policy over actual
returned fields**, not a single Google overall verdict. Missing local evidence
always remains UNKNOWN, including alongside an authenticated passing verdict.
Optional strong/virtual labels do not silently substitute for the declared
MEETS_DEVICE_INTEGRITY policy. IV_* reason codes are fixed and do not echo input.

## Later physical verification

No device action is part of this sprint. A later authorized test needs an
independently configured trusted backend, public pin and secure token handoff.
It can verify a real receipt → backend decode → signed import → separate UI
state, then rejection of modified signature/profile/user/artifact/request hash,
stale data and missing local evidence. Until then all new verdict tests are
synthetic, with ephemeral test-only signing keys; no physical verdict is claimed.
