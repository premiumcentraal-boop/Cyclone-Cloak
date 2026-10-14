# Batch privileged-shell session — 2026-10-14

Work is on local `main`, based on `d60a4a6`. No commit, release, device install,
reboot, data clear or binding changes were performed during verification.

## Change

Binding confirmation, retry, direct bind, fleet bind, enable/disable and removal
now open one `su --mount-master` session before touching bindings. A read-only
`id -u` preflight must confirm UID 0 before the existing work callback executes.
Denial, missing su or expiry aborts before any binding mutation.

All synchronous Root Doctor publication and mirror calls in that batch reuse
the session. Existing scripts run unchanged through stdin in child `sh` shells;
these are not additional su grants. Unique completion markers preserve output
and exit codes, and a script's `exit`/`set -eu` cannot close the parent session.
One configurable deadline (`budgetMs`, default 30,000) applies to the batch,
including its initial approval wait. No per-command approval deadline resets it.
Standalone Root Doctor operations retain their existing operation budgets.

After a one-second command delay the existing busy/note surface says waiting for
approval. The same session remains alive and completion resumes the UI. This is
a shell-wait indication, not direct inspection of Magisk's prompt. A denied or
terminated su process fails; it is not silently reopened to create more prompts.
Root diagnostics record fixed preflight/wait/resume reasons without script bodies.

Secondary-user results include a one-line own-profile approval hint, suggesting
grant until reboot if fewer prompts are wanted. Timed-out preflight clears the
waiting note and shows the existing approval-timeout state with explicit Magisk
instructions. Existing saved records remain available for retry.

Source correction: current main already saves multi-selected apps together and
publishes once. The old transport opened an independent su process for each
Root Doctor invocation, including later sync/mirror work, rather than an explicit
su loop per selected app. The reported seven prompts were not reproducible
without the phone. This change guarantees one su process for the synchronous
binding batch regardless of the number of root commands.

## Changed files

- `android/app/src/main/java/dev/cyclone/cloak/root/PrivilegedSession.kt`:
  persistent transport, preflight, shared deadline, worker-thread session scope.
- `android/app/src/main/java/dev/cyclone/cloak/root/CloakRootDoctor.kt`:
  use the shared transport; clearer timeout instructions. Result parsers unchanged.
- `android/app/src/main/java/dev/cyclone/cloak/ui/CloakViewModel.kt`:
  preflight around existing binding work, approval progress and per-user hint.
- `android/app/src/test/java/dev/cyclone/cloak/root/PrivilegedSessionTest.kt`:
  nine focused tests, including a real POSIX shell via WSL.
- This handoff.

Publication, merge, recovery and lock scripts were compared with HEAD and are
unchanged. Module, schema, storage, connector and permissions are untouched.
The two older untracked UI/profile-app handoffs remain untouched and unstaged.

## Commands and results

Run from `C:/Users/Agent/Cyclone/cloak-repo`:

```powershell
$env:JAVA_HOME='C:/Program Files/Eclipse Adoptium/jdk-17.0.20.101-hotspot'
$env:CLOAK_TEST_WSL='1'
& "$env:TEMP/cloak-build/gradle-8.11.1/bin/gradle.bat" -p android :app:compileDebugKotlin --offline --no-daemon
& "$env:TEMP/cloak-build/gradle-8.11.1/bin/gradle.bat" -p android :app:testDebugUnitTest --tests '*PrivilegedSessionTest' --offline --no-daemon
& "$env:TEMP/cloak-build/gradle-8.11.1/bin/gradle.bat" -p android :app:testDebugUnitTest :module:testDebugUnitTest :app:assembleDebug --offline --no-daemon
uv run --with pytest -- python -m pytest forge/tests -q
uv run --with pytest --with jsonschema -- python -m pytest forge/schema_tests -q
git diff --check
& C:/Users/Agent/android-sdk/platform-tools/adb.exe devices
```

453 app tests (nine new), 12 module tests, 227 forge tests and 33 schema tests
passed; zero failures and zero skips in the Android reports. Debug build passed.
The debug APK is `android/app/build/outputs/apk/debug/app-debug.apk`.

During development, the first run caught a reader-start name-shadowing bug;
the process-launch injection is now named `startProcess`, and `Thread.start()`
is explicit. The corrected focused suite passed all nine tests. An overlapping
Gradle attempt hit a Windows file lock and Kotlin daemon-session warning; it
was replaced by serial verification. A test worker running stale compiled code
was stopped before the corrected run. Final serial builds had no compiler warnings.

## Expected device behavior and limitations

Reported before: stacked prompts for a seven-app batch, serial waiting and
approval expiry after local saves. Expected after: at most one initial grant
request for the batch's Android-user UID (zero if already granted), no binding
work before that grant, and no extra su requests for later commands. Seven
commands plus preflight were tested with exactly one process launch. Once
granted, runtime depends on existing publication/connector work; a seconds-level
phone timing has not been measured.

`adb devices` returned no device. Actual Magisk interaction, grant persistence
and seven-app timing are **UNVERIFIED**. Verification on the phone: confirm seven
apps, approve Cyclone Cloak once for that user, observe binding readiness, then
repeat with a denied preflight and confirm existing bindings remain unchanged.
No APK-installability claim is made by this sprint.
