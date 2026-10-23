# Alpha.17 cross-user diagnostics verification

Result: **incomplete / FAIL**, 30.66 seconds. No platform verdict.

Device: Pixel 8, API 36. Companion and module both alpha.17 / code 41.
Existing full secondary user 17 started normally and reached RUNNING_UNLOCKED;
no forced unlock, user creation, reboot or production APK/module install.
Foreground returned to user 0; user 17 remains running in the background.

The diagnostics APK was built in a clean detached checkout of exact tag
v0.11.0-alpha.17, commit `25d6ea906be856ef96dd88aa02ad67d3d4c1ef2a`.
Build: successful, 37 seconds, all 38 tasks executed. Local and installed APK
SHA-256 in BOTH users:
`53f1f033096b03879f621af9d3c8cd8b9cfcf4d7352080dec590bf05ca27cbbe`.

## Captures

| Requested phase | User | Fixture | PASS | OBSERVED | BLOCKED | FAIL | MISSING |
|---|---:|---|---:|---:|---:|---:|---:|
| Initial | 0 | A | 82 | 7 | 0 | 0 | 0 |
| Secondary warm | 17 | B | 81 | 4 | 4 | 0 | 0 |
| Secondary cold | 17 | B | 81 | 4 | 4 | 0 | 0 |
| Return warm | 0 | A | not completed | — | — | — | — |
| Return cold | 0 | A | not run | — | — | — | — |

Retained primer captures prove process reuse for the completed warm captures;
the secondary cold capture proves replacement. Primers are excluded from the
three-completed-phase aggregate: 244 PASS, 15 OBSERVED, 8 BLOCKED, zero FAIL/MISSING
rows. Classification FAIL includes one **collection-phase** failure and four
distinct permission warnings. It is not a failed profile-field comparison.
Both completed comparison edges passed bridge stability; the return legs are
unverified. All five retained reports (three requested, two primers) passed
reported user/UID, fixture label, profile hash and published-body hash checks.

Secondary-user precise denials (permissions were not changed):

- wifi.ssid / wifi.bssid: PERMISSION_DENIED:android.permission.ACCESS_FINE_LOCATION
- bluetooth.address / bluetooth.name: PERMISSION_DENIED:android.permission.BLUETOOTH_CONNECT

## Failure and limits

Returning to user 0 succeeded, but its warm-primer capture was rejected by the
tag harness as `INVALID_OR_DUPLICATE_ROW_ID`. The exact-tag probe was not changed.
The malformed raw report was not retained: the tag harness validates before
redaction, and its temporary report was removed during cleanup. Therefore the
specific duplicate ID and failed report's profile/hash cannot be established.
Do not claim the return-to-owner path is verified or silently retry it as PASS.

Possible cause, not proven: the probe's onNewIntent resets the activity-level row
array and starts another asynchronous reader. Activity restoration around a user
switch could overlap that work. Investigate only in a separate diagnostics fix;
there is no evidence here of a production module or binding regression.

## Preservation and cleanup

Before/after audits match: 327 production entries (including index bytes), 654
published body files, 1310 publisher-share files, 3 module files and 2 accessible
private binding-store files. File paths/entry keys are hashed; profile contents
are absent. Other locked users' private stores are outside the audit.
Both diagnostics-only overlay entries, both body directories, scratch/staging
files and both probe-private report/expected-profile files were removed using
the tag harness's cleanup path. No lock inode was deleted.
Runtime grants/flags across all recorded users and app-op modes for users 0/17
match exactly. No permissions were granted/revoked. No production connector calls
or profile/binding-store writes were performed by the tooling. Ordinary user
start/switch can run existing profile/system apps; audits cover the listed files.
Module digest is an installed-file hash, not in-process binary identity proof.

## Files and commands

New diagnostics-only files: tools/run_user_transition.py,
forge/tests/test_user_transition.py, and this handoff. No production source or
metadata changed; no commit/release was requested. Older handoffs are untouched.

```powershell
$env:JAVA_HOME='C:/Program Files/Eclipse Adoptium/jdk-17.0.20.101-hotspot'
$env:ANDROID_HOME='C:/Users/Agent/android-sdk'
& "$env:TEMP/cloak-build/gradle-8.11.1/bin/gradle.bat" -p "$env:TEMP/cloak-alpha17-clean-probe-20261017/android" :probe:assembleDiagnosticsDebug --offline --no-daemon --console=plain
uv run -- python -X utf8 tools/run_user_transition.py --source-checkout "$env:TEMP/cloak-alpha17-clean-probe-20261017" --adb C:/Users/Agent/android-sdk/platform-tools/adb.exe --serial 3B171FDJH0061G --secondary 17 --output docs/runtime-evidence/2026-10-17-alpha17-cross-user-025936
uv run --with pytest -- python -X utf8 -m pytest forge/tests/test_user_transition.py -q
uv run --with pytest -- python -X utf8 -m pytest forge/tests -q
uv run -- python -X utf8 -m unittest discover -s android/probe/tests -v
git diff --check
```

Tests: 18 focused orchestration cases; full forge 429 passed (includes those 18);
7 probe host tests passed. No compiler warnings in the clean diagnostics build.
Production Android suites were not rerun for this host-tooling-only extension.

Evidence: docs/runtime-evidence/2026-10-17-alpha17-cross-user-025936/.
The bundle contains redacted reports, fixture/hash checks, full hashed before/after
inventories, permission states, cleanup proof, provenance and the executed runner.
Historical alpha.16/Run 3 evidence is unchanged.
