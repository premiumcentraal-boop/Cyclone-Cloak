# Cyclone Cloak 1.0.0-rc.2

Prerelease candidate, matching companion/module version code **61**.

## Scope since rc.1

- **Package reads no longer fail on bound profiles.** Generated profiles used to carry an empty
  `packages` section, so every allowlisted package lookup threw. The hook rethrew that into the
  hosting app, meaning `getInstalledPackages` and `getPackageInfo` returned an exception instead
  of the real result. This release removes that failure.
- `forge/cloak_forge/reality.py` derives a `packages` section from facts a template can actually
  prove: the framework release/API level, and the declared WebView provider with a Chromium
  release code. Nothing else is asserted, and the section is omitted when nothing is provable,
  so an empty section can no longer exist.
- `RuntimeEnvelopeHooks` keeps the host `PackageInfo` when a package has no declared version.
  Membership, names and host values survive; nothing is fabricated.
- The derived section is excluded from the instance-realism baseline anchor, so provenance
  stays valid. Affected generated vectors were refreshed.
- The reversibility harness accepts an explicit `version:code` pair for `--expected-version`,
  so candidates can be measured without editing the tool. A bare `v1.0.0-rc.1` still resolves
  to the rc.1 pair.

Inherited rc.1 capabilities are unchanged: optional denylist configuration, optional per-profile
`pif` configuration, and the three read-only self-visibility readers. Declared configuration is
still configuration, not an authenticated platform response.

## Changed files and integration points

- `forge/cloak_forge/reality.py`, `forge/cloak_forge/instance_realism.py`: package derivation
  and anchor exclusion.
- `android/module/src/hook/java/dev/cyclone/cloak/hook/RuntimeEnvelopeHooks.java`: host
  fallback for undeclared package versions.
- `android/app/src/main/java/dev/cyclone/cloak/forge/DeviceReality.kt`,
  `CloakForge.kt`, `InstanceRealism.kt`: the Kotlin twin of the same change.
- `tools/run_probe_reversibility.py`: `--expected-version` pair support.
- Regenerated `catalog/instance-realism-v1.json`, `forge/tests/vectors/reality.json`,
  `forge/tests/vectors/reality-runtime-contract.json`.
- Tests: `test_packages_only_carry_versions_the_profile_can_prove` (forge) and
  `allowlistedPackageWithoutDeclaredVersionKeepsTheHostCopy` (module), which replaces the old
  fail-closed assertion.
- App/module version metadata and the fix handoffs.

## Exact local verification

| Suite or build | Result |
| --- | --- |
| Forge tests, dependency-complete | 1,139 passed |
| App debug unit tests | Passed, including both Kotlin/Python parity tests |
| Module debug unit tests | 80 passed |
| Diagnostics probe build | Passed |
| `git diff --check` | Passed |

The forge invocation needs `jsonschema` and `cryptography`; without them four test modules fail
to collect. Signed artifact validation is performed by the unchanged release workflow. Local
build success alone is not signed-release verification.

```powershell
$env:JAVA_HOME='C:/Program Files/Eclipse Adoptium/jdk-17.0.20.101-hotspot'
& "$env:TEMP/cloak-build/gradle-8.11.1/bin/gradle.bat" -p android :app:testDebugUnitTest :module:testDebugUnitTest --offline --no-daemon --console=plain
uv run --with pytest --with jsonschema --with cryptography -- python -X utf8 -m pytest forge/tests -q
```

## Evidence limits and pending physical gate

Physical confirmation for this pair is **PENDING**. Historical evidence does not validate the
rc.2 APK/module pair, and the fix itself is module-side: rebuilding the diagnostics probe alone
cannot deploy it.

The archived R2 measurement table still retains eight PARTIAL paths and one UNVERIFIED concrete
instance user-agent path; unavailable values remain null. The self-visibility readers remain
capabilities, not proof. `OBSERVED`, `BLOCKED`, `MISSING` and unavailable results are not passes.
An installed-module file hash is not in-process binary proof.

The planned gate measures users 0 and 17, three restoration cycles, self-visibility rows,
permissions/AppOps, artifact provenance and cleanup. `NOT_PRESENT` is required for the masked
package-list row; `FOUND:` is a real sighting and `DENIED:` means an exception still escapes.
Any FOUND result or failed gate is retained and reported without relaxing the rule.

No platform verdict, certification, permanent trust, hardware equivalence or broader device
compatibility is claimed.

## Upgrade

Use the signed `app-release.apk` and matching `cyclone-cloak-1.0.0-rc.2.zip`. Retain app data,
saved profiles and permissions. The module update needs the normal restart flow. Physical
testing and its evidence stay separate from publication and must name the measured artifacts.
