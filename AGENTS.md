# AGENTS.md — Cyclone Cloak working compass

## Where to work

Primary local repository:

`C:\Users\Agent\Cyclone\cloak-repo`

Before changing anything, verify the current branch and tag. Then fetch the latest tags and confirm you are aligned with the latest published release tag unless the sprint explicitly says otherwise.

```powershell
git fetch --tags
git status --short
git log --oneline -5
```

If you are behind the latest release tag, sync to the latest release tag before starting work.

## Project orientation

Cyclone Cloak is a companion layer for Cyclone. It stores coherent device-configuration profiles and binds them to apps inside specific Android users.

Current focus is on ordinary compatibility engineering:

1. Data validation and coherence.
2. Phone catalog quality.
3. Binding and health UX.
4. Diagnostics and test coverage.
5. Reliable synchronization.

Please keep implementation scoped to the assigned sprint.

## Useful docs

Read these before making changes:

- `README.md`
- `docs/ARCHITECTURE.md`
- `docs/STATE_LAYOUT.md`
- `docs/CYCLONE_PROFILES_REPORT.md`

If docs are inconsistent with the actual code, trust the code and note the discrepancy in your report.

## Repository layout

- `android/app` — Android application.
- `android/module` — Android module and native runtime.
- `catalog` — Shared phone catalog data.
- `forge` — Shared profile generation and validation helpers.
- `docs` — Architecture, checkpoints, release notes, and test notes.

## Build and test commands

Run the Android tests:

```powershell
gradle -p android :app:testDebugUnitTest
```

Build the app and module:

```powershell
gradle -p android :app:assembleDebug :module:packageModule
```

Run the Python forge tests:

```powershell
uv run --with pytest -- python -m pytest forge/tests
```

If a test command fails, report the failing target and the relevant error rather than deleting or bypassing the test.

## Working rules

1. Do not rename public connector IDs, scopes, storage keys, or schema fields unless the sprint explicitly allows it.
2. Do not add networking, analytics, telemetry, or new permissions.
3. Do not change the user-facing wording of existing health or binding states unless requested.
4. Keep UI changes consistent with the existing Cyclone design language.
5. Prefer narrow, reviewable changes.
6. Add or update tests for behavior changes.
7. Report changed files, test results, build warnings, and any assumptions made.

## Sprint boundary

Unless the handoff explicitly says otherwise:

- Do not alter the connector contract.
- Do not introduce new storage formats.
- Do not remove existing bindings.
- Do not change release versioning without instructions.

## Deliverable format

Each completed sprint should include:

1. Short summary of what changed.
2. Changed files.
3. Test results.
4. Build results.
5. Any known limitations or follow-ups.

This file is intentionally scoped so agents can orient quickly without needing the full product narrative.
