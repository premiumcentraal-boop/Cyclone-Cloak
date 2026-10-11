# Coherence engine completion handoff — 2026-10-11

## Scope and baseline

Read the attached AGENT-BRIEF.md first, then AGENTS.md and the required orientation
files. Work started on local `main`, `a240456`, containing latest published
prerelease tag `v0.10.0-rc.1` (`433779f`). Fetched tags and confirmed GitHub's
release list. Existing untracked `AGENTS.md`, `docs/AGENT-BRIEF.md` and
`docs/MISSION-RUNS.md` were left untouched.

This is a local, focused validation change set. No release, tag or GitHub
publication was performed in this run. Release metadata remains RC/22.
Profile schema 0.2, connector contract, identity storage, synchronization,
publication and runtime module sources are unchanged.

## Implemented

- Retained existing Finding/list APIs and stable reason codes. Added the `info`
  level and checked constructor invariants for severity/code/explanation.
- Added immutable ValidationResult summaries with ordered findings, error/warning/
  info counts and an error flag. Exposed result APIs for Python profiles and Kotlin
  profiles/phone templates, plus summarizers for existing finding lists.
- Sorted all returned findings, including structural-error early returns, by error,
  warning, info, then field, code and message. Null field sorts as empty text.
- Retained manufacturer/model, Android release/API, fingerprint, display,
  locale/timezone and identifier checks. Added tests covering every requested group.
- Tightened carrier/MCC/MNC checks against control characters, trailing newlines and
  non-ASCII operator digits; added an all-zero operator info note.
- Validated the existing opaque network egress hint's type, length (128 Unicode
  code points) and control characters; malformed network blocks remain visible.
- Checked existing bootloader/baseband types and control characters; warned when
  an explicitly unspecified modem accompanies declared cellular service or 5G is
  paired with an older launch API; generic hardware produces an info note.
- Kept blank/absent optional modem values compatible with existing templates.
  Did not infer modem/SIM capability from identifier presence or manufacturer names.
- Updated the editor's severity handling so info appears as `Note` and does not
  count as a warning. Error-only save gating remains unchanged.
- Added hard expectations for valid/warning/error/info results, stable ordering,
  summary immutability/counts, missing optional data and nonmutation. Expanded
  shared validation vectors from 88 to 108 and compared exact messages as well
  as severity, field and code in both languages.

## Changed files

| File | Change |
| --- | --- |
| `android/app/src/main/java/dev/cyclone/cloak/forge/ProfileValidator.kt` | Result model, sorting, info severity, carrier/network/hardware rules |
| `android/app/src/main/java/dev/cyclone/cloak/ui/PhoneEditorScreen.kt` | Correct info-note label and warning count |
| `android/app/src/test/java/dev/cyclone/cloak/forge/ProfileValidatorTest.kt` | Six new JVM tests, including rule-group loop and summary contracts |
| `forge/cloak_forge/validate.py` | Matching Python result model, sorting and rules |
| `forge/cloak_forge/__init__.py` | Export INFO and result/summarizer APIs |
| `forge/tests/test_validation_rules.py` | New parameterized rule and result tests; preserve valid old release pairs with nonblocking modem warning |
| `forge/tools/make_vectors.py` | Add shared coherence cases; explicit UTF-8 output |
| `forge/tests/vectors/validation.json` | Regenerated sorted expectations and 20 new cases |
| `docs/VALIDATION.md` | Result contract, reason-code table, rule scope and current test evidence |
| `docs/HANDOFF-2026-10-11-COHERENCE.md` | This completion report |

## Exact commands run and results

Working directory: `C:/Users/Agent/Cyclone/cloak-repo`.

Regenerated vectors:

```powershell
uv run -- python forge/tools/make_vectors.py
```

Python suite:

```powershell
uv run --with pytest -- python -m pytest forge/tests -q
```

Final result: **176 passed**. Initial run exposed eight failures: optional blank
modems were being warned on existing catalog/runtime fixtures, and two new test
expectations used the mutation field instead of the actual finding field. Corrected
blank-modem handling and expected fields; retained all catalog/fixture assertions.

Full Android unit/screen suite attempt:

```powershell
$env:JAVA_HOME='C:/Program Files/Eclipse Adoptium/jdk-17.0.20.101-hotspot'
& "$env:TEMP/cloak-build/gradle-8.11.1/bin/gradle.bat" -p android :app:testDebugUnitTest --offline --no-daemon
```

**Blocked**, before executing tests, after 38 seconds at `:app:copyRobolectricRuntime`:
`android-all-instrumented-14-robolectric-10818077-i7.jar` is not cached for offline
mode. No tests were removed or bypassed. No claim of full-suite success in this run.

Direct JVM validation fallback:

```powershell
& "$env:TEMP/run-cloak-coherence-tests.ps1"
```

Final result: **50 tests passed**, including all forge test classes and exact
Kotlin/Python parity for **108 shared validation cases**. The temporary runner
compiles current production forge sources and all forge tests using
`org.jetbrains.kotlin.cli.jvm.K2JVMCompiler`, then runs `org.junit.runner.JUnitCore`.
It uses the cached real org.json/JUnit/Hamcrest JARs, existing compiled unchanged
data classes, and catalog/vector resources. Initial runner attempts failed to
compile because its data-package classpath was missing and then Android's stub
JSONObject preceded real org.json; corrected the runner classpath, not tests.
This fallback does not execute Compose/Robolectric screens.

App/module build:

```powershell
$env:JAVA_HOME='C:/Program Files/Eclipse Adoptium/jdk-17.0.20.101-hotspot'
& "$env:TEMP/cloak-build/gradle-8.11.1/bin/gradle.bat" -p android :app:assembleDebug :module:packageModule --offline --no-daemon
```

**BUILD SUCCESSFUL**, 30 seconds, 74 tasks (14 executed, 60 up-to-date).
Outputs: `android/app/build/outputs/apk/debug/app-debug.apk` and
`android/module/build/module/cyclone-cloak-0.10.0-rc.1.zip`.
No compiler/build warnings emitted by the successful build.

Whitespace check:

```powershell
git -c core.safecrlf=false diff --check
```

Passed. The per-command Git setting avoids unrelated Windows line-ending warnings.

## Assumptions and remaining limitations

- The storage schema contains no camera/sensor/hardware feature flags. Hardware
  consistency is therefore limited to existing platform/modem/network/launch-API
  values. Adding a feature schema would violate this sprint's scope.
- OEM relationships, modem provenance, display ranges and old-launch-API/5G pairs
  are uncertainty checks. Warnings and info never reject otherwise valid profiles.
- Locale/timezone checks validate syntax, not membership in a particular platform
  timezone database. Network egress hints are opaque labels, not verified egress.
- Existing catalog entries and runtime probe fixtures remain valid and clean.
  No data migration, identifier reseeding or runtime changes are required.
- Full Android/Compose tests must be rerun when the missing Robolectric dependency
  is available. Prior RC CI success does not establish this new change's screen tests.
- No phone/device/reboot test was run. Previously deferred device checks remain
  recorded in `DEFERRED-DEVICE-CHECKS.md`.
- Some historical sections in STATE_LAYOUT.md and CYCLONE_PROFILES_REPORT.md still
  describe the earlier full-Gradle blocker; CURRENT-STATE.md records that published
  RC CI subsequently passed. This run separately reproduces the local offline
  dependency blocker. Those historical documents were not edited in this sprint.
- Release metadata changed: **No**. No release or tag published in this run.
