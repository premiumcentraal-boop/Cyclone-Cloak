# D1 candidate build - full staged validation

Complete the four-stage validation of the candidate module build (`main` at
`d4f0a5c`, packaged as `android/module/build/module/cyclone-cloak-0.11.0-alpha.28.zip`)
in one session. Each stage has a pass gate; never start the next stage until the
current one is green. Any failure: stop, apply the documented rollback, report
the failing stage with its evidence. No release, no version bump, no production
app/module source edits, no bank or Play Store testing.

Device: `3B171FDJH0061G`, USB attached, unlocked.

---

## Stage 0 - Preflight and rollback drill

1. Record baseline: installed module version, `getprop ro.build.version.sdk`,
   Zygisk state, SHA-256 of the currently installed module zip and of the
   candidate zip.
2. Backup via su into the evidence directory:
   `/data/adb/cyclone_cloak/state-v1` (recursive copy) and the current binding
   JSONs. This is the restore point for the whole session.
3. Baseline smoke: launch Settings and the standard probe on user 0; both must
   complete. Record probe result counts.
4. **Rollback drill (live but reversible).** Create the marker
   `su -c "touch /data/adb/modules/cyclone_cloak/disable_native_hook"`, launch
   the diagnostics probe on user 0, confirm it completes, then delete the
   marker. Record the logcat lines that differ with the marker present. This
   proves the emergency brake works while the phone is calm, before anything
   is installed.

Gate: all four items recorded, probe completed both with and without marker.

## Stage 1 - Candidate install and boot validation

1. Install the candidate zip: `su -c magisk --install-module <zip>`. Record
   installer exit code and output.
2. Reboot once; wait for `sys.boot_completed=1` (300 s timeout). Record boot time.
3. Startup verification: Zygisk reports the module enabled at the candidate
   version; bounded logcat (boot to probe launch) contains both module startup
   confirmation lines (`CloakModule` tag: property-hook line and file-hook
   line) with timestamps; no `FATAL EXCEPTION` or tombstone mentioning the
   module.
4. Post-boot smoke: repeat the Stage 0 baseline smoke (Settings + standard
   probe, user 0). Both must complete; record counts.
5. Rollback ladder is documented and ready, in order: (a) disable_native_hook
   marker + reboot, (b) uninstall candidate and reinstall the published
   alpha.28 zip + reboot, (c) restore the Stage 0 state backup.

Gate: boot completed, both startup lines present, smoke passed.

## Stage 2 - Single-cycle observation run

Use the locally built diagnostics probe APK
(`android/probe/build/outputs/apk/diagnostics/debug/probe-diagnostics-debug.apk`,
SHA-256 recorded) and the existing reversibility harness.

1. Run **one complete cycle** with the standard policy:
   baseline-cold (unbound) -> bound-cold -> post-removal-warm ->
   post-removal-cold -> restoration comparison, on user 0 first, then user 17.
2. The four filesystem observation rows (build.prop, /proc/version,
   /proc/cpuinfo, maps snapshot) record digests and counts as OBSERVED. No row
   is promoted to VERIFIED in this stage; classification of parity happens in
   Stage 3.
3. Confirm the session audit: production unchanged, permissions/AppOps
   snapshots identical before/after, cleanup checks pass.

Gate: cycle completes, restoration result not worse than the published
alpha.28 baseline (2026-10-21 follow-up), zero unexplained production delta.

## Stage 3 - Consistency check (privileged reference vs process-visible)

1. Via su, compute reference SHA-256 digests of the same four files as served
   by the device: `/system/build.prop`, `/proc/version`, `/proc/cpuinfo`,
   `/proc/self/maps` (bounded snapshot).
2. Compare against the Stage 2 process-visible digests, per user and per
   capture. Classify each row: MATCH (digests equal), DIFFERS (record both
   digests and the differing line count), or UNAVAILABLE (with reason).
3. This is a consistency measurement only. Do not edit runtime code to make a
   row match; do not mark any row VERIFIED. The verdict table goes into the
   evidence summary.

Gate: comparison table complete for both users, no crash during measurement.

---

## Evidence

One directory: `docs/runtime-evidence/<timestamp>-d1-full-validation/`
containing per-stage artifacts, `summary.md` with the Stage 3 table, all
hashes, and explicit limits: one boot, one lifecycle cycle, users 0 and 17
only, digests are caller-visible views not independent truth, maps are
volatile, no platform verdict, multi-cycle soak and release remain follow-ups.

## Out of scope

Release, tagging, version bump, runtime source edits, evidence rewriting,
Play or bank app checks, more than one lifecycle cycle, more than one reboot.
If Stage 1 boot fails or a scoped app crashes at startup twice, stop at Stage 1,
apply rollback step (a), reboot, and report.
