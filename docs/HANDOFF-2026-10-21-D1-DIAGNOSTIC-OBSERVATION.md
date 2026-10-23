# Filesystem observation surfaces - follow-up sprint

Following the safe follow-up scoped in your review
(`docs/HANDOFF-2026-10-21-D1-REVIEW.md`). This sprint is diagnostics and
contract work only: it adds observation rows, wires them through the existing
generator, and replaces fixed assumptions with measured host facts. No release,
no version bump, no device actions.

## Scope

1. **Replace fixed hardware assumptions with measured facts** in
   `android/module/src/main/cpp/cloak.cpp`:
   - `render_cpuinfo()`: derive the processor count from
     `sysconf(_SC_NPROCESSORS_ONLN)` instead of the hard-coded 8.
   - `render_proc_version()`: take the kernel release from `uname(2)` instead
     of `kernel_for_sdk()`. Drop `kernel_for_sdk` once unused.
   - Keep profile-sourced identity strings (manufacturer, model, fingerprint)
     exactly as they are; only the previously hard-coded values change.
   - Update or add focused module tests covering the two renderers with the
     measured values.
2. **Observation rows through the generator.** In
   `catalog/reality-runtime-fields.json`, add diagnostics surface IDs separate
   from profile schema paths, per your review's suggestion:
   - `diagnostics.filesystem.build_prop`
   - `diagnostics.filesystem.proc_version`
   - `diagnostics.filesystem.proc_cpuinfo`
   - `diagnostics.filesystem.maps_snapshot`
   Status `DIAGNOSTIC_ONLY`, `runtime_covered: false`, reason: observation
   surfaces; physical evidence pending. Run
   `tools/generate_runtime_contract.py` so the probe asset, Kotlin table,
   schema and shared vectors regenerate together, then confirm
   `--check` and `forge/tests/test_runtime_contract.py` pass.
3. **Probe read rows.** Extend the diagnostics probe with read rows for
   `/system/build.prop`, `/proc/version`, `/proc/cpuinfo` and
   `/proc/self/maps`, recording content hashes plus row counts as
   observations. They classify as OBSERVED; no parity expectation and no
   VERIFIED promotion in this sprint.
4. **Evidence-bound tests.** Extend the probe host tests so the new rows
   appear with `OBSERVED` semantics and the generated files stay consistent.

## Suggested commands

```powershell
$env:JAVA_HOME='C:/Program Files/Eclipse Adoptium/jdk-17.0.20.101-hotspot'
& "$env:TEMP/cloak-build/gradle-8.11.1/bin/gradle.bat" -p android :module:testDebugUnitTest :module:packageModule :probe:testDiagnosticsDebugUnitTest --offline --no-daemon --console=plain
uv run --offline -- python -X utf8 tools/generate_runtime_contract.py --check
uv run --offline --with pytest --with jsonschema -- python -X utf8 -m pytest forge/tests/test_runtime_contract.py -q
```

## Out of scope for you

Release packaging and publication stay with the project owner this round.
Historical evidence, existing rows and the 97 reviewed paths are unchanged
except for the four added diagnostics rows. No platform verdict is claimed.
