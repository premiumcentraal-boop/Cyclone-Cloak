# Companion copies in Cyclone profiles — 2026-10-14

## Findings

The installed alpha.9 launcher exists. Read-only device inspection found user 16
running and `RUNNING_UNLOCKED`; its ordinary launcher query resolved
`dev.cyclone.cloak/.MainActivity`. User 14 was not started. A launcher query for
that user with `--query-flags 786432` (both direct-boot activity classes) resolved
the same activity. A stopped/locked user is therefore not evidence of a missing
APK or manifest activity. Cloak deliberately keeps credential-encrypted storage;
this sprint does not make it direct-boot aware or move identity files.

The older alpha116 Cyclone checkout omitted Cloak from its support allowlist.
That was an initial investigation lead, not the current implementation. In the
user-selected `profile-inventory-alpha128` checkout, `ProfileCornerstones` already
discovers Cloak by connector ID and makes it a required Essential. Both profile
creation and bootstrap use that lookup. Bootstrap installs existing packages for
the target user, enables them, starts the user and requires `RUNNING_UNLOCKED`
before private settings/bootstrap verification. Keep that mechanism; do not add
a second installer in Cloak or grant a new install permission.

The earlier “cannot verify the receiving profile” message is a separate Cyclone
bootstrap acknowledgement failure. It is **not proven fixed** by launcher
resolution. The device disconnected before the acknowledgement could be
inspected. No user was started/switched, no APK installed, and no device data or
settings were changed in this sprint.

## Implementation

Health has a compact **Companion copies** card, with one row per Cyclone registry
profile. Each row shows the assigned Android user, installation, launcher,
private directory presence and write access, plus an actionable reason.
`Check companion copies` performs bounded, read-only root inspection of other
users. Reconnecting resets remote results rather than presenting old user
assignments as current facts. Concurrent refreshes cannot reuse a result for a
changed profile/user assignment.

States are ready, missing, not launchable, storage unavailable and not verified.
Missing/negative user assignments never fall back to user 0. Errors, incomplete
responses and duplicate markers stay unverified. The same user's facts are
queried once even if more than one registry entry references it. Diagnostics
record fixed state codes and Android user numbers, without profile contents.

Root directory access does **not** prove an app process can write there. The
cross-user writable field stays “not verified.” In the selected profile's own
Cloak, the check uses its actual application directory and process permissions;
all checks must pass before the row says ready. This is a read-only permission
check, not a test file write or a platform compatibility verdict.

Cyclone's provisioning lookup was extracted into a testable function and made
fail-closed for malformed/ambiguous connector candidates. Its existing
install/enable/start/unlock sequence and connector wire protocol are unchanged.

## Changed files

Cloak (`C:/Users/Agent/Cyclone/cloak-repo`, main):

- `android/app/src/main/java/dev/cyclone/cloak/cyclone/CompanionPresence.kt`
- `android/app/src/main/java/dev/cyclone/cloak/cyclone/CompanionPresenceProbe.kt`
- `android/app/src/main/java/dev/cyclone/cloak/ui/CompanionPresenceCard.kt`
- `android/app/src/main/java/dev/cyclone/cloak/ui/CloakViewModel.kt`
- `android/app/src/main/java/dev/cyclone/cloak/ui/HealthScreen.kt`
- `android/app/src/test/java/dev/cyclone/cloak/cyclone/CompanionPresenceTest.kt`
- `android/app/src/test/java/dev/cyclone/cloak/cyclone/CompanionPresenceProbeTest.kt`
- `android/app/src/test/java/dev/cyclone/cloak/ui/CompanionPresenceCardTest.kt`
- This handoff.

Cyclone (`C:/Users/Agent/.codex/worktrees/profile-inventory-alpha128/Cyclone`,
existing `codex/profile-inventory-alpha128` branch, explicitly selected by user):

- `apps/mobile/app/src/main/java/com/cyclone/mobile/runtime/workspaces/ProfileCornerstones.kt`
- `apps/mobile/app/src/test/java/com/cyclone/mobile/runtime/workspaces/ProfileCompanionProvisioningTest.kt`

Binding logic, publication scripts, connector protocol, identity handling,
storage layout, module, permissions and versions are untouched. Existing
untracked handoffs were preserved. No commit, push or release was requested.

## Verification commands

From Cloak, PowerShell:

```powershell
$env:JAVA_HOME='C:/Program Files/Eclipse Adoptium/jdk-17.0.20.101-hotspot'
$env:CLOAK_TEST_WSL='1'
& "$env:TEMP/cloak-build/gradle-8.11.1/bin/gradle.bat" -p android --offline --no-daemon :app:testDebugUnitTest :module:testDebugUnitTest :app:assembleDebug
uv run --with pytest -- python -m pytest forge/tests -q
uv run --with pytest --with jsonschema -- python -m pytest forge/schema_tests -q
git diff --check
```

From the selected Cyclone checkout:

```powershell
$env:JAVA_HOME='C:/Program Files/Eclipse Adoptium/jdk-17.0.20.101-hotspot'
$env:ANDROID_HOME='C:/Users/Agent/android-sdk'
& "$env:TEMP/cloak-build/gradle-8.11.1/bin/gradle.bat" -p apps/mobile --no-daemon :app:testDebugUnitTest
git diff --check
```

Initial attempts: Cloak's architecture guard caught a probe placed in the wrong
layer; it was moved to `cyclone`, with the guard preserved. The schema command
without `--with jsonschema` failed dependency collection; the corrected command
passes. Cyclone's first offline attempt could not resolve its AGP plugin; the
online build command above retries normal dependency resolution. These are not
waived tests.

Final results:

| Check | Result |
|---|---|
| Cloak app unit/Compose tests | 444 passed, 0 failures/errors/skips; 16 new tests |
| Cloak module tests | 12 passed, 0 failures/errors/skips |
| Forge tests | 227 passed |
| Schema tests | 33 passed |
| Cloak debug build | Passed; no compiler warnings |
| Cyclone Android unit tests | 2,640 passed, 0 failures/errors/skips; 4 new tests |
| `git diff --check`, both repositories | Passed |

Cloak final build log: `$env:TEMP/cloak-managed-final.log` (1m32s).
Cyclone log: `$env:TEMP/cyclone-companion-checks.log` (6m55s). Cyclone reports
53 existing compiler/build-script warnings, including deprecated `jvmTarget`,
Android system-bar APIs and unused parameters. None points to the changed
provisioning files. These unrelated warnings were not suppressed or changed.
The debug APK remains `android/app/build/outputs/apk/debug/app-debug.apk`.

Read-only device commands used (ADB executable
`C:/Users/Agent/android-sdk/platform-tools/adb.exe`, serial `3B171FDJH0061G`):

```text
adb -s 3B171FDJH0061G shell pm list users
adb -s 3B171FDJH0061G shell dumpsys package dev.cyclone.cloak
adb -s 3B171FDJH0061G shell cmd package resolve-activity --brief --user 16 -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -p dev.cyclone.cloak
adb -s 3B171FDJH0061G shell am get-started-user-state 16
adb -s 3B171FDJH0061G shell am get-started-user-state 14
adb -s 3B171FDJH0061G shell cmd package resolve-activity --brief --user 14 --query-flags 786432 -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -p dev.cyclone.cloak
adb -s 3B171FDJH0061G shell su --mount-master -c 'ls -ld /data/user/16/dev.cyclone.cloak /data/user/14/dev.cyclone.cloak; stat -c %u /data/user/16/dev.cyclone.cloak'
adb -s 3B171FDJH0061G shell su --mount-master -c 'su 1610377 -c "test -d /data/user/16/dev.cyclone.cloak/files && test -w /data/user/16/dev.cyclone.cloak/files && echo CLOAK_STORAGE_OK"'
adb -s 3B171FDJH0061G shell dumpsys package com.cyclone.mobile
adb -s 3B171FDJH0061G shell su --mount-master -c "cat /data/user_de/16/com.cyclone.mobile/files/profile-bootstrap-result.json"
adb devices
```

Directory listing confirmed user 16's private application directory. The stopped
user 14 directory was inaccessible; `stat` returned permission denied and the
UID-shell write-access experiment did not return its success marker. None is
accepted as proof of app-process writability. The last package/acknowledgement
reads failed with device-not-found; the acknowledgement collector would print
only `ok`, `user`, `stage`, and `reasonCode`, never the payload or nonce.
`adb devices` then returned an empty list. No new APK or provisioning change
was exercised on the device.

## How to check in Health

1. Use the updated apps when distributed; this sprint did not install them.
2. Open Cloak → Health → Reconnect to Cyclone to load the registry.
3. Select **Check companion copies**. Missing copies point to Cyclone's normal
   profile provisioning/app selection. Stopped/locked users say not launchable.
4. Open and unlock the target profile through Cyclone, then open that profile's
   Cloak → Health. Its own row can verify private directory write access and
   become ready. Running/unlocked and installed status alone do not suffice.
5. If Cyclone cannot verify the receiving profile, investigate its bootstrap
   failure separately. Do not work around it by rebooting, clearing data,
   relocating identity storage or changing bindings.
