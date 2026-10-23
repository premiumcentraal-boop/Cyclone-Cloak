# Diagnostics reversibility handoff — 2026-10-18

## Outcome

The device reversibility sequence completed all eight captures in **57.92 seconds**.
Cold restoration, production preservation and temporary-state cleanup passed.
The overall runner result remains **FAIL / PERMISSION_STATE_CHANGED** because of
a host-side permission classifier defect. Do not relabel the recorded result.

Repository: `C:\Users\Agent\Cyclone\cloak-repo`, branch `main`.
Runner commit: `b58ac1cd4f2b40073e94fe4634be51bcf431228e`.
APK/source tag: `v0.11.0-alpha.18`, commit
`fb1e3c6ae2e4caca56778a54d8025dba696b53b8`.
Installed companion/module: alpha.18 / code 42. Only diagnostics was installed
by this run; no production APK or module installation occurred.

## Exact defect and next fix

`tools/run_probe_reversibility.py::effective_permission_state(before, after)`
requires both allowlisted AppOps entries to be **new additions for every user**.
Both records already existed from the earlier run, so identical before/after
snapshots were rejected. Actual permission/AppOps delta: **none**.

The next change should be limited to this diagnostics classifier and its tests:

- Accept identical snapshots, including when the allowed records already exist.
- Accept any subset of these newly added records, only for the diagnostics package:
  `READ_DEVICE_IDENTIFIERS=deny` and
  `USE_ICC_AUTH_WITH_DEVICE_IDENTIFIER=default`.
- Reject every removal or change to an existing mode, including allowed operations.
- Reject every other addition, grant/flag change, package/user-set change, or
  production-package delta. Check package sets on both sides.
- Normalize tuple/list representation if necessary so JSON snapshot replay and
  in-memory comparisons agree, without weakening duplicate/malformed checks.
- Add regression tests for unchanged snapshots, already-present allowed entries,
  one/both allowed additions, wrong modes, removals, unrelated additions,
  production changes and JSON round-trip replay.

Do not reset AppOps or alter phone permissions to make the test pass. No production
runtime, connector, binding storage, module, schema or release changes are needed.

## Lock-screen check

After the owner unlocked the phone, a separate read-only check showed:

- Current Android user: 0.
- Users 0 and 17: `RUNNING_UNLOCKED`.
- Keyguard: `showing=false`, `mIsShowing=false`.

Both users were also unlocked at run preflight. The recorded audit error is
explained by the classifier rejecting identical snapshots; a locked screen does
not explain that code path. Recheck current user states before another run.

## Capture results

Counts below are the original probe's fixture comparison statuses. Unbound
fixture mismatches are expected; cold restoration compares actual hashes with
the same user's unbound baseline, rather than treating those mismatches as errors.

| Phase | User | Fixture | PASS | FAIL | OBSERVED | BLOCKED | MISSING | Reused process |
| --- | ---: | --- | ---: | ---: | ---: | ---: | ---: | --- |
| Baseline cold | 0 | A | 27 | 47 | 7 | 8 | 0 | false |
| Baseline cold | 17 | B | 15 | 58 | 4 | 12 | 0 | false |
| Bound cold | 0 | A | 82 | 0 | 7 | 0 | 0 | false |
| Bound cold | 17 | B | 81 | 0 | 4 | 4 | 0 | false |
| Post-removal warm | 0 | A | 82 | 0 | 7 | 0 | 0 | true |
| Post-removal warm | 17 | B | 81 | 0 | 4 | 4 | 0 | true |
| Post-removal cold | 0 | A | 27 | 47 | 7 | 8 | 0 | false |
| Post-removal cold | 17 | B | 15 | 58 | 4 | 12 | 0 | false |

Warm post-removal processes retained fixture values, as expected for in-memory
hooks. Cold restoration comparisons had no FAIL rows:

- User 0: 73 PASS, 8 OBSERVED, 8 BLOCKED.
- User 17: 72 PASS, 5 OBSERVED, 12 BLOCKED.

Blocked, unavailable and external-state observations remain non-passes.

## Preservation and cleanup

Production before/after audits matched exactly: **327 index entries**, **654
published body files**, **1310 publisher-share files**, **3 module files**, and
**2 accessible private binding stores**. Permission grants/flags, install grants
and recorded AppOps modes were identical for diagnostics and the production app.

Cleanup proof is true for both users: temporary binding entries removed,
temporary body directories removed, private expected-profile/report/temp files
removed, and run scratch files removed. Original foreground user 0 was restored.
No reboot, user creation/removal, data clearing, permission grant/revoke, or
AppOps set/reset command was performed.

## Evidence and APK provenance

New run directory (preserve all contents):

`docs/runtime-evidence/2026-10-18-005705-alpha18-reversibility-b58ac1c/`

- `complete-evidence-bundle.zip`: original runner ZIP plus analysis and build/run logs.
- `evidence-bundle.zip`, `summary.md`: original runner output, retained unchanged.
- `permissions-before.json`, `permissions-after.json`: identical snapshots.
- `audit-before.json`, `audit-after.json`: matching production audits.
- `cleanup-proof.json`: all checks true.
- `audit-analysis.json`: exact failing phase and empty permission delta.
- `execution-provenance.json`: actual build command, checkout, runner/APK hashes.
- `user-0-cold-restoration.json`, `user-17-cold-restoration.json`: paired results.
- Eight per-phase capture JSON files, overlay add/remove records and manifest.

Complete ZIP SHA-256:
`f1379ae86dfa49bd1b20b52f1f7f6f989258e5b3cdb9bdddb9cd81d4be77339a`.

Clean detached checkout:
`C:\Users\Agent\AppData\Local\Temp\cloak-alpha18-reversibility-clean-1792277762`.

Diagnostics APK SHA-256, verified against installed APKs for users 0 and 17:
`3654e66710ba64fb7229132c69b44424976b578736c343d4c4219013d59294bb`.

The runner-generated `build-provenance.json` has an older hard-coded build path.
Use `execution-provenance.json` for the actual command and new checkout; the
original provenance file was preserved rather than silently edited.

Earlier failed evidence was not overwritten. The new run was **not committed**
because the user's instruction was to commit evidence only on success. This
handoff is a new local Markdown file; no code fix, tag or release was made.

## Commands and checks

```powershell
$env:JAVA_HOME='C:/Program Files/Eclipse Adoptium/jdk-17.0.20.101-hotspot'
$env:ANDROID_HOME='C:/Users/Agent/android-sdk'
& "$env:TEMP/cloak-build/gradle-8.11.1/bin/gradle.bat" -p "$env:TEMP/cloak-alpha18-reversibility-clean-1792277762/android" :probe:assembleDiagnosticsDebug :probe:testDiagnosticsDebugUnitTest --offline --no-daemon --console=plain
uv run --with pytest -- python -X utf8 -m pytest forge/tests/test_probe_reversibility.py -q
```

Build successful; **91 diagnostics probe tests** and **31 existing runner tests**
passed. No compiler warnings. An additional negative control confirmed the
classifier returns false for identical snapshots; that missing regression case
must be covered by the fix.

Rerun command (use a fresh output directory, never the previous one):

```powershell
uv run -- python -X utf8 tools/run_probe_reversibility.py --source-checkout "$env:TEMP/cloak-alpha18-reversibility-clean-1792277762" --adb C:/Users/Agent/android-sdk/platform-tools/adb.exe --serial 3B171FDJH0061G --secondary 17 --output "docs/runtime-evidence/<new-Europe-Berlin-timestamp>-alpha18-reversibility"
```

After the classifier fix, record its new runner commit separately while keeping
APK/source verification pinned to the clean alpha.18 tag. Recheck both users are
already unlocked; do not start or force-unlock unavailable users. Preserve all
snapshots, capture files and cleanup proof. If the rerun passes, commit only the
new evidence directory as originally requested; on failure stop and report the
exact phase, delta and cleanup result.

## Limits

This is local diagnostic evidence, not a platform verdict. Locked users' private
stores remain outside the audit. AppOps access times/counters are excluded from
mode comparison. Installed module file hashes do not prove in-process binary
identity. Normal Android user switching does not verify Cyclone bootstrap.
