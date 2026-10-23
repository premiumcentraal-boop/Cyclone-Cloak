# Cyclone profile runtime test harness — design brief

## Goal

Build one repeatable test system that can prove whether a profile switch worked,
whether the expected device profile is active, and whether the resulting app
environment is coherent. The system should produce one compact evidence bundle per
run so debugging can happen without repeatedly burning the expensive model on
manual device work.

The design principle is simple:

1. Make the normal agent run and collect evidence.
2. Make the expensive agent only read the compact diff and make targeted fixes.
3. Make the device evidence deterministic enough that a fix can be verified in one short run.

## Non-goals

- No broad UI redesign yet.
- No public API or storage schema change.
- No new app permissions.
- No behavior change beyond diagnostics.
- No manual screenshot-only debugging.

## Core architecture

### 1. Deterministic device harness

One PowerShell/ADB script should be able to run a full test scenario and save a
single bundle under `docs/runtime-evidence/<date>-<scenario>/`.

The script should always produce the same artifact names:

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

Every phase should be independently runnable:

```powershell
./tools/run-profile-check.ps1 -Scenario bound-a -Phase launch
./tools/run-profile-check.ps1 -Scenario bound-a -Phase all
```

### 2. On-device probe app

The existing diagnostics app should become the main evidence collector. It should
not rely on UI automation for the important values.

It should expose a small local-only command surface:

```text
probe snapshot --profile <profile-id> --user <android-user> --output /sdcard/Download/cyclone-probe.json
probe compare --before file.json --after file.json --output diff.json
```

The probe app should collect and classify:

```text
android.os.Build fields
ro.product.* properties
Build.getSerial()
TelephonyManager identifiers
Subscriber id / network country
SIM state / carrier name
WifiInfo fields
Bluetooth adapter fields
Settings.Secure.ANDROID_ID
MediaDrm widevine id
Camera characteristics
Display metrics / refresh rate / DPI
Locale, timezone, calendar
Installed package visibility
Process user, package name, UID
Loaded module/runtime state
```

Every row should have:

```json
{
  "id": "build.model",
  "source": "android.os.Build",
  "expected": "Pixel 7 Pro",
  "actual": "Pixel 8",
  "status": "PASS | FAIL | BLOCKED | OBSERVED | MISSING",
  "detail": "short reason",
  "duration_ms": 3
}
```

The probe must never claim platform compatibility. It only reports observed values.

### 3. Switch/change verification

For each profile transition, capture the same probe set three times:

```text
before switch
after switch
after cold restart
```

The diff report should answer four questions directly:

1. Did the active Android user change?
2. Did the expected profile follow the user?
3. Did every expected identifier field change?
4. Did any unexpected field leak from the previous profile?

The diff should use stable row IDs, not prose. Example:

```json
{
  "id": "build.model",
  "before": "Pixel 8",
  "after": "Pixel 7 Pro",
  "changed": true,
  "expected": true
}
```

and:

```json
{
  "id": "settings.android_id",
  "before": "abc123",
  "after": "abc123",
  "changed": false,
  "expected": true,
  "severity": "FAIL"
}
```

### 4. Runtime hook verification

The launch phase should record:

- whether the process started,
- whether the module library was loaded,
- whether native hooks installed,
- whether Java callbacks installed,
- whether the process UID/user matches the target profile,
- whether the target package is in the published state,
- whether the expected identity summary is present,
- whether the process survived long enough to finish initialization,
- whether the UI reached a stable state.

This should be one JSON object, not a long log dump.

## Evidence compression

The harness should produce two levels of reporting.

### Raw bundle

Kept locally, never committed by default.

### Compact report

A single `summary.md` with:

```text
Scenario: bound-a
Result: FAIL
Duration: 42s
Profile: Pixel 7 Pro
Android user: 17

Critical failures
- build.model expected Pixel 7 Pro, got Pixel 8
- settings.android_id did not change after switch

Warnings
- camera.characteristics blocked by permission

Artifacts
- phase-05-probes.json
- phase-08-diff.json
```

The normal agent should return only this compact report unless the expensive
agent asks for a specific raw file.

## Delegation model

### Normal agent owns

- running scripts,
- capturing evidence,
- summarizing failures,
- running test suites,
- preparing release notes,
- publishing after a green gate.

### Dirty agent owns

- deciding what a failing row means,
- changing hook/engine behavior,
- changing probe classifications,
- deciding whether an observed value is safe to trust,
- making narrow fixes based on the compact report.

## Build sequence

### Sprint T1 — harness skeleton

Add:

- `tools/run-profile-check.ps1`
- stable phase names
- artifact naming
- JSON/JSONL schema
- `summary.md`

No behavior changes.

### Sprint T2 — probe expansion

Extend the diagnostics probe to collect all categories above with stable IDs.

Add:

- expected-value injection,
- status classification,
- compact diff mode,
- local-only output path,
- unit tests for parsing/classification.

### Sprint T3 — switch workflow

Add a scripted sequence for:

1. select profile,
2. bind,
3. publish,
4. launch,
5. capture probes,
6. switch Android user,
7. capture probes again,
8. cold restart,
9. capture third probe,
10. diff all three.

### Sprint T4 — failure-driven fixes

Only after the harness is stable:

- read the compact report,
- fix the highest-confidence failing rows,
- rerun only the affected phase,
- update the coverage ledger.

### Sprint T5 — release gate

Release only when:

- all unit/schema tests pass,
- one small scenario passes,
- one large 300+ app scenario passes,
- switch diff has no unexpected unchanged row,
- no new native crash occurs,
- artifacts can be regenerated from one command.

## Worker prompt A — harness scaffold

```text
Please add a repeatable Android test harness to the Cyclone companion project.

The goal is to make live debugging cheap and deterministic. Create `tools/run-profile-check.ps1` with stable phases:

1. environment
2. bind
3. publish
4. launch
5. probes
6. switch
7. post-switch-probes
8. diff
9. crash-log
10. summary

Requirements:

- each phase can run independently,
- each phase writes a separate JSON/JSONL file,
- the full run writes one compact `summary.md`,
- artifacts go under `docs/runtime-evidence/<date>-<scenario>/`,
- no UI changes,
- no public API or storage schema changes,
- no new permissions,
- all existing tests must still pass.

The script should support:

```powershell
./tools/run-profile-check.ps1 -Scenario bound-a -Phase environment
./tools/run-profile-check.ps1 -Scenario bound-a -Phase all
```

It should use the connected device through ADB and should not ask for manual screenshots unless a phase fails. The output should be concise and easy to paste back.

Repo: C:/Users/Agent/Cyclone/cloak-repo
Branch: main
```

## Worker prompt B — probe and switch verification

```text
Please extend the existing diagnostics probe so it can verify profile-state changes without UI automation.

Add stable probe rows for:

- Build fields
- ro.product properties
- Build serial
- telephony identifiers
- SIM/carrier state
- Wi-Fi fields
- Bluetooth fields
- Settings.Secure.ANDROID_ID
- MediaDrm ID
- camera characteristics
- display metrics
- locale/timezone
- installed-package visibility
- process UID/user
- loaded runtime/module state

Each row must use this shape:

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

Then add a compare mode that accepts before/after reports and writes a stable diff. The diff must flag:

- expected changes that did not happen,
- unexpected changes that did happen,
- blocked/missing rows,
- rows whose values stayed identical when they should have changed.

Keep all evidence local to the app/device. Do not infer any external service verdict. Add unit tests for parsing, classification, and diff generation.

Repo: C:/Users/Agent/Cyclone/cloak-repo
Branch: main
```

## Worker prompt C — failure triage and release prep

```text
Please run the new profile-state test harness and return only the compact report.

Do not paste long logs unless a phase crashes.

Run:

1. a small scenario with one app,
2. a large scenario with the current full profile,
3. one Android-user switch,
4. one cold restart after the switch.

For each scenario return:

```text
Scenario:
Result:
Duration:
Critical failures:
Warnings:
Blocked rows:
Artifacts:
```

Then run the normal Android/module/schema test suites and prepare release notes. Do not publish until all tests and packaging checks pass. Do not change runtime behavior unless a failing test clearly requires it.

Repo: C:/Users/Agent/Cyclone/cloak-repo
Branch: main
```
