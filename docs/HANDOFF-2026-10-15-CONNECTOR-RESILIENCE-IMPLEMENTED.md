# Connector resilience and run-history implementation

Date: 2026-10-15

## Result

The existing connection coordinator and binding-run journal are hardened in
place. Reconnect skips root reads/publication, so a healthy connector does not
wait for a separate Magisk approval on app resume or manual reconnect. Existing
single-flight scheduling, bounded backoff, cached registry, and authorization
gates remain in use. The connection dialog gives one next step and does not offer
another Retry while reconnecting. Expired connector confirmation is a waiting
for user action state, rather than an automatic confirmation loop.

Binding history now displays active attempts, retains checkpoints at approval
wait/resume, local save, publication, and sync, and recovers unfinished attempts
as needs-attention. Records carry start/end time, duration, selection, final
state, first observed failure code, optional retry reference, reconciliation
counts, handshake observations, and phase markers. A failed final publication
cannot be masked by an earlier successful publication during the same sync.
Missing reconciliation or connector failure cannot promote a run to ready.

Unchanged saved bindings retry publication/reconciliation without recreation.
An unsaved bind can reuse a valid in-memory review acknowledgement; historical
unsaved selections return to review. Changed or ambiguous targets require
review. No binding/identity file is restored from a diagnostic record.

## Changed files

Paths below are relative to `android/app/src/main/java/dev/cyclone/cloak/`:

- `cyclone/ConnectionLifecycle.kt`
- `cyclone/CycloneBridge.kt`
- `cyclone/ReconnectCoordinator.kt`
- `diagnostics/BindingRunJournal.kt`
- `ui/BindingFlow.kt`
- `ui/BindingFlowScreen.kt`
- `ui/BindingRunBundle.kt`
- `ui/BindingRunResult.kt`
- `ui/BindingRunsSection.kt`
- `ui/CloakViewModel.kt`
- `ui/ConnectionStatusChip.kt`

Tests under `android/app/src/test/java/dev/cyclone/cloak/`:

- `cyclone/ReconnectCoordinatorTest.kt`
- `diagnostics/BindingRunJournalTest.kt`
- `diagnostics/BindingRunResultTest.kt`
- `diagnostics/BindingRunBundleTest.kt`
- `ui/BindingFlowTest.kt`
- `ui/BindingRunCardTest.kt`

Documentation: `docs/ARCHITECTURE.md` and this handoff. The input handoff and
preexisting untracked handoffs/device evidence are preserved.

## Journal and bundle compatibility

Existing `no_backup/binding-runs/` paths, JSONL rotation, 200-run maximum, and
schema-version-1 envelope remain. Added fields are optional: `active`,
`end_timestamp`, `first_failure_reason`, `retry_of`, and `reconciliation`.
Old entries remain readable with defaults. Checkpoints use the existing
`pending.json` and `pending.json.new`, with a synced write and checked atomic
replacement; legacy `pending.json.bak` recovery remains supported. Failed
replacement retains the previous complete checkpoint. This also avoids the
silent rename-over-existing failure reproduced with AtomicFile on Windows.
If appending a recovered record fails, its pending checkpoint is retained for
the next recovery attempt rather than deleted.
Recovered end time means recovery observation time, not exact crash time.

Example ZIP contents:

```text
manifest.json
runs.json
diagnostics.json
bindings-index.json
module-state.json
connector-handshake.json
reconciliation.json
```

`reconciliation.json` adds historical written/status/cleared/issue counts and
fixed stop/failure codes. It excludes issue text. Existing redaction excludes
profile bodies, seeds, credentials, mirrored summaries, and raw shell/connector
output. Identifier references are normalized with the existing privacy helper.
Per-run and 24-hour exports retain time-window filtering; active runs can be
included through export time. Top-level snapshots describe export time; run
observations remain historical. The module-state hash is a state digest, not an
installed-binary digest. No platform verdict is claimed.

## Verification

Run from `C:/Users/Agent/Cyclone/cloak-repo`:

```powershell
$env:JAVA_HOME='C:/Program Files/Eclipse Adoptium/jdk-17.0.20.101-hotspot'
$env:CLOAK_TEST_WSL='1'
& "$env:TEMP/cloak-build/gradle-8.11.1/bin/gradle.bat" -p android :app:compileDebugKotlin --offline --no-daemon
& "$env:TEMP/cloak-build/gradle-8.11.1/bin/gradle.bat" -p android :app:testDebugUnitTest :module:testDebugUnitTest :app:assembleDebug --offline --no-daemon
& "$env:TEMP/cloak-build/gradle-8.11.1/bin/gradle.bat" -p android :app:testDebugUnitTest :module:testDebugUnitTest :app:assembleDebug --offline --no-daemon --rerun-tasks
uv run --with pytest -- python -m pytest forge/tests -q
uv run --with pytest --with jsonschema -- python -m pytest forge/schema_tests -q
git diff --check
```

Focused regression commands used during repair:

```powershell
& "$env:TEMP/cloak-build/gradle-8.11.1/bin/gradle.bat" -p android :app:testDebugUnitTest --tests '*BindingRunJournalTest' --tests '*BindingRunCardTest' --offline --no-daemon
& "$env:TEMP/cloak-build/gradle-8.11.1/bin/gradle.bat" -p android :app:testDebugUnitTest --tests '*BindingRunJournalTest' --tests '*BindingRunCardTest' --tests '*BindingRunResultTest' --tests '*ReconnectCoordinatorTest' --offline --no-daemon
```

Initial runs reproduced two failures: checkpoint replacement on the Windows
AtomicFile implementation, and a Compose test tapping below its viewport.
Checkpoint replacement now uses a checked atomic move, and the test scrolls to
the retry action as the history list allows. The final focused rerun passed.

Final results:

| Check | Result |
| --- | --- |
| App unit/Compose tests | 515 passed; 0 failed, 0 skipped (18 added tests) |
| Module unit tests | 12 passed; 0 failed, 0 skipped |
| Forge | 227 passed |
| Schema | 33 passed |
| Debug build | Passed; `android/app/build/outputs/apk/debug/app-debug.apk` |
| Whitespace | `git diff --check` passed |

The final Gradle command used `--rerun-tasks`: all 96 tasks executed, including
the module tests. The build log contains no compiler warnings. The WSL-backed
publication tests ran rather than being skipped. Final log:
`%TEMP%/cloak-resilience-all-rerun.log`. No source changes to `android/module`,
root/publication scripts, binding/profile stores, SyncEngine, manifest, or
connector SDK were needed.

## Limits and follow-up

- No device actions were performed. Update/restart latency, root prompt timing,
  and share-chooser behavior on user 17 remain unverified for this change.
- Reconnect is root-free; Main-binding mirror reads and publication still
  require the existing explicit privileged repair/binding path. A stale root
  status is not inferred healthy from a successful connector session.
- Recovery retains bounded checkpoint evidence, not every instruction before a
  process death. Storage errors are counted and shown in Health → Runs.
- History retry of an unsaved selection spanning multiple Cyclone profiles
  requires review in Profiles rather than recreating the fleet automatically.
- Protocol, permissions, binding/profile schemas, publication scripts, runtime
  module, engine internals, and release version are unchanged. No release was
  created; changes remain local for review.
