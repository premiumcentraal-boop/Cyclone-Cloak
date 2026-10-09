# Cyclone Cloak Alpha 5 handoff

## Published release

- Release: [v0.8.0-alpha.5](https://github.com/premiumcentraal-boop/Cyclone-Cloak/releases/tag/v0.8.0-alpha.5)
- APK: [app-release.apk](https://github.com/premiumcentraal-boop/Cyclone-Cloak/releases/download/v0.8.0-alpha.5/app-release.apk)
- Root module: [cyclone-cloak-0.8.0-alpha.5.zip](https://github.com/premiumcentraal-boop/Cyclone-Cloak/releases/download/v0.8.0-alpha.5/cyclone-cloak-0.8.0-alpha.5.zip)
- Release commit: `4bad0eb` (`release: Cyclone Cloak 0.8.0-alpha.5`)
- UI commit included in the release: `3662fe5` (`feat(android): clarify cloak profile identifiers`)
- GitHub Actions release workflow completed successfully; both release assets uploaded. The local release build also succeeded.

## What Alpha 5 contains

- Expanded identity cards with grouped device, identifier, SIM/network, display, and locale details.
- Copy actions for individual values and clearer selected-identity/profile binding guidance.
- Version updates: Android app `0.8.0-alpha.5` / version code `18`; module `v0.8.0-alpha.5` / version code `15`.
- Release workflow now uses `docs/RELEASE_0.8.0-alpha.5.md`.

## In-app phone-profile generation status

- **Not included in Alpha 5:** an in-app catalog for targeting additional phone models such as Pixel 4.
- The pushed development branch `dev/profile-identifiers-ui` contains a separate one-at-a-time creation prototype for the existing Pixel 7 and Galaxy S23 templates (`b046d1a`). It is not part of `main` or the Alpha 5 release.
- Pixel 4 profile data, model selection/catalog support, and a release/on-device verification of that expanded feature have not been completed.

## Planned next step

Add a phone-model selector inside Cyclone Cloak, include the intended target models, and let the user create and save a new profile for the selected model in the app. Show the created profile in the identity list so it can be reviewed and bound. Then build and verify that feature as a separate release.

## Repository state

- `main` points to `4bad0eb` and is tagged `v0.8.0-alpha.5`.
- `main` matches `origin/main`. The working tree has this new handoff and the pre-existing untracked `docs/HANDOFF-2026-10-09.md`; the pre-existing file was left untouched.
