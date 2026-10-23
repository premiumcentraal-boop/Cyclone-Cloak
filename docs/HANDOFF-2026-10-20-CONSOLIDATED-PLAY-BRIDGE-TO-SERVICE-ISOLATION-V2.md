# Cyclone Cloak — consolidated handoff: Play Verdict Bridge through Service Surface Isolation v2

Date: 2026-10-20
Repository: `C:/Users/Agent/Cyclone/cloak-repo`
Branch: `main`

## Current state — read this before the historical handoffs

This document combines all seven handoffs in chronological order. Their full
contents, changed-file lists, commands, test results and limitations are retained
below. They describe successive checkpoints; historical statements such as
“uncommitted” or “backend not implemented” are superseded where later sections
complete that work. Test counts are per checkpoint and must not be added together.
Creating this combined handoff did not rerun tests, change code, commit or publish.

Latest published release remains **v0.11.0-alpha.27**. These seven sprints have not
been released. Local HEAD and the local `origin/main` tracking reference both
resolve to `bed5535e3292545c94fa5607381963e350e3c002` at consolidation time.

| Work | Current Git state |
| --- | --- |
| Play verdict bridge | Committed as `4ce82d16c1612a2b9f8a33b471e6aa1014b8b3f5`; included in pushed ancestry |
| Integrity verification exchange | Committed and pushed as `1637e272f793498a7297336a32d065c099ea2113` |
| Verification operations kit | Committed and pushed as `bed5535e3292545c94fa5607381963e350e3c002` |
| Cohort separation engine v1 | Uncommitted local implementation |
| Instance realism expansion v1 | Uncommitted local implementation |
| Runtime envelope phase 2 | Uncommitted local implementation, including module/native changes |
| Service surface isolation v2 | Uncommitted local implementation |

Preserve all existing local changes. Do not stage unrelated old handoffs,
evidence directories, screenshots or archives with implementation commits.
Original handoff files remain untouched.

## What the combined upgrade delivers

1. **Authenticated verdict import:** strict signed safe-field envelopes, runtime
   public trust pins, request/profile/user/artifact/freshness binding, private
   evidence storage and separate verdict presentation. Missing evidence never
   becomes READY.
2. **Owner-controlled verification exchange:** explicit encrypted-token retention
   in the separate diagnostics app, private document export, an owner-invoked
   Google backend decode/sign tool and Python/Kotlin importer interoperability.
3. **Offline operations kit:** doctor, synthetic self-test and public-pin display,
   with fixed privacy-safe output and no automatic network requests.
4. **Cohort analysis:** deterministic Python/Kotlin separation reports, shared
   groups, duplicate heuristics, missing facts and review recommendations without
   raw identifier disclosure or profile mutation.
5. **Instance realism:** opt-in approved complete variation tuples, deterministic
   generation, provenance and uniqueness checks. Unsupported facts stay fixed,
   unknown or unavailable; existing generation remains compatible.
6. **Runtime envelope:** scoped, profile-backed read-only callback expansion and
   15 digest-only probe rows. This sprint does change module/native code; do not
   describe the combined work as entirely non-runtime. New surfaces remain
   PARTIAL and physically UNVERIFIED.
7. **Service isolation state:** app-scoped ISOLATED, SHARED_CONFIRMED, CONFLICT,
   STALE, UNKNOWN and NEEDS_REVIEW, exact-group consent and conservative readiness
   gating. This tracks local evidence; it does not enforce Android isolation.

No device actions, account operations, permission changes or release/tag actions
were performed during these seven sprints. The explicit local backend command can
make an owner-authorized Google request later; Android does not silently forward
its token. No production credential or private signing key was committed.

## Latest verification snapshot

| Suite | Latest recorded pass count | Checkpoint |
| --- | ---: | --- |
| Production app | 781 | Service isolation v2 |
| Module | 80 | Service isolation v2 |
| Standard probe | 114 | Service isolation v2 |
| Diagnostics probe | 114 | Service isolation v2 |
| Forge/schema combined | 1,245 | Service isolation v2 |
| Play evidence APK | 30 | Verification exchange; not rerun in the latest service sprint |
| Host probe tools | 7 | Runtime envelope phase 2; not rerun in the latest service sprint |

The latest app debug build passed without compiler warnings. Runtime-envelope
module packaging and both probe builds passed at that earlier checkpoint.
Historical lint: production 0 errors / 19 existing warnings; Play evidence 0
errors / 0 warnings at the exchange checkpoint. These are not fresh lint results
for the final combined tree. Git whitespace checks passed; existing LF-to-CRLF
notices remain. Exact commands and intermediate corrections are preserved below.

## Remaining work and operating boundaries

- Review and commit the four outstanding implementations deliberately, preserving
  unrelated local files. No new release is authorized by this handoff request.
- Before any later release, run a fresh combined release/signing/lint gate; the
  results above are development checkpoints, not signed-release validation.
- Owner setup is still required: authorized Play/Cloud project and ADC, an external
  RSA signing key, doctor/self-test, independently reviewed public pin provisioning,
  explicit real token export, one backend decode/sign run, then receipt and signed
  verdict import. The exact checklist appears in the operations-kit section.
- Genuine credentialed decode/export/import remains UNVERIFIED. Synthetic tests
  are implementation checks, not real Google verdicts. Backend signatures
  authenticate the configured backend assertion, not an offline Google signature.
- New runtime surfaces require a separately authorized matching-module device run:
  two supported Reality fixtures, repeated cold starts, unbound restoration,
  cross-user isolation, permission/API limits, audits and cleanup proof. Existing
  schema-0.3 A/B evidence does not cover the new Reality callbacks.
- Existing Play diagnostics do not prove account-slot ownership, backup policy or
  private-data boundaries. Service evidence therefore remains UNKNOWN where these
  facts are missing. Passing Integrity or isolation cannot fill missing evidence.
- Cohort scores are conservative heuristics. Realism varies only catalog-supported
  tuples. Neither supplies hardware facts or assurance that was not observed.
- Existing profile/binding/publication layouts and connector protocol are retained.
  Local evidence sidecars and additive schema metadata are documented per sprint.
  No platform certification, permanent trust or broader device compatibility is
  claimed.

## Included source handoffs

1. [Play verdict bridge](HANDOFF-2026-10-20-PLAY-VERDICT-BRIDGE.md)
2. [Integrity verification exchange v1](HANDOFF-2026-10-20-INTEGRITY-VERIFICATION-EXCHANGE.md)
3. [Verification operations kit](HANDOFF-2026-10-20-VERIFICATION-OPERATIONS-KIT.md)
4. [Cohort separation engine v1](HANDOFF-2026-10-20-COHORT-SEPARATION-V1.md)
5. [Instance realism expansion v1](HANDOFF-2026-10-20-INSTANCE-REALISM-V1.md)
6. [Runtime envelope phase 2](HANDOFF-2026-10-20-RUNTIME-ENVELOPE-PHASE-2.md)
7. [Service surface isolation v2](HANDOFF-2026-10-20-SERVICE-SURFACE-ISOLATION-V2.md)

---

## 1. Play verdict bridge — complete source handoff

Source: [HANDOFF-2026-10-20-PLAY-VERDICT-BRIDGE.md](HANDOFF-2026-10-20-PLAY-VERDICT-BRIDGE.md)

### Play verdict bridge — 2026-10-20

Repository: `C:/Users/Agent/Cyclone/cloak-repo`, `main`, base
`cb60012a121743c8374dda5cf164349dfe3b0b28`. No commit, tag, release or device
action is part of this run. Prior unrelated untracked handoffs/evidence remain
untouched, including the consolidated alpha.27 onward handoff.

#### Delivered

- Strict format-1 `IntegrityVerdict` parser: bounded input, duplicate/unknown-key
  rejection, typed allowlists, strict dates and authenticated RS256 backend
  envelopes. Trust pins are separately provisioned at runtime; default trusts
  nobody. Unsigned decode JSON or self-supplied keys cannot establish a verdict.
- Request binding against the explicitly saved diagnostics receipt: exact
  request ID, recomputed request hash, profile ID/body hash, artifact hash and
  Android user. Freshness, future times and receipt/decode ordering are checked.
- Separate safe app-private sidecar with atomic writes and evidence digest.
  Invalid imports preserve prior accepted bytes; reads recheck signature, current
  binding, receipt and freshness. Explicit valid diagnostics reimport can repair
  a corrupt sidecar without changing saved profiles.
- Separate NO_REQUEST, TOKEN_RECEIVED, DECODED_AVAILABLE, PASS, DEGRADED, FAIL
  and NEEDS_REVIEW states. Local policy maps actual returned app/licensing/device
  labels; a passing verdict only retains existing local readiness. UNKNOWN is
  never promoted to READY by Integrity, and mismatches require review.
- Existing Profiles readiness card shows the separate verdict state, reasons,
  provider, decode time and returned labels. Added **Import authenticated
  verdict** using the existing local document picker. No new screen.
- Backend adapter interface only: no network implementation, service-account
  files, private project IDs, tokens, secrets or committed key material.

#### Changed files and public entry points

New production files, relative to `android/app/src/main/java/dev/cyclone/cloak/`:

- `play/IntegrityVerdict.kt`: `IntegrityVerdict`, `IntegrityRequest`,
  `IntegrityState`, `IntegrityResult`; `IntegrityVerdicts.parse/evaluate/readiness`;
  `IntegrityTrust.key`; `IntegrityBackendAdapter.authenticatedVerdict`;
  `IntegrityPlayReadinessProvider.evaluate`.
- `play/IntegrityVerdictStore.kt`: `LocalIntegrityTrust.key`;
  `IntegrityVerdictStore.recordRequest/read/save`.

Modified production files in the same source root:

- `play/PlayEvidenceImport.kt`: validate and retain request binding on explicit
  diagnostics save, authenticate verdict import, compose conservative card state.
- `play/PlayReadinessEvaluation.kt`: fixed readable IV_* reasons.
- `ui/CloakViewModel.kt`, `ui/ProfilesScreen.kt`, `ui/PlayReadinessCard.kt`:
  existing-picker import, fixed error/partial-save feedback, separate verdict UI.

Tests: new `play/IntegrityVerdictTest.kt` and `IntegrityVerdictStoreTest.kt` under
the corresponding app test root; extended `ui/PlayReadinessCardTest.kt`.
New `schema/integrity-verdict.schema.json` and
`forge/schema_tests/test_integrity_verdict_schema.py`.
Documentation: `docs/PLAY-INTEGRITY-VERDICT-BRIDGE-V1.md`, an additive link in
`docs/PLAY-READINESS-EVALUATION-V1.md`, and this handoff.

#### Storage and trust

New private preferences `play-integrity-bridge-v1`, keyed by profile ID, hold a
format-1 entry with a safe `request`, nullable signed `envelope` and nullable
`evidence_digest`. No raw encrypted token or profile body is stored. Existing
readiness/assignment/profile formats and all binding/publication layouts stay
unchanged. Original diagnostics save and receipt save are separate atomic
operations; a receipt failure after readiness save is explicitly reported.

`files/play-integrity-trust-v1.json` is optional independent runtime config for
public provider pins only (RSA >= 2048 bits). This run creates no such production
config and ships no trusted provider. See the contract guide for exact format,
signature bytes, request-hash formula, mappings and backend obligations.

#### Verification

Test results: **2,088 passed**, zero failures/errors/skips:

| Suite | Passed |
| --- | ---: |
| Production app (including 33 new Kotlin/Compose tests) | 736 |
| Module | 40 |
| Play evidence APK | 20 |
| Standard probe | 109 |
| Diagnostics probe | 109 |
| Forge + schema (including 22 new schema cases) | 1,067 |
| Host probe tools | 7 |

Focused Play evidence/schema rerun: **60 passed**, included in the totals above.
Ruff check/format passed. Tracked/staged diff checks and a separate whitespace
check for the eight new intended files passed. No source/version/permission
changes occurred outside the listed files.

An intermediate lint run caught suspicious indentation in the new store; the
code was corrected rather than bypassing that check. A narrow UseKtx annotation
documents the intentionally checked synchronous commit result. The corrected
full Android gate passed in 3m 1s, including debug assembly and lint: **0 errors,
19 existing warnings**, no new compiler warnings. Debug APK:
`android/app/build/outputs/apk/debug/app-debug.apk`.
Final Android stdout: `%TEMP%/cloak-integrity-final-fixed.log`;
forge/schema stdout: `%TEMP%/cloak-integrity-python.log`.

Commands, from the repository root:

```powershell
$env:JAVA_HOME='C:/Program Files/Eclipse Adoptium/jdk-17.0.20.101-hotspot'
$env:ANDROID_HOME='C:/Users/Agent/android-sdk'
$env:CLOAK_TEST_WSL='1'
# Initial focused import/parser check and debug assembly:
& "$env:TEMP/cloak-build/gradle-8.11.1/bin/gradle.bat" -p android :app:testDebugUnitTest --tests dev.cyclone.cloak.play.IntegrityVerdictTest --tests dev.cyclone.cloak.play.PlayEvidenceImportTest :app:assembleDebug --offline --no-daemon --console=plain
# Full final Android gate, including all new parser/store/Compose tests:
& "$env:TEMP/cloak-build/gradle-8.11.1/bin/gradle.bat" -p android :app:testDebugUnitTest :module:testDebugUnitTest :playEvidence:testDebugUnitTest :probe:testStandardDebugUnitTest :probe:testDiagnosticsDebugUnitTest :app:assembleDebug :app:lintDebug --offline --no-daemon --console=plain
uv run --offline --with pytest --with jsonschema -- python -X utf8 -m pytest forge/tests forge/schema_tests -q
uv run --offline -- python -X utf8 -m unittest discover -s android/probe/tests -v
uv run --offline --with pytest --with jsonschema -- python -X utf8 -m pytest forge/tests/test_play_evidence_harness.py forge/schema_tests/test_integrity_verdict_schema.py -q
uv run --offline --with ruff -- ruff check forge/schema_tests/test_integrity_verdict_schema.py
uv run --offline --with ruff -- ruff format --check forge/schema_tests/test_integrity_verdict_schema.py
git diff --check
git diff --cached --check
```

Tests cover authentication/tampering, all required binding mismatches, malformed
and incomplete records, stale/future dates, missing receipt, state mapping,
unknown-preservation, rejected-save preservation, request replacement, corrupt
storage recovery, public-pin revocation, digest tampering, binding-file
preservation and visible UI/error states. Tests use synthetic verdict data and
ephemeral test keys only, not platform evidence.

#### What a later physical test can verify

With an independently trusted backend/public pin and a separately implemented
secure token handoff, a later authorized run can verify an actual request →
Google backend decode → signed safe envelope → binding/freshness checks → local
import and separate verdict display. It can also prove tampered/foreign/stale
verdict rejection and no promotion of missing local evidence.

This sprint cannot itself obtain a real decoded verdict: no concrete backend or
token delivery is deployed, and the existing diagnostics APK discards encrypted
tokens after receipt. No trust pin was installed and no real verdict was tested.
Artifact binding identifies the companion APK/bundled-module set, not independent
attestation of the loaded module. Backend signatures authenticate a trusted
backend assertion; they are not offline Google signatures or certification.

---

## 2. Integrity verification exchange v1 — complete source handoff

Source: [HANDOFF-2026-10-20-INTEGRITY-VERIFICATION-EXCHANGE.md](HANDOFF-2026-10-20-INTEGRITY-VERIFICATION-EXCHANGE.md)

### Integrity verification exchange v1 — 2026-10-20

#### Git and boundaries

The previous authenticated verdict bridge was committed separately on `main` as
`4ce82d16c1612a2b9f8a33b471e6aa1014b8b3f5`
(`feat(play): add authenticated evidence-bound Integrity verdict bridge`).
Unrelated untracked handoffs, evidence, screenshots and archives were excluded.
The new exchange changes remain uncommitted for review. No push, release, tag,
device action, permission/account change or module modification was performed.

#### Delivered

- Separate affirmative token-retention choice in the existing diagnostics activity;
  default collection does not retain. Only the current generation can persist.
- One atomic private ciphertext record, explicit confirmation and document-picker
  export, current binding/digest/type/size/freshness validation, and deletion on
  new collection or successful export. Normal reports never contain tokens.
- Minimal format-1 wrapper (`format`, declared `request_hash`, `bundle`); the nested
  bundle contains exactly the eight requested payload fields. This framing resolves
  the requirement for versioning and a declared hash without adding payload data.
- Owner-invoked Python backend using ADC, the fixed Google decode endpoint for
  `dev.cyclone.cloak.playevidence`, strict decoded package/hash/time/verdict checks,
  safe-only extraction, RS256 signing and atomic new-file output. No unsigned
  decoded JSON input, redirects, automatic decode retries or secret error output.
- Existing importer/trust behavior preserved. A synthetic Python-signed envelope
  is accepted by the actual Kotlin importer with an in-memory ephemeral test key.
- CI changes provision cryptography for tests only; release triggers, artifact
  signing and publication steps are unchanged and were not invoked.

Private diagnostics storage: `files/integrity-verification.json`, at most 64 KiB,
one record, excluded from backup by the existing manifest. Export expires after
24 hours; expired ciphertext is not deleted by a background job. A canceled export
retains the private copy. A failed document-provider write can leave a partial file
at the explicitly selected destination. No production storage layout changes.

Public helpers: `VerificationBundle.retain(token, binding, at, explicitConsent)`,
`parse(text, now, expected)`, `export()`; optional retention callback on
`IntegrityEvidence.request`; Python `parse_bundle`, `safe_payload`, `sign_envelope`.
The token container's `toString()` is redacted.

#### Changed files

- `.github/workflows/forge.yml`, `signed-validation.yml`, `release.yml`: test dependencies.
- `android/playEvidence/src/main/java/dev/cyclone/cloak/playevidence/EvidenceActivity.kt`
- `android/playEvidence/src/main/java/dev/cyclone/cloak/playevidence/IntegrityEvidence.kt`
- `android/playEvidence/src/main/java/dev/cyclone/cloak/playevidence/VerificationBundle.kt`
- `android/playEvidence/src/main/res/values/strings.xml`
- `android/playEvidence/src/test/java/dev/cyclone/cloak/playevidence/VerificationBundleTest.kt`
- `android/app/src/test/java/dev/cyclone/cloak/play/IntegrityBackendInteropTest.kt`
- `tools/integrity_backend.py`, `tools/integrity-backend-requirements.txt`
- `schema/integrity-verification-bundle.schema.json`
- `forge/tests/test_integrity_exchange.py`
- `docs/INTEGRITY-VERIFICATION-EXCHANGE-V1.md`
- `docs/PLAY-EVIDENCE-HARNESS-V1.md`, `docs/PLAY-INTEGRITY-VERDICT-BRIDGE-V1.md`
- This handoff.

#### Verification

| Suite | Passed |
| --- | ---: |
| Production app | 737 |
| Module | 40 |
| Play evidence diagnostics | 30 |
| Standard probe | 109 |
| Diagnostics probe | 109 |
| Forge and schema combined | 1,105 |
| Host probe runner | 7 |
| Total | 2,137 |

49 new focused tests are included above (38 Python, 10 bundle tests, one
cross-language importer test). No tests skipped. Synthetic tokens and ephemeral
test keys only. Debug builds of production and Play evidence APKs passed. Lint:
production 0 errors / 19 pre-existing warnings; diagnostics 0 errors / 0 warnings.
Final compiler output has no warnings. Initial test-compatibility and translation
warnings were fixed before the final gate.

Exact successful gate commands, from the repository root:

```powershell
$env:JAVA_HOME='C:/Program Files/Eclipse Adoptium/jdk-17.0.20.101-hotspot'
$env:ANDROID_HOME='C:/Users/Agent/android-sdk'
$env:CLOAK_TEST_WSL='1'
& "$env:TEMP/cloak-build/gradle-8.11.1/bin/gradle.bat" -p android :app:testDebugUnitTest :module:testDebugUnitTest :playEvidence:testDebugUnitTest :probe:testStandardDebugUnitTest :probe:testDiagnosticsDebugUnitTest :app:assembleDebug :playEvidence:assembleDebug :app:lintDebug :playEvidence:lintDebug --offline --no-daemon --console=plain
uv run --offline --with pytest --with jsonschema --with cryptography -- python -X utf8 -m pytest forge/tests forge/schema_tests -q
uv run --offline -- python -X utf8 -m unittest discover -s android/probe/tests -v
uv run --offline --with pytest --with jsonschema --with cryptography -- python -X utf8 -m pytest forge/tests/test_integrity_exchange.py -q
uv run --offline --with ruff -- ruff check tools/integrity_backend.py forge/tests/test_integrity_exchange.py --select E9,F
git diff --check
git diff --cached --check
```

Dependency bootstrap (package download only, no Google API request):
`uv run --with cryptography --with google-auth --with requests -- python -c "import cryptography, google.auth, requests; print('backend dependencies available')"`.
The initial offline dependency probe failed because google-auth/requests were not
cached; this bootstrap resolved it.

Local final console logs are `%TEMP%/cloak-exchange-complete-android.log` and
`%TEMP%/cloak-exchange-complete-python.log`. No sensitive evidence was generated.

#### Owner setup and later phone test

Follow [the exchange guide](INTEGRITY-VERIFICATION-EXCHANGE-V1.md). The owner must
configure a linked Play/Google Cloud project and authorized ADC identity, supply
an external private RSA key, and separately provision its public provider/key pin
in the production app's private trust configuration. No provider is trusted by
default; missing/revoked pins still fail closed on every read.

Then explicitly request and retain a real diagnostics token, export it privately,
run the backend once, import the matching receipt report and signed envelope, and
inspect the local readiness UI. This entire physical/credentialed path is
UNVERIFIED in this sprint. A sideloaded diagnostics package may receive unavailable
or unevaluated results. Backend signatures authenticate backend assertions, not an
offline Google signature, certification, permanent trust, or unrelated apps.

---

## 3. Verification operations kit — complete source handoff

Source: [HANDOFF-2026-10-20-VERIFICATION-OPERATIONS-KIT.md](HANDOFF-2026-10-20-VERIFICATION-OPERATIONS-KIT.md)

### Verification operations kit — 2026-10-20

Exchange commit: `1637e272f793498a7297336a32d065c099ea2113`, pushed to
`origin/main` before operations-kit work began. It includes only the intended
17 exchange files; unrelated untracked handoffs/evidence/screenshots/archives were
preserved. The earlier verdict bridge commit was included in the push ancestry.

Operations kit: the separate commit containing this handoff. Its exact SHA is
returned in the final operator report and can be read with
`git log -1 --format=%H -- tools/integrity_backend.py`.

#### Delivered and changed files

- `tools/integrity_backend.py`: `--doctor`, `--self-test`, `--show-pin`, shared
  key/payload validation and pinned signed-envelope parser; the existing explicit
  decode/sign command and fixed diagnostics package target remain compatible.
- `forge/tests/test_integrity_operations.py`: 39 new focused tests.
- `forge/tests/fixtures/integrity_decode_synthetic.json`: explicit synthetic-only
  UNEVALUATED decode-shape fixture, no real token/account/credential/verdict.
- `android/app/src/test/java/dev/cyclone/cloak/play/IntegrityBackendInteropTest.kt`:
  additional test verifies the self-test artifact with the actual Kotlin parser.
- `docs/INTEGRITY-VERIFICATION-EXCHANGE-V1.md`: owner checklist and mode commands.
- This handoff.

Doctor reports seven fixed named checks, with PASS/FAIL/NOT_REQUESTED states.
It imports dependencies without resolving ADC, loads/validates the external RSA
key and optional PEM password, derives the public key, optionally checks the
format-1 public trust file and supplied verification bundle, and tests directory
writability using an empty automatically removed temporary file. It never calls
Google or prints paths, input contents or exception messages.

Show-pin emits only provider ID, key ID, SHA-256 of DER SubjectPublicKeyInfo and
base64 DER public key. The fingerprint is comparison metadata; copy only the
three existing entry fields into the app's closed trust config.

Self-test uses an in-memory ephemeral key and a labelled synthetic fixture. It
exercises strict bundle/hash validation, decode-shape binding and safe-field
extraction, signing, pinned-envelope parsing, and negative hash/signature checks.
It prints only a fixed synthetic-only result, count and public digests. The CLI
uses the shared Python contract verifier; the Android interoperability test checks
the generated artifact with the actual Kotlin importer. No Android SDK is needed
for the CLI self-test. No synthetic result is real platform evidence.

#### Checks

- App unit tests: **738 passed**, including both Python/Kotlin interoperability tests.
- Forge/schema combined: **1,144 passed**.
- Focused operations + existing exchange tests: **77 passed**, included above
  (39 new operations cases + 38 existing exchange cases).
- **1,882 distinct tests passed**, no failures/skips. App debug build passed;
  no compiler warnings. Python static checks and both Git whitespace checks passed.
- A real dependency-import doctor smoke test passed with a temporary synthetic
  RSA key/public pin. The optional bundle check was NOT_REQUESTED. No owner key,
  ADC resolution or Google request was used. The temporary directory was removed.
- CLI offline self-test passed. Only fixed results, counts and public digests printed.

Exact test/build commands from repository root:

```powershell
$env:JAVA_HOME='C:/Program Files/Eclipse Adoptium/jdk-17.0.20.101-hotspot'
$env:ANDROID_HOME='C:/Users/Agent/android-sdk'
$env:CLOAK_TEST_WSL='1'
& "$env:TEMP/cloak-build/gradle-8.11.1/bin/gradle.bat" -p android :app:testDebugUnitTest :app:assembleDebug --offline --no-daemon --console=plain
uv run --offline --with pytest --with jsonschema --with cryptography -- python -X utf8 -m pytest forge/tests forge/schema_tests -q
uv run --offline --with pytest --with jsonschema --with cryptography -- python -X utf8 -m pytest forge/tests/test_integrity_operations.py forge/tests/test_integrity_exchange.py -q
uv run --offline --with ruff -- ruff check tools/integrity_backend.py forge/tests/test_integrity_operations.py --select E9,F
uv run --offline --with cryptography -- python tools/integrity_backend.py --self-test
git diff --check
git diff --cached --check
```

Final build logs: `%TEMP%/cloak-operations-android.log` and
`%TEMP%/cloak-operations-python.log`. Module/probe suites were unchanged and were
not rerun in this backend-only sprint; their passing exchange-run results remain
in the preceding handoff. No new release gate or device compatibility is claimed.

#### Exact remaining owner steps

1. Prepare authorized Play/Cloud configuration and standard ADC on the owner's PC;
   keep credentials and the RSA signing key outside the repository and phone.
2. Run `--doctor` with the signing key, provider/key ID and intended output
   directory. Set an encrypted PEM password privately through the existing env var.
3. Optionally run the cached/offline `--self-test`; it checks implementation only.
4. Run `--show-pin`, independently review its fingerprint, and provision only its
   public fields in the existing app-private trust config. No provider is trusted
   by default; doctor pass does not provision trust or validate Google authorization.
5. During the later physical test, explicitly request and retain a genuine token,
   export its sensitive bundle, and retain the matching ordinary receipt report.
6. Optionally rerun doctor with `--bundle` and `--pin-file`; run the existing
   confirmed backend decode/sign command **once** to a new output filename.
7. Import the matching receipt report first, then the signed verdict. Check current
   profile/user/artifact/hash/freshness and trust; missing evidence stays unknown.
8. Capture only redacted check states, timestamps and digests. Never attach the
   verification bundle/token, password, private key, ADC files or Google HTTP body.

Real credential authorization, Play project linkage, API response, phone export
and import remain **UNVERIFIED**. A backend assertion is not an offline Google
signature, platform certification, permanent trust or proof about unrelated apps.

No device action, install, reboot, clear-data operation, permission/account change,
module/runtime change, release, tag or release-workflow dispatch occurred.

---

## 4. Cohort separation engine v1 — complete source handoff

Source: [HANDOFF-2026-10-20-COHORT-SEPARATION-V1.md](HANDOFF-2026-10-20-COHORT-SEPARATION-V1.md)

### Cohort separation v1 handoff — 2026-10-20

Operations kit commit `bed5535e3292545c94fa5607381963e350e3c002` was pushed
to `origin/main` first. No release/tag or device action occurred. Cohort work is
left as intended local changes for review; unrelated untracked handoffs/evidence
were not staged or changed.

#### Changed files and public entry points

- `catalog/cohort-separation-v1.json`: shared version-1 category/path/tuple policy.
- `forge/cloak_forge/cohort_separation.py`: `analyze(profiles: list[dict]) -> dict`.
- `android/app/src/main/java/dev/cyclone/cloak/forge/CohortSeparation.kt`:
  `CohortSeparation(policyJson: String).analyze(input: List<JSONObject>): JSONObject`.
- `forge/tests/test_cohort_separation.py`: 24 focused cases.
- `android/app/src/test/java/dev/cyclone/cloak/forge/CohortSeparationTest.kt`:
  eight tests, including full-report Python/Kotlin comparison across 14 libraries.
- `docs/COHORT-SEPARATION-V1.md`: API, privacy, policy/scoring, limits and examples.
- `docs/ARCHITECTURE.md`, `docs/CURRENT-STATE.md`: unreleased additive analysis notes.
- This handoff.

Output includes profile IDs/labels, pairwise/category distance and confidence,
shared membership groups, exact/near identifier duplicates, weakest fields,
missing/unavailable Reality sections, correlated device tuples, regeneration or
completion review recommendations, cohort score and conservative warning codes.
No raw values or value hashes are emitted. Malformed/duplicate-ID libraries fail
closed with a fixed message. Schema 0.2/0.3/0.4 remains unchanged and inputs are
not mutated. The existing FleetDiversity UI/planner is unchanged.

#### Verification commands

Run from the repository root, PowerShell:

```powershell
git push origin main
$env:JAVA_HOME='C:/Program Files/Eclipse Adoptium/jdk-17.0.20.101-hotspot'
$env:ANDROID_HOME='C:/Users/Agent/android-sdk'
$env:CLOAK_TEST_WSL='1'
& "$env:TEMP/cloak-build/gradle-8.11.1/bin/gradle.bat" -p android :app:testDebugUnitTest --tests '*CohortSeparationTest' --offline --no-daemon --console=plain
& "$env:TEMP/cloak-build/gradle-8.11.1/bin/gradle.bat" -p android :app:testDebugUnitTest :module:testDebugUnitTest :app:assembleDebug --offline --no-daemon --console=plain
uv run --offline --with pytest --with jsonschema --with cryptography -- python -X utf8 -m pytest forge/tests/test_cohort_separation.py -q
uv run --offline --with pytest --with jsonschema --with cryptography -- python -X utf8 -m pytest forge/tests forge/schema_tests -q
uv run --offline --with ruff -- ruff check forge/cloak_forge/cohort_separation.py forge/tests/test_cohort_separation.py --select E9,F
git diff --check
git diff --cached --check
```

Final results: **746 app tests passed**, including all eight cohort tests and
the complete cross-language comparison. **1,168 forge/schema tests passed**,
including all 24 new Python cases. Module task passed up-to-date, retaining its
unchanged **40-test** successful result. No failures or skips. Debug APK built
successfully; no compiler warnings were emitted. Focused Python static checks and
both Git whitespace checks passed. The Unicode-label parity case caught a Windows
test-subprocess encoding issue; explicit UTF-8 fixed it and the full Android gate
was rerun successfully.

Final logs: `%TEMP%/cloak-cohort-android.log` and
`%TEMP%/cloak-cohort-python.log`. The debug APK is at
`android/app/build/outputs/apk/debug/app-debug.apk`. No release build, workflow,
tag, device capture, installation or runtime change was performed.

#### Limits and next steps

This is pure analysis, not a UI/export feature. A caller supplies the decoded saved
profile list and, on Kotlin, the trusted bundled policy asset. There are no storage
callbacks or mutation hooks. The 200-profile cap bounds pairwise work; reports may
be large near that cap. Unknown/unavailable facts earn no distance. Fewer than two
profiles have no meaningful score (null, confidence zero). Near-duplicate detection
is a conservative equal-length, one-substitution heuristic, not exhaustive.

The score is a documented description-separation heuristic, not statistical
independence, enforcement, high-trust assurance, or any platform verdict. Generated
identifiers cannot diversify fixed hardware. Recommendations require separate
user review; unsupported or unavailable hardware must never be invented.

---

## 5. Instance realism expansion v1 — complete source handoff

Source: [HANDOFF-2026-10-20-INSTANCE-REALISM-V1.md](HANDOFF-2026-10-20-INSTANCE-REALISM-V1.md)

### Instance realism v1 handoff — 2026-10-20

Implemented locally on main based on `bed5535e3292545c94fa5607381963e350e3c002`.
No release, tag, device command, installation or version bump. The pre-existing
uncommitted Cohort Separation work and unrelated handoffs/evidence are preserved.

#### Changes

- New `forge/cloak_forge/instance_realism.py`: deterministic approved tuple
  selection, application, provenance validation and cross-slot uniqueness checks.
- New `catalog/instance-realism-v1.json` and `tools/generate_instance_realism.py`:
  134 tuples / 24 baseline sources / 12 model families. Generated Kotlin rules
  are pinned to the exact checked-in source. Twenty-two sources offer matching
  counterpart-catalog firmware alternatives; no firmware strings are invented.
- Updated Python `device_factory.py`, `engine.py`, `validate.py`: opt-in generation,
  complete-tuple coherence, deterministic collision retries through the engine,
  and optional provenance validation. Existing generation defaults remain intact.
- New Kotlin `InstanceRealism.kt`, generated `InstanceRealismRules.kt`; updated
  `IdentityEngine.kt`, `DeviceProfileEngine.kt`, `ProfileValidator.kt` and
  `EngineBundleCodec.kt` for matching generation/replay/bundle validation.
- Updated `ProfileBackup.kt` to round-trip allowlisted realism provenance and
  existing network hint on full identity export. Default redacted exports omit
  the derivation claim. No storage layout or format version changed.
- Added `schema/instance-realism.schema.json`; additive optional fields in
  `cloak-profile.schema.json` and `engine-bundle.schema.json`.
- Added `forge/tests/test_instance_realism.py` (58 cases),
  `forge/schema_tests/test_instance_realism_schema.py` (10 cases), and
  Android `InstanceRealismTest.kt` (8 tests, including Python/Kotlin parity).
- Added `INSTANCE-REALISM-V1.md`, this handoff, and updated `ARCHITECTURE.md` /
  `CURRENT-STATE.md` while retaining the previous cohort documentation.

#### Public API and metadata

Python `InstanceRequest` adds defaulted `instance_realism=False` and
`realism_tuple=None`; Factory batch and engine requests accept the same options.
Kotlin `IdentityEngine.GenerationRequest` adds the equivalent defaulted fields.
`InstanceRealism.plan(source, seed, tuple)`, `apply`, `validate` and `conflicts`
are pure helpers. Unsupported opt-in templates fail explicitly.

`meta.instance_realism` format 1 records catalog digest, source/tuple, per-field
source/confidence/availability and axis policies. It contains no raw identifiers
or seeds. Engine metadata also keeps the private `realism_request_seed`; pin the
returned tuple and use that input to reproduce selection. `chosen_seed` remains
the final profile seed, compatible with the existing bundle invariant.

#### Verification

Full gate: **1,073 forge + 163 schema = 1,236 Python tests passed**;
**754 app + 40 module = 794 Android tests passed**, zero failures/errors.
Module tasks were up-to-date; existing XML results confirmed all 40 tests pass.
Debug APK built successfully at `android/app/build/outputs/apk/debug/app-debug.apk`.
Eight new Kotlin tests and 68 new Python/schema cases are included in those totals.
No compiler warnings appeared. Git reports its existing LF-to-CRLF normalization
notice for `DeviceProfileEngine.kt`; both whitespace checks pass.

Exact final commands (repository root, PowerShell):

```powershell
$env:JAVA_HOME='C:/Program Files/Eclipse Adoptium/jdk-17.0.20.101-hotspot'
$env:ANDROID_HOME='C:/Users/Agent/android-sdk'
$env:CLOAK_TEST_WSL='1'
& "$env:TEMP/cloak-build/gradle-8.11.1/bin/gradle.bat" -p android :app:testDebugUnitTest :module:testDebugUnitTest :app:assembleDebug --offline --no-daemon --console=plain
uv run --offline --with pytest --with jsonschema --with cryptography -- python -X utf8 -m pytest forge/tests forge/schema_tests -q
uv run --offline -- python -X utf8 tools/generate_instance_realism.py --check
uv run --offline --with ruff -- ruff check forge/cloak_forge/instance_realism.py forge/cloak_forge/device_factory.py forge/cloak_forge/engine.py forge/tests/test_instance_realism.py forge/schema_tests/test_instance_realism_schema.py tools/generate_instance_realism.py --select E9,F
git diff --check
git diff --cached --check
```

Additional focused/regression checks during implementation:

```powershell
uv run --offline --with pytest --with jsonschema --with cryptography -- python -X utf8 -m pytest forge/tests/test_instance_realism.py forge/schema_tests/test_instance_realism_schema.py -q
uv run --offline --with pytest --with jsonschema --with cryptography -- python -X utf8 -m pytest forge/tests/test_device_factory.py forge/tests/test_reality.py forge/tests/test_engine.py -q
& "$env:TEMP/cloak-build/gradle-8.11.1/bin/gradle.bat" -p android :app:testDebugUnitTest --tests '*InstanceRealismTest' --offline --no-daemon --console=plain
```

The focused backup test exposed an omitted existing network hint; preserving its
allowlisted value fixed the round-trip. The final full Android gate includes
that fix. Production compilation also caught an unavailable Android JSONObject
comparison method; canonical digest comparison replaced it. Final logs:
`%TEMP%/cloak-realism-android.log`, `%TEMP%/cloak-realism-python.log`.

#### Limits and next steps

No UI toggle was added; generation is opt-in through the existing pure APIs.
Only reviewed SKU pairs and existing matching firmware bundles vary. Carrier
issuers, camera/sensor inventories, DRM, and package/provider versions stay fixed
or unknown/unavailable without supporting alternatives. US timezone choices are
configuration preferences, not measured location. Inherited firmware remains
approximated, not device-verified. No credentials, attestation, issued identifier
claims, key material or platform verdicts are generated.

Callers must supply the complete library snapshot for collision checks. Imported
catalog templates without an approved realism baseline remain usable through
legacy generation, but cannot opt into guessed variations. Future registry
versions must retain v1 resolution for saved provenance. Physical compatibility
is UNVERIFIED; this run intentionally performed no device or runtime work.

---

## 6. Runtime envelope phase 2 — complete source handoff

Source: [HANDOFF-2026-10-20-RUNTIME-ENVELOPE-PHASE-2.md](HANDOFF-2026-10-20-RUNTIME-ENVELOPE-PHASE-2.md)

### Runtime envelope phase 2 handoff — 2026-10-20

Main base: `bed5535e3292545c94fa5607381963e350e3c002`; latest tag alpha.27.
No release/tag, device actions, version bump, permission changes or profile
migration. Existing local Cohort Separation and Instance Realism work, old
handoffs and evidence are preserved. This work remains uncommitted.

#### Implemented

The existing user/package binding lookup passes a bounded in-memory profile
projection to the existing pinned Java loader. `RuntimeEnvelope` validates an
immutable process snapshot; new callbacks preserve API denials, fail closed on
missing/invalid facts and fail open only on implementation errors. No new reader
format, publisher path, profile storage or connector change.

Callbacks cover visible allowlisted package versions, real sensor inventories and
descriptors, explicit private-directory free-space reads, GPU renderer metadata,
provider/package/UA consistency, real display size, SIM state/slot edges and
match-only DRM security-level reads. Existing camera mapping is deterministic by
real ID/facing and object identity, with physical pixel/capability ceilings.
Sensor descriptors likewise cannot claim better physical capabilities.

Nominal RAM/storage, physical CPU count, GPU vendor, SoC rewriting, dynamic sensor
enumeration and unsupported hardware capabilities are not fabricated or hooked.
See [the exact surface matrix and boundaries](RUNTIME-ENVELOPE-PHASE-2.md).
This is a partial runtime expansion, not proof that every descriptive Reality
fact can be enforced or that incomplete profiles will work with every app.

#### Changed files in this run

- `android/module/build.gradle`: enable Kotlin runtime tests (JVM 17); embedded
  Java hook compilation remains Java 11.
- `android/module/src/main/cpp/cloak.cpp`: bounded per-process JNI snapshot from
  the existing saved profile; existing binding/user lookup is untouched.
- `android/module/src/hook/java/dev/cyclone/cloak/hook/HookBridge.java`:
  configure snapshot, integrate callbacks and harden camera object/ordinal mapping.
- New module `RuntimeEnvelope.java`, `RuntimeEnvelopeHooks.java` in that package.
- New module test `RuntimeEnvelopeTest.kt` and synthetic resources
  `envelope-a.json`, `envelope-b.json`. They are test-only, not device definitions.
- New probe `RuntimeEnvelopeReport.java` and `RuntimeEnvelopeReportTest.java`;
  updated `ProbeActivity.java` and `ProbeReportPolicy.java`.
- Updated `catalog/reality-runtime-fields.json` and generated
  `android/app/src/main/java/dev/cyclone/cloak/forge/RealityRuntimeTable.kt`,
  probe `assets/reality-runtime-fields.json`,
  `forge/tests/vectors/reality-runtime-contract.json`,
  `schema/reality-runtime.contract.json`.
- New `schema/runtime-envelope-report.schema.json` and
  `forge/schema_tests/test_runtime_envelope_report_schema.py`.
- Updated `ARCHITECTURE.md`, `CURRENT-STATE.md`, `RUNTIME-COVERAGE.md`,
  `REALITY-RUNTIME-BACKLOG.md`; added the surface guide and this handoff.

The optional format-1 `runtime_envelope` report has 15 digest-only rows and always
states `NOT_VERIFIED`. No raw names/identifiers are added to reports. Legacy and
linked-parity sections keep their format. Contract counts: 10 APPLIED linked-base
paths, 17 PARTIAL, 3 PENDING, 27 DIAGNOSTIC_ONLY, 37 HOST_ONLY, 3 UNSUPPORTED.
No new surface is APPLIED or verified. Existing evidence does not cover this
changed module.

#### Verification commands

Repository root, PowerShell:

```powershell
$env:JAVA_HOME='C:/Program Files/Eclipse Adoptium/jdk-17.0.20.101-hotspot'
$env:ANDROID_HOME='C:/Users/Agent/android-sdk'
$env:CLOAK_TEST_WSL='1'
& "$env:TEMP/cloak-build/gradle-8.11.1/bin/gradle.bat" -p android :app:testDebugUnitTest :module:testDebugUnitTest :probe:testStandardDebugUnitTest :probe:testDiagnosticsDebugUnitTest :app:assembleDebug :module:packageModule :probe:assembleStandardDebug :probe:assembleDiagnosticsDebug --offline --no-daemon --console=plain
uv run --offline --with pytest --with jsonschema --with cryptography -- python -X utf8 -m pytest forge/tests forge/schema_tests -q
uv run --offline -- python -X utf8 -m unittest discover -s android/probe/tests -v
uv run --offline -- python -X utf8 tools/generate_runtime_contract.py --check
uv run --offline --with pytest --with jsonschema --with cryptography -- python -X utf8 -m pytest forge/schema_tests/test_runtime_envelope_report_schema.py -q
uv run --offline --with ruff -- ruff check forge/schema_tests/test_runtime_envelope_report_schema.py --select E9,F
git diff --check
git diff --cached --check
```

Full Python/schema: **1,245 passed** (1,073 forge, 172 schema). Probe Python:
**7 passed**. Focused new schema checks: **9 passed**. Generated consumer and
static checks pass. Android: **754 app, 80 module, 114 standard probe and 114
diagnostics probe tests passed**, zero failures/errors. The 20 new Kotlin methods
run on both APIs 34/35 (40 added executions); each probe flavor includes five new
report tests. App debug, module package (both ABIs), standard probe and diagnostics
probe builds pass. No compiler warnings were emitted. Git emits only LF-to-CRLF
normalization notices for existing working-copy settings; whitespace checks pass.

After narrowing the SIM count check to the existing schema's 1–2 slots, the
affected runtime tests and packaging were rerun successfully with:

```powershell
& "$env:TEMP/cloak-build/gradle-8.11.1/bin/gradle.bat" -p android :module:testDebugUnitTest :app:assembleDebug :module:packageModule :probe:testStandardDebugUnitTest :probe:testDiagnosticsDebugUnitTest --offline --no-daemon --console=plain
```

This also confirms the filesystem restat regression guard: a failed new path
validation cannot retain the old private-path scope. Block-alignment, SIM parity,
real camera/sensor capability ceilings and unbound callback controls pass.

Initial schema collection caught a missing new schema `$id`; adding its local
registry identifier fixed collection and the full Python/schema suite passed.
No tests were removed or bypassed.

Logs: `%TEMP%/cloak-envelope-verified.log`,
`%TEMP%/cloak-envelope-final-bounds.log`, `%TEMP%/cloak-envelope-python.log`,
`%TEMP%/cloak-envelope-probe-python.log`.
Local builds retain existing release metadata; they are development artifacts,
not a newly published alpha or evidence for the signed alpha.27 binary.

#### Remaining verification

Kotlin tests use two explicitly synthetic fixtures and repeat reconstructed cold
snapshots/unbound controls on Robolectric APIs 34/35. They test actual callback
bodies, not ART installation, physical cold starts or cross-user behavior.
Physical two-fixture, repeated-cold, unbound-restoration and isolation evidence is
**UNVERIFIED for every new surface**. It requires a separate matching-module
device pass with source-supported facts, permission/API limits, production audits
and cleanup proof. Do not invent catalog facts to make those captures pass.
The existing legacy A/B probe fixtures are schema 0.3 and opt out of new Reality
callbacks; their old evidence cannot exercise or verify this expansion.

Missing data in an active 0.4 envelope can now produce an explicit unavailable
exception; callers must handle it. Out-of-scope reads remain host-owned. Hook
availability varies by Android/provider implementation. No claims about platform
certification, attestation, DRM provisioning or physical hardware operation.

---

## 7. Service surface isolation v2 — complete source handoff

Source: [HANDOFF-2026-10-20-SERVICE-SURFACE-ISOLATION-V2.md](HANDOFF-2026-10-20-SERVICE-SURFACE-ISOLATION-V2.md)

### Service surface isolation v2 — handoff

Date: 2026-10-20. Branch: main. Base: bed5535.
Implementation is uncommitted; no commit, push, tag or release was requested.
Earlier uncommitted cohort, instance-realism and runtime-envelope work, plus
unrelated handoffs/evidence, were preserved.

#### Result

Added a pure binding-scoped service isolation evaluator with ISOLATED,
SHARED_CONFIRMED, CONFLICT, STALE, UNKNOWN and NEEDS_REVIEW. It checks current
assignment/inventory, allowed and forbidden packages, exact-group sharing consent,
freshness and profile/app/user/artifact binding. Consent and its inventory are
read under one existing store lock. Sticky review flags cannot clear themselves.

Existing explicit diagnostics import saves only measured package observations
for enabled apps bound to the selected identity. Its confirmation stamp covers
that scope. The existing readiness card displays per-app states and reasons and
gates its overall verdict. Another identity's evidence cannot be borrowed.
Passing isolation never supplies missing Play evidence or a platform verdict.

#### Changed files in this sprint

New:

- `android/app/src/main/java/dev/cyclone/cloak/play/ServiceSurfaceIsolation.kt`
- `android/app/src/main/java/dev/cyclone/cloak/play/ServiceSurfaceStore.kt`
- `android/app/src/test/java/dev/cyclone/cloak/play/ServiceSurfaceIsolationTest.kt`
- `android/app/src/test/java/dev/cyclone/cloak/play/ServiceSurfaceStoreTest.kt`
- `docs/SERVICE-SURFACE-ISOLATION-V2.md`
- this handoff

Updated:

- `android/app/src/main/java/dev/cyclone/cloak/play/GoogleSurfaceStore.kt`
- `android/app/src/main/java/dev/cyclone/cloak/play/PlayEvidenceImport.kt`
- `android/app/src/main/java/dev/cyclone/cloak/ui/CloakViewModel.kt`
- `android/app/src/main/java/dev/cyclone/cloak/ui/PlayReadinessCard.kt`
- `android/app/src/test/java/dev/cyclone/cloak/play/PlayEvidenceImportTest.kt`
- `android/app/src/test/java/dev/cyclone/cloak/ui/PlayReadinessCardTest.kt`
- `docs/ARCHITECTURE.md`
- `docs/CURRENT-STATE.md`

#### Verification

Final counts: **781 app**, **80 module**, **114 standard probe**, **114 diagnostics
probe**, **1,245 forge/schema** tests passed; zero failures/errors. App total
includes **27 new tests**. Debug APK build succeeded. No compiler warnings.
Whitespace checks passed; Git only reported pre-existing LF-to-CRLF notices for
DeviceProfileEngine.kt, module/build.gradle and ProbeReportPolicy.java.

Exact commands, run from `C:/Users/Agent/Cyclone/cloak-repo`:

```powershell
$env:JAVA_HOME='C:/Program Files/Eclipse Adoptium/jdk-17.0.20.101-hotspot'
$env:ANDROID_HOME='C:/Users/Agent/android-sdk'
$env:CLOAK_TEST_WSL='1'

# Focused run: 54 tests passed before the final two UI/binding safeguards.
& "$env:TEMP/cloak-build/gradle-8.11.1/bin/gradle.bat" -p android :app:testDebugUnitTest --tests '*ServiceSurface*' --tests '*PlayEvidenceImportTest' --tests '*PlayReadinessCardTest' --offline --no-daemon --console=plain

# Full Android regression gate and debug build.
& "$env:TEMP/cloak-build/gradle-8.11.1/bin/gradle.bat" -p android :app:testDebugUnitTest :module:testDebugUnitTest :probe:testStandardDebugUnitTest :probe:testDiagnosticsDebugUnitTest :app:assembleDebug --offline --no-daemon --console=plain

uv run --offline --with pytest --with jsonschema --with cryptography -- python -X utf8 -m pytest forge/tests forge/schema_tests -q

# Final affected-target rerun after the different-identity guard and recovery test.
& "$env:TEMP/cloak-build/gradle-8.11.1/bin/gradle.bat" -p android :app:testDebugUnitTest :module:testDebugUnitTest :app:assembleDebug --offline --no-daemon --console=plain

git diff --check
git diff --cached --check
```

Build/test console logs: `%TEMP%/cloak-service-focused.log`,
`%TEMP%/cloak-service-android.log`, `%TEMP%/cloak-service-python.log`,
`%TEMP%/cloak-service-final.log`. An initial focused compilation found a test-only
Robolectric helper invocation error; it was corrected before the successful runs.

#### Storage and limits

Bounded format-1 binding sidecars use additional hashed `binding:` keys in the
existing private Google surface preferences. Existing assignment library format,
profile files and binding storage are unchanged. Corrupt records are hidden and
preserved; writes refuse to overwrite them. Other app records remain readable;
restoring a valid record restores evaluation. Details and public APIs are in
[SERVICE-SURFACE-ISOLATION-V2.md](SERVICE-SURFACE-ISOLATION-V2.md).

The existing diagnostics harness does not prove account-slot ownership, backup
policy or private-data isolation. Imports therefore leave these facts absent and
cannot alone reach ISOLATED or READY. A future authorized provider must supply
those observations; no facts are fabricated. Local metadata is not Android
enforcement, platform certification or a service verdict. Separate evidence saves
can partially succeed; the UI reports this and retains the current readiness gate.

No device actions, account operations, permission changes, runtime/module edits,
connector or publication changes were made in this sprint. No release or tag.
