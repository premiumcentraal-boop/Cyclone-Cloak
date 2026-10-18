# Batch Identity Workshop — 2026-10-18

Release: `v0.11.0-alpha.19`, app/module code `43`.

## Changes

- Pure `forge/BatchIdentityPlanner.kt`: `Request`, `Draft`, `Plan`, `plan`,
  `regenerate`, `review`, `encode`, `decode`, `deriveSeed`; `BatchIdentitySave.save`
  returns one `Item(key, status, reason)` per draft.
- `ui/BatchIdentityWorkshop.kt`: template/count/prefix selection, aggregate review,
  selected regeneration/removal, private details, explicit save confirmation and
  per-item results, local document plan import/export. Entry in Identities;
  `CloakApp`/`CloakViewModel` route the workshop through existing serialized IO work.
- New planner, Compose and storage integration tests. README, architecture,
  current-state and alpha.19 release notes updated; app/module metadata agree.

Profile bodies and the engine sidecar format are unchanged. Existing atomic
additive storage writes one approved draft at a time; it never replaces an
occupied target. Successful saves survive later failures. No binding/reconcile/
publication callback exists in the save helper. Integration tests preserve
binding-file bytes and verify the unchanged generated body and separate sidecar.

Plan format v1 contains engine/schema versions, selected template IDs, counts,
batch seed, label prefix, per-draft ordinal/revision/chosen seed/retry count/label/
template SHA-256, and a descriptive validation summary. Import strictly validates
derivation and template compatibility, regenerates bodies and recomputes findings.
Seeds are private reproduction material; export warns explicitly and omits raw
identifier values and profile bodies. Plans are separate documents, not a profile
storage migration.

## Local verification

537 app tests (15 added), 12 module tests, 91 probe tests per flavor, 503 forge
tests, 33 schema tests and 7 probe host tests passed. No skipped Android tests.
Initial failures in the new code/tests were corrected: planner hashing now stays
in the pure forge layer, and the Android storage test compares canonical hashes
instead of calling an unsupported Android `JSONObject.similar` method.

Exact final Android gate:

```powershell
$env:JAVA_HOME='C:/Program Files/Eclipse Adoptium/jdk-17.0.20.101-hotspot'
$env:CLOAK_TEST_WSL='1'
& "$env:TEMP/cloak-build/gradle-8.11.1/bin/gradle.bat" -p android :app:testDebugUnitTest :module:testDebugUnitTest :probe:testStandardDebugUnitTest :probe:testDiagnosticsDebugUnitTest :app:assembleDebug :app:assembleRelease :module:packageModule :probe:assembleStandardDebug :probe:assembleDiagnosticsDebug --offline --no-daemon --console=plain
uv run --with pytest -- python -X utf8 -m pytest forge/tests -q
uv run --with pytest --with jsonschema -- python -X utf8 -m pytest forge/schema_tests -q
uv run -- python -X utf8 -m unittest discover -s android/probe/tests -v
uv run -- python -X utf8 tools/verify_release.py --tag v0.11.0-alpha.19 --artifacts
git diff --check
git diff --cached --check
```

Debug/release APKs, both probe variants and module packaging are checked locally.
Existing signed-validation CI gates tagging, followed by release CI with the
existing signer. Published APK signature, version/code and embedded module ZIP
are checked again against downloaded assets. No signer, permissions or workflow
changes are part of this sprint.

## Limits

Workshop and SAF flows on a physical device are **UNVERIFIED**; no device actions
were performed. Drafts remain in memory until saved/exported. No existing identity
replacement is offered. The library snapshot contains readable profiles; occupied
unreadable files are still protected by the additive write guard. Local uniqueness
and validation do not prove runtime behavior or any platform verdict. Historical
diagnostics assets attached by the unchanged release workflow remain historical.

Unrelated untracked handoffs and older evidence remain unstaged and unchanged.
