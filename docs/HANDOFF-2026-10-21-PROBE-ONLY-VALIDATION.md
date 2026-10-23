# Diagnostics probe validation - standalone

Validate the read-only diagnostics probe APK and its report handling on the
device. This sprint does not involve installing, replacing, rebooting with, or
exercising any module build. The candidate module stays as-is on the device
(published alpha.28 behavior); nothing about it is changed in this run.

Device: `3B171FDJH0061G`, USB attached, unlocked.

## Scope

The diagnostics probe APK was built locally at
`android/probe/build/outputs/apk/diagnostics/debug/probe-diagnostics-debug.apk`
(contains the four filesystem observation rows in its contract assets). It is
an ordinary app: bounded, allowlisted, streaming reads; digest and line-count
output only; no raw file export.

## Steps

1. Record the probe APK SHA-256, then install on user 0
   (`adb install --user 0 -r`). No module change, no reboot.
2. Launch the diagnostics probe on user 0 within an existing capture session.
   Confirm it completes and produces a report.
3. Verify the report structure against
   `schema/filesystem-observations.schema.json`: the four observation rows
   (build.prop, /proc/version, /proc/cpuinfo, maps snapshot) are present as
   OBSERVED with digests and counts; missing/denied reads stay explicit.
4. Repeat launch on user 17 if the device has that user; confirm the report is
   user-scoped and both runs complete.
5. Verify report determinism: two consecutive runs on the same user produce
   identical digests for build.prop and /proc/version (maps is volatile; skip
   it for determinism). Record both.
6. Uninstall the probe (`adb uninstall`) and confirm the report files are
   preserved in the evidence directory. Production app and module state are
   untouched throughout.

## Evidence

`docs/runtime-evidence/<timestamp>-probe-only-validation/` with `summary.md`,
both reports, and explicit limits: probe-only, no module interaction, no
parity verdict, no VERIFIED promotion.

## Out of scope

Module install, reboot, marker changes, binding changes, release, runtime
edits. If any read row denies or fails, record it as an explicit limitation
and continue; do not modify any code.
