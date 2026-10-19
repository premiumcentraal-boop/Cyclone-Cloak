# Play/Google Readiness Integration v1 — 2026-10-19

Branch: `codex/play-readiness-integration-v1`. Released foundation remains
`v0.11.0-alpha.26` / code 50, main base `5c70727`.

## Delivered

- Reviewed and committed only the existing separate diagnostics harness as
  `947693d` (`feat(diagnostics): add user-initiated Play evidence harness`).
  Inherited isolation/evaluation commits are `287f8c1` and `fa7a2fd`.
- Production document import validates the complete shared strict envelope,
  matching projection, exact file hash, Android user, current artifact set and
  freshness. Preview and explicit save reuse the existing readiness store.
- Profiles now shows local Play/Google readiness, provenance, package/account
  states, stale/mismatch reasons, identity selection and import/review controls.
- Shared slot conflicts list affected profiles and require a second explicit
  confirmation bound to the current assignment inventory. No account writes.
- Existing profiles/binding files remain untouched; tests compare real binding
  store bytes before/after import. Missing required evidence stays UNKNOWN.

## Changed files

Harness commit: `android/settings.gradle`; `android/playEvidence/**` (build,
manifest, resources, collector/activity, strict report and tests);
`schema/play-evidence-report.schema.json`; `tools/run_play_evidence.py`;
`forge/tests/test_play_evidence_harness.py` and its synthetic shared report;
`docs/PLAY-EVIDENCE-HARNESS-V1.md`, harness handoff, architecture/current-state.

Integration:

- `android/app/build.gradle` — copy only the pure strict envelope parser.
- `android/app/src/main/java/dev/cyclone/cloak/play/PlayEvidenceImport.kt` —
  import context/preview, revalidation, artifact hashing and existing-store facade.
- `play/GoogleSurfaceStore.kt`, `play/PlayReadinessStore.kt` — read-only validated
  evidence/consent accessors, readable-store precheck.
- `ui/CloakViewModel.kt`, `ui/ProfilesScreen.kt`, `ui/PlayReadinessCard.kt` —
  local selection, import/review, provenance/status and shared-slot confirmation.
- `android/app/src/test/java/dev/cyclone/cloak/ArchitectureTest.kt` — explicitly
  checks the Play layer's allowed dependencies.
- `play/PlayEvidenceImportTest.kt`, `ui/PlayReadinessCardTest.kt` — focused
  repository/preservation/rejection and Compose state/confirmation tests.
- `docs/ARCHITECTURE.md`, `docs/CURRENT-STATE.md`,
  `docs/PLAY-EVIDENCE-HARNESS-V1.md`,
  `docs/PLAY-READINESS-INTEGRATION-V1.md`, this handoff.

Paths prefixed only with `play/` or `ui/` above are relative to the app's
`src/main/java/dev/cyclone/cloak/` or corresponding `src/test/java/` directory.
Unrelated old handoffs and evidence directories were not staged.

## Public entry points and storage

`PlayEvidenceImport.projection(text, context)`; `PlayEvidenceRepository.target`,
`preview`, `save`, `card`; `PlayEvidenceArtifacts.combine/current`.

The report binds exact saved-file bytes. Existing assignment records keep their
canonical JSON hash. Artifact digest is SHA-256 over the documented APK/bundled
module ZIP digest manifest, not proof of the installed root module. See the
[integration contract](PLAY-READINESS-INTEGRATION-V1.md) for the exact text.

Existing private preferences `play-readiness-evaluation` and
`google-surface-isolation` keep their formats. No profile/binding/publication
layout change. The full envelope is validated in memory; only its consistent
readiness projection goes into existing readiness storage. Each store commits
atomically, but they are not a combined transaction. A readiness write failure
after approved account metadata explicitly reports the partial save.

## Exact local commands

From `C:/Users/Agent/Cyclone/cloak-repo`:

```powershell
$env:JAVA_HOME='C:/Program Files/Eclipse Adoptium/jdk-17.0.20.101-hotspot'
$env:CLOAK_TEST_WSL='1'
& "$env:TEMP/cloak-build/gradle-8.11.1/bin/gradle.bat" -p android :app:testDebugUnitTest --tests dev.cyclone.cloak.play.PlayEvidenceImportTest --tests dev.cyclone.cloak.ui.PlayReadinessCardTest :playEvidence:testDebugUnitTest --offline --no-daemon --console=plain
& "$env:TEMP/cloak-build/gradle-8.11.1/bin/gradle.bat" -p android :app:testDebugUnitTest :playEvidence:testDebugUnitTest :module:testDebugUnitTest :probe:testStandardDebugUnitTest :probe:testDiagnosticsDebugUnitTest :app:assembleDebug :app:assembleRelease :playEvidence:assembleDebug :playEvidence:assembleRelease :module:packageModule :probe:assembleStandardDebug :probe:assembleDiagnosticsDebug --offline --no-daemon --console=plain
& "$env:TEMP/cloak-build/gradle-8.11.1/bin/gradle.bat" -p android :app:lintDebug :playEvidence:lintDebug :module:lintDebug :probe:lintStandardDebug :probe:lintDiagnosticsDebug --continue --offline --no-daemon --console=plain
# Sequential rebuild after lint regenerated the embedded module artifact:
& "$env:TEMP/cloak-build/gradle-8.11.1/bin/gradle.bat" -p android :app:assembleDebug :app:assembleRelease --offline --no-daemon --console=plain
# Final app changes: retain historical provenance when current context is unavailable.
& "$env:TEMP/cloak-build/gradle-8.11.1/bin/gradle.bat" -p android :app:testDebugUnitTest :playEvidence:testDebugUnitTest :module:testDebugUnitTest :probe:testStandardDebugUnitTest :probe:testDiagnosticsDebugUnitTest :app:assembleDebug :app:assembleRelease :app:lintDebug --offline --no-daemon --console=plain
uv run --offline --with pytest --with jsonschema -- python -X utf8 -m pytest forge/tests forge/schema_tests -q
uv run --offline -- python -X utf8 -m unittest discover -s android/probe/tests -v
uv run --offline -- python -X utf8 tools/generate_play_surface.py --check
uv run --offline --with ruff -- ruff check tools/run_play_evidence.py forge/tests/test_play_evidence_harness.py
uv run --offline --with ruff -- ruff format --check tools/run_play_evidence.py forge/tests/test_play_evidence_harness.py
uv run --offline -- python -X utf8 tools/verify_release.py --tag v0.11.0-alpha.26 --artifacts
& C:/Users/Agent/android-sdk/build-tools/35.0.0/apksigner.bat verify android/playEvidence/build/outputs/apk/debug/playEvidence-debug.apk
& C:/Users/Agent/android-sdk/build-tools/35.0.0/aapt.exe dump permissions android/playEvidence/build/outputs/apk/debug/playEvidence-debug.apk
Get-FileHash android/playEvidence/build/outputs/apk/debug/playEvidence-debug.apk -Algorithm SHA256
git diff --check
git diff --cached --check
& C:/Users/Agent/android-sdk/platform-tools/adb.exe devices -l
```

The first broad Android
run found the architecture guard missing the Play UI dependency; it was fixed
by explicitly checking the new layer, without weakening existing layer checks.

The initial APK/module comparison failed because overlapping local Gradle builds
regenerated the module ZIP while packaging assets. A subsequent sequential build
passed the unchanged artifact verifier. No verifier or module source was changed.

### Final results

| Gate | Result |
| --- | --- |
| Production app | 692 tests passed (28 new import/UI tests) |
| Play evidence app | 20 tests passed |
| Module | 40 tests passed |
| Standard probe | 109 tests passed |
| Diagnostics probe | 109 tests passed |
| Forge + schema | 1,038 passed (907 forge, 131 schema) |
| Probe Python runner | 7 tests passed |
| Total | **2,015 passed**, no failed/skipped tests |
| Builds | App debug/release, evidence debug/release, both probe flavors, module ZIP passed |
| Artifact/metadata verifier | Passed for unchanged alpha.26/code 50; embedded module ZIP matches exactly |
| Contract generation, Ruff, diff checks | Passed |
| Compiler warnings | None in the final build log |
| Full lint | Failed only in unchanged legacy probes; see below |
| Device evidence / signed release | Pending / not run |

The final app/module/probe test and app-build/lint invocation completed successfully
in 3m19s, followed by the passing artifact verifier. Complete task logs remain at
`%TEMP%/cloak-play-integration-verification-final.log`; broader build and lint logs
are named `cloak-play-integration-full-final.log` and
`cloak-play-integration-lint.log`. No integration tag or release was created.

### Lint gate blocker

The full lint command exits 1. Production app: 0 errors / 19 advisory warnings
in inherited code. New Play evidence APK and module: no issues. Both legacy probe
flavors: **9 errors / 12 warnings each**. The failing source files are byte-unchanged
against `5c70727` (`git diff 5c70727 -- android/probe` is empty):

| File and lines | Error |
| --- | --- |
| `ProbeActivity.java:368,370,371` | `MissingPermission`, privileged IMEI reader |
| `ProbeActivity.java:421` | `MissingPermission`, Bluetooth identity reader |
| `ProbeActivity.java:424`, `RealityProbe.java:111` | `MissingPermission`, Bluetooth name reader |
| `ProbeActivity.java:375` | `NewApi`, active modem count requires API 30, min is 29 |
| `ProbeActivity.java:407` (two findings) | `NewApi`, Wi-Fi builder/`build()` requires API 30 |

No permission was added, lint suppressed, baseline created or probe/runtime code
changed to hide these findings. Full reports remain under each module's
`build/reports/lint-results-*.txt`; logs are in `%TEMP%/cloak-play-integration-*.log`.

The diagnostics debug APK is
`android/playEvidence/build/outputs/apk/debug/playEvidence-debug.apk`, SHA-256
`e6d49fd4dc7682d5117a930822ca26ae8cab100f11d53064b526160bfbbb2567`.
Debug signature verification passes; packaged permissions output contains only
the package name (zero permissions). Local production/diagnostics release APKs
are unsigned build outputs; signed CI validation has not been claimed.

## Device and release boundary

ADB returned an empty attached-device list. No installation, account request,
permission change, capture, production import on a phone or device evidence bundle
occurred. The device portion is **PENDING**. No release/tag/version bump or signed
release CI is run without the required real-device evidence. The probe lint
failures independently block the requested all-green release gate.

Next: attach an unlocked device; install only the separate diagnostics APK;
collect by explicit tap for the selected profile/user and current artifact hash;
preserve the fresh evidence bundle; exercise the validated production import;
then rerun the release gate before any signed prerelease. Production UI has only
local Compose/repository evidence so far.

Play Protect and actual Google settings access remain unknown. Optional Integrity
uses the public API after separate confirmation; token receipt metadata is not a
decoded verdict. No credentials are stored. Claims are limited to evidence-bound
local readiness for the supplied artifact and measured caller state, never
certification, broader compatibility or permanent trust.
