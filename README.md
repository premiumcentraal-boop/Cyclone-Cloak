# Cyclone Cloak

**Per-profile device characteristics for development, testing, and privacy.**

Cyclone Cloak is the device-profile engine of the [Cyclone](https://github.com/premiumcentraal-boop/Cyclone)
agent environment. It attaches to Cyclone as a first-class phone connector (contract
`cyclone.connector/1`): each Cyclone profile on the phone can present a chosen, internally
consistent set of device characteristics — build fingerprint, system properties, and the
identifiers an app reads — so the code running in that profile behaves as it would on the
modelled device.

The goal is to make one physical phone a useful bench for work that otherwise needs a drawer
full of handsets:

- **App development and QA.** Run your own app against many device models, build fingerprints
  and API levels — check layouts, feature gates and compatibility — without buying every phone.
- **Privacy and anti-tracking research.** Study how apps and SDKs fingerprint a device, and
  reduce the passive cross-app tracking that device identifiers enable, on a device you own.
- **A reproducible test bench for Cyclone.** Give each Cyclone profile a stable, documented
  device identity so automation runs are repeatable and easy to reason about.

### Intended use, and what this is not for

Use Cyclone Cloak only on devices and accounts **you own or are authorised to test**, and only
where presenting modified device characteristics is permitted. It is **not** a tool for evading
fraud, abuse, security or anti-cheat controls, for circumventing bans, rate limits, KYC or
identity checks, or for making multiple accounts or installs look like unrelated people or
devices to a service that forbids it. Those uses are out of scope, unsupported, and in many
places against the law or the service's terms. If you are unsure whether a use is allowed, get
written permission from the device owner and the service operator first.

## Status: 0.8.0-alpha.5 (pre-release; on-device validation required)

- **Companion app** (`android/app`): a Cyclone connector. Imports cloak profiles (JSON, same
  schema as the forge emits), binds them to Cyclone profiles via the connector contract
  (`profiles`, `ext.set`, selector entries), and writes the per-app config the module applies
  (over su, to `/data/adb/cyclone_cloak/config.json`).
- **Per-profile isolation**: bindings are keyed by Android user id and package name, so the
  same app in two Cyclone profiles cannot collide.
- **platform module** (`android/module`): for every app bound to a cloak profile, rewrites the
  `android.os.Build` statics and callbacks `SystemProperties` reads so the app sees the bound device.
  Callbacks apply once at process start; unscoped apps are untouched.
- **Root Doctor**: checks Magisk, Zygisk, module version and architecture, and profile-state publishing. Its
  explicit repair action can use the matching module bundled inside the signed app, then explains when a
  reboot and a second check are needed.
- **Profile Forge** (`forge/`): reference implementation of the coherence rules (fingerprint vs.
  model vs. patch level vs. API level), dump parsing, and stable identifier derivation
  (HMAC seeds, Luhn-valid IMEIs). Stdlib-only Python, pytest-tested.
- **Roadmap**: docs/ROADMAP.md. Remaining for Phase 1: on-device validation (issue #1), then
  identifier callbacks and the integrity layer (Phase 2).

## Development

```sh
# forge tests
uv run --with pytest -- python -m pytest forge/tests

# android build (needs JDK 17 + Android SDK)
gradle -p android :app:assembleDebug :module:packageModule
```

CI runs both on every push and PR. The module zip lands in the CI artifacts
(`module/build/module/cyclone-cloak-0.1.0.zip` locally).

## Repository layout

```
android/                  companion app (connector) + platform module
  app/                    connector app: profile import, binding, su bridge
  module/                 platform module: Build/props callbacks, platform zip packaging
docs/ARCHITECTURE.md      system design and integration contract
docs/COMPAT_SURFACE.md      every renderable surface, by phase
forge/                    profile forge: dump parsing, coherence validation, ID derivation
schema/                   profile JSON Schema (v0.1)
```

## License and disclaimer

MIT. You are responsible for how you use it: run it only on devices and accounts you own or are
authorised to test, and follow the laws and the terms of service that apply to you. See
"Intended use, and what this is not for" above.
