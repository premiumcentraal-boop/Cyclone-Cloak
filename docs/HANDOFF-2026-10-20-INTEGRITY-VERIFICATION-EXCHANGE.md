# Integrity verification exchange v1 — 2026-10-20

## Git and boundaries

The previous authenticated verdict bridge was committed separately on `main` as
`4ce82d16c1612a2b9f8a33b471e6aa1014b8b3f5`
(`feat(play): add authenticated evidence-bound Integrity verdict bridge`).
Unrelated untracked handoffs, evidence, screenshots and archives were excluded.
The new exchange changes remain uncommitted for review. No push, release, tag,
device action, permission/account change or module modification was performed.

## Delivered

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

## Changed files

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

## Verification

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

## Owner setup and later phone test

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
