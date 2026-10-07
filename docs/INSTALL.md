# Installing Cyclone Cloak

Cyclone Cloak 0.2.0-alpha.4 requires Android 10 (API 29) or newer.

## Upgrading from Alpha 3

Alpha 3 was published with an Android debug signing certificate. Alpha 4 is signed with the stable Cyclone Cloak release certificate. Android does not allow an app update when the signing certificates differ, so installing Alpha 4 over Alpha 3 can show **App not installed**.

Uninstall the older Cyclone Cloak build, then install Alpha 4 again. Uninstalling removes Cyclone Cloak's local app data, including saved bindings; keep copies of any profile JSON files you may need before uninstalling. A fresh Alpha 4 installation does not require uninstalling anything.

Future releases use the same stable release key as Alpha 4, so upgrades from Alpha 4 should install normally as long as the release signing key remains unchanged.
