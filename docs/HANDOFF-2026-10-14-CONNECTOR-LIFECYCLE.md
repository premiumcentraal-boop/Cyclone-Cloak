# Sprint A — connector session lifecycle — 2026-10-14

## Result

Added one process-owned reconnect coordinator, immediate display-only registry restoration, and a persistent connection chip with details and retry. Existing wire versions, connector SDK, binding algorithms, publication scripts, identity files, permissions, and release metadata remain unchanged. No commit or release was requested.

After an APK update, Android's `MY_PACKAGE_REPLACED` notification starts verification without a tap. Process start, activity resume, the existing protected Cyclone registry wake, Cyclone package changes, and completion of the existing module repair/check path also trigger verification. Rapid triggers coalesce; an in-flight attempt permits at most one follow-up. Five attempts per cycle use exponential jittered delays, beginning at 250 ms (before jitter), capped at four seconds. Existing bridge serialization also prevents overlap with explicit sync calls.

Background attempts do not request new root approval. Manual retry and resume retain the existing foreground root policy. Successful connections re-register the existing startup provider. Each attempt emits trigger reason, attempt number, and outcome through LogBus. Permanent approval/version/install failures give directions rather than looping; exhausted transient attempts show Connection paused with Retry.

## Cache and UI

The existing app-private `cloak` preferences gain `registrySnapshot` and `registrySeenAt`. Snapshot and timestamp are written in one preference commit. Only registry metadata is cached; approval, scope grants, identity contents, and credentials are not cached. Corrupt, oversized, or implausibly future-dated entries are ignored. Existing preference keys and profile storage are preserved.

The top-bar chip is also visible during binding and profile editing. It shows Connected, Reconnecting (attempt N), Needs update, Not installed, Needs approval, or Connection paused. One tap opens the reason and Retry. Cached rows remain visible with Last seen while live verification runs; cached data does not authorize binding or drive reconciliation. Handshake messages identify the outdated side when the existing contract major/minor provides that evidence; missing legacy contract fields remain accepted.

## Changed files

All paths below are relative to the repository.

- `android/app/src/main/AndroidManifest.xml`: protected system package/update receiver registration; no permissions added.
- `android/app/src/main/java/dev/cyclone/cloak/ConnectorService.kt`: route existing registry wakes through the coordinator.
- `android/app/src/main/java/dev/cyclone/cloak/MainActivity.kt`: resume trigger.
- `android/app/src/main/java/dev/cyclone/cloak/cyclone/ConnectionLifecycle.kt`: application ownership, trigger wiring, provider registration, bounded broadcast lifetime, diagnostics.
- `android/app/src/main/java/dev/cyclone/cloak/cyclone/ReconnectCoordinator.kt`: single-flight scheduling, outcomes, retry policy.
- `android/app/src/main/java/dev/cyclone/cloak/cyclone/RegistryCache.kt`: safe display cache.
- `android/app/src/main/java/dev/cyclone/cloak/cyclone/ConnectorHandshake.kt`: existing handshake interpretation and actionable errors.
- `android/app/src/main/java/dev/cyclone/cloak/cyclone/CycloneApi.kt`: apply handshake interpretation locally.
- `android/app/src/main/java/dev/cyclone/cloak/cyclone/SyncEngine.kt`: preserve failure code in the local SyncReport; synchronization algorithm unchanged.
- `android/app/src/main/java/dev/cyclone/cloak/diagnostics/LogBus.kt`: process-start trigger.
- `android/app/src/main/java/dev/cyclone/cloak/ui/ConnectionStatusChip.kt`: compact connection details and retry.
- `android/app/src/main/java/dev/cyclone/cloak/ui/CloakApp.kt`: chip placement.
- `android/app/src/main/java/dev/cyclone/cloak/ui/CloakViewModel.kt`: observe coordinator, restore display rows, module-check trigger.
- `android/app/src/main/java/dev/cyclone/cloak/ui/HomeAttention.kt`: cached-state attention text.
- `android/app/src/test/java/dev/cyclone/cloak/cyclone/ReconnectCoordinatorTest.kt`: 12 scheduling/cache/handshake/outcome tests.
- `android/app/src/test/java/dev/cyclone/cloak/cyclone/RegistryCacheStoreTest.kt`: two preference persistence/corruption tests.
- `android/app/src/test/java/dev/cyclone/cloak/ui/ConnectionStatusChipTest.kt`: three visible-state/details/retry tests.
- This handoff.

## Verification

PowerShell, repository root:

```powershell
$env:JAVA_HOME='C:/Program Files/Eclipse Adoptium/jdk-17.0.20.101-hotspot'
$env:CLOAK_TEST_WSL='1'
& "$env:TEMP/cloak-build/gradle-8.11.1/bin/gradle.bat" -p android :app:compileDebugKotlin --offline --no-daemon
& "$env:TEMP/cloak-build/gradle-8.11.1/bin/gradle.bat" -p android :app:testDebugUnitTest :module:testDebugUnitTest :app:assembleDebug --offline --no-daemon
uv run --with pytest -- python -m pytest forge/tests -q
uv run --with pytest --with jsonschema -- python -m pytest forge/schema_tests -q
git diff --check
& C:/Users/Agent/android-sdk/platform-tools/adb.exe devices
```

Results: 470 app tests (17 added), 12 module tests, 227 forge tests, 33 schema tests; debug APK built at `android/app/build/outputs/apk/debug/app-debug.apk`. The combined Gradle command was repeated after preserving the foreground policy for manual/resume triggers. No compiler warnings. Git reports only an existing line-ending normalization notice for LogBus.kt. No device was attached; update/reboot timing is unverified.

## Boundaries and follow-ups

- Direct boot notification is not implemented: this manifest does not hold `RECEIVE_BOOT_COMPLETED`, which Android requires for `BOOT_COMPLETED`. Adding it would violate the sprint's no-new-permissions constraint. Reconnection after boot depends on the existing Cyclone wake or the next process launch/resume. See Android's [permission reference](https://developer.android.com/reference/android/Manifest.permission#RECEIVE_BOOT_COMPLETED).
- External module changes do not broadcast a supported notification to this app. The existing in-app check/repair completion triggers reconnection; changes outside Cloak are checked on the next process/resume/Cyclone wake. No module code was changed.
- Broadcast pending results finish after an attempt or eight seconds. Later retries are best-effort while Android keeps the process alive; there is no new background service or durable scheduled work.
- The two older untracked handoffs were preserved unchanged and unstaged. No device installation, reboot, data clearing, or release work occurred.
