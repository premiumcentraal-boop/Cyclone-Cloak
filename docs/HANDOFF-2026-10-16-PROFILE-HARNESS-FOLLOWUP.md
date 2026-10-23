# Profile harness follow-up — delegated run

## Context

The profile test harness is working. The completed run produced useful evidence
for both a small scenario and the full 327-target profile transition. Existing
production records and runtime behavior were not changed.

The work is currently local only. It has not been committed, pushed, tagged, or
published. The local packaging result is alpha.14/code 38, but the published
release is still alpha.13.

## Immediate goal

Turn the completed harness work into a clean, reviewable release, then add the
small amount of missing observability needed to understand the real companion
flow without changing user-facing behavior.

Repo: `C:/Users/Agent/Cyclone/cloak-repo`
Branch: `main`
Local packaging version: `0.11.0-alpha.14`
Version code: `38`

## Task 1 — finalize and publish the harness

1. Review the working tree and separate the harness work from unrelated untracked
   evidence.
2. Commit the harness implementation, tests, and documentation.
3. Run the full local gate:

```powershell
$env:JAVA_HOME='C:/Program Files/Eclipse Adoptium/jdk-17.0.20.101-hotspot'
$env:CLOAK_TEST_WSL='1'
& "$env:TEMP/cloak-build/gradle-8.11.1/bin/gradle.bat" -p android :app:testDebugUnitTest :module:testDebugUnitTest :app:assembleDebug :module:packageModule :probe:assembleStandardDebug :probe:assembleDiagnosticsDebug --offline --no-daemon
uv run --with pytest -- python -m pytest forge/tests -q
uv run --with pytest --with jsonschema -- python -m pytest forge/schema_tests -q
uv run -- python -m unittest discover -s android/probe/tests -v
uv run -- python tools/verify_release.py --tag v0.11.0-alpha.14
uv run -- python tools/verify_release.py --tag v0.11.0-alpha.14 --artifacts
git diff --check
```

4. Push `main`.
5. Create tag `v0.11.0-alpha.14`.
6. Let the release workflow build, sign, verify, and publish the prerelease.
7. Confirm the published APK and module ZIP have matching versions/code and that
   the APK embeds the exact module ZIP.

Do not publish if any local or CI gate fails.

## Task 2 — stabilize the remaining report rows

The full transition report currently has:

- four field changes classified only as `OBSERVED`,
- five blocked rows in the user-17 snapshot,
- one Advertising ID row blocked in the user-0 snapshot.

Please do the following:

1. Inspect the saved evidence bundles and determine whether the four unmapped
   changes can be mapped to stable, meaningful row IDs.
2. Add expected-value definitions only where the relationship is clear and
   deterministic.
3. Leave genuinely device-dependent rows as `OBSERVED`; do not fabricate PASS.
4. Add a short table to the harness documentation explaining:
   - stable row ID,
   - source API,
   - expected behavior,
   - reason a row may be blocked.
5. Add focused tests for any newly mapped row.

For the blocked rows, do not add new production permissions. If permission-aware
handling is useful, keep it limited to the diagnostics probe and clearly separate
it from production behavior.

## Task 3 — add a neutral debug-only flow observer

The current harness audits the already-published state. It does not yet measure
the real companion binding/publication flow.

Please add a debug-only observability path that can:

1. invoke the existing companion flow used by the normal UI,
2. record phase start/end timings,
3. record success/failure transitions,
4. capture bounded diagnostics for each phase,
5. expose the result through the existing diagnostics output,
6. avoid changing release behavior, storage formats, or public APIs.

This should be a test seam, not a new user-facing feature. It should be safe to
run repeatedly and should not create new records unless the existing companion
flow would create them.

The goal is to answer these questions in one future run:

```text
Which phase is slow?
Does the full companion flow complete within its budget?
Does a retry reuse the saved selections correctly?
Does the flow leave the state consistent after interruption?
```

## Task 4 — release notes and boundaries

Update the release notes to state clearly:

```text
The harness adds local evidence collection and transition comparison.
It does not claim external service compatibility.
It does not add production permissions.
It does not change public APIs, storage schemas, connector behavior, or runtime behavior.
```

Also include:

```text
Small run: completed with zero critical failures.
Full transition: 327 targets audited, zero critical failures.
70 expected field changes were observed.
Blocked rows are diagnostics limitations, not compatibility verdicts.
```

## Definition of done

- Harness work is committed and pushed.
- Release CI passes.
- Signed prerelease is published.
- Published APK/module metadata agree.
- Unmapped rows are either mapped or explicitly documented as device-dependent.
- Blocked rows are documented without new production permissions.
- The debug-only flow observer is implemented or explicitly deferred with a reason.
- Existing behavior remains unchanged.
