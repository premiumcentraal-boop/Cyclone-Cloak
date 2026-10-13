# Cyclone Cloak v0.11.0-alpha.7 — Native property callback coverage

This prerelease packages the native property-callback work already on main
(`27d3a11`). The module now intercepts `__system_property_read_callback`, alongside
the existing `__system_property_get` path. The probe exercises the callback path
through `properties.nativeCallback.ro.product.model`. Profile A also supplies
bootloader and baseband expectations for a future device pass.

The callback path is implemented and probe-exercised in source, with local build
coverage. Physical-device verification remains **pending**. Existing checked-in
device evidence predates the new probe row and does not automatically verify it,
the updated fixture expectations, or this release's artifact. No device tests
were run during release preparation. No platform verdict is claimed.

## Upgrade and scope

App and module metadata agree at `0.11.0-alpha.7`, version code **31**. Install the
signed APK as an update; the matching module ZIP contains the existing main
callback implementation. Module activation and reboot checks remain separate
device verification work.

- No profile schema, storage-layout, connector, binding or publication changes.
- No migration or rewrite of saved profiles.
- No new permissions, networking or analytics.
- No behavior changes introduced during release packaging.
- Release verification logic is unchanged.

## Validation and artifacts

Standard local app/module, forge/schema, debug-build, metadata and whitespace
checks are recorded in the release handoff. Release CI reruns the suites, signs
the APK, packages the module, and verifies version and embedded-module agreement
before publishing `app-release.apk` and `cyclone-cloak-0.11.0-alpha.7.zip`.
