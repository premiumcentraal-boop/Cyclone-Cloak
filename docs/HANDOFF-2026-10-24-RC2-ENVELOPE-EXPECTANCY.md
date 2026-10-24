# RC2 resume: masked rows green, envelope row semantics fixed

The package fix is confirmed on device: **all three masked rows returned `NOT_PRESENT`** and
cleanup, production, permission and AppOps audits were clean. Two failures in
`2026-10-24-175455-rc2-resume` were mine, and both are fixed on `main`.

## Finding 1: `envelope.packages.visibleVersions` HASH_MISMATCH — real defect

The row hashes the full caller-visible list of six allowlisted packages. The profile now
declares versions only for the ones it can prove (`android`, and the WebView provider when the
template has one), so the comparison was declared-subset against full-list and could never match.
That made the row permanently red on any profile.

The row now merges declared versions over the caller-visible list, keyed by package name.
Declared values win, undeclared packages keep the host value, and membership and order are
untouched. Visibility is still never broadened: the same reader supplies both sides.

## Finding 2: row count 16 vs 15 — stale pin

The envelope section grew by exactly one row when the packages row was added. The pin is now 16.
The guard still catches a section that is short or contains a FAIL.

## Changed files

- `android/probe/src/main/java/dev/cyclone/cloak/probe/RuntimeEnvelopeReport.java`: extracted
  `actualPackages(Activity)`, added `mergePackages`, rewired the row.
- `android/probe/src/test/java/dev/cyclone/cloak/probe/RuntimeEnvelopeReportTest.java`:
  `declaredPackageVersionsOverrideOnlyThePackagesTheyName`.
- `tools/run_probe_reversibility.py`: envelope row pin 15 -> 16.

## Verification

Probe diagnostics and standard suites plus the diagnostics APK build passed, including the new
test. Focused runner suite: 79 passed. No module change, so the installed RC2 module and its
pinned hashes still apply.

## What you need to do

1. Rebuild the diagnostics APK and record its SHA-256. It changed; the previous hash is void.
2. Rerun S8 unchanged otherwise, keeping both pins:
   `--expected-version v1.0.0-rc.2:61 --expected-module-hash 5b4c6d01e3ff180c2d535bd7913a43960b469d34815100ac8dd0615c0c9b34f8`
3. No reinstall or reboot is needed for this change; the module is untouched.

`NOT_PRESENT` still applies to all three masked rows. `OBSERVED`, `MISSING` and `BLOCKED` remain
non-passes, and no row is promoted to VERIFIED. Stop at the first failed gate.

Note for the record: a declared version is a profile assertion merged for comparison, not proof
the host returned it. The row is still hash-match only.
