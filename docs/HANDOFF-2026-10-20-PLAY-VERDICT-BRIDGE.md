# Play verdict bridge — 2026-10-20

Repository: `C:/Users/Agent/Cyclone/cloak-repo`, `main`, base
`cb60012a121743c8374dda5cf164349dfe3b0b28`. No commit, tag, release or device
action is part of this run. Prior unrelated untracked handoffs/evidence remain
untouched, including the consolidated alpha.27 onward handoff.

## Delivered

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

## Changed files and public entry points

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

## Storage and trust

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

## Verification

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

## What a later physical test can verify

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
