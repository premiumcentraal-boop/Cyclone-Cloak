# Deferred phone checks

On 2026-10-11 the owner asked to skip phone tests requiring a reboot while away
from home. Do not install the locally patched module or reboot the phone until
the owner resumes these checks. Local JVM tests and builds can continue.

## Resume checklist

1. When the owner is home and authorizes the reboot, install the module ZIP built
   from this checkout, reboot and check Root Doctor and the loaded module hash.
2. Run the independent probe's five phases: unbound before, profile A, repeated A,
   profile B and unbound after. Follow [RUNTIME-COVERAGE.md](RUNTIME-COVERAGE.md)
   and preserve the existing baseline evidence.
3. Save new redacted evidence and regenerate a separate coverage matrix. Check
   Build/VERSION, properties, telephony counts/types/operator, Settings/Android ID,
   serial, Advertising ID, Wi-Fi, Bluetooth, MediaDrm, display, camera, sensors,
   country, locale and timezone individually. Local callback tests do not prove
   hooks were installed in ART; blocked/unmapped reads must remain explicit.
4. Confirm repeat stability, restored unbound behavior and unchanged production
   bindings. Include secondary-user and both ARM targets where available.
5. Recheck Root Doctor after reboot, cross-user approval/revocation,
   restore/reconciliation, health pills, open requests and signed upgrades.

## Partial resumption: B2A, 2026-10-13

The owner explicitly authorized installation and reboot for DRM verification.
The patched module was installed and rebooted; the five-phase probe completed
on Pixel 8/API 36/user 0 with **54/70 verified reads**, including DRM unique ID.
See [the completion report](HANDOFF-2026-10-13-B2A-DRM-VERIFICATION.md).
The original 13/70 baseline is historical, not the current measurement.

This run does not complete the broader checklist: secondary-user/ARM32 checks,
Root Doctor workflows, publication concurrency, signed companion upgrades and
restricted/unmapped inspection surfaces still require separate verification.

Publication sprint follow-up: once phone checks resume, update every Cloak
publisher install, exercise simultaneous Main/profile publication on Android
Toybox, confirm locked/recovered reasons and read-back of both shares, and verify
module startup still reads the unchanged index/profile format. No reboot or phone
interaction was performed for the local locking tests.
