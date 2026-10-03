# Cyclone Cloak Architecture

Cyclone Cloak gives the Cyclone profile selector real phone identities: one rooted Android device
serves any number of internally coherent virtual devices - one per profile - to scoped apps. It
attaches on top of Cyclone without forking it.

## System view

```
Cyclone Desktop (profile selector, fleet dashboard)
        |  localhost HTTP + SSE (existing Cyclone transport)
        v
Cyclone Core (orchestration, policy, automation)
        |  loopback HTTPS + bearer token  (Cloak driver)
        v
Cloak Companion (Android app: forge, vault, hook control, API)
        |  Zygisk
        v
Cloak Hook Module  ---> scoped apps (Instagram, banking, ...)
```

## Layers

### 1. Profile Forge

Turns real device dumps into validated Cloak profiles. Source of truth for profile content is
the dump (build.prop + vendor build.prop, e.g. from the public tadiphone dump index); the forge
cross-checks every field against every other field and rejects incoherent identities. The
validator in `forge/cloak_forge/validate.py` is the canonical rule set:

- fingerprint must parse and agree with brand, product/device name, release, build ID,
  incremental, and build type of the device block;
- `ro.build.version.sdk` must agree with the release string where the mapping is unambiguous;
- security patch must be a real date, not in the future, not after the build date;
- identifiers (if pre-filled) must be structurally valid: 64-bit Android ID, UUIDv4 advertising
  and App Set IDs, Luhn-valid IMEIs, well-formed MACs;
- locale/timezone must be well-formed.

### 2. Hook Engine

A Zygisk module (Magisk, KernelSU, and APatch via Zygisk Next) that runs in each scoped app's
process and rewrites what the app reads, per the profile bound to that app: build fields, system
properties, serial number, and the Phase 2 identifier set from `docs/HOOK_SURFACE.md`.

Stealth posture: hooks apply once at process start and then unload; no code stays mapped in the
app's memory; nothing is injected into `system_server`; unscoped apps are untouched.

### 3. Integrity Layer

Per-profile Play Integrity management: each profile carries its own pif.json, Tricky Store
handles key attestation where a valid keybox is available, a self-test runs the Play Integrity
verdict before a profile is assigned, and a burned fingerprint rotates automatically.

Honest limits: device integrity (the verdict most apps gate on) is reliably achievable. STRONG
integrity needs a genuine hardware keybox - that is a supply question, and the health panel
reports per profile what was actually verified.

### 4. Identity Vault

Every profile stores a 256-bit seed; all identifiers derive from it with HMAC-SHA256 (see
`forge/cloak_forge/derive.py`), so values are stable for the profile's lifetime, unique across
profiles, and never duplicated. The vault tracks health state (integrity verdict, fingerprint
age, last egress IP, bound account) and supports the recycle flow: a new seed regenerates the
entire identifier set, in place, with the DeviceResetSpoofer sentinel pattern triggering it on
app data clear.

### 5. Orchestration API

The companion app exposes a loopback HTTPS API with a bearer token:

| Endpoint | Purpose |
| --- | --- |
| `GET /profiles` | list profiles with health state |
| `POST /profiles` | forge a new profile (dump text or spec in, validated profile out) |
| `POST /profiles/{id}/apply` | bind a profile to an app package |
| `POST /profiles/{id}/recycle` | reseed the identity, keep the slot |
| `POST /profiles/{id}/rotate-fingerprint` | pull a fresh, valid fingerprint |
| `GET /profiles/{id}/health` | coherence + integrity + last-seen state |

Cyclone Core gets a thin driver for these endpoints; the desktop profile selector lists Cloak
profiles as first-class profiles. No fork of Cyclone, no fork of the cloak - one contract.

## Design principles

1. **System hooks, not containers.** Apps run as normal system apps; only what they read is
   rewritten. No VirtualApp-style engine anywhere.
2. **Schema first.** `schema/cloak-profile.schema.json` is the contract between forge, vault,
   module, and API.
3. **Coherence over creativity.** Generated values must be indistinguishable from a real dump;
   the validator, not the generator, has the final word.
4. **Per-app, always.** One profile per app binding; the device's real identity stays intact for
   everything else.
