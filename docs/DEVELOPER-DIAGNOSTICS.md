# Local developer diagnostics

Open **Health → Developer diagnostics**. The plain panel lists recent events
and filters subsystem, severity, action ID and text. Copy diagnostics produces
redacted JSON; Save diagnostics bundle creates a ZIP containing `diagnostics.json`
through Android's existing document picker. It never reads clipboard contents.

The bundle contains schema version 1, app version, last known installed module
version (null if not checked), API/ABI, recent events, process-session counters,
recent failures, current observed actions, dropped-event/persistence-failure
counts and flush completeness. Completion means an operation returned, not that
its result proved readiness. Separate attention events retain reason codes.
This is local debugging evidence, never a platform verdict.

## Registry and privacy

`LogBus.event(subsystem, action, transition, target, reason, error)` accepts only
structured metadata; callers must use fixed subsystem/action/reason vocabulary
and business profile IDs or package names as targets, never identity values.
`LogBus.trace(subsystem, action, target) { ... }` records start/end, elapsed time,
and exception class while preserving the original return value or thrown object.
Diagnostic events include epoch-millisecond timestamp, severity, source,
subsystem, correlated action ID, thread/process IDs, nullable target, fixed
message, transition, reason code, nullable exception class and duration.

There is no API for logging a profile body, account content, exception message,
stack trace, clipboard contents, tokens, keys, media or arbitrary message text.
Targets and labels are constrained; private profile display names are omitted.
Release and debug builds both default to the same concise, privacy-safe events.
The existing native log stream is not copied verbatim by the helper.

Producers offer to a 256-item queue without waiting. One daemon worker maintains
a 256-event ring and bounded counter/current-action maps. Storage is separate at
app-private `no_backup/diagnostics/`: `recent.jsonl`, one `previous.jsonl` rotation,
and `crash.jsonl`, each capped at 256 KiB (768 KiB total). No background service,
permission, network call or profile/binding storage change is introduced.
Crash handling attempts a capped, synced snapshot and delegates to the previous
uncaught-exception handler. Startup recovers valid rows, deduplicates and sorts
them by timestamp; malformed rows and oversized files are hidden safely.

Instrumented paths: application startup, profile read/load/save/import, binding
save, connector connect/sync, reconciliation (including retry reason codes),
publication/staging, module checks, local UI actions and exception handling.
The probe emits field/status/time/process/thread metadata without actual or
expected identity values. Module runtime behavior and native source are untouched.

## Read-only support helper

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File tools/diagnose.ps1 -Serial 3B171FDJH0061G -OutputDirectory C:/Temp/cloak-support
powershell -NoProfile -ExecutionPolicy Bypass -File tools/diagnose.ps1 -Serial 3B171FDJH0061G -Subsystem reconciliation -Severity WARNING -ActionId retry -Text RATE_LIMITED
powershell -NoProfile -ExecutionPolicy Bypass -File tools/diagnose.ps1 -InputLog C:/Temp/captured.log -OutputDirectory C:/Temp/cloak-support
```

`-Adb` overrides the default SDK path. The helper requires an explicit serial
and reads only the last 2,000 relevant logcat lines with `-d`; it does not clear
logs, install, root, restart or contact a cloud service. Offline input is capped
at 2 MiB. Only reconstructed, bounded metadata enters its timestamped ZIP.
Unknown/malformed lines are counted and discarded. The execution-policy option
applies only to that invocation, not the machine's persistent policy.

## Limits

- Evidence is best effort. Queue overflow is reported; export flush is bounded.
  Abrupt process kill, native crash, ANR, power loss, or unavailable storage may
  prevent a crash snapshot. No automatic restart or cleanup is performed.
- Counters/current actions concern this process session; recovered historical
  events are not resumed actions. Failure lists cover the retained window.
- The app cannot read privileged native module logcat without new permissions.
  The helper folds existing `CloakModule` lines into safe categories, explicitly
  marking legacy process/thread metadata unavailable and timestamp as collection
  time. For exact native ordering, retain the separately captured device evidence.
- Helper ZIPs contain filtered logs, not in-app version/counter snapshots; use
  the in-app ZIP alongside them when needed. No device collection was run in this
  sprint; helper tests use synthetic files.
- Document-provider export errors show retry guidance. A provider may leave a
  partial destination on failure; saved profiles and binding files are untouched.
