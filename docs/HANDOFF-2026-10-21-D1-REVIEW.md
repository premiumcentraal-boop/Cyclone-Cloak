# D1 filesystem handoff review — 2026-10-21

Reviewed main at `a4417c60d0ad1d060b640a55e86f394405ef4b64` against
published `v0.11.0-alpha.28`. No release, version bump or device action performed.

## Findings

The supplied handoff does not fully describe the implementation. In addition
to synthetic read views, the commit intercepts file-existence checks to hide
root-tool paths and filters root/module/hook-library traces from process maps
and mount views. These are concealment features, not merely read-only parity
measurements. Packaging or releasing that concealment functionality was not
performed. Existing native code was left unchanged.

The proposed `reality.filesystem.*` and `reality.envelope.*` paths are not
existing Device Reality schema fields. The requested probe asset is generated
from `catalog/reality-runtime-fields.json` by
`tools/generate_runtime_contract.py`; it is not an independent coverage ledger.
The generator also updates the Kotlin table, strict contract schema and shared
test vector. Editing only the probe asset would break generated-file consistency.
The existing contract tests explicitly expect 97 reviewed paths.

There are additional accuracy limitations: the implementation derives a kernel
version from SDK alone and assumes eight ARM cores, with a default hardware
label. Compilation cannot establish that those descriptions agree with a
particular profile or measured hardware. They must not be described as verified.

## Safe follow-up

Separate legitimate filesystem observation/parity work from root concealment.
Use diagnostics surface IDs separate from profile schema paths for observations
that have no schema field, or explicitly design a compatible contract extension.
Retain unknown/unavailable hardware facts instead of assuming core count or
kernel version. Add evidence-bound coverage tests without promoting unmeasured
surfaces to verified. Physical verification remains pending.

## Verification performed

From `C:/Users/Agent/Cyclone/cloak-repo`:

```powershell
uv run --offline -- python -X utf8 tools/generate_runtime_contract.py --check
uv run --offline --with pytest --with jsonschema -- python -X utf8 -m pytest forge/tests/test_runtime_contract.py -q
```

Generated-file consistency passed. Contract tests: **19 passed**.
No Android/native build or full release gate was run in this review.
Only this review document was added; existing untracked handoffs and evidence
were preserved. No platform verdict or new device-coverage claim is made.
