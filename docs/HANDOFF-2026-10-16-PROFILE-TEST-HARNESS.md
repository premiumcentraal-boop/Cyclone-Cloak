# Companion profile test harness — delegated run

## Objective

Build a repeatable Android test harness that verifies whether a profile transition
worked correctly and whether the expected device fields followed the active
Android user. The harness should produce one compact evidence bundle per run so
future debugging can be done from a short report instead of manual device work.

Repo: `C:/Users/Agent/Cyclone/cloak-repo`
Branch: `main`
Latest release: `v0.11.0-alpha.13`

## Required work

### 1. Stable test runner

Add `tools/run-profile-check.ps1` with independently runnable phases:

```text
environment
bind
publish
launch
probes
switch
post-switch-probes
diff
crash-log
summary
```

The runner must support:

```powershell
./tools/run-profile-check.ps1 -Scenario bound-a -Phase environment
./tools/run-profile-check.ps1 -Scenario bound-a -Phase all
```

Artifacts should be written under:

```text
docs/runtime-evidence/<date>-<scenario>/
```

Use stable artifact names:

```text
manifest.json
phase-01-environment.json
phase-02-bind.json
phase-03-publish.json
phase-04-launch.json
phase-05-probes.json
phase-06-switch.json
phase-07-post-switch-probes.json
phase-08-diff.json
phase-09-crash.log
phase-10-logcat.log
summary.md
```

### 2. Expanded diagnostics probe

Extend the existing diagnostics probe so it can collect the important profile and
device fields without UI automation.

Each row must use a stable ID and this structure:

```json
{
  "id": "build.model",
  "source": "android.os.Build",
  "expected": null,
  "actual": null,
  "status": "PASS | FAIL | BLOCKED | OBSERVED | MISSING",
  "detail": "short reason",
  "duration_ms": 0
}
```

Cover these categories:

```text
Build fields
ro.product properties
Build serial
telephony state
SIM/carrier state
Wi-Fi fields
Bluetooth fields
Settings.Secure.ANDROID_ID
MediaDrm ID
camera characteristics
display metrics
locale/timezone
installed-package visibility
process UID/user
loaded runtime/module state
```

The probe must not infer any external service verdict. It only records observed
values and compares them with the expected profile fields.

### 3. Profile transition verification

For each profile transition, capture the same probe set three times:

```text
before switch
after switch
after cold restart
```

The diff must use stable row IDs and clearly report:

```text
expected changes that did not happen
unexpected changes that did happen
blocked or missing rows
values that stayed identical when they should have changed
```

### 4. Compact report

Every full run must produce a short `summary.md` with only the useful decision
information:

```text
Scenario:
Result:
Duration:
Profile:
Android user:
Critical failures:
Warnings:
Blocked rows:
Artifacts:
```

The raw evidence stays in the artifact directory. The summary should be small
enough to paste back without noise.

### 5. Final run

After implementation, run:

1. a small scenario with one app,
2. a large scenario using the current full profile,
3. one Android-user transition,
4. one cold restart after the transition.

Then run the normal Android/module/schema test suites and prepare release notes.
Do not publish until the usual test and packaging checks are green.

## Constraints

- No UI redesign.
- No public API changes.
- No storage schema changes.
- No new permissions.
- No runtime behavior changes.
- Keep existing binding history and connector behavior intact.
- Add focused tests for parsing, classification, diff generation, and phase naming.
- Keep the final report concise and pasteable.

## Definition of done

- The runner works for individual phases and full runs.
- The probe produces stable, structured reports.
- The transition diff clearly distinguishes expected and unexpected changes.
- A compact summary is generated automatically.
- Existing tests still pass.
- New tests cover the probe report, diff logic, and harness phases.
- The release is prepared only after a green test/packaging gate.
