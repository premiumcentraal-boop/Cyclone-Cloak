# Cyclone Cloak

**One phone, twenty identities.**

Cyclone Cloak is the identity engine of the [Cyclone](https://github.com/premiumcentraal-boop)
agent environment. It attaches on top of the Cyclone app and arms its profile selector with real,
fully coherent phone identities: a banking app installed under profile A and the same app under
profile B see two completely different devices - build fingerprint, identifiers, telephony, DRM,
locale, network. Nothing is shared, nothing is detectable as a clone.

The thin line that turns one playboy billionaire into twenty masked vigilantes.

## How it works

Cyclone Cloak is a companion app plus a Zygisk module for rooted Android:

1. **Profile Forge** builds each identity from real device dumps and refuses anything internally
   inconsistent (fingerprint vs. model vs. patch level vs. API level must all agree).
2. **Hook Engine** (Zygisk) feeds each scoped app its profile: build fields, properties, serial,
   and later telephony, MACs, Android ID, advertising/App Set ID, Widevine, locale. Hooks unload
   after injection, so nothing stays resident in the app's memory.
3. **Integrity Layer** keeps Play Integrity green per profile (pif.json + key attestation) and
   self-tests before a profile is ever used.
4. **Identity Vault** derives every identifier from a per-profile seed (HMAC), tracks health, and
   can recycle a burned identity in one step.
5. **Orchestration API** lets Cyclone apply, recycle, rotate, and health-check profiles without
   touching the phone.

No virtual containers anywhere: apps run as normal system apps; only what they read is rewritten.

## Status

Design locked, forge prototype landed (forge/, stdlib-only, pytest-tested). The Android scaffold
is the next milestone - see ROADMAP.md.

## Repository layout

```
docs/ARCHITECTURE.md      system design and integration contract
docs/HOOK_SURFACE.md      every spoofable surface, by phase
ROADMAP.md                build phases and working pattern
schema/                   profile JSON Schema (v0.1)
forge/                    profile forge: dump parsing, coherence validation, ID derivation
```

## Development

```sh
uv run --with pytest -- python -m pytest forge/tests
```

The forge is stdlib-only Python 3.11+; the profile schema is JSON Schema draft 2020-12.

## License and disclaimer

MIT. Use only on devices and accounts you own - profile spoofing can violate app terms of
service, and banking apps in particular decide their own risk appetite.
