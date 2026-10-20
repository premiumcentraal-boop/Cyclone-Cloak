# Cohort separation v1 handoff — 2026-10-20

Operations kit commit `bed5535e3292545c94fa5607381963e350e3c002` was pushed
to `origin/main` first. No release/tag or device action occurred. Cohort work is
left as intended local changes for review; unrelated untracked handoffs/evidence
were not staged or changed.

## Changed files and public entry points

- `catalog/cohort-separation-v1.json`: shared version-1 category/path/tuple policy.
- `forge/cloak_forge/cohort_separation.py`: `analyze(profiles: list[dict]) -> dict`.
- `android/app/src/main/java/dev/cyclone/cloak/forge/CohortSeparation.kt`:
  `CohortSeparation(policyJson: String).analyze(input: List<JSONObject>): JSONObject`.
- `forge/tests/test_cohort_separation.py`: 24 focused cases.
- `android/app/src/test/java/dev/cyclone/cloak/forge/CohortSeparationTest.kt`:
  eight tests, including full-report Python/Kotlin comparison across 14 libraries.
- `docs/COHORT-SEPARATION-V1.md`: API, privacy, policy/scoring, limits and examples.
- `docs/ARCHITECTURE.md`, `docs/CURRENT-STATE.md`: unreleased additive analysis notes.
- This handoff.

Output includes profile IDs/labels, pairwise/category distance and confidence,
shared membership groups, exact/near identifier duplicates, weakest fields,
missing/unavailable Reality sections, correlated device tuples, regeneration or
completion review recommendations, cohort score and conservative warning codes.
No raw values or value hashes are emitted. Malformed/duplicate-ID libraries fail
closed with a fixed message. Schema 0.2/0.3/0.4 remains unchanged and inputs are
not mutated. The existing FleetDiversity UI/planner is unchanged.

## Verification commands

Run from the repository root, PowerShell:

```powershell
git push origin main
$env:JAVA_HOME='C:/Program Files/Eclipse Adoptium/jdk-17.0.20.101-hotspot'
$env:ANDROID_HOME='C:/Users/Agent/android-sdk'
$env:CLOAK_TEST_WSL='1'
& "$env:TEMP/cloak-build/gradle-8.11.1/bin/gradle.bat" -p android :app:testDebugUnitTest --tests '*CohortSeparationTest' --offline --no-daemon --console=plain
& "$env:TEMP/cloak-build/gradle-8.11.1/bin/gradle.bat" -p android :app:testDebugUnitTest :module:testDebugUnitTest :app:assembleDebug --offline --no-daemon --console=plain
uv run --offline --with pytest --with jsonschema --with cryptography -- python -X utf8 -m pytest forge/tests/test_cohort_separation.py -q
uv run --offline --with pytest --with jsonschema --with cryptography -- python -X utf8 -m pytest forge/tests forge/schema_tests -q
uv run --offline --with ruff -- ruff check forge/cloak_forge/cohort_separation.py forge/tests/test_cohort_separation.py --select E9,F
git diff --check
git diff --cached --check
```

Final results: **746 app tests passed**, including all eight cohort tests and
the complete cross-language comparison. **1,168 forge/schema tests passed**,
including all 24 new Python cases. Module task passed up-to-date, retaining its
unchanged **40-test** successful result. No failures or skips. Debug APK built
successfully; no compiler warnings were emitted. Focused Python static checks and
both Git whitespace checks passed. The Unicode-label parity case caught a Windows
test-subprocess encoding issue; explicit UTF-8 fixed it and the full Android gate
was rerun successfully.

Final logs: `%TEMP%/cloak-cohort-android.log` and
`%TEMP%/cloak-cohort-python.log`. The debug APK is at
`android/app/build/outputs/apk/debug/app-debug.apk`. No release build, workflow,
tag, device capture, installation or runtime change was performed.

## Limits and next steps

This is pure analysis, not a UI/export feature. A caller supplies the decoded saved
profile list and, on Kotlin, the trusted bundled policy asset. There are no storage
callbacks or mutation hooks. The 200-profile cap bounds pairwise work; reports may
be large near that cap. Unknown/unavailable facts earn no distance. Fewer than two
profiles have no meaningful score (null, confidence zero). Near-duplicate detection
is a conservative equal-length, one-substitution heuristic, not exhaustive.

The score is a documented description-separation heuristic, not statistical
independence, enforcement, high-trust assurance, or any platform verdict. Generated
identifiers cannot diversify fixed hardware. Recommendations require separate
user review; unsupported or unavailable hardware must never be invented.
