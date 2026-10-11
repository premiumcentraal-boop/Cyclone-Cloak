# Upgrade to 0.10.0-rc.1

1. Export identities from the Identities tab before upgrading. Keep existing app
   data; uninstalling or clearing data removes private local configuration. Profile
   schema 0.2, binding schema 2 and the module reader format are unchanged.
2. Use the release APK signed with the same key as your installed app. Upgrade
   every Cloak install in Main and Cyclone profiles. A locally built unsigned
   release APK is a build artifact, not an installable signed upgrade.
3. Install the matching `cyclone-cloak-0.10.0-rc.1.zip` in Magisk, or use the
   existing Health Doctor Check & repair action to queue the embedded matching
   module. Confirm app/module release names and version codes are both RC/22.
4. A module upgrade needs a reboot to activate. The owner deferred phone/reboot
   checks: perform this device step later, then rerun Health Doctor and reconnect
   to Cyclone. A queued module version does not prove the loaded runtime version.
5. Review binding warnings and the Health coverage matrix. Resolve duplicate or
   missing records explicitly; reconnect can resume paused records without
   discarding identities. A locked publication can be retried once its owner
   finishes. A failed rollback retains `state-v1.previous` for the next attempt.

Signed upgrade, reboot, multi-user publication and RC runtime behavior are all
**unverified on a device**. Follow [DEFERRED-DEVICE-CHECKS.md](DEFERRED-DEVICE-CHECKS.md)
when device checks resume. Health Doctor Ready describes reported readiness;
it does not establish full hook coverage or Play Integrity compatibility.
