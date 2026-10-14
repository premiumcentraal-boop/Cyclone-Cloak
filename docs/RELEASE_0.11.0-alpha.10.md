# Cyclone Cloak 0.11.0-alpha.10 — companion presence

App and module packaging version code: **34**.

- Health adds a read-only Companion copies card for each Cyclone profile: app installation, launcher availability, private directory presence and write access, with explicit missing/not-launchable/unverified reasons.
- Stopped or locked Android users are distinguished from missing app copies. The current Cyclone Essentials/Cornerstone provisioning sequence remains the installation mechanism.
- Root can inspect directory presence in another user but cannot prove app-process write access. Run Health inside that profile's own Cloak to verify its private directory.
- No profile schema, identity handling, storage layout, binding, publication, connector contract, permissions or runtime/module behavior changes. The module version changes for matching packaging only.
- These checks are local evidence; no platform verdict is claimed. Earlier device evidence does not verify this build. Alpha.10 physical-device checks and receiving-profile bootstrap verification are **UNVERIFIED**.

Local preparation: 444 app tests, 12 module tests, 227 forge tests and 33 schema tests passed, with a successful debug build. Standard release CI reruns tests, signs the APK and verifies exact APK/module agreement before publishing.

Upgrade using the normal APK/module update flow. Keep existing app data and saved profiles. No migration or reset is required. A separately updated Cyclone build is needed for Cyclone-side bootstrap acknowledgement fixes.

Assets: `app-release.apk`, `cyclone-cloak-0.11.0-alpha.10.zip`.
