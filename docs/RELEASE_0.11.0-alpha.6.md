# Cyclone Cloak v0.11.0-alpha.6 — Local library quality

This prerelease adds a read-only **Library quality** summary to Health, below
local identifier duplicates. It reports readable/unreadable inventory, duplicate
groups, profiles with validation errors or warnings, missing/stale/attention-state
local compatibility records, and bound/unbound profiles. Rows offer existing
profile review actions, disabled while work is in progress. Identifier values
are hidden. The summary does not repair, mutate, reset, rebind or publish data.

This release also includes the existing main diagnostics cause-classification
change following alpha.5. No diagnostics or posture behavior was altered during
release preparation. Stale top-level status text and obsolete local-test blockers
have been reconciled with current versions and checks.

## Upgrade and scope

Install the signed APK as an update. App/module metadata agrees at alpha.6,
version code 30. The module ZIP has updated packaging metadata only.

- No profile schema change, migration, rewrite or storage-layout change.
- No connector, binding, publication or runtime/module behavior change.
- No new permissions, networking or analytics.
- Local compatibility evidence and quality counts are not platform verdicts.
- Device behavior remains separately verified against the measured artifact.
  Existing runtime evidence is not alpha.6 verification; no device tests were
  performed during this release run.

## Validation and artifacts

Local checks: app 398 tests (375 passed, 23 existing Windows shell-test skips),
module 12 passed, forge 227 passed, schema 33 passed; debug build and whitespace
checks passed. Release CI reruns all suites, builds/signs and verifies matching
artifacts, then publishes `app-release.apk` and
`cyclone-cloak-0.11.0-alpha.6.zip`. See the release CI run for publication status.
