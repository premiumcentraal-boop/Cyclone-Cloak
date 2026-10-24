# Package-list blocker: fixed, ready for the S8 retry

Commit `15d5ae2` on `main`. Pushed. No release, tag, version bump or device action.

## What the diagnosis got right, and what it missed

The delegated diagnosis was correct that the digest was `DENIED:Unavailable` and that the
bound fixture lacked package-version metadata. Two things sat behind it:

1. **The generator never emitted a usable section.** Every catalog template ships
   `packages: {availability: unknown, values: {}}`, and `generate()` copied that through
   untouched. `RuntimeEnvelope.values()` therefore threw for the whole section, not just for
   one package.
2. **The hook turned that into a host-visible exception.** `guarded()` rethrows
   `Unavailable`, so any bound app calling `getInstalledPackages` or `getPackageInfo` on an
   allowlisted package received an exception instead of its real result. This was never only
   a probe problem.

## Changes

- `forge/cloak_forge/reality.py`: new `_packages()` derives a `packages` section from facts the
  template can actually prove, namely the framework release/SDK (`android`) and the declared
  WebView provider/version with a Chromium release code. The section is omitted when nothing is
  provable, so an empty section can no longer exist.
- `android/.../RuntimeEnvelopeHooks.java`: `declaredOrHost()` keeps the host `PackageInfo` when a
  package has no declared version. Real membership, names and host values survive; nothing is
  fabricated.
- `forge/cloak_forge/instance_realism.py` and `InstanceRealism.kt`: the derived section is
  excluded from the baseline anchor, like the other derived values.
- Regenerated `catalog/instance-realism-v1.json`, `forge/tests/vectors/reality.json` (base
  profile only, cases reapply) and `forge/tests/vectors/reality-runtime-contract.json` (four
  `present: false -> true` flips).
- Tests: `test_packages_only_carry_versions_the_profile_can_prove` in the forge suite, and the
  module test `allowlistedPackageWithoutDeclaredVersionKeepsTheHostCopy` replaces the old
  fail-closed assertion.

## Verification

- forge: **1139 passed**
- app: **passed** (includes both Kotlin/Python parity tests)
- module: **80 passed**
- diagnostics probe: builds

## What is left for the delegated run

1. Rebuild the diagnostics APK from `15d5ae2` and record its SHA-256.
2. Rerun S8 with the existing harness and gates unchanged.
3. Expected: the masked package-list row now reaches `NOT_PRESENT` for both users, because the
   probe's own package is exempted and the exception no longer fires.
4. If a bound capture still mismatches, capture the row's actual value. A `FOUND:` prefix means
   a real package sighting; `DENIED:` means an exception still escapes.

Do not relax the `NOT_PRESENT` requirement and do not promote any row to VERIFIED. Device
behaviour remains unproven until the run completes.
