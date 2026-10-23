# Module load evidence and cycle completion

The boot gate run stopped on one assertion: no module-load line was found in
the target probe's retained log buffer, even though the installed module
files match the selected ZIP exactly and the bound capture completed with
valid markers. The retained buffer is bounded and the module's own logging is
best-effort, so absence from that buffer is not proof that the module failed.
The clean fix is not more logcat reading: it is making the load evidence
first-class, then finishing the interrupted cycle. Everything else from the
previous run (snapshots, hashes, cleanup behavior) was correct and should be
reused unchanged.

## Part 1 - strengthen load evidence (module code, small)

1. In `android/module/src/main/cpp/cloak.cpp`, inside
   `preAppSpecialize`, after `g_active` is set, write one persistent,
   per-capture load record: a small JSON file under the module directory
   (`/data/adb/modules/cyclone_cloak/`) containing timestamp, target package,
   Android user id, module artifact digest, and the hook-library path. Keep
   it append-safe under concurrent processes (open with O_APPEND, write
   atomically small enough for one write call). Cap the file at 64 KB with
   truncation. This is the durable counterpart to the transient LOGI line.
2. The record must be written before `postAppSpecialize` runs, so a later
   crash still proves load. No behavior change beyond this record.
3. Rebuild both ABIs and verify the module ZIP agreement checks still pass.

## Part 2 - finish the boot/removal cycle

1. Reinstall the exact same alpha.27 embedded module ZIP (SHA-256 recorded
   previously) and reboot once. After unlock, the new load record must exist
   for the user-17 bound capture, containing the matching artifact digest.
   That record is the PASS condition that was previously unmeasurable.
2. Complete the two remaining stages exactly as before: user-0 bound capture,
   then the removal/reboot stage with post-removal restoration digests and
   the final production/permission/AppOps equality audit.
3. Reuse the existing runner and archive conventions; a new dated directory
   under docs/runtime-evidence with summary.md and the full command log.

## Gates

- One fix-and-retest cycle only. If the load record is absent after a
  completed bound capture, the bundle with boot logs and the record's
  absence is the deliverable; stop and report.
- OBSERVED stays OBSERVED, BLOCKED stays BLOCKED; no VERIFIED promotion and
  no platform verdict. No second fix attempt, no speculative variants.

## Limits

No production APK changes, no permission or AppOps mutations, no user
creation or removal, no release, tag or version bump. The only authorized
device actions are the single module reinstall, one reboot, the two bound
captures through the proven switch-user path, the removal reboot, the
restoration captures, and the standard user round trip. Preserve all prior
evidence.
