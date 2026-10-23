# Candidate build boot validation - D1 module

A new module build (`main` at `d4f0a5c`, packaged as
`android/module/build/module/cyclone-cloak-0.11.0-alpha.28.zip`, SHA-256 to be
recorded at install time) adds read-only filesystem parity surfaces to the
existing envelope. Before any evidence run, it needs one controlled boot
validation. This is the standard candidate-build gate used before the alpha.18
lifecycle runs: install, boot once, verify startup markers, one smoke probe.

## Preconditions

- Device `3B171FDJH0061G`, USB attached, unlocked, on published alpha.28.
- Record before starting: installed module version, `getprop ro.build.version.sdk`,
  Zygisk state, and the SHA-256 of both the currently installed module zip and
  the candidate zip.
- Keep the standard Magisk module disable marker documented as rollback:
  `su -c "touch /data/adb/modules/cyclone_cloak/disable"` plus one reboot
  restores the previous module behavior. This is ordinary Magisk module
  management, included here so the rollback path is explicit before boot.

## Steps

1. **Baseline smoke (before install).** Launch one unscoped app (Settings) and
   the standard probe on user 0. Confirm both complete. This is the
   boot-comparison reference.
2. **Install candidate.** Push the candidate zip and install via Magisk
   (`su -c magisk --install-module <zip>`). Record installer exit code and
   output.
3. **Reboot once.** Wait for boot complete
   (`sys.boot_completed=1`), with a 300-second timeout. Record boot time.
4. **Startup verification.**
   - Zygisk reports the module enabled, matching the candidate version.
   - Bounded logcat window (boot to probe launch) contains the module's
     startup confirmation lines: the property hook line and the file-hook line
     (`CloakModule` tag). Record both with timestamps.
   - No `FATAL EXCEPTION` or tombstone mentioning the module appears.
5. **Post-boot smoke.** Launch the same unscoped app and the standard probe on
   user 0 as in step 1. Both must complete. Record probe result counts.
6. **Rollback drill (dry run only).** Verify the disable marker command exists
   and is documented; do not execute it. This keeps the drill non-destructive.

## Evidence

Save `boot-validation.md` plus a bounded logcat excerpt and the before/after
hashes in `docs/runtime-evidence/<timestamp>-d1-boot-validation/`. State
explicitly: one boot, one user, smoke scope only; no parity verdict; physical
restoration and multi-cycle behavior remain follow-ups.

## Out of scope

No probe lifecycle run, no user 17 switching, no release or version bump, no
runtime source edits, no claim about what the module does internally. If boot
fails or a scoped app crashes at startup twice, stop, apply the documented
disable marker, reboot, and report the failure with the logcat excerpt.
