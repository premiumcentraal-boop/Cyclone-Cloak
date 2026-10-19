# Google Surface Isolation v1 — 2026-10-19

Branch: `codex/google-surface-isolation-v1`.
Base: `5c70727b15d44c6e06a1d8de45a960ac741847df` (released alpha.26 contract).
No release metadata changes, device actions or release publication.

## Implemented

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

## Changed files

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

## Verification commands

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

## Deliberate limits

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
