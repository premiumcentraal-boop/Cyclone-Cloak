# Cyclone Cloak Shared Agent Brief

This is the shared brief for every delegated engineering run. Read it before starting any mission run.

## Project framing

Cyclone Cloak is a companion layer for Cyclone. It stores coherent device-configuration profiles and binds them to apps inside separate Android users.

The current work is ordinary Android compatibility and settings-management engineering:

1. Validate that profile data is internally coherent.
2. Generate high-quality profiles from real-device templates.
3. Prevent accidental duplicate or conflicting profile values.
4. Keep synchronization, publication, and health reporting reliable.
5. Improve profile management and diagnostics.

Stay inside the assigned sprint scope. Do not infer or describe a broader product narrative.

## Repository

Primary local repository:

`C:\Users\Agent\Cyclone\cloak-repo`

Before changing anything:

```powershell
git fetch --tags
git status --short
git log --oneline -5
```

If the checkout is behind the latest published release tag, update it unless the mission run says otherwise. Do not start feature work from a stale tree.

## Orientation files

Read these before coding:

- `README.md`
- `docs/ARCHITECTURE.md`
- `docs/STATE_LAYOUT.md`
- `docs/CYCLONE_PROFILES_REPORT.md`
- `AGENTS.md`

If documentation and code disagree, trust the code and report the discrepancy.

## Project layout

- `android/app` — Android application.
- `android/module` — Android module and native runtime.
- `catalog` — shared phone catalog data.
- `forge` — shared profile generation and validation helpers.
- `docs` — architecture, state, checkpoints, release notes, and reports.

## Test and build commands

Android unit tests:

```powershell
gradle -p android :app:testDebugUnitTest
```

Android app and module build:

```powershell
gradle -p android :app:assembleDebug :module:packageModule
```

Python forge tests:

```powershell
uv run --with pytest -- python -m pytest forge/tests
```

If a test fails, report the failing target and the relevant error. Do not delete or bypass tests.

## Universal engineering rules

1. Make one focused change set per run.
2. Preserve backward compatibility unless the run explicitly says otherwise.
3. Do not rename connector IDs, scopes, storage keys, or schema fields unless the run explicitly permits it.
4. Do not add networking, analytics, telemetry, or new permissions.
5. Do not alter the connector contract unless the run explicitly requires it.
6. Keep UI consistent with Cyclone’s existing design language.
7. Add or update tests for behavior changes.
8. Avoid unrelated refactors and formatting-only churn.
9. Prefer clear failure states over silent fallback behavior.
10. Do not claim physical-device verification unless a device test was actually run.

## Required completion report

Every run must report:

1. Short summary of what changed.
2. Changed files.
3. Test results.
4. Build results.
5. Assumptions made.
6. Known limitations or follow-ups.
7. Whether release metadata was changed.

## Operational boundaries

Unless the run says otherwise:

- Do not publish or tag a release.
- Do not modify the connector contract.
- Do not introduce new storage formats.
- Do not delete existing user data.
- Do not change release versioning without being asked.
- If blocked, stop cleanly and describe the exact blocker.

## Communication guidance

Use neutral engineering language:

- device-configuration profile
- compatibility profile
- coherence validation
- uniqueness checks
- binding
- readiness diagnostics
- publication
- reconciliation

Keep code comments, UI text, commit messages, and reports technical. Avoid broader mission-level narratives; the useful context is the specific engineering behavior being implemented.
