# Cyclone Cloak 1.1.0-alpha.2

Prerelease with matching companion/module version **1.1.0-alpha.2 / code 64**.

## Scope since 1.1.0-alpha.1

- Creating a space leads straight into binding. The binding flow opens with the space's apps
  and its own device profile preselected. Resuming an unfinished space does the same.
- Spaces are binding targets in the binding flow. A ready space has a "Bind apps" action on its card.
- Binding a space publishes Cloak's state only. It does not use the Cyclone connector.
- The spaces card shows how many apps are bound to each space.
- A space-only binding run is not reported as "connector not ready", because it never reaches Cyclone.

## Not in this release

- **Spaces are unverified on a phone.** Root, Magisk, the app launch and the foreground check have
  not been measured on a device.
- A new space's device profile is made automatically from the first built-in phone template. The
  create dialog does not offer a phone choice. To use another phone, choose or generate one in the
  binding flow's device profile step.
- No automated test drives the whole space flow from creation to confirmation.

## Compatibility

No connector, scope or permission change. Existing `Cyclone_` profiles, saved profiles and bindings
are unchanged. Space records are stored in `cloak-spaces-v1.json`. Space bindings use the existing
bindings file and schema.

## Verification

- Before the version bump, the app's unit-test suite passed on the binding change: 833 tests,
  0 failures, 0 errors and 0 skipped.
- Two edits came after that run: a doc comment was moved, and parentheses were added to a rule in
  `CycloneModel.kt`. Neither changes behavior. The release gate reruns the full suite on this tree.
- The release workflow runs the full gate in CI, then checks the APK and module pair before
  attaching them.
- No physical device suite was run.
