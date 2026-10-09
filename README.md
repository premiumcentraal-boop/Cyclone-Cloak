# Cyclone Cloak

**One phone, twenty profiles.**

Cyclone Cloak is the device-profile engine of the [Cyclone](https://github.com/premiumcentraal-boop/Cyclone)
agent environment. It attaches to Cyclone as a first-class phone connector (contract
`cyclone.connector/1.3`): each Cyclone profile on the phone gets its own real, fully coherent device
profile. A banking app under profile A and the same app under profile B see two completely
different phones - build fingerprint, properties, identifiers. Nothing is shared, nothing is
linked to another install.

The thin line that turns one playboy billionaire into twenty masked vigilantes.

## Status: 0.9.0-alpha.1 (pre-release; on-device validation required)

- **Companion app** (`android/app`): a Cyclone connector (contract `cyclone.connector/1.3`, Cyclone
  5.0.0-alpha.122 or newer). Imports or creates cloak profiles, binds them to the apps of Cyclone
  profiles, publishes the bound profiles for the module over su
  (`/data/adb/cyclone_cloak/state-v1/`, see docs/STATE_LAYOUT.md), and reports each binding's
  health to Cyclone so Cyclone's Rooted pill tells the truth.
- **Per-profile isolation**: bindings are keyed by Cyclone profile id and package name; the Android
  user number follows the profile (a profile restored under a new user keeps its bindings).
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

## Working with Cyclone's profiles

Each Cyclone profile is its own Android user, with its own Cyclone and (since Cyclone alpha.120) its
own copy of Cloak. Cloak only talks to the Cyclone in its own Android user.

- **Approve once.** Approve Cyclone Cloak in Cyclone → Settings → Connectors (normally in Main).
  Cyclone carries the approval into each profile on a switch made on the phone. Until a profile's
  Cyclone has it, Cloak there shows one line: *Approve Cyclone Cloak in Cyclone → Settings →
  Connectors in this profile*.
- **Cloak in Main is the authority.** It binds apps in every Cyclone profile (never in Main itself)
  and writes those bindings to Main's Cyclone. Cloak inside profile C reads what Main published for C
  and writes it to C's Cyclone, so C's Profiles page shows the same pill. It may also bind C's own
  apps; for an app both bound, Main wins.
- **Health.** Cloak reports `ready`, `degraded` (for example, root not granted here, or a reboot
  pending) or `failed` (module missing or disabled, Zygisk off, identity file gone) for every bound
  app. Cyclone shows Rooted / Rooted · check / Rooted · not working / Native from it. Cloak shows its
  own pill per profile: Rooted ✓ (healthy and Cyclone's last root check passed), Rooted ! (with the
  reason), or Native.
- **Open in Cyclone.** Cloak can ask Cyclone to open another profile. Cyclone asks *you* on its own
  screen (Open / Not now); Cloak never switches anything. Cloak learns the result from Cyclone's
  `profile.switched` event.
- **Staying in step.** On start, on every Cyclone wake (`profile.switched`, `profile.updated`,
  `profile.restored`, `profile.removed`, …) Cloak re-reads the profile list, moves bindings to a
  profile's new user number, forgets permanently deleted profiles, writes what is missing or
  different, and clears what it no longer binds. Writing the same value twice is harmless.

### What Cloak sends to Cyclone

Per bound app, through `config.set.v1`, only this summary:

```json
{"cloakProfileId": "pixel-8-work", "identityVersion": 1, "name": "Work phone",
 "manufacturer": "Google", "model": "Pixel 8", "androidRelease": "15", "sdkInt": 35}
```

and through `config.status.v1` the health state (`unknown`, `ready`, `degraded`, `failed`).

### What Cloak never sends

No Android ID, IMEI, serial number, MAC address, build fingerprint, advertising or GSF id, account
name, token, password or other secret: those stay in Cloak's own profile files and the root-only
state tree. Cloak never asks Cyclone to run a command or use root, and never asks it to approve,
send, delete, grant, or create, switch or remove a profile; opening a profile is only a question
you answer on Cyclone's screen.

### Revoking

Cyclone → Settings → Connectors → Cyclone Cloak → **Revoke**, in each profile where you want it
gone. Cyclone deletes everything Cloak stored in that profile's Cyclone (the bindings above), and
its pill falls back to Native. Your cloak profiles and the module state stay on the phone until you
remove the bindings in Cloak or uninstall it.

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
