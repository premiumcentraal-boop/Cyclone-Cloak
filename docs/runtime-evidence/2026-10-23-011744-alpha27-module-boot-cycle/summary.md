# Alpha.27 module boot gate — NOT VALIDATED

Selected artifact: exact alpha.27/code 51 module embedded in installed production APK. No production APK install occurred.

Magisk install succeeded. One authorized reboot completed; user 0 was unlocked and existing user 17 was subsequently switched to normally and became unlocked. Installed module properties and both ABI libraries exactly match the selected ZIP. No disable/remove/update marker was present.

Stopped at MODULE_LOAD_NOT_PROVEN after the user-17 bound cold capture. That capture completed with matching STARTED/COMPLETED markers, 89 unique legacy rows, valid filesystem schema and 2 OBSERVED / 2 BLOCKED filesystem rows. The temporary binding body matched its expected canonical profile hash. Report profile hash matches the same fixture asset serialized with original whitespace; it is a different byte encoding from canonical publication, and is explicitly recorded rather than claimed byte-identical.

The module has no persistent loaded-state marker. The probe process mapped Pine/ShadowHook libraries, but no matching module load line survived in the bounded target-PID logs. Logs for other app PIDs do not establish the probe's loaded artifact. This evidence limitation does not establish that the module failed to load. No fix, repeated bound capture or second boot was attempted. User-0 bound capture, module removal/reboot and post-removal restoration were NOT RUN. The alpha.27 module remains installed; it has not been marked for removal.

Cleanup: temporary overlay removed, both temporary body directories and scratch path absent, diagnostics APK uninstalled from both users, active user returned to 0. Final production APK, stable permission/AppOps and publication/binding-store hash comparisons: UNCHANGED; see production-comparison.json. Private bytes were compared by SHA-256 rather than exporting bodies. Filesystem rows retain observations and permission limitations; no VERIFIED or parity promotion.

Exact device commands and exit codes: commands.jsonl. Install command: su --mount-master -c 'set -eu; magisk --install-module /data/local/tmp/cloak-boot-gate-<run>.zip'. Reboot command: adb -s 3B171FDJH0061G reboot. Run-only orchestration: boot_gate_ops.py (install, bootcheck installed, bound).

A host-only first preflight failed because CRLF input was not normalized; this was corrected before any module action, and the original failure is retained in failure.json. Bound collection stopped on its load-evidence assertion; no module implementation changed. No source changes, release, tag, version bump, permission grant/reset or AppOps write. No platform verdict or broader compatibility claim.

## Audit comparison correction

Persisted before/after JSON snapshots are exactly equal. An initial in-memory comparison incorrectly compared tuple AppOps entries against deserialized lists. The preliminary result is preserved separately. Comparing the persisted snapshots requires no AppOps exceptions: there are no added, removed or changed modes, and no grants changed. No reset or normalization of device state occurred.
