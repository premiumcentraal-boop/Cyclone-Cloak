# Candidate module install and boot gate - infrastructure check

A packaged Magisk module zip needs the standard installation gate run: install,
boot once, verify startup log lines, run one smoke probe, and confirm the
module management rollback path. This is device-infrastructure validation of
the packaged artifact only - the same gate applied before the alpha.18
lifecycle runs. No probe lifecycle run, no binding changes, no user switching,
no release.

Device: `3B171FDJH0061G`, USB attached, unlocked.

## Artifact

`android/module/build/module/cyclone-cloak-0.11.0-alpha.28.zip` (record SHA-256
at install time). Installation and uninstallation of Magisk modules is routine
module management on this device, as performed in prior validation runs.

## Steps

1. Baseline: record installed module version, Zygisk state, and launch Settings
   plus the standard probe on user 0; both complete.
2. Install: `su -c magisk --install-module <zip>`. Record exit code and output.
3. Reboot once; wait for `sys.boot_completed=1` (300 s). Record boot time.
4. Verify: Zygisk lists the module enabled at the candidate version; bounded
   logcat (boot to probe launch) contains the module's two startup confirmation
   lines (`CloakModule` tag) with timestamps; no `FATAL EXCEPTION` or tombstone
   mentioning the module.
5. Smoke: repeat step 1's launches; both complete; record counts.
6. Rollback path (documented, not executed): the module's disable marker
   `su -c "touch /data/adb/modules/cyclone_cloak/disable_native_hook"` plus
   reboot returns the device to property-hook-only behavior; uninstalling the
   module and reinstalling the published zip is the full revert.

## Evidence

`docs/runtime-evidence/<timestamp>-module-install-gate/` with `summary.md`,
logcat excerpt, and before/after hashes. Limits: one boot, smoke scope,
infrastructure validation only, no parity or coverage verdict.

## Failure script

If boot fails or a scoped app crashes at startup twice: stop, apply the
disable marker, reboot, report Stage failure with the logcat excerpt.
