# Cyclone Cloak 1.1.0-alpha.1

Prerelease with matching companion/module version **1.1.0-alpha.1 / code 63**.

## Scope since 1.0.0-rc.3

- **Spaces.** Cloak can create its own managed Android user (a space), seed an identity
  for it through the existing forge, install chosen apps into it, open an app inside it,
  and remove it. Cyclone is not needed for spaces.
- Creating and removing spaces run only from Main, need root, and need Android 13 or newer.
  Opening an app in a space also needs root.
- Creation is resumable. A failed run keeps its record, and retrying finds the existing
  Android user by name instead of creating a second one.
- Removing a space unbinds its apps and republishes the published state before the
  Android user is removed, so no published binding points at a missing user.
- Space bindings are kept out of Cyclone reconciliation and out of the "missing Cyclone
  profile" health count, because Cyclone never lists spaces.

## Not in this release

- **Spaces are unverified on a phone.** The root, Magisk, `LauncherApps` and `dumpsys`
  steps have not been measured on a device. Treat this build as a test build.
- Binding an app to a space's identity is not wired into the binding flow yet. A space
  gets an identity and its apps, but apps cannot be bound to it from the app yet.

## Compatibility

No connector, scope, permission, schema or storage-format change for existing data.
Existing `Cyclone_` profile ids, saved profiles and bindings are unchanged. Spaces store
their records in `cloak-spaces-v1.json`, which older builds ignore. The native module is
unchanged apart from its version metadata.

## Verification

- `gradle -p android :app:testDebugUnitTest` passed locally: 822 tests, 0 failures,
  0 errors, 0 skipped. That includes 31 new space tests.
- The release workflow runs the full gate in CI, including module, probe, play-evidence
  and forge tests, and checks the APK and module pair before attaching them.
- No physical device suite was run.
