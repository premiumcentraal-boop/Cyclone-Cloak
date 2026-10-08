# Cyclone Cloak 0.5.0-alpha.3

- Add Root Doctor to identify Magisk approval, module installation/enablement,
  pending reboot, module version, device architecture, and profile-state publishing.
- Combine the root check and profile-state publish into one Magisk request.
- Add direct actions to open Magisk and download the matching release module ZIP.
- Require the installed Zygisk module to match the companion app version before
  reporting a rooted profile route as ready.
- Keep Cyclone's launch callback fast and report an enabled binding as degraded
  until this app version has passed Root Doctor and published its state.

## Setup and updates

1. Install the APK from this release and open Cyclone Cloak.
2. Tap **Check & repair** and allow Cyclone Cloak in the Magisk prompt.
3. If Root Doctor says the module is missing, disabled, or outdated, install this
   release's `cyclone-cloak-0.5.0-alpha.3.zip` from Magisk → Modules.
4. Restart when Magisk asks, reopen Cyclone Cloak, and run **Check again**.
5. A **Root setup ready** result confirms that the app and matching module can
   publish profile state. Start one of the bound apps to exercise the Zygisk route.

The app cannot silently grant itself superuser access or install/enable a Magisk
module. Root Doctor checks each step and points to the action required.
