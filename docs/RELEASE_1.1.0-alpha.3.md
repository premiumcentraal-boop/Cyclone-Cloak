# Cyclone Cloak 1.1.0-alpha.3

Prerelease with matching companion/module version **1.1.0-alpha.3 / code 65**.

## Scope since 1.1.0-alpha.2

- Home has four quick actions: **New profile** opens the Profiles tab with the new-space dialog already up.
  **New identity** opens the Identities tab with the phone picker up. **Repair** runs the root check and repair.
  **Refresh** syncs from Cyclone.
- The Phones tab is merged into Identities. Phone profiles, built-in and your own, sit above the saved identities.
  The tabs are now Home, Identities, Profiles and Health.
- Profiles has one search field. As you type it matches each word against Cyclone profiles, spaces and identities.
  Tapping a matching identity starts its binding flow. While searching, the hero and action buttons hide.
- New spaces install Cyclone Cloak, and Cyclone when it is installed, as essentials. They appear locked in the create
  dialog and count toward the 50-app limit.

## Not in this release

- **Spaces and the new layout are unverified on a phone.** Root, Magisk, the app install and launch, the foreground
  check, and the Home layout have not been measured on a device. Robolectric renders the screens on the JVM only.
- The essentials list has two entries: Cyclone Cloak, and `com.cyclone.mobile` when present. Cyclone's full
  essentials list is on another branch and is not in this checkout.
- A space's own Cloak does not see the phone library in Main. Main's phone list is the complete one.
- Repair runs the root check with module repair, so it can ask for a Magisk approval.

## Compatibility

No connector, scope or permission change. Existing `Cyclone_` profiles, saved profiles, identities and bindings are
unchanged. Space records stay in `cloak-spaces-v1.json`. No new storage format.

## Verification

- The app's unit-test suite passed on this tree: 844 tests, 0 failures, 0 errors and 0 skipped.
- The debug APK and the module package build on this tree.
- The release workflow runs the full gate in CI, then checks the APK and module pair before attaching them.
- No physical device suite was run.
