# Durable load record via the root companion, and the resumed cycle

The instrumented run answered the load-evidence question precisely: the
zygote context cannot write into the module directory because SELinux denies
it (avc: denied { write } for zygote into cyclone_cloak, permissive=0), while
the companion process runs as root and already has a socket channel. The
fix is therefore architectural, not diagnostic: move the durable record
write into the existing companion root context. No new mechanisms, no
SELinux changes. Everything else is a resume of the interrupted cycle.

## Part 1 - move the record write into the companion (module code)

1. Keep the pre-specialization record assembly in
   `module_load_record.hpp`, but instead of writing directly, send the
   record payload over the existing companion socket (extend the existing
   `StateRequest` pattern with a small record request or a second socket
   message on the same connection; read the current code first).
2. In `companion_handler` (cloak.cpp, runs as root), write the received
   record with the existing bounded JSONL writer: one write, inode flock,
   64 KB cap, fdatasync. Failure to write must not change hook behavior,
   exactly as before.
3. The record must land before the capture's end marker: the companion
   write happens while the capture is still in flight, so a completed
   capture plus a present record is the PASS condition.
4. Rebuild both ABIs. Run the existing module unit tests plus one new test
   covering the companion write path (host-level where possible).

## Part 2 - resume the stopped cycle

1. Reinstall the rebuilt instrumented ZIP (record its new hashes as
   before; alpha.27 metadata retained; the published release stays
   untouched). One reboot, then verify installed immutable files match.
2. User-17 fixture B bound cold capture via the proven switch-user path.
   PASS requires: completed capture markers AND a durable load record whose
   artifact digest matches the installed instrumented ZIP and whose
   package/user fields match the capture.
3. Then complete the remaining previously-skipped stages in order: user-0
   bound capture with its own record, module removal through the
   established flow, second reboot, post-removal restoration captures per
   user, final production/permission/AppOps equality audit, cleanup checks.
4. Archive everything in a new dated directory under docs/runtime-evidence
   with summary.md and the full command log, as before.

## Gates

- One fix-and-retest cycle only. If the record is still absent or invalid
  after a completed bound capture, archive the AVC/permission evidence and
  the companion-side failure reason; stop and report.
- OBSERVED stays OBSERVED, BLOCKED stays BLOCKED; nothing promoted to
  VERIFIED; no platform verdict, parity claim, or coverage claim.

## Limits

No SELinux or policy mutation, no permissive mode, no production APK
changes, no permission/AppOps writes, no user creation/removal, no release,
tag or version bump. Only authorized device actions: the instrumented
reinstall, two reboots (boot stage and removal stage), the bound and
restoration captures through the proven switch-user path, and the standard
user round trip. Preserve all prior evidence.
