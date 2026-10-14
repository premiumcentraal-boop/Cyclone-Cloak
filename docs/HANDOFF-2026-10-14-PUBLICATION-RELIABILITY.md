# Publication reliability handoff — 2026-10-14

## Exact cause

The connected Pixel's Android mksh (`MIRBSD KSH R59 2020/10/31 Android`)
opens descriptor 9 for `exec 9>>publication.lock`, but does not pass that extra
descriptor to external commands. The file exists in the parent, is absent in
an external shell child, and Toybox `flock -n 9` returns 1 with **Bad file
descriptor**. FD 8 behaves the same. The previous script classified every flock
failure as another publisher, falsely reporting contention.

Toybox 0.8.13-android documents an **FD-only** interface. The proposed path
argument returned success even while the publication lock was held; it does
not provide reliable path-based exclusion on this device. It must not be used
as a workaround. Standard descriptor 0 survives external exec. Holding it open
for the complete publication subshell preserves the kernel lock acquired by
the flock child. A separate open of that same file was refused, including with
the command-substitution form used by the fixed script.

[Device locking evidence](runtime-evidence/publication-lock-2026-10-14.json).
No normal app-to-device publication of the updated code was performed; the
uncommitted app has not been installed. Device evidence is limited to the real
shell/lock comparisons. Whole production merge/swap/recovery scripts were run
against local temporary trees through WSL, with real kernel locks.

## Change

`exec 0>> "$root/publication.lock"` then `flock -n 0`, inside the existing
publication subshell. The descriptor remains open throughout recovery, publisher
share replacement/merge, final tree swap and exit-trap rollback. The lock inode
is never unlinked. A silent busy refusal maps to LOCKED; stderr-producing flock
errors map to LOCK_FAILED; missing flock maps to LOCK_UNAVAILABLE. Lock-open
failure is inferred from its unmatched phase marker. Fixed user-facing reasons
distinguish these failures without changing the screen layout.

Staging now keeps the unique directory created by `createTempDirectory` instead
of deleting/recreating it. Before root publication, structural validation checks
index-part JSON, referenced safe keys, required JSON bodies, and rejects missing
files, extra entries or symlinks. It never rewrites profile/PIF contents or
changes their schema/identity semantics.

Fixed shell markers and LogBus events cover precheck, staging creation/validation,
lock open/acquire, merge, atomic swap, recovery and completion. Diagnostics contain
only phase/state/reason codes and exception classes, never profile bodies. Phase
markers are ingested after the root command returns; outer app action duration
remains available. Reader format, share precedence, binding, connector and state
directory layout remain unchanged.

## Sprint changed files (in addition to preserved diagnostics work)

- `android/app/src/main/java/dev/cyclone/cloak/root/CloakRootDoctor.kt`
- `android/app/src/main/java/dev/cyclone/cloak/root/CloakResolver.kt`
- `android/app/src/main/java/dev/cyclone/cloak/ui/HealthDiagnostics.kt`
- `android/app/src/test/java/dev/cyclone/cloak/root/PublicationRecoveryTest.kt`
- `android/app/src/test/java/dev/cyclone/cloak/root/PublicationReliabilityTest.kt`
- This handoff and the device locking evidence JSON.

Existing uncommitted diagnostics backend and unrelated handoff files were kept.
No commit, push or release was requested/performed.

## Exact checks

```powershell
$env:JAVA_HOME='C:/Program Files/Eclipse Adoptium/jdk-17.0.20.101-hotspot'
$env:CLOAK_TEST_WSL='1'
& "$env:TEMP/cloak-build/gradle-8.11.1/bin/gradle.bat" -p android :app:testDebugUnitTest --tests '*PublicationRecoveryTest' --tests '*PublicationReliabilityTest' --tests '*CloakPublishMergeTest' --tests '*CloakRootDoctorTest' --offline --no-daemon
& "$env:TEMP/cloak-build/gradle-8.11.1/bin/gradle.bat" -p android :app:testDebugUnitTest :module:testDebugUnitTest :app:assembleDebug --offline --no-daemon
uv run --with pytest -- python -m pytest forge/tests tools/tests -q
uv run --with pytest --with jsonschema -- python -m pytest forge/schema_tests -q
git diff --check
```

Focused: **39 passed, zero skipped** (9 merge, 8 doctor, 18 recovery, 4 structural/
parser). Eight focused tests were added. Python: **230 passed** (227 forge + 3
helper), schema **33 passed**. Final Android: **428 app tests and 12 module tests
passed**, zero failures/errors/skips with WSL enabled. Debug app build and module
packaging succeeded. No compiler warnings in the final run. `git diff --check`
passed. The preserved diagnostics backend is included in those full counts.

Read-only/temporary-lock device comparison used `adb -s 3B171FDJH0061G shell
su --mount-master -c sh` with fixed shell snippets:

```sh
exec 9>> /data/adb/cyclone_cloak/publication.lock
ls -l /proc/$$/fd/9
flock -n 9; echo FD9_STATUS=$?
/system/bin/sh -c 'test -e /proc/$$/fd/9; echo CHILD_FD9_PRESENT=$?'
(
  if message=$(flock -n 0 2>&1); then echo HOLDER=0; else echo HOLDER=1; fi
  ( if message=$(flock -n 0 2>&1); then echo CONTENDER=0; else echo CONTENDER=1; test -z "$message"; echo BUSY_STDERR_EMPTY=$?; fi ) 0>> /data/adb/cyclone_cloak/publication.lock
  flock -n /data/adb/cyclone_cloak/publication.lock; echo PATH_WHILE_HELD=$?
) 0>> /data/adb/cyclone_cloak/publication.lock
```

## Limits

The existing two-rename tree swap retains its brief reader gap; this fix does
not claim power-loss atomicity. Prior/external writers must use the same inode
and locking discipline. Java/app crashes and filesystem rollback failures remain
best-effort recovery, preserving the previous tree when restoration cannot finish.
No reboot, uninstall, data clear, module install, unrelated system change or
platform verdict. An updated-app normal publication on this phone remains a
follow-up; the kernel locking fix itself was verified on its attached shell.
