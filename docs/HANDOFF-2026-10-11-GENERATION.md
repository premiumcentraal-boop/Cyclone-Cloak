# Profile generation completion handoff — 2026-10-11

## Summary and baseline

Read `docs/AGENT-BRIEF.md` first, then the required orientation documents and
`AGENTS.md`. Fetched tags and confirmed local `main` (`a240456`) contains published
prerelease `v0.10.0-rc.1`. Preserved the preceding uncommitted coherence-engine
work and the user's untracked brief/mission files.

Added a Phones-tab template selector and a single **Generate profile** action.
The first available catalog phone is selected by default; built-in and custom
phone templates use the same existing forge. The app chooses an unused default
name, allocates one cryptographic seed, generates the full existing forge payload,
validates it and atomically saves it using `CloakStore.save` / `JsonDirectory`.
Blocking errors show findings without calling the writer. Warning/info findings
permit saving and remain available in the success state.

Generation is serialized with the existing ViewModel work mutex. Running state is
set before launching work to prevent repeated taps from enqueuing duplicate jobs.
Missing templates, rejected validation, generation failures, write failures and
successful saves have explicit states. Selecting a different template or dismissing
a result clears it. Successful profiles appear in the existing Identities tab.
Existing ID/name collisions are rejected; existing identity records are not rewritten.

## Files changed in this run

| File | Change |
| --- | --- |
| `android/app/src/main/java/dev/cyclone/cloak/forge/CloakForge.kt` | Extract reusable secure `newSeed`; preserve existing derivation and generation behavior |
| `android/app/src/main/java/dev/cyclone/cloak/ui/ProfileGeneration.kt` | Local forge/validate/save orchestration, default names and typed outcomes |
| `android/app/src/main/java/dev/cyclone/cloak/ui/ProfileGenerationCard.kt` | Template selection, one-click action and visible results |
| `android/app/src/main/java/dev/cyclone/cloak/ui/PhonesScreen.kt` | Add generation card and stable test tags |
| `android/app/src/main/java/dev/cyclone/cloak/ui/CloakViewModel.kt` | Serialized action, generation state and dismissal |
| `android/app/src/test/java/dev/cyclone/cloak/ui/ProfileGenerationTest.kt` | Ten JVM tests including real atomic-directory saves/read-back |
| `android/app/src/test/java/dev/cyclone/cloak/ui/ProfileGenerationCardScreenTest.kt` | Four Compose tests for selection, busy/empty state and result feedback |
| `android/app/src/test/java/dev/cyclone/cloak/ui/ScreensTest.kt` | Assert new action; retain custom-phone assertion with scrolling and unambiguous card selector |
| `README.md` | Explain the Phones generation flow |
| `docs/ARCHITECTURE.md` | Record generation orchestration and existing storage boundary |
| `docs/HANDOFF-2026-10-11-GENERATION.md` | This completion report |

No validator/Python/schema/storage implementation changes were made in this run.
The previously modified validator files remain the preceding run's change set.
No connector, binding, synchronization, runtime module or existing identity record
was changed. No networking, new tab, migration or release metadata change was added.

## Tests and exact commands

Working directory: `C:/Users/Agent/Cyclone/cloak-repo`.

### Full app tests — dependency blocker

```powershell
$env:JAVA_HOME='C:/Program Files/Eclipse Adoptium/jdk-17.0.20.101-hotspot'
& "$env:TEMP/cloak-build/gradle-8.11.1/bin/gradle.bat" -p android :app:testDebugUnitTest --offline --no-daemon
```

Blocked before test execution at `:app:copyRobolectricRuntime`, after 43 seconds:
`android-all-instrumented-14-robolectric-10818077-i7.jar` is not cached for offline
mode. No tests were removed or bypassed.

### Direct JVM tests — passed

```powershell
& "$env:TEMP/run-cloak-generation-tests.ps1"
```

**60 tests passed**: 50 existing/coherence forge tests plus 10 new generation tests.
The temporary runner compiles current production forge sources, `ProfileGeneration`,
`JsonDirectory`, all forge tests and `ProfileGenerationTest` with the installed
Kotlin compiler. It runs JUnit with cached real JSON/JUnit/Hamcrest libraries and
the shared catalog/vector resources. It does not run Android/Compose screens.

New tests cover:

1. Complete payload save/read-back, seed allocation exactly once, validation before
   writer invocation, no temporary files remaining and unchanged template data.
2. Invalid release/API coherence prevents every save and retains existing file bytes.
3. Same seed, name and template produce identical JSON in independent stores.
4. Different seeds produce different IDs and every derived identifier, retaining
   the same template device values.
5. All seven catalog entries generate clean profiles and save successfully.
6. Warnings are saved and returned for display.
7. Duplicate IDs cannot replace existing file bytes.
8. Case-insensitive automatic name suffixes retain existing identities.
9. Seed and real filesystem write failures produce explicit safe failure states.
10. Fresh random seeds have the expected format and reconstruct the stored profile.

### Python regression suite — passed

```powershell
uv run --with pytest -- python -m pytest forge/tests -q
```

**176 passed**. Python derivation and shared rules remain unchanged in this run.

### Build plus screen-test compilation attempt

```powershell
$env:JAVA_HOME='C:/Program Files/Eclipse Adoptium/jdk-17.0.20.101-hotspot'
& "$env:TEMP/cloak-build/gradle-8.11.1/bin/gradle.bat" -p android :app:assembleDebug :module:packageModule :app:compileDebugUnitTestKotlin --offline --no-daemon
```

The combined invocation failed at `:app:compileDebugUnitTestKotlin` after 37 seconds:
Robolectric 4.14.1 and Compose `ui-test-junit4` 1.7.0 (and related test dependencies)
are not cached. Production Kotlin compiled, but the test compilation and screen
execution cannot be claimed as verified. Then ran the build separately:

```powershell
$env:JAVA_HOME='C:/Program Files/Eclipse Adoptium/jdk-17.0.20.101-hotspot'
& "$env:TEMP/cloak-build/gradle-8.11.1/bin/gradle.bat" -p android :app:assembleDebug :module:packageModule --offline --no-daemon
```

The first separate build passed in 24 seconds (74 tasks, 12 executed). After adding
stable Phones/card test tags and updating the existing screen assertion, reran the
same build command: **BUILD SUCCESSFUL in 37 seconds**, 74 tasks
(15 executed, 59 up-to-date). No compiler/build warnings were emitted.

Outputs: `android/app/build/outputs/apk/debug/app-debug.apk` and
`android/module/build/module/cyclone-cloak-0.10.0-rc.1.zip`.

### Whitespace and docs

```powershell
git -c core.safecrlf=false diff --check
```

Passed. Local documentation links were also checked.

## Assumptions and known limits

- “Profile” here is the existing stored identity, not a new phone template, binding
  or Cyclone profile. Optional live observations remain the existing forge's
  unknown/null defaults; no device result or integrity verdict is fabricated.
- Deterministic reproduction requires the same seed, name, template values and
  forge version. New button clicks receive new random seeds; UI recomposition does
  not regenerate any stored identity. Names receive suffixes to avoid collisions.
- The flow uses the existing coherence engine, not a new JSON Schema library.
  A pre-existing contract discrepancy remains: the schema file specifies UUIDv4
  for profile IDs and strings for integrity `checked_at`/`keybox_tag`, while both
  existing forges use deterministic UUIDv5 profile IDs and null unknown integrity
  values. This run preserves those existing forge/storage semantics; it does not
  claim strict JSON Schema validation or silently migrate old identities.
- Four new generation-screen tests and the updated Phones screen assertion await
  the missing Robolectric/Compose dependencies. JVM logic success is not UI or
  physical-device proof.
- No phone install/reboot/runtime test was performed. Existing deferred device
  checks remain in `DEFERRED-DEVICE-CHECKS.md`.
- Release metadata changed: **No** (app/module remain RC/22).
- No release/tag publication or GitHub push was performed. Changes remain local.
