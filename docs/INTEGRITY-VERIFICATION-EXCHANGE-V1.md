# Integrity verification exchange v1

This is an explicit owner-operated exchange for the separate
`dev.cyclone.cloak.playevidence` diagnostics APK. The backend assertion describes
that package's measured request, not an unrelated app, certification, or permanent
trust. Its RS256 signature authenticates the configured backend assertion; it is
not an offline Google signature.

## Export on the phone (later owner test)

1. Select the saved compatibility profile JSON, profile ID, Android user, and
   current app/module artifact digest in the existing Play evidence activity.
2. Supply the owner's configured Google Cloud project number. Tap the existing
   explicit Integrity request action and confirm. The separate retention prompt
   offers **Do not retain** or **Request and retain**. There is no launch-time,
   background, or automatic forwarding path. Default collection does not retain.
3. With retention selected, one ciphertext bundle is stored atomically in the
   diagnostics app's private `files/integrity-verification.json`. Backup is
   disabled by the existing manifest. A superseded activity worker cannot persist
   its bundle. Ordinary reports retain receipt hashes only.
4. Tap **Export verification bundle**, confirm the sensitivity warning, and pick
   a local file destination. The entire bundle is validated again, including the
   current profile/request/user/artifact binding. Export is allowed only within
   24 hours of receipt. A new collection or successful export deletes the retained
   private copy. Expiration blocks export; no background job deletes expired files.
   Canceling export leaves the private copy available. A failed document-provider
   write is reported and may leave a partial file at the explicitly chosen destination.
5. Transfer this sensitive file privately to the owner's PC. Do not commit it,
   put it in ordinary diagnostics bundles, or attach it to release evidence.
   The document picker may offer remote providers: choose local storage for the
   local workflow. The app does not upload the file itself.

No token is retained without the dedicated affirmative choice. Canceling the
retention dialog does not send a request. The separately saved receipt report must
also be imported into the production app before importing the matching verdict.

## Bundle format

`schema/integrity-verification-bundle.schema.json` describes the transport. To
reconcile the minimal payload with versioning and the declared request-hash check,
the wrapper has exactly `format: 1`, `request_hash`, and `bundle`. The nested bundle
has only the eight permitted fields: `request_id`, `profile_id`, `profile_hash`,
`artifact_hash`, `android_user_id`, `received_at`, `token_digest`, and `token`.
It contains no body, account names, credentials, verdict labels, or logs.

`request_hash` is the existing lowercase SHA-256 of UTF-8 fields, separated by
newlines without a trailing newline, in this order:
`request_id`, `profile_id`, `profile_hash`, `android_user_id`, `artifact_hash`.
`token_digest` is lowercase SHA-256 of the exact UTF-8 ciphertext. Digests detect
inconsistency; they do not authenticate a bundle. Authentication comes from the
Google decode path and the separately pinned backend signature. Both parsers reject
extra/duplicate fields, malformed types, incomplete/tampered bundles, stale/future
receipts, and oversized input (64 KiB). The current profile and user must match
before the diagnostics app exports.

## Owner backend setup and command

Use an owner-controlled backend with the Google Play Integrity API enabled and
the appropriate linked Play/Cloud configuration and authorized service identity.
Configure standard [Application Default Credentials](https://google-auth.readthedocs.io/en/latest/reference/google.auth.html),
for example `GOOGLE_APPLICATION_CREDENTIALS` pointing to a private file outside the
repository. The backend requests scope `https://www.googleapis.com/auth/playintegrity`.
No project number, credentials, or private signing key is bundled with the app.
The diagnostics APK must meet Google's actual distribution/project prerequisites;
a sideloaded debug APK may receive unavailable/unevaluated or unrecognized results.
Nothing here bypasses those requirements.

Provide a private RSA PKCS#8/PEM key of 2048–8192 bits outside the repository, a
provider ID, and a key ID. For encrypted PEM keys, set
`CYCLONE_SIGNING_KEY_PASSWORD` locally; do not pass the password in command text.
These are backend keys, not Android signing keys. Runtime dependencies are listed
in `tools/integrity-backend-requirements.txt`.

```powershell
uv run --with 'cryptography>=44' --with 'google-auth[requests]>=2.38' -- python -X utf8 tools/integrity_backend.py `
  --bundle C:/private/verification.json --output C:/private/verdict.json `
  --provider owner.backend --key-id owner-key-1 `
  --signing-key C:/private/backend-signing.pem --confirm-send
```

This explicit command is the only new network path. It uses Google's documented
[`decodeIntegrityToken` backend endpoint](https://developer.android.com/google/play/integrity/standard),
fixed to the diagnostics package. It checks the decoded request package and hash,
timestamp sanity, actual required app/licensing/device verdict fields, and optional
app package name. Missing required verdict fields fail closed; no defaults or
client-supplied verdicts fill them in. It never accepts a locally supplied decoded
JSON result as authenticated input. No automatic decode retries or redirects are
allowed; replayed decoding can clear verdicts. Errors print a fixed safe message,
never token/HTTP response/credential details. Resolve owner configuration errors
privately and make a fresh request when necessary.

Only the existing `IntegrityVerdict` safe allowlist is signed as canonical compact
sorted JSON using RS256. The final envelope is atomically created at a new output
path; existing evidence is never overwritten. Only synthetic responses and ephemeral
keys are used by repository tests. Delete the sensitive exported token file locally
when no longer needed; the tool does not delete owner input.

## Import and trust

Provision the matching **public** provider/key pin independently into the production
app's private `files/play-integrity-trust-v1.json` using the existing owner setup
described in [the verdict bridge guide](PLAY-INTEGRITY-VERDICT-BRIDGE-V1.md). No
provider is trusted by default. Never put the backend private key in the phone or
repository. Revocation/removal of a public pin makes stored verdicts fail closed.

Import the matching diagnostics receipt report first, then use the existing
**Import authenticated verdict** action for the signed envelope. The importer checks
signature, freshness, request ID/hash, selected profile ID/hash, Android user, and
artifact digest. Missing/mismatched local evidence stays UNKNOWN or NEEDS REVIEW;
a passing decoded verdict cannot silently promote missing evidence to READY.

## Owner operations checklist (before the later phone test)

All three operational modes are local-only. They do not resolve ADC, contact
Google, provision trust, or change Android state. They cannot be combined with
`--confirm-send`. No real credentials, bundles or verdicts are repository fixtures.

1. **Preflight** with the owner's external signing key and non-secret provider/key
   IDs. Encrypted PEM uses the existing `CYCLONE_SIGNING_KEY_PASSWORD` environment
   variable, set privately; never place its value in a command or log.

   ```powershell
   uv run --with 'cryptography>=44' --with 'google-auth[requests]>=2.38' -- python tools/integrity_backend.py --doctor --provider owner.backend --key-id owner-key-1 --signing-key C:/private/backend-signing.pem --output-dir C:/private
   ```

   Doctor checks dependency imports, RSA size (2048–8192 bits), derivation of the
   public key, IDs and output-directory writability using an empty temporary file
   removed on close. Add `--pin-file C:/private/public-trust.json` to check the
   existing format-1 trust config and exact selected key. Add `--bundle` after
   phone export to check its strict parser, hashes and freshness without printing
   it. Optional checks say `NOT_REQUESTED`. Pass means local setup checks passed,
   not that Google credentials, project linkage or platform access were verified.

2. **Optional offline synthetic test**, after dependencies are cached:

   ```powershell
   uv run --offline --with cryptography -- python tools/integrity_backend.py --self-test
   ```

   This generates a fresh in-memory RSA key, rebases only the clock in the explicitly
   synthetic repository fixture, and exercises bundle/hash/decode-shape extraction,
   safe signing and strict pinned-envelope verification, including negative hash
   and signature checks. Only a fixed `SYNTHETIC_ONLY_SELF_TEST_PASS`, check count
   and public digests are printed. No artifact or owner key is written/read. The
   Python contract verifier is also tested against the actual Kotlin importer in
   `IntegrityBackendInteropTest`; the CLI itself does not require Android/Gradle.
   Synthetic UNEVALUATED fields are not a real Google result or readiness evidence.

3. **Derive and independently provision the public pin**:

   ```powershell
   uv run --offline --with cryptography -- python tools/integrity_backend.py --show-pin --provider owner.backend --key-id owner-key-1 --signing-key C:/private/backend-signing.pem
   ```

   Output has only `provider`, `key_id`, `public_key_sha256` and `public_key`.
   The fingerprint hashes DER SubjectPublicKeyInfo bytes. Put only `provider`,
   `key_id`, `public_key` into the existing `{"format":1,"keys":[...]}` trust config;
   retain the fingerprint separately for owner comparison. Do not add the fingerprint
   to the app's closed entry format. Provision that public config separately using
   the existing approved owner setup. Missing/revoked pins remain fail-closed.

4. **Later, on the phone**, request explicitly, opt into retention, export the
   sensitive verification bundle to local storage, and retain the matching ordinary
   receipt report. Do not reuse a historical bundle. Run doctor again with the
   exported `--bundle` and independently reviewed `--pin-file` if desired.
5. **Run the existing real backend command once**, using ADC/project configuration
   prepared by the owner, `--confirm-send`, the current bundle and a new output
   filename. The package target remains exactly `dev.cyclone.cloak.playevidence`.
   None of the operational modes can substitute for a genuine decode response.
6. **Import the matching ordinary receipt report**, then **import the signed
   verdict** through the existing authenticated import action. Confirm current
   profile/hash/user/artifact/freshness and independently provisioned trust.
7. **Capture redacted evidence only**: safe operation result/check names, digests,
   timestamps and local review state. Exclude ciphertext bundles, credentials,
   private keys/passwords and Google HTTP response bodies. A backend assertion is
   not an offline Google signature, platform certification or proof for other apps.

Failure output is bounded and fixed: doctor emits named PASS/FAIL/NOT_REQUESTED
checks; other failures use `INTEGRITY_SHOW_PIN_FAILED`,
`SYNTHETIC_ONLY_SELF_TEST_FAILED`, `INTEGRITY_ARGUMENTS_FAILED`, or the unchanged
`INTEGRITY_EXCHANGE_FAILED` message. No exception or arbitrary command-line value
is echoed. Doctor tests imports only and never inspects credential path contents.

This sprint includes no device collection, real Google decode, release, permission
change, account write, module change, or credential provisioning. Later owner
verification must exercise the real project, diagnostics package, pin, document
picker, receipt import, backend decode, and signed import together.
