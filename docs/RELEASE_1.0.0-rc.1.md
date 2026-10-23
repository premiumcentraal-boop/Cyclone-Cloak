# Cyclone Cloak 1.0.0-rc.1

Prerelease candidate, matching companion/module version code **60**.

## Scope since alpha.28

- Optional `cloak.denylist_enabled` configuration requests Magisk denylist
  configuration through the existing root companion. This does not establish
  that Shamiko is installed or that any self-visibility result was measured.
- An optional per-profile `pif` section carries integrity configuration into
  the runtime envelope. Declared fingerprint, patch, model, first API level and
  boot-state inputs are configuration, not an authenticated platform response.
- Three read-only diagnostics readers cover caller-visible self-visibility:
  `masked.package_list`, `masked.maps` and `masked.services`.
- Removed a stale `ProbeReportPolicy` debug log line and refreshed ledger wording,
  architecture and release status. Engineering closure and physical proof are
  recorded separately.
- Preserved the preceding local verification/evidence baseline. Existing profile
  imports remain supported; optional sections do not require destructive migration.

## Changed files and integration points

- `android/module/src/main/cpp/cloak.cpp`: optional configuration integration.
- `schema/cloak-profile.schema.json`: optional denylist and `pif` configuration.
- `android/module/src/test/resources/envelope-a.json` and
  `forge/tests/test_pif_section.py`: configuration fixtures/tests.
- `android/probe/src/main/java/dev/cyclone/cloak/probe/RealityProbe.java`:
  three self-visibility readers.
- `android/probe/src/main/java/dev/cyclone/cloak/probe/ProbeReportPolicy.java`:
  stale log removal.
- App/module version metadata, README, architecture, current state, changelog
  and coverage/engineering ledgers: RC packaging and documentation.

This list identifies the RC integration changes; the inherited baseline includes
the earlier diagnostics work and its archived evidence. This is not a
diagnostics-only release because native configuration code also changed.

## Exact local verification

S5 tested source `c475c254be1d52ead39d64372cb1e72134a41816`. Subsequent commits
before the release tag change documentation only.

| Suite or build | Result |
| --- | --- |
| App debug unit tests | 784 passed; 0 failed, errors or skipped |
| Module debug unit tests | 80 passed; 0 failed, errors or skipped |
| Standard probe unit tests | 148 passed; 0 failed, errors or skipped |
| Diagnostics probe unit tests | 148 passed; 0 failed, errors or skipped |
| Forge tests, dependency-complete rerun | 1,134 passed |
| Schema tests | 192 passed |
| Debug APK, release APK and module packaging | Passed |
| `git diff --check` | Passed |

No compiler warnings were reported. The original forge invocation stopped with
four collection errors because its environment omitted `cryptography` and
`jsonschema`. The dependency-complete rerun resolved collection without source
changes. Signed artifact validation is performed by the unchanged release workflow;
local build success alone is not signed-release verification.

Exact successful commands from the repository root:

```powershell
$env:JAVA_HOME='C:/Program Files/Eclipse Adoptium/jdk-17.0.20.101-hotspot'
$env:CLOAK_TEST_WSL='1'
& "$env:TEMP/cloak-build/gradle-8.11.1/bin/gradle.bat" -p android :app:testDebugUnitTest :module:testDebugUnitTest :probe:testStandardDebugUnitTest :probe:testDiagnosticsDebugUnitTest :app:assembleDebug :app:assembleRelease :module:packageModule --offline --no-daemon --console=plain
uv run --with pytest --with jsonschema --with cryptography -- python -X utf8 -m pytest forge/tests -q
uv run --with pytest --with jsonschema -- python -X utf8 -m pytest forge/schema_tests -q
git diff --check
```

## Evidence limits and pending physical gate

RC1 physical confirmation is **PENDING** at publication preparation. Historical
alpha evidence does not validate this RC APK/module pair. The archived
[R2 measurement table](r2-measurement.json) retains eight PARTIAL paths and one
UNVERIFIED concrete instance user-agent path; unavailable values remain null.
Default-UA matches do not establish concrete instance-UA behavior.

Self-visibility readers are implementation capabilities, not proof of NOT_PRESENT.
Caller package visibility, service-access restrictions and collection errors limit
their meaning. OBSERVED, BLOCKED, MISSING and unavailable results are not passes.
The planned device gate separately measures users 0 and 17, three restoration
cycles, self-visibility rows, permissions/AppOps, artifact provenance and cleanup.
Any FOUND result or failed gate is retained and reported without source fixes.

No platform verdict, certification, permanent trust, hardware equivalence or
broader device compatibility is claimed. A declared integrity/boot configuration
does not prove the real boot chain or an authenticated service verdict.

## Upgrade

Use the signed `app-release.apk` and matching
`cyclone-cloak-1.0.0-rc.1.zip` through the existing update flow. Retain app data,
saved profiles and permissions. Module activation follows the normal module
update/restart flow. Physical testing and its resulting evidence are separate
from publication and must identify the exact measured artifacts.
