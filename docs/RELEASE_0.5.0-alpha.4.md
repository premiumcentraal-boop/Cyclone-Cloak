# Cyclone Cloak 0.5.0-alpha.4

Root Doctor can now repair the most common rooted setup failure without asking users to find a matching module ZIP:

- On **Check & repair**, it checks Magisk root, the installed module, its version and ABI, and profile-state publishing.
- It also checks the Zygisk setting, so existing module files cannot be reported as ready while Zygisk is disabled.
- The exact release module ZIP is bundled inside the signed APK, so repair works offline and does not depend on a GitHub asset being available after installation.
- If the Cyclone Cloak module is missing, disabled, pending removal, or from another app version, Root Doctor validates the bundled ZIP and asks Magisk to schedule it.
- Before asking Magisk to install it, the app checks the ZIP's module id, version, archive paths, size, and device ABI library.
- Magisk schedules the matching module for the next reboot. Root Doctor reports that reboot is required and keeps rooted profile routes unavailable until the module is active and state publishing succeeds.
- Publish failures now include a short command detail in the Doctor card so permission and filesystem errors are visible instead of looking like a ready binding.

## First setup or app update

1. Install this release's APK and open Cyclone Cloak.
2. Tap **Check & repair**, then allow Cyclone Cloak in the Magisk prompt.
3. If the module needs repair, Root Doctor schedules the bundled matching module. Restart the phone when it reports **Reboot required**.
4. Open Cyclone Cloak and tap **Check again**. **Ready** confirms that the matching module is active and the profile state reached the module's root-only directory.
5. Start a bound app to exercise the route.

If the bundled archive fails validation, use **Get module ZIP** and install the same release's `cyclone-cloak-0.5.0-alpha.4.zip` in Magisk. Root approval and a reboot still require the device owner; Android does not permit an app to grant its own root access or silently reboot the phone.

The repair only updates the `cyclone_cloak` Magisk module. It does not change other Magisk modules or grant Cyclone Cloak new permissions.
