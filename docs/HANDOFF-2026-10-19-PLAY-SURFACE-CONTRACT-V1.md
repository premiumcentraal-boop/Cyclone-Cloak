# Play Surface Contract v1 — handoff

Branch: `codex/play-surface-contract-v1`. Base: `843920e` (alpha.25 publication
verification receipts after release commit `119df83`). No new release or metadata
changes are part of this contract sprint.

## Delivered

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

## Changed files

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

## Checks

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

## Limits and follow-up

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
