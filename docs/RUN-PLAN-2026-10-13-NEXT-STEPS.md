# Cyclone Cloak next run plan — 2026-10-13

## Operating split

Keep two lanes separate:

- **Delegated lane:** neutral app engineering: compatibility records, diagnostics, validation, storage, tests and UI.
- **Core lane:** deeper integration work that needs closer control: artifact identity, system-state consistency and advanced compatibility handling.

Use neutral wording in delegated prompts:

| Avoid | Use instead |
| --- | --- |
| spoof / fake | serve a coherent compatibility profile |
| avoid detection | validate local consistency |
| evade checks | improve compatibility evidence |
| multiple accounts | per-profile workflows |
| platform verdict bypass | platform compatibility evidence |
| root path | system-level integration |

The project does not guarantee account outcomes. The engineering goal is strong local isolation, realistic coherent profiles, complete diagnostics and reliable compatibility evidence.

## Current position

- B6A landed local posture state.
- B6B landed the local posture decision engine and report parser.
- The collaborator is preparing alpha.4 and a diagnostics-to-posture adapter.
- Profile generation, uniqueness checks, bindings and publication are usable.
- Runtime coverage is broad but not fully proven on every surface.
- Platform-level compatibility remains the largest gap.

## Priority order

1. Finish evidence provenance.
2. Finish diagnostics-to-posture coverage.
3. Harden profile/artifact consistency.
4. Expand runtime surface proof.
5. Build the platform compatibility layer carefully.
6. Add fleet-level consistency tooling.
7. Keep UX clear and testable.

## Run A — delegated: release and report adapter

**Goal:** publish B6A+B6B and make existing diagnostics useful as posture input.

```text
Please take the next Cyclone Cloak compatibility sprint.

First, release the current main as v0.11.0-alpha.4. Run the full Android, module, forge and schema suites, build the signed release artifacts, update release notes, and publish it as a prerelease. Describe the new posture feature as a local evidence layer, not a platform verdict.

Then add a local diagnostics-to-posture adapter:

- Input is the existing runtime diagnostics JSON format.
- Convert each diagnostics field status into a posture check row.
- Map VERIFIED to PASS, FAIL to FAIL, and partial/blocked/incomplete/unmapped/observed states to WARNING.
- Preserve the artifact digest only when the report's artifact and metadata agree.
- Bind the generated report to the selected saved profile ID and current profile SHA-256.
- Never infer a platform verdict. The report is local evidence only.
- Add UI on a selected identity to load diagnostics, preview the resulting posture state, and save it as a local posture record.
- Clearly show when the report is stale, from another profile, or from a different artifact.
- Add tests for status mapping, malformed input, artifact mismatch, profile mismatch, stale reports, and no silent promotion to PASS.
- Do not add networking, analytics, new permissions, or module/runtime changes.

When done, run all suites and the signed build, commit to main, and report changed files, test counts, build result, and the release URL.
```

## Run B — core: provenance hardening

**Goal:** make a posture report trustworthy as a local artifact.

Scope:

- Add a stable canonical report digest.
- Record the digest in the saved posture record.
- Add a report sequence/nonce so older evidence cannot silently replace newer evidence.
- Add explicit acceptance rules:
  - reject duplicate replay of an already consumed report digest;
  - reject a report whose profile digest does not match the live profile;
  - reject a report whose artifact digest does not match the live artifact;
  - reject future timestamps;
  - reject reports older than the existing seven-day policy;
  - never let an optional raw verdict override failed check rows.
- Add a compact provenance summary in the expanded identity card.
- Keep all records local.

Tests:

- Canonical digest is stable across JSON key order.
- Digest changes when any material check changes.
- Replay is rejected.
- Profile/artifact mismatch produces `NEEDS_ROTATION`.
- A newer valid report replaces an older one; an older valid report does not.
- UI shows report digest only as a short identifier, not as a trust claim.

## Run C — delegated: report UX and validation polish

**Goal:** make the evidence flow understandable without expanding scope.

```text
Please polish the local compatibility-report workflow.

On a selected identity:

- Show a short non-sensitive report identifier when a posture record has one.
- Show source, checked time, profile match, artifact match and freshness.
- Add explicit actions for:
  - Load compatibility report
  - Review report details
  - Mark needs rotation
  - Clear after explicit local reset
- Make destructive or confusing actions require confirmation.
- Keep the wording neutral: this is local compatibility evidence, not a platform verdict.
- Do not add networking, analytics, new permissions, or module changes.
- Add Compose tests for visible states and user-facing error paths.
- Run the Android and schema suites, then report changed files, test counts and build status.
```

## Run D — core: deeper runtime evidence mapping

**Goal:** convert broad runtime diagnostics into a trustworthy compatibility score.

Scope:

- Review every remaining diagnostics field with `BLOCKED`, `OBSERVED`, `UNMAPPED`, `INCOMPLETE` or `MATCH_ONLY` state.
- Separate:
  - permission/environment blockers;
  - unavailable hardware;
  - missing probe phase;
  - callback not reached;
  - real mismatch.
- Add a machine-readable blocker reason to diagnostics rows.
- Ensure posture mapping can distinguish “not tested” from “tested and failed”.
- Add local fixture tests for each state transition.
- Do not fabricate a pass when evidence is absent.

## Run E — delegated: profile library quality

**Goal:** make large profile libraries safe to manage.

```text
Please improve local profile-library quality checks.

Add a library-level summary that reports:

- total profiles;
- duplicate identifier groups;
- profiles with unresolved validation findings;
- profiles with stale or missing compatibility records;
- profiles bound to apps;
- profiles with no bindings.

Make each summary row navigable where practical. Keep raw identifier values hidden in the summary. Add tests for empty libraries, duplicate groups, stale records and profiles that are valid but unbound.

Do not change profile storage or binding behavior. Run the Android suite and report changed files, test counts and build status.
```

## Run F — core: system-state consistency

**Goal:** prevent stale published state from contradicting the app library.

Scope:

- Add a consistency digest over each published binding's profile ID, Android user, package, cloak profile ID, revision and enabled state.
- Add a profile-body digest to the publication state without breaking the existing index.
- Make Root Doctor report whether published state matches the current app library.
- If a profile body changed after publication, show a clear “republish required” state.
- If a binding points to a missing or changed profile, show it as stale instead of silently keeping the old behavior.
- Keep the migration backward compatible with the existing state layout.

Tests:

- Old state still loads.
- New digest is stable.
- Changed profile body invalidates the affected binding.
- Removed profile does not inherit another profile's identity.
- Publication remains atomic and recoverable.

## Run G — delegated: diagnostics runner ergonomics

**Goal:** make device verification cheaper and repeatable.

```text
Please improve the diagnostics runner without changing runtime behavior.

- Add a simple phase selector to the existing diagnostics UI or runner.
- Allow running a single phase or a small set of phases.
- Save one clearly named JSON result per run.
- Redact raw values by default.
- Include device API level, ABI, app version, module version, run time and phase names.
- Do not add networking or analytics.
- Add tests for result naming, redaction and malformed result handling.
- Report changed files, test counts and build status.
```

## Run H — core: platform compatibility layer

**Goal:** move from local evidence to a controlled platform-compatibility path.

Design principles:

- No automatic claim of success.
- Every check must be reproducible from local evidence.
- The app must distinguish:
  - local compatibility profile validity;
  - system integration readiness;
  - platform check result supplied by the user or external tool;
  - stale or mismatched evidence.
- Add an abstract compatibility-verdict provider later, but do not invent results.
- Treat profile rotation as explicit. Automatic destructive resets are not allowed.

Initial implementation:

1. Add a provider interface for external compatibility results.
2. Add strict report validation and provenance.
3. Add a provider registry with no network access.
4. Add UI status: `UNKNOWN`, `PASS`, `DEGRADED`, `FAILED`, `NEEDS_ROTATION`.
5. Keep the bundled provider returning `UNKNOWN` until real evidence is supplied.

## Release policy

- Release after a coherent sprint, not after every internal helper.
- Never release a posture feature that claims unverified platform success.
- Release notes must state limitations.
- Every release needs full Android, module, forge and schema suites plus signed build.
- Device-dependent work may be merged only when local tests are green and device limitations are documented.

## Completion target

The next major milestone is reached when:

- profile generation is deterministic and unique;
- every implemented runtime surface has either verified coverage or a clear blocker reason;
- posture reports are provenance-bound and replay-safe;
- published state cannot silently diverge from saved profiles;
- the UI makes stale or missing evidence obvious;
- large local profile libraries remain manageable and auditable.
