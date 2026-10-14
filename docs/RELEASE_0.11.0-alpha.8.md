# Cyclone Cloak v0.11.0-alpha.8 — Companion UI redesign

This prerelease packages the companion app redesign merged in PR #13. It changes how the app looks and how its
screens are organised; it does not change what the app or module does.

## What's new

- **Home dashboard** (new start screen): lists what needs attention — Cyclone connection or approval, missing
  binding permissions, Root Doctor, profiles needing attention, bound apps that aren't ready, orphaned bindings,
  shared identifiers and unreadable identities — and opens the screen that fixes each one. It also shows the
  phone → identity → bind steps.
- **One design library** (`ui/CloakDesign.kt`) in Cyclone's grouped-list style, used by every screen.
- **Phones**: compact rows with one *Make identity* action; Clone, Edit, Share and Delete move to a ⋮ menu.
- **Identities**: *One identity / A fleet* switch, phone dropdown and count stepper; *Bind to profile apps* as the
  main action, other actions in ⋮, diagnostics behind an expandable section.
- **Profiles**: one main *Bind a device profile* action, grouped profile list, dropdown binding filter, tidier cards.
- **Health**: local-diagnostics note and clear sections.

## Upgrade and scope

App and module metadata agree at `0.11.0-alpha.8`, version code **32**. Install the signed APK as an update. The
module ZIP is rebuilt from the same runtime source as alpha.7 with only the version bumped.

- No profile schema, storage-layout, connector, binding or publication changes.
- No migration or rewrite of saved profiles.
- No new permissions, networking or analytics.
- Existing actions, warnings and binding/health state wording are preserved.
- Physical-device verification of the alpha.7 native callback row remains **pending**; no platform verdict is claimed.

## Validation and artifacts

PR CI passed the app/module unit tests (including new Home tests) and forge suites. Release CI reruns the suites,
signs the APK, packages the module, and verifies version and embedded-module agreement before publishing
`app-release.apk` and `cyclone-cloak-0.11.0-alpha.8.zip`.
