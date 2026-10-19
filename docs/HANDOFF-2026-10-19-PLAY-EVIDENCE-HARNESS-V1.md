# Play/Google Evidence Harness v1 handoff

Date: 2026-10-19. Branch: `codex/play-evidence-harness-v1`.
Implementation is local and uncommitted for review. No push, tag, release,
installation, permission change or device collection was performed in this run.

## Delivered

- Standalone `dev.cyclone.cloak.playevidence` APK, with no production app/module
  dependency and no declared permissions/services. Package visibility is scoped
  to six named Google/Play/WebView packages and the Google settings action.
- Explicit local-collection button plus separately confirmed public Standard
  Integrity request. Sixteen complete, strict, redacted check rows with stable
  PASS/FAIL/OBSERVED/BLOCKED/MISSING/NOT_RUN states and reason codes.
- Current-capture-only atomic private report writing, bounded inputs/reports and
  a 20-report retention limit. Backup/device-transfer exclusions cover the
  diagnostics app's own private evidence.
- Strict checked-in report schema, Kotlin semantic parser and Python schema/
  semantic validator. A shared synthetic fixture passes both readers. Optional
  package/account observations cannot silently become availability verdicts.
- `tools/run_play_evidence.py`: user-tap-driven ADB collection or offline report
  validation/export, fresh timestamped ZIPs, SHA-256, private scratch cleanup,
  profile/user/artifact mismatch rejection and fixed-code failure evidence.

The bundle contains `report.json`, `readiness-report.json`, `provenance.json`,
`cleanup.json` and `summary.md`. Device runs additionally retain
`capture-attempt.json` beside the ZIP. See [usage and limits](PLAY-EVIDENCE-HARNESS-V1.md).

## Changed files

- `android/settings.gradle`: include `:playEvidence`.
- `android/playEvidence/build.gradle`: separate APK and test dependencies; copies
  only the existing pure `StrictJson` and `PlayReadinessReport` sources at build.
- `android/playEvidence/src/main/AndroidManifest.xml`.
- `android/playEvidence/src/main/java/dev/cyclone/cloak/playevidence/`:
  `EvidenceActivity.kt`, `EvidenceReport.kt`, `SurfaceCollector.kt`,
  `AndroidSurfaceReader.kt`, `IntegrityEvidence.kt`.
- `android/playEvidence/src/main/res/values/strings.xml` and
  `android/playEvidence/src/main/res/xml/no_evidence_backup.xml`.
- `android/playEvidence/src/test/java/dev/cyclone/cloak/playevidence/EvidenceReportTest.kt`.
- `schema/play-evidence-report.schema.json`.
- `tools/run_play_evidence.py`.
- `forge/tests/test_play_evidence_harness.py` and
  `forge/tests/fixtures/play_evidence_report.json` (synthetic, not device evidence).
- `docs/PLAY-EVIDENCE-HARNESS-V1.md`, `docs/ARCHITECTURE.md`,
  `docs/CURRENT-STATE.md` and this handoff.

No production source, runtime/module source, connector, binding/publication path,
profile schema, profile storage layout or release metadata was changed.
Unrelated existing untracked handoffs/evidence were preserved.

## Results

| Suite | Passed | Failed |
| --- | ---: | ---: |
| New evidence APK unit tests | 20 | 0 |
| Production app | 664 | 0 |
| Module | 40 | 0 |
| Standard probe | 109 | 0 |
| Diagnostics probe | 109 | 0 |
| Forge, including 31 new harness tests | 907 | 0 |
| Schema | 131 | 0 |
| Probe host unittest | 7 | 0 |
| **Total** | **1,987** | **0** |

Production and evidence debug builds passed. Final evidence lint: **No issues
found**. No compiler warnings in the successful final gates. Python lint/format
and Git whitespace checks passed. Git emits its existing LF→CRLF advisory for
`android/settings.gradle`; this is not a compiler warning or whitespace failure.
Some unchanged Android targets were correctly UP-TO-DATE in the full gate.

APK: `android/playEvidence/build/outputs/apk/debug/playEvidence-debug.apk`.
Size: **4,131,970 bytes**. SHA-256:

```text
e6d49fd4dc7682d5117a930822ca26ae8cab100f11d53064b526160bfbbb2567
```

Debug APK signature verification passed. `aapt dump permissions` lists only
`package: dev.cyclone.cloak.playevidence`, with zero requested permissions.
Merged manifest also contains zero permissions and zero services.

## Exact successful commands

Run from `C:/Users/Agent/Cyclone/cloak-repo`:

```powershell
$env:JAVA_HOME='C:/Program Files/Eclipse Adoptium/jdk-17.0.20.101-hotspot'
$env:CLOAK_TEST_WSL='1'
& "$env:TEMP/cloak-build/gradle-8.11.1/bin/gradle.bat" -p android :playEvidence:testDebugUnitTest :playEvidence:assembleDebug :playEvidence:lintDebug :app:testDebugUnitTest :module:testDebugUnitTest :probe:testStandardDebugUnitTest :probe:testDiagnosticsDebugUnitTest :app:assembleDebug --offline --no-daemon --console=plain

# Repeat the changed evidence target after final parser/resources/lint cleanup:
& "$env:TEMP/cloak-build/gradle-8.11.1/bin/gradle.bat" -p android :playEvidence:testDebugUnitTest :playEvidence:assembleDebug :playEvidence:lintDebug --offline --no-daemon --console=plain

uv run --offline --with pytest --with jsonschema -- python -X utf8 -m pytest forge/tests/test_play_evidence_harness.py -q
uv run --offline --with pytest --with jsonschema -- python -X utf8 -m pytest forge/tests -q
uv run --offline --with pytest --with jsonschema -- python -X utf8 -m pytest forge/schema_tests -q
uv run --offline -- python -X utf8 -m unittest discover -s android/probe/tests -v
uv run --offline --with ruff -- ruff check tools/run_play_evidence.py forge/tests/test_play_evidence_harness.py
uv run --offline --with ruff -- ruff format --check tools/run_play_evidence.py forge/tests/test_play_evidence_harness.py

& C:/Users/Agent/android-sdk/build-tools/35.0.0/apksigner.bat verify android/playEvidence/build/outputs/apk/debug/playEvidence-debug.apk
& C:/Users/Agent/android-sdk/build-tools/35.0.0/aapt.exe dump permissions android/playEvidence/build/outputs/apk/debug/playEvidence-debug.apk
Get-FileHash android/playEvidence/build/outputs/apk/debug/playEvidence-debug.apk -Algorithm SHA256
git diff --check
git diff --cached --check
```

Earlier exploratory checks found and fixed a disabled-WebView classification
issue and use of a host-only JSON comparison API. Final gates above are green.

## Remaining limits / next steps

- Physical collection, OEM package/settings visibility and live Integrity project
  configuration are **UNVERIFIED**. No device evidence is claimed.
- Play Protect has no connected supported reader and remains NOT_RUN. Google
  settings resolution is not access proof. Account visibility is a boolean for
  the caller only, not proof of global account absence/ownership/profile assignment.
- Public Integrity returns encrypted token bytes. Only real receipt metadata is
  retained; no token, authenticated decoded verdict or fabricated response is
  saved. Backend verification is outside v1; `integrity.verdict` stays NOT_RUN.
- The selected file's exact byte hash is checked; production binding membership
  is not inspected. Artifact hash is operator-supplied, not attested by this app.
  The runner independently records the installed diagnostics APK digest.
- Exported `readiness-report.json` is compatible with the existing local parser,
  but unresolved required evidence stays UNKNOWN. It is never auto-saved into
  production readiness records. No platform verdict or permanent proof is claimed.
- The runner requires the debug diagnostics APK already installed for the
  specified unlocked user. It neither installs it nor grants anything. A separate
  authorized device run can now collect timestamped evidence with the documented
  command and an explicit on-device tap.
