# Reality Studio and Fleet Diversity — 2026-10-18

## Scope and release

Alpha.20 was already complete: signed-validation 39085714493 and release
39121224929 succeeded at f070cd7. This sprint targets alpha.21/code 45 through
the same existing signer and signed-validation-before-tag lane.

Studio and repair are local draft operations inside identity details, with
schema-driven fields, precise rejection reasons, private-value masking,
editor-session locks, a complete changed-path preview and explicit confirmation.
Repair fills missing supported fields only; unavailable/unknown hardware is not
fabricated. Compatible templates require exact manufacturer/model/device/hardware,
SDK and fingerprint. Regeneration restores the existing catalog/seed derivations.
No new physical combinations are invented.

Fleet analysis returns paths, labels, counts and recommendations, not raw
identifiers. Six local characteristics contribute to pairwise diversity; missing
facts earn no credit, and no comparable pairs has no score. Clustering and scoring
use grouped hashes rather than repeatedly hashing every pair.

Batch planning uses balanced rounds over distinct selected reality clusters and
keeps per-template counts, deterministic seeds and uniqueness retries. Format 1
adds optional request.diversity_policy (balanced/legacy). Missing policy means
legacy. Imported summaries cannot authorize a save.

## Safety and limitations

No connector, binding, publication, permissions, profile schema, storage layout,
runtime or module behavior changed. Module metadata only. The confirmed Studio
save calls the existing atomic profile writer directly, not saveIdentity, and
checks the original hash, complete validation and current uniqueness index.
Existing publication is not automatically refreshed. Hash-bound engine metadata
and local evidence can become stale after an edit. No platform verdict.

Locks and drafts are editor-local, not persisted. Older schema 0.2/0.3 profiles
stay readable and read-only here. User-set fields are marked using the existing
needs-verification source and reason envelope; no new profile format was added.
Fixed hardware cannot be diversified by reseeding. Missing catalog facts cannot
be repaired. Fleet analysis includes readable profiles only, not unreadable files.
Physical UI/SAF behavior is UNVERIFIED; no phone tests were performed. Historical
release evidence remains historical and does not verify this UI.

## Checks

App 566, module 12, probe standard 96, probe diagnostics 96, forge 558,
schema 60, probe host 7: **1,395 tests passed**. Android failures/errors/skips: zero. Debug/release
APK, both probe variants and module packaging passed; release metadata and exact
embedded-module check passed. No compiler warnings. Local release APK is unsigned;
the unchanged CI lane signs using the existing release key. Signed-CI and
published-asset results are included in the completion report.
The first full gate caught forbidden forge-to-storage hash imports; these were
removed in favor of a pure canonical helper, without weakening ArchitectureTest.
The scalar-dialog test also caught an intrinsic sizing loop; explicit dialog
width and bounded content fixed it, and the same focused test passed without
changing idle policies or removing assertions.
Focused editor/planner/Compose/store tests cover valid and invalid edits,
contradiction rejection, preservation locks, missing repair, unavailable facts,
stale/confirmed saves, redaction, clustering, score, anti-repetition and v1 plans.
The atomic-store integration test also checks the existing body hash agreement
and unchanged binding/publication evidence files.

Exact commands (PowerShell, repository root):

```powershell
$env:JAVA_HOME='C:/Program Files/Eclipse Adoptium/jdk-17.0.20.101-hotspot'
$env:CLOAK_TEST_WSL='1'
& "$env:TEMP/cloak-build/gradle-8.11.1/bin/gradle.bat" -p android :app:testDebugUnitTest --tests '*RealityStudioCardTest.contradictoryScalarShowsExactReasonWithoutSave' --offline --no-daemon --console=plain
& "$env:TEMP/cloak-build/gradle-8.11.1/bin/gradle.bat" -p android :app:testDebugUnitTest --tests '*RealityStudio*' --tests '*FleetDiversityTest' --tests '*BatchIdentityPlannerTest' --tests '*BatchIdentityWorkshopTest' --offline --no-daemon --console=plain
& "$env:TEMP/cloak-build/gradle-8.11.1/bin/gradle.bat" -p android :app:testDebugUnitTest :module:testDebugUnitTest :probe:testStandardDebugUnitTest :probe:testDiagnosticsDebugUnitTest :app:assembleDebug :app:assembleRelease :module:packageModule :probe:assembleStandardDebug :probe:assembleDiagnosticsDebug --offline --no-daemon --console=plain
uv run --with pytest -- python -X utf8 -m pytest forge/tests -q
uv run --with pytest --with jsonschema -- python -X utf8 -m pytest forge/schema_tests -q
uv run -- python -X utf8 -m unittest discover -s android/probe/tests -v
uv run -- python -X utf8 tools/generate_reality_rules.py --check
uv run -- python -X utf8 tools/verify_release.py --tag v0.11.0-alpha.21 --artifacts
git diff --check
git diff --cached --check
```

## Public local APIs

- RealityStudio.Session(profile): body, locked, lock(path, locked), edit(path, json),
  repair(template), regenerate(section, template), preview().
- RealityStudio.save(preview, current, library, confirmed, persist): validation and
  optimistic local replacement, with no binding/publication capability.
- FleetDiversity.analyze(profiles), clusterKey(profile), order(templates).
- BatchIdentityPlanner.Request gains trailing default diversityPolicy = balanced;
  legacy decoding remains compatible. Existing plan version and seed derivations
  are unchanged.

## Changed files

- README.md
- android/app/build.gradle
- android/module/module.prop (version/code only)
- android/app/src/main/java/dev/cyclone/cloak/forge/RealityStudio.kt
- android/app/src/main/java/dev/cyclone/cloak/forge/FleetDiversity.kt
- android/app/src/main/java/dev/cyclone/cloak/forge/BatchIdentityPlanner.kt
- android/app/src/main/java/dev/cyclone/cloak/ui/RealityStudioCard.kt
- android/app/src/main/java/dev/cyclone/cloak/ui/IdentitiesScreen.kt
- android/app/src/main/java/dev/cyclone/cloak/ui/BatchIdentityWorkshop.kt
- android/app/src/main/java/dev/cyclone/cloak/ui/CloakViewModel.kt
- android/app/src/test/java/dev/cyclone/cloak/forge/RealityStudioTest.kt
- android/app/src/test/java/dev/cyclone/cloak/forge/FleetDiversityTest.kt
- android/app/src/test/java/dev/cyclone/cloak/ui/RealityStudioCardTest.kt
- android/app/src/test/java/dev/cyclone/cloak/ui/BatchIdentityWorkshopTest.kt
- android/app/src/test/java/dev/cyclone/cloak/data/RealityStudioStorageTest.kt
- docs/REALITY-STUDIO.md
- docs/ARCHITECTURE.md
- docs/CURRENT-STATE.md
- docs/RELEASE_0.11.0-alpha.21.md
- docs/HANDOFF-2026-10-18-REALITY-STUDIO.md

Older untracked handoffs and evidence were preserved and excluded from commits.
