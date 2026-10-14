# Diagnostics backend handoff — 2026-10-14

## Scope and outcome

Started from current main `99c3fca` (fast-forwarded from alpha.7-era local main
after fetching). Added a local diagnostics leaf, bounded asynchronous registry,
automatic capped file rotation, crash snapshot/recovery, path instrumentation,
an expandable Health panel, redacted JSON/ZIP export, safe probe metadata and a
read-only PowerShell support collector. Native module source is unchanged.

Profile/binding formats, storage layout, connector protocol, reconciliation and
publication decisions, permissions and release metadata/workflow are unchanged.
The diagnostics directory is separate app-private evidence storage. No device
commands, cleanup, restarts, networking, release, commit or push were performed.
The two unrelated pre-existing untracked handoff files were preserved.

## Changed files

New files:

- `android/app/src/main/java/dev/cyclone/cloak/diagnostics/DiagnosticsRegistry.kt`
- `android/app/src/main/java/dev/cyclone/cloak/diagnostics/LogBus.kt`
- `android/app/src/main/java/dev/cyclone/cloak/ui/DeveloperDiagnostics.kt`
- `android/app/src/test/java/dev/cyclone/cloak/diagnostics/DiagnosticsRegistryTest.kt`
- `android/app/src/test/java/dev/cyclone/cloak/ui/DeveloperDiagnosticsTest.kt`
- `tools/diagnose.ps1`
- `tools/tests/test_diagnose.py`
- `docs/DEVELOPER-DIAGNOSTICS.md`
- This handoff.

Instrumentation/integration:

- `android/app/src/main/AndroidManifest.xml`: Application startup hook only.
- `android/app/src/main/java/dev/cyclone/cloak/data/CloakStore.kt`
- `android/app/src/main/java/dev/cyclone/cloak/data/CloakBindingStore.kt`
- `android/app/src/main/java/dev/cyclone/cloak/cyclone/CycloneBridge.kt`
- `android/app/src/main/java/dev/cyclone/cloak/cyclone/SyncEngine.kt`
- `android/app/src/main/java/dev/cyclone/cloak/cyclone/Reconcile.kt`
- `android/app/src/main/java/dev/cyclone/cloak/root/CloakResolver.kt`
- `android/app/src/main/java/dev/cyclone/cloak/root/CloakRootDoctor.kt`
- `android/app/src/main/java/dev/cyclone/cloak/ui/CloakViewModel.kt`
- `android/app/src/main/java/dev/cyclone/cloak/ui/HealthScreen.kt`: diagnostics entry.
- `android/app/src/test/java/dev/cyclone/cloak/ArchitectureTest.kt`: validates the
  diagnostics leaf and explicitly permits downward diagnostics dependencies.
- `android/probe/src/main/java/dev/cyclone/cloak/probe/ProbeActivity.java`: logs
  row/status/timestamp/process/thread, never actual/expected values.

## Exact verification commands

```powershell
$env:JAVA_HOME='C:/Program Files/Eclipse Adoptium/jdk-17.0.20.101-hotspot'
& "$env:TEMP/cloak-build/gradle-8.11.1/bin/gradle.bat" -p android :app:testDebugUnitTest --tests '*DeveloperDiagnosticsTest' --tests '*DiagnosticsRegistryTest' --offline --no-daemon
& "$env:TEMP/cloak-build/gradle-8.11.1/bin/gradle.bat" -p android :app:testDebugUnitTest :module:testDebugUnitTest :app:assembleDebug :probe:assembleDebug --offline --no-daemon
uv run --with pytest -- python -m pytest forge/tests -q
uv run --with pytest --with jsonschema -- python -m pytest forge/schema_tests -q
uv run --with pytest -- python -m pytest tools/tests -q
git diff --check
```

Focused tests: **14 passed** (11 registry, 3 Compose). Forge: **227 passed**.
Schema: **33 passed**. PowerShell helper: **3 passed**, synthetic local logs only.
Final Android results: **420 app tests: 397 passed, 23 existing Windows
POSIX-shell skips**, zero failures/errors; **12 module tests passed**. Debug app,
module packaging and both standard/diagnostics probe variants built successfully.
`git diff --check` passed. Final compilation produced **no compiler warnings**.
Earlier interrupted/failing runs emitted a Kotlin daemon-session tooling warning;
it did not recur in the successful final run. No new dependencies or permissions.

The first compile exposed absent BuildConfig generation; version lookup now
uses package metadata without changing build settings. Early test passes caught
the architecture allowlist and a Compose modal idle timeout. The final surface
is an inline panel with pushed StateFlow updates, not periodic polling. Old-API
thread-ID access is documented/suppressed narrowly to avoid deprecated API
warnings while retaining supported Android compatibility. No tests were removed
or bypassed; the test vocabulary was extended for the new leaf.

## Public surface and evidence format

`LogBus.event(...)`, `LogBus.trace(...)`, `DiagnosticsRegistry.event(...)`,
`events()`, `recentEvents`, `counters()`, `flush()`, `crash()`, `bundle(...)` and
`zip(...)`. Severity: INFO/WARNING/ERROR; transitions:
STARTED/COMPLETED/DEGRADED/FAILED/CRASH. JSON bundle schema version 1.

256-item queue/ring/maps. `no_backup/diagnostics/{recent,previous,crash}.jsonl`
each capped at 256 KiB. Fixed event messages; reason codes and exception classes
only. Export has versions, API/ABI, events, counters, retained failures, current
observed actions and drop/persistence/flush status. See
[developer diagnostics](DEVELOPER-DIAGNOSTICS.md) for usage and privacy limits.

## Known limits

- No physical-device verification. Probe and helper changes were exercised
  through builds and synthetic logs, not a connected phone.
- Native module events are normalized from existing logcat by the helper;
  the app has no new privileged log-reading permission or runtime transport.
  Legacy timestamps are marked as collection time, missing metadata as unknown.
- Abrupt kills, ANRs, native crashes, power loss and storage failure may prevent
  crash snapshots. Counters/actions are process-session evidence; overflow and
  bounded flush completeness are explicit.
- A document provider can leave a partial exported destination after an I/O
  error. The panel reports failure/retry; profile and binding storage is untouched.
- No platform verdict is inferred. No release was published.
