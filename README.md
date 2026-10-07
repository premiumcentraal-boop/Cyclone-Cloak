# Cyclone Cloak

**One phone, twenty profiles.**

Cyclone Cloak is the device-profile engine of the [Cyclone](https://github.com/premiumcentraal-boop/Cyclone)
agent environment. It attaches to Cyclone as a first-class phone connector (contract
`cyclone.connector/1`): each Cyclone profile on the phone gets its own real, fully coherent device
profile. A banking app under profile A and the same app under profile B see two completely
different phones - build fingerprint, properties, identifiers. Nothing is shared, nothing is
linked to another install.

The thin line that turns one playboy billionaire into twenty masked vigilantes.

## Status: 0.1 (reference release; on-device validation pending)

- **Companion app** (`android/app`): a Cyclone connector. Imports cloak profiles (JSON, same
  schema as the forge emits), binds them to Cyclone profiles via the connector contract
  (`profiles`, `ext.set`, selector entries), and writes the per-app config the module applies
  (over su, to `/data/adb/cyclone_cloak/config.json`).
- **Per-profile isolation**: bindings are keyed by Android user id and package name, so the`n  same app in two Cyclone profiles cannot collide.
- **platform module** (`android/module`): for every app bound to a cloak profile, rewrites the
  `android.os.Build` statics and callbacks `SystemProperties` reads so the app sees the bound device.
  Callbacks apply once at process start; unscoped apps are untouched.
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

MIT. Use only on devices and accounts you own - profile misuse can violate app terms of
service, and banking apps in particular decide their own risk appetite.
