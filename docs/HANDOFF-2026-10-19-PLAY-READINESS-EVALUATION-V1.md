# Play Readiness Evaluation v1 — 2026-10-19

Branch: `codex/play-readiness-evaluation-v1`.
The prior isolation work was preserved as local commit `287f8c1` on its branch
before creating this branch. No push, release, device action or metadata bump.

## Delivered

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

## Changed files

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

## Exact verification commands

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

## Limits and preserved behavior

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
