# Verification operations kit — 2026-10-20

Exchange commit: `1637e272f793498a7297336a32d065c099ea2113`, pushed to
`origin/main` before operations-kit work began. It includes only the intended
17 exchange files; unrelated untracked handoffs/evidence/screenshots/archives were
preserved. The earlier verdict bridge commit was included in the push ancestry.

Operations kit: the separate commit containing this handoff. Its exact SHA is
returned in the final operator report and can be read with
`git log -1 --format=%H -- tools/integrity_backend.py`.

## Delivered and changed files

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

## Checks

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

## Exact remaining owner steps

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
