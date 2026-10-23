# Combined Play/Google compatibility handoff

Date: 2026-10-19. Repository: `C:/Users/Agent/Cyclone/cloak-repo`.
Current branch: `codex/play-evidence-harness-v1`.

This single document combines all five Play/Google handoffs, including the
published contract release, isolation/state layer, readiness evaluation and
separate evidence harness. All source handoff bodies are included below with
only their heading levels adjusted. Original documents remain unchanged.

## Current status at consolidation

| Layer | Status | Commit / release |
| --- | --- | --- |
| Play Surface Contract v1 | Published in alpha.26 | Feature `7d2c6a2`; release `ca285b8` |
| Alpha.26 signed release verification | Recorded | `5c70727` |
| Google Surface Isolation v1 | Committed locally, inherited by current branch | `287f8c1` |
| Play Readiness Evaluation v1 | Committed locally, current HEAD | `fa7a2fd` |
| Play/Google Evidence Harness v1 | Implemented and tested; uncommitted | Current working tree |

Published prerelease:
[**v0.11.0-alpha.26 / code 50**](https://github.com/premiumcentraal-boop/Cyclone-Cloak/releases/tag/v0.11.0-alpha.26).
The later isolation, readiness and harness work is not part of that published
release. No later release, device installation or physical Play validation is
claimed by this combined handoff.

The sections below preserve historical per-run statements. For example, early
statements that no collector or persistence exists describe that phase only;
later phases add separate state stores and the diagnostics collector. Likewise,
only the contract phase was contract-only: the isolation and readiness phases
add production-app local state code without runtime enforcement changes.

## Combined result

- An additive optional `play_surface` contract, shared by Kotlin and Python,
  preserves existing schema 0.2/0.3/0.4 profiles.
- Separate private per-profile assignment and readiness records preserve existing
  profile files, binding stores, engine/posture records and publication layout.
- Account-slot sharing requires explicit, current-inventory-bound approval.
  Package scope is configuration/review metadata, not Android access enforcement.
- Conservative readiness evaluation rejects foreign, stale or malformed evidence;
  missing required observations remain UNKNOWN. Default providers return UNKNOWN.
- A separate diagnostics APK collects 16 user-initiated, caller-visible checks.
  It declares zero permissions and exports strict redacted JSON/ZIP evidence.
- Optional public Integrity requests require an explicit confirmation. Only real
  encrypted-token receipt metadata is retained; no raw token or decoded verdict
  is exported. Play Protect and actual settings access remain unmeasured.
- No module/runtime hooks, connector protocol, binding/publication behavior or
  production permissions changed. The contract release changed packaging/version
  metadata; subsequent development phases did not.
- No platform verdict, attestation, certification or broader device compatibility
  is claimed. Physical collection and live Integrity project configuration remain
  **UNVERIFIED**.

## Latest local verification snapshot

These results are from the evidence-harness implementation run, not a new test
run performed while writing this documentation. Earlier totals below are
historical snapshots of overlapping suites and must not be added together.

| Suite | Passed | Failed |
| --- | ---: | ---: |
| Evidence APK | 20 | 0 |
| Production app | 664 | 0 |
| Module | 40 | 0 |
| Standard probe | 109 | 0 |
| Diagnostics probe | 109 | 0 |
| Forge | 907 | 0 |
| Schema | 131 | 0 |
| Probe host tooling | 7 | 0 |
| **Total** | **1,987** | **0** |

Production and evidence debug builds passed. Final evidence lint had no issues;
no compiler warnings were reported in the final successful gates. APK signature,
zero-permission manifest, Python lint/format and whitespace checks passed.

Diagnostics APK:
`android/playEvidence/build/outputs/apk/debug/playEvidence-debug.apk`.
Size: **4,131,970 bytes**. SHA-256:
`e6d49fd4dc7682d5117a930822ca26ae8cab100f11d53064b526160bfbbb2567`.

Runner: `tools/run_play_evidence.py`. It requires the diagnostics APK already
installed in the selected unlocked user; it never installs, grants permissions,
switches users or changes production bindings. Output is a fresh timestamped
`docs/runtime-evidence/` directory containing the redacted report, readiness
projection, provenance, cleanup proof, compact summary and ZIP with SHA-256.

## Remaining work and boundaries

1. Review/commit the uncommitted evidence-harness files before any later release
   preparation. Do not include unrelated untracked handoffs or older evidence.
2. Perform a separately authorized diagnostics device run to establish actual
   package/account visibility, OEM settings behavior and optional API availability.
3. Keep Play Protect and actual Google settings access unknown until supported
   evidence exists. This harness cannot make the required-evidence evaluator READY
   while those observations remain unknown.
4. Any future decoded Integrity verification needs the documented backend trust
   boundary. Current receipt digests authenticate neither providers nor verdicts.
5. Profile hash is the selected file's exact byte hash for the harness; the
   artifact-set digest is operator-supplied. Use the same conventions in the
   evaluation context. The runner separately records its diagnostics APK digest.
6. No report is automatically imported into production readiness storage, and no
   account setup/sharing approval UI or Android service-isolation mechanism is
   claimed. Cross-user private-store inventories are not automatically inspected.

## Included handoffs

1. [Play Surface Contract v1](#phase-1) — source: [HANDOFF-2026-10-19-PLAY-SURFACE-CONTRACT-V1.md](HANDOFF-2026-10-19-PLAY-SURFACE-CONTRACT-V1.md)
2. [Alpha.26 release and signed artifact verification](#phase-2) — source: [HANDOFF-2026-10-19-ALPHA26-PLAY-SURFACE-RELEASE.md](HANDOFF-2026-10-19-ALPHA26-PLAY-SURFACE-RELEASE.md)
3. [Google Surface Isolation v1](#phase-3) — source: [HANDOFF-2026-10-19-GOOGLE-SURFACE-ISOLATION-V1.md](HANDOFF-2026-10-19-GOOGLE-SURFACE-ISOLATION-V1.md)
4. [Play Readiness Evaluation v1](#phase-4) — source: [HANDOFF-2026-10-19-PLAY-READINESS-EVALUATION-V1.md](HANDOFF-2026-10-19-PLAY-READINESS-EVALUATION-V1.md)
5. [Play/Google Evidence Harness v1](#phase-5) — source: [HANDOFF-2026-10-19-PLAY-EVIDENCE-HARNESS-V1.md](HANDOFF-2026-10-19-PLAY-EVIDENCE-HARNESS-V1.md)

---

<a id="phase-1"></a>

## Play Surface Contract v1 — handoff

Branch: `codex/play-surface-contract-v1`. Base: `843920e` (alpha.25 publication
verification receipts after release commit `119df83`). No new release or metadata
changes are part of this contract sprint.

### Delivered

Optional `play_surface` on existing schema 0.2/0.3/0.4 profiles describes account
mode, stable local slot, display hint, explicit sharing consent, provisioning,
package scope, review time and Play/service expectations. Strict Kotlin/Python
parsers share the source schema, generated Kotlin rules, vectors and messages.
Existing profile validators check only a present section; absent sections and
existing generators remain unchanged.

Separate format-1 local readiness records carry READY/DEGRADED/BLOCKED/UNKNOWN,
reason codes, profile ID/hash, artifact/evidence hashes, timestamp and provider.
Missing complete evidence cannot be encoded as READY. Binding checks only retain
or downgrade supplied state, preserve original report bindings, and never infer
a platform verdict. Account-slot reuse conflicts unless every active assignment
explicitly permits sharing. Package/service comparisons distinguish unknown
evidence from known conflicts, including partial observations.

### Changed files

- `schema/play-surface.schema.json`, `schema/play-readiness.schema.json`: versioned
  local contracts; additive reference in `schema/cloak-profile.schema.json`.
- `forge/cloak_forge/play_surface.py`: validation, strict parsing/encoding,
  fixed findings, slot conflicts, expectation comparisons and evidence binding.
- `forge/cloak_forge/validate.py`: validate a present optional section.
- `android/app/src/main/java/dev/cyclone/cloak/forge/PlaySurfaceContract.kt`:
  equivalent pure Kotlin API; `ProfileValidator.kt`: additive validation.
- `PlaySurfaceRules.kt` in the same package: generated from the shared schema by
  `tools/generate_play_surface.py`, with `--check` drift detection.
- `forge/tests/vectors/play-surface.json`: synthetic shared vectors and constants.
- `forge/tests/test_play_surface.py`,
  `forge/schema_tests/test_play_surface_schema.py`,
  `android/app/src/test/java/dev/cyclone/cloak/forge/PlaySurfaceContractTest.kt`.
- `docs/PLAY-SURFACE-CONTRACT-V1.md`, architecture section, and this handoff.

Public signatures and complete field vocabulary are in the
[contract guide](PLAY-SURFACE-CONTRACT-V1.md). No new storage record is written.

### Checks

Final gate: **1,880 tests passed**, zero failures/errors/skips: app **608**,
module **40**, each probe variant **109**, forge **876**, schema **131**,
host probe **7**. Debug APK assembly passed. No compiler warnings. Ruff and
generated-rule/diff checks passed. Git emitted only existing LF-to-CRLF working
copy notices when staging; these are not compiler warnings.

Exact final commands (PowerShell, repository root):

```powershell
$env:JAVA_HOME='C:/Program Files/Eclipse Adoptium/jdk-17.0.20.101-hotspot'
$env:ANDROID_HOME='C:/Users/Agent/android-sdk'
$env:CLOAK_TEST_WSL='1'
& "$env:TEMP/cloak-build/gradle-8.11.1/bin/gradle.bat" -p android :app:testDebugUnitTest :module:testDebugUnitTest :probe:testStandardDebugUnitTest :probe:testDiagnosticsDebugUnitTest :app:assembleDebug --offline --no-daemon --console=plain
uv run --offline --with pytest -- python -X utf8 -m pytest forge/tests -q
uv run --offline --with pytest --with jsonschema -- python -X utf8 -m pytest forge/schema_tests -q
uv run --offline -- python -X utf8 -m unittest discover -s android/probe/tests -v
uv run --offline -- python tools/generate_play_surface.py --check
uv run --offline --with ruff -- ruff check forge/cloak_forge/play_surface.py forge/tests/test_play_surface.py forge/schema_tests/test_play_surface_schema.py tools/generate_play_surface.py
uv run --offline --with ruff -- ruff format --check forge/cloak_forge/play_surface.py forge/tests/test_play_surface.py forge/schema_tests/test_play_surface_schema.py tools/generate_play_surface.py
git diff --check
git diff --cached --check
```

Focused gate also ran `:app:testDebugUnitTest --tests
dev.cyclone.cloak.forge.PlaySurfaceContractTest`, the new forge test file, and the
new schema test file before the complete gate. An additional partial-observation
regression was added during review; the affected full forge/Android gates were
rerun afterward. Date-time checking in schema tests uses a standard-library
calendar validator, avoiding optional format-checker dependencies.

### Limits and follow-up

This is a data/validation foundation, not service isolation or account setup.
No provider collection, observation acquisition, readiness decision engine,
persistence or UI is added. Hash binding compares supplied exact-byte digests;
it does not authenticate providers or verify signatures. Free-text hints must
remain labels; a parser cannot identify credentials disguised as a label.
No networking, new dependencies/permissions, device actions, runtime/module,
publication, connector, bindings, release metadata or existing storage changes.
No platform verdict is claimed. Unrelated untracked handoffs/evidence remain
untouched. Future work can collect local observations through a separately
authorized provider and consume these records without changing their meaning.

---

<a id="phase-2"></a>

## Alpha.26 Play Surface Contract release handoff

Published prerelease: https://github.com/premiumcentraal-boop/Cyclone-Cloak/releases/tag/v0.11.0-alpha.26

Release commit: `ca285b8c98bc5fc83a00229006f16c950c91a680`; feature commit: `7d2c6a26fedae39e79e6bcb56a8acb64c1a46e4d`. App/module `0.11.0-alpha.26` / code `50`.

### Changes

Play Surface Contract v1 adds an optional profile section, shared Kotlin/Python schemas and validation, explicit account-slot/package conflicts, and separate hash-bound local readiness records. Missing or mismatched evidence remains UNKNOWN. Existing profiles remain compatible without migration. This is a contract foundation, not account setup, service enforcement or a platform verdict.

Implementation files, public APIs and limitations: [implementation handoff](HANDOFF-2026-10-19-PLAY-SURFACE-CONTRACT-V1.md) and [contract guide](PLAY-SURFACE-CONTRACT-V1.md). Release changes: app/module packaging metadata, README, architecture/current-state headers, release notes and workflow attachment paths only. Runtime, connector, publication, binding storage and permissions are unchanged.

### Verification

1,880 local tests passed, zero failures/errors/skips: app 608; module 40; each probe flavor 109; forge 876; schema 131; host probe 7. Debug/release assembly, probe builds and module packaging passed. Lint/format, generated-rule, release metadata and diff checks passed. No compiler warnings. Local release output is unsigned; existing CI secrets sign the validated public build.

- Main CI: https://github.com/premiumcentraal-boop/Cyclone-Cloak/actions/runs/39232277001
- Signed validation: https://github.com/premiumcentraal-boop/Cyclone-Cloak/actions/runs/39232287345
- Release CI: https://github.com/premiumcentraal-boop/Cyclone-Cloak/actions/runs/39233055932

Existing release certificate SHA-256: `17ca3617b0cbaf250d2ef03e945b319e57695de6d977c652ded243ad5656f553`. Signature verification passed. Versions/codes and tag match; APK embeds the exact published module ZIP. Published sizes/SHA-256 match GitHub metadata. Supporting assets match the tag bytes. Signed-validation and release payload comparison passed. Module native libraries match the alpha.25 signed artifact.

### Assets

| Asset | Bytes | SHA-256 |
| --- | ---: | --- |
| app-release.apk | 43806231 | `c27c7a3c1a95617f323f8b9539dc2c56e24dab907db39cc6ab1e380ec7848707` |
| cyclone-cloak-0.11.0-alpha.26.zip | 1683427 | `a2e932b9bf0bbefa6022d590fc68f6606ebdf3cd36b41d63d6c13ebf0e38382f` |
| HANDOFF-2026-10-19-PLAY-SURFACE-CONTRACT-V1.md | 5245 | `6a653e2cbfa52a43e4c8772f417163f0fb8b1db05ee0c886f12fef577d21c8d0` |
| play-readiness.schema.json | 272 | `d134d2336b91921efe3fa204f7bc1bfe21bbd0a6289d6066bdb529764990935a` |
| PLAY-SURFACE-CONTRACT-V1.md | 6562 | `8f351e6736c1862d9ef9d9b805764f48d564a07dd29d83d99c152498d4446c52` |
| play-surface.schema.json | 11005 | `9474c58d6d922c349f38f9b567b42136c013fcaeac95675521762485100c588d` |

Receipts: [public asset checks](release-verification/v0.11.0-alpha.26/published-assets-verification.json), [signing and packaging](release-verification/v0.11.0-alpha.26/release-artifact-verification.json), [checksums](release-verification/v0.11.0-alpha.26/SHA256SUMS).

### Exact local commands

```powershell
$env:JAVA_HOME='C:/Program Files/Eclipse Adoptium/jdk-17.0.20.101-hotspot'
$env:ANDROID_HOME='C:/Users/Agent/android-sdk'
$env:CLOAK_TEST_WSL='1'
& "$env:TEMP/cloak-build/gradle-8.11.1/bin/gradle.bat" -p android :app:testDebugUnitTest :module:testDebugUnitTest :probe:testStandardDebugUnitTest :probe:testDiagnosticsDebugUnitTest :app:assembleDebug :app:assembleRelease :module:packageModule :probe:assembleStandardDebug :probe:assembleDiagnosticsDebug --offline --no-daemon --console=plain
uv run --offline --with pytest -- python -X utf8 -m pytest forge/tests -q
uv run --offline --with pytest --with jsonschema -- python -X utf8 -m pytest forge/schema_tests -q
uv run --offline -- python -X utf8 -m unittest discover -s android/probe/tests -v
uv run --offline -- python -X utf8 tools/verify_release.py --tag v0.11.0-alpha.26 --artifacts
git diff --check
git diff --cached --check
uv run --offline -- python tools/generate_play_surface.py --check
uv run --offline --with ruff -- ruff check forge/cloak_forge/play_surface.py forge/tests/test_play_surface.py forge/schema_tests/test_play_surface_schema.py tools/generate_play_surface.py
uv run --offline --with ruff -- ruff format --check forge/cloak_forge/play_surface.py forge/tests/test_play_surface.py forge/schema_tests/test_play_surface_schema.py tools/generate_play_surface.py
```

Published assets were downloaded from the tag release; Android build-tools 35.0.0 `apksigner verify --verbose --print-certs` verified signing and `aapt dump badging` checked app versions. Python zipfile/hashlib checked embedded module, source attachments, archive payloads and digests. These checks are recorded in the receipts.

### Limitations

No device actions or new physical verification. No platform verdict. No account provisioning, service isolation, observation acquisition, persistence or UI is added. Hash binding compares supplied exact-byte digests; provider authentication and report-signature verification remain future work. Existing physical evidence remains scoped to the measured artifact. Unrelated handoffs and old evidence were not staged or changed.

---

<a id="phase-3"></a>

## Google Surface Isolation v1 — 2026-10-19

Branch: `codex/google-surface-isolation-v1`.
Base: `5c70727b15d44c6e06a1d8de45a960ac741847df` (released alpha.26 contract).
No release metadata changes, device actions or release publication.

### Implemented

- Immutable per-Cyclone-profile records bound to Android user and canonical saved
  profile SHA-256, with validated Play Surface account/service expectations.
- App-private, bounded, atomic assignment storage, separate from all existing
  profile/binding/posture/engine/publication stores. Corrupt libraries stay intact
  and block new assignments instead of silently losing reuse protection.
- Explicit digest-bound shared-slot review. A saved shared flag cannot substitute
  for confirmation; stale reviews and new peers are rejected before writing.
- Surface/control package metadata with required/optional/forbidden policies and
  declared package state/version comparisons.
- Pure six-area local isolation evaluation, stable reasons and readable messages:
  READY, NEEDS_ACCOUNT, NEEDS_PACKAGE_REVIEW, SHARED_ACCOUNT_CONFIRMED, CONFLICT,
  UNKNOWN. Missing/foreign/malformed evidence never produces success.

### Changed files

- `android/app/src/main/java/dev/cyclone/cloak/play/GoogleSurfaceIsolation.kt`
- `android/app/src/main/java/dev/cyclone/cloak/play/GoogleSurfaceCodec.kt`
- `android/app/src/main/java/dev/cyclone/cloak/play/GoogleSurfaceStore.kt`
- `android/app/src/test/java/dev/cyclone/cloak/play/GoogleSurfaceIsolationTest.kt`
- `android/app/src/test/java/dev/cyclone/cloak/play/GoogleSurfaceStoreTest.kt`
- `docs/GOOGLE-SURFACE-ISOLATION-V1.md`
- `docs/ARCHITECTURE.md`
- `docs/CURRENT-STATE.md`
- This handoff.

See [record format, API entry points, confirmation sequence and limits](GOOGLE-SURFACE-ISOLATION-V1.md).
Kotlin reuses the released Play Surface validation contract; its schema, generated
rules and Python behavior are unchanged. No profile migration is introduced.

### Verification commands

From `C:/Users/Agent/Cyclone/cloak-repo`:

```powershell
$env:JAVA_HOME='C:/Program Files/Eclipse Adoptium/jdk-17.0.20.101-hotspot'
$env:CLOAK_TEST_WSL='1'
& "$env:TEMP/cloak-build/gradle-8.11.1/bin/gradle.bat" -p android :app:testDebugUnitTest --tests 'dev.cyclone.cloak.play.*' --offline --no-daemon --console=plain
& "$env:TEMP/cloak-build/gradle-8.11.1/bin/gradle.bat" -p android :app:testDebugUnitTest :module:testDebugUnitTest :probe:testStandardDebugUnitTest :probe:testDiagnosticsDebugUnitTest :app:assembleDebug --offline --no-daemon --console=plain
uv run --offline --with pytest -- python -X utf8 -m pytest forge/tests -q
uv run --offline --with pytest --with jsonschema -- python -X utf8 -m pytest forge/schema_tests -q
uv run --offline -- python -X utf8 -m unittest discover -s android/probe/tests -v
git diff --check
```

| Suite | Passed |
| --- | ---: |
| App | 642 |
| Module | 40 |
| Standard probe | 109 |
| Diagnostics probe | 109 |
| Forge | 876 |
| Schema | 131 |
| Host probe tooling | 7 |
| **Total** | **1,914** |

The 34 new app tests comprise 24 pure model/codec tests and 10 Robolectric
store/integration tests. Unchanged module/probe Gradle tasks use valid up-to-date
results. Debug build succeeds, with no compiler warnings. Output:
`android/app/build/outputs/apk/debug/app-debug.apk`. Whitespace checks pass.

### Deliberate limits

- This run provides the isolation/state layer, not Android service isolation or
  package access enforcement. No new UI is wired; future callers must display
  `review()` and obtain explicit approval before invoking shared confirmation.
- Observations are supplied by a trusted local caller, not collected or persisted
  automatically. No account names, credentials or tokens are supported. Source
  and checked time describe the observation; automatic freshness policy is future
  work. No Google/Play/platform verdict is inferred.
- Slot reuse detection is scoped to the local supplied inventory. Other Android
  users' private stores are neither inspected nor represented as verified.
- Private sandbox separation is modeled in tests; physical verification remains
  UNVERIFIED. The store supports the existing single-process app architecture,
  not simultaneous writers in hypothetical additional app processes.
- Malformed inventory recovery requires explicit repair/restoration; no automatic
  reset discards assignments or consent history.
- Unrelated pre-existing handoffs and evidence directories remain untouched.

---

<a id="phase-4"></a>

## Play Readiness Evaluation v1 — 2026-10-19

Branch: `codex/play-readiness-evaluation-v1`.
The prior isolation work was preserved as local commit `287f8c1` on its branch
before creating this branch. No push, release, device action or metadata bump.

### Delivered

- Strict bounded format-1 local report parser for package/service/account state,
  profile/user/artifact bindings, timestamps, coded warnings/failures and optional
  supplied Integrity response digest/time/provider metadata. Unknown fields,
  credentials/tokens, malformed data and incomplete structure are rejected.
- Pure four-state local readiness evaluation, fixed readable `PR_*` findings and
  readiness output validated by the released Play Surface Contract v1 schema.
- Profile/hash/user/artifact mismatch and stale/future reports yield UNKNOWN and
  NEEDS_REVIEW. Default freshness is 24 hours; caller can configure the window.
- Required unknown evidence never becomes READY, even with an optional response.
  Permission and package limitations remain explicit. Integrity availability and
  response metadata are observations, never pass checks or platform verdicts.
- Default UNKNOWN provider, supplied-report provider and shared provider interface.
- Separate atomic app-private per-profile storage for exact report bytes,
  shared-contract snapshots and persistent review flags. Reads recompute readiness
  and validate evidence/snapshot binding. A stored READY cannot promote unknown
  evidence. Stale/changed bindings and explicit review remain flagged until the
  user explicitly reviews a current matching report. No automatic reset occurs.

### Changed files

- `android/app/src/main/java/dev/cyclone/cloak/play/PlayReadinessReport.kt`
- `android/app/src/main/java/dev/cyclone/cloak/play/PlayReadinessEvaluation.kt`
- `android/app/src/main/java/dev/cyclone/cloak/play/PlayReadinessStore.kt`
- `android/app/src/test/java/dev/cyclone/cloak/play/PlayReadinessEvaluationTest.kt`
- `android/app/src/test/java/dev/cyclone/cloak/play/PlayReadinessStoreTest.kt`
- `docs/PLAY-READINESS-EVALUATION-V1.md`
- `docs/ARCHITECTURE.md`
- `docs/CURRENT-STATE.md`
- This handoff.

[Detailed report format, decision rules and persistence contract](PLAY-READINESS-EVALUATION-V1.md).

Public APIs: `PlayReadinessReport.parse`, `PlayReadinessProvider.evaluate`,
`DefaultPlayReadinessProvider`, `LocalPlayReadinessProvider`, `PlayReadinessContext`,
`PlayReadinessResult`, and `PlayReadinessStore.read/save/requestReview/clear`.
The store uses MODE_PRIVATE preferences `play-readiness-evaluation`, keyed by
Cyclone profile ID; records contain `format`, `profile_id`, `report`, `readiness`
and `review_requested`. Report input is capped at 64 KiB, stored envelope at 128 KiB.

### Exact verification commands

Run from `C:/Users/Agent/Cyclone/cloak-repo`:

```powershell
$env:JAVA_HOME='C:/Program Files/Eclipse Adoptium/jdk-17.0.20.101-hotspot'
$env:CLOAK_TEST_WSL='1'
& "$env:TEMP/cloak-build/gradle-8.11.1/bin/gradle.bat" -p android :app:testDebugUnitTest --tests 'dev.cyclone.cloak.play.PlayReadiness*' --offline --no-daemon --console=plain
& "$env:TEMP/cloak-build/gradle-8.11.1/bin/gradle.bat" -p android :app:testDebugUnitTest :module:testDebugUnitTest :probe:testStandardDebugUnitTest :probe:testDiagnosticsDebugUnitTest :app:assembleDebug --offline --no-daemon --console=plain
uv run --offline --with pytest -- python -X utf8 -m pytest forge/tests -q
uv run --offline --with pytest --with jsonschema -- python -X utf8 -m pytest forge/schema_tests -q
uv run --offline -- python -X utf8 -m unittest discover -s android/probe/tests -v
git diff --check
git diff --cached --check
```

| Suite | Passed |
| --- | ---: |
| App | 664 |
| Module | 40 |
| Standard probe | 109 |
| Diagnostics probe | 109 |
| Forge | 876 |
| Schema | 131 |
| Host probe tooling | 7 |
| **Total** | **1,936** |

22 new app tests: 14 pure evaluator/parser tests and 8 Robolectric storage tests.
Coverage includes all state transitions, missing/optional evidence, malformed
reports, profile/user/artifact mismatch, freshness boundaries, persistent review,
exact-byte evidence digests, corrupt storage and no snapshot-based promotion.
Unchanged module/probe Gradle targets retain valid up-to-date results.
Debug build succeeds at `android/app/build/outputs/apk/debug/app-debug.apk`.
No compiler warnings. Tracked and new-file whitespace checks pass.

### Limits and preserved behavior

- READY describes local readiness observations only, never platform acceptance.
  Caller-supplied response metadata is not authenticated by this layer. No raw
  tokens, signed response bodies, accounts or credentials are retained.
- No new report collector or UI is wired. Providers perform no Google/Integrity
  requests or installed-package/artifact inspection. Current binding hashes and
  artifact-set provenance must come from a trusted local caller.
- Required report structure must be complete; explicit unknown observations
  remain valid UNKNOWN evidence. Rejected imports preserve older stored evidence.
- The store supports the existing single-process private-storage pattern, not
  hypothetical extra process writers. Corrupt records are hidden and preserved;
  explicit sidecar clear is available, with no profile/account reset.
- Profile files/schema, Google isolation assignments, engine/posture metadata,
  bindings, connector, publication, permissions and module/runtime are unchanged.
- Physical collection and real platform-response provenance remain UNVERIFIED.
- Unrelated handoffs and older evidence were left untouched.

---

<a id="phase-5"></a>

## Play/Google Evidence Harness v1 handoff

Date: 2026-10-19. Branch: `codex/play-evidence-harness-v1`.
Implementation is local and uncommitted for review. No push, tag, release,
installation, permission change or device collection was performed in this run.

### Delivered

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

### Changed files

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

### Results

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

### Exact successful commands

Run from `C:/Users/Agent/Cyclone/cloak-repo`:

```powershell
$env:JAVA_HOME='C:/Program Files/Eclipse Adoptium/jdk-17.0.20.101-hotspot'
$env:CLOAK_TEST_WSL='1'
& "$env:TEMP/cloak-build/gradle-8.11.1/bin/gradle.bat" -p android :playEvidence:testDebugUnitTest :playEvidence:assembleDebug :playEvidence:lintDebug :app:testDebugUnitTest :module:testDebugUnitTest :probe:testStandardDebugUnitTest :probe:testDiagnosticsDebugUnitTest :app:assembleDebug --offline --no-daemon --console=plain

## Repeat the changed evidence target after final parser/resources/lint cleanup:
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

### Remaining limits / next steps

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

