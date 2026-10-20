# Restoration harness follow-up — 2026-10-21

Final result: **RESTORED_WITH_EXPECTED_LIMITATIONS**. The focused cycle and all
three final lifecycle cycles passed. Both users restored cold in every cycle;
no unexplained production, package, permission or AppOps delta remained.
Cleanup passed after every cycle. Zero installations and reboots.

Base main: `1c9ce900930b7c52d3d7130b8ca8a741e183b73a`. Installed candidate retains
alpha.27/code 51 metadata; it is not the published alpha.27 binary. See
`starting-state.json` for exact installed companion, diagnostics APK and module
hashes. No installation or reboot occurred in this follow-up.

## Diagnosis

- **Historical packages.visible (users 0/17):** old raw inventories were not
  retained. Exact old packages/line diffs cannot be reconstructed from SHA-256;
  root cause remains unassigned. No blanket expected-transition policy was added.
  New comparisons retain complete inventories (including special package
  `android`), sorted additions/removals and unified diffs. Successful but invalid
  inventories now fail rather than silently losing values.
- **settings.unrelatedControl (user 17):** historical hashes match the bounded
  accessibility domain values `"0"` then `"1"`. A new strict run reproduced that
  exact change in `lifecycle-3cycles/cycle-002`. Android's own per-user setting
  also changed. Final captures independently read root CLI host values before
  and after the probe. Only matching host state with correct user/request binding
  permits `OBSERVED / EXTERNAL_STATE_HOST_SETTING`; missing/foreign/contradictory
  evidence still fails. It never becomes a profile-backed PASS. The process
  responsible for changing the host setting was not attributed.
- **Production audit:** the old delta was solely the raw serialization of
  `/data/adb/cyclone_cloak/state-v1/index.json`, from `c7d97e4f...` to
  `409d6d53...`. Canonical index, 327 entries, bodies, shares, module and bindings
  matched. Existing temporary-overlay JSON reserialization caused the byte-only
  delta. Old byte sizes were not retained. Final audits record paths/sizes/hashes;
  only raw index serialization is non-gating. Semantic fields and all other
  audited file changes still fail. Product publication and cleanup scripts were
  not changed. Repeated cleanup is tested to preserve concurrent entries.
- **Permission collector:** later dumpsys visibility User blocks overwrote grants.
  Installed-package blocks now remain separate; flags are sorted and malformed or
  duplicate records rejected. Numeric UID AppOps are collected separately and
  matched against Android's multiline UID prefix, preserving package versus UID
  modes. Access times/counters are excluded. Unexpected permission/mode changes
  fail; the two already-approved diagnostics read-record additions remain the
  only allowed additions.
- **Wi-Fi external state:** `complete-values-3cycles/cycle-002` exposed changing
  Wi-Fi SSID/link speed. These rows already have EXTERNAL_STATE policies in both
  checked-in fixtures, but external Factory profiles did not load those policies.
  The harness now evaluates the identical shared rules for non-profile-backed
  Wi-Fi/Bluetooth observations, records their fingerprints, preserves denial and
  malformed states, and never promotes them to PASS. Sensitive names stay hashed.
  This preserved failed attempt was not relabelled.

## Files changed

- `tools/run_probe_reversibility.py`
- `tools/restoration_evidence.py` (new)
- `forge/tests/test_restoration_evidence.py` (new)
- `forge/tests/test_profile_check.py`
- `docs/PROFILE-TEST-HARNESS.md`
- This handoff and the new evidence directory below.

Candidate mode is explicit, retains install-once/hash checks, and leaves the
default pinned alpha.18 workflow available. It uses source-supported schema-0.4
Factory profiles labelled external, checks linked/envelope rows, and latches an
unexpected disconnect. No product source, binding storage, connector, module,
publication, permissions or release metadata changed. Unrelated untracked files
remain untouched/unstaged.

Only non-sensitive package/control values are retained raw; private readings
remain digest-only with explicit raw-value limitations. Unknown/blocked/missing
observations are never promoted to PASS.

## Local verification

- Android: **781 app, 80 module, 30 Play diagnostics, 114 standard probe,
  114 diagnostics probe**, zero failures/errors/skips.
- Full forge/schema: **1,284 passed**. Final focused harness subset: **103 passed**
  (already included in full count).
- Host probe unittest: **7 passed**; generated realism/contract checks and offline
  synthetic-only Integrity backend self-test passed.
- All requested debug builds and module packaging passed. No compiler warnings.
  Git's LF/CRLF advisory is non-failing; whitespace checks pass.

Exact commands from repository root:

```powershell
$env:JAVA_HOME='C:/Program Files/Eclipse Adoptium/jdk-17.0.20.101-hotspot'
$env:ANDROID_HOME='C:/Users/Agent/android-sdk'
$env:CLOAK_TEST_WSL='1'
& "$env:TEMP/cloak-build/gradle-8.11.1/bin/gradle.bat" -p android :app:testDebugUnitTest :module:testDebugUnitTest :playEvidence:testDebugUnitTest :probe:testStandardDebugUnitTest :probe:testDiagnosticsDebugUnitTest :app:assembleDebug :module:packageModule :playEvidence:assembleDebug :probe:assembleStandardDebug :probe:assembleDiagnosticsDebug --offline --no-daemon --console=plain
uv run --offline --with pytest --with jsonschema --with cryptography -- python -X utf8 -m pytest forge/tests forge/schema_tests -q
uv run --offline --with pytest --with jsonschema -- python -X utf8 -m pytest forge/tests/test_restoration_evidence.py forge/tests/test_probe_reversibility.py forge/tests/test_profile_check.py -q
uv run --offline -- python -X utf8 -m unittest discover -s android/probe/tests -v
uv run --offline -- python -X utf8 tools/generate_instance_realism.py --check
uv run --offline -- python -X utf8 tools/generate_runtime_contract.py --check
uv run --offline --with cryptography -- python tools/integrity_backend.py --self-test
git diff --check
git diff --cached --check
```

## Device evidence

Directory: `docs/runtime-evidence/2026-10-20-235457-restoration-follow-up/`.

Authoritative final runs: `shared-policy-cycle1/` then
`shared-policy-3cycles/`. Each contains reports, complete package/control
comparisons, host-setting context, file inventories, permission/AppOps snapshots,
cleanup proofs, provenance and console logs. Initial state, exact known historical
values and limitations, earlier attempts, and old-evidence hashes are preserved.

```powershell
uv run --offline -- python -X utf8 tools/run_probe_reversibility.py --source-checkout C:/Users/Agent/Cyclone/cloak-repo --candidate-commit 1c9ce900930b7c52d3d7130b8ca8a741e183b73a --adb C:/Users/Agent/android-sdk/platform-tools/adb.exe --serial 3B171FDJH0061G --secondary 17 --cycles 1 --output docs/runtime-evidence/2026-10-20-235457-restoration-follow-up/shared-policy-cycle1
# Only after the focused cycle passes:
uv run --offline -- python -X utf8 tools/run_probe_reversibility.py --source-checkout C:/Users/Agent/Cyclone/cloak-repo --candidate-commit 1c9ce900930b7c52d3d7130b8ca8a741e183b73a --adb C:/Users/Agent/android-sdk/platform-tools/adb.exe --serial 3B171FDJH0061G --secondary 17 --cycles 3 --output docs/runtime-evidence/2026-10-20-235457-restoration-follow-up/shared-policy-3cycles
```

Preserved intermediate attempts exposed collector issues (multiline UID modes,
a shell control reader already bound for user 17, and the `android` package-name
exception). The final host reader uses root CLI UID 0, outside app-process hooks.
Intermediate evidence was not relabelled. Earlier three-cycle hash-only success
is not substituted for the final complete-value proof.

No release, tag, version bump, account operation, data clearing, permission reset,
production installation or broader compatibility/platform claim was made.

Final cold restoration counts (117 rows per user): user 0 each cycle 102 PASS /
7 OBSERVED / 8 BLOCKED / 0 FAIL; user 17 cycle 1 100 PASS / 4 OBSERVED /
13 BLOCKED / 0 FAIL, cycles 2 and 3 101 PASS / 4 OBSERVED / 12 BLOCKED / 0 FAIL.
Package inventories stayed 132 and 126 respectively, with zero line differences.
All final raw index bytes and semantic fields also stayed unchanged.

Evidence: `summary.md`, `failure-analysis.json` and `restoration-follow-up-evidence.zip`
in the directory above. Changes remain uncommitted for review; unrelated files
remain unstaged. Historical package attribution and host-setting writer remain
unresolved, with evidence limitations stated rather than invented explanations.
