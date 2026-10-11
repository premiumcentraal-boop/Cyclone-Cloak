# Upgrade to 0.10.0-rc.2

RC2 is a published prerelease with a signed APK and module ZIP. Use the published
signed APK for upgrade; a local unsigned build is not a signed upgrade.
Device steps below are **unverified**.

1. Preserve app data. If using RC2 backups, export the library to Downloads and
   explicitly include identifiers/seeds if exact identity restoration is needed.
   Default exports restore fresh values. Bindings are not backed up. Private
   backups disappear on uninstall/data clear; keep a separate Downloads copy.
2. Upgrade using an APK signed with the existing key.
   Upgrade every Cloak install in Main and Cyclone profiles together. Profile
   schema 0.2, binding schema 2 and reader keys require no migration.
3. Install matching `cyclone-cloak-0.10.0-rc.2.zip`, or explicitly queue the
   bundled module through Health repair. App/module metadata must show RC2/23.
4. A module change needs a reboot and another Health check. Reboot tests are
   deferred until the owner resumes them. Queued metadata does not prove which
   runtime is loaded. Follow [DEFERRED-DEVICE-CHECKS.md](DEFERRED-DEVICE-CHECKS.md).
5. Review Readiness Doctor reasons and local duplicates. Existing profiles may
   gain warnings but are not rewritten. Review guided binding before confirming;
   retry publication/reconciliation failures without discarding local records.
   A failed swap keeps the previous tree for recovery; avoid external concurrent
   edits and mixed publisher versions.
6. Import profile backups through preview and confirmation. Wrong schemas or
   invalid entries reject the whole batch. Duplicate values require acknowledgement;
   collisions receive new local IDs/names rather than replacing saved profiles.

Ready does not establish complete runtime interception or attestation. Check
[CURRENT-STATE.md](CURRENT-STATE.md) and [RUNTIME-COVERAGE.md](RUNTIME-COVERAGE.md).
