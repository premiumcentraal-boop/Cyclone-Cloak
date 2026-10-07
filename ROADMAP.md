# Cyclone Cloak Roadmap

## Working pattern

- **Schema first.** `schema/cloak-profile.schema.json` is the contract. Code that reads or writes
  profiles validates against it, and `forge/` is the reference implementation of those rules.
- **Every surface earns its way in.** Nothing goes into the profile engine that is not listed in
  `docs/COMPAT_SURFACE.md` with a phase.
- **Coherence gates everything.** A profile that fails `forge` validation cannot be applied.
  Coherence rules ship with tests and are the definition of "done" for every generator change.
- **One milestone, one PR series.** Each phase below lands through reviewed PRs against `main`,
  tracked by milestone issues.

## Phase 0 - Foundation (done)

- Profile Forge prototype: build.prop dump parsing, coherence validator, stable identifier
  derivation (Luhn-valid IMEIs, UUIDv4 advertising IDs, MAC and serial generators).
- Profile schema v0.1, compat-surface inventory, architecture and integration contract.

## Phase 1 - Android scaffold (0.1: code complete, on-device validation pending)

- Companion app (`android/app`) as a Cyclone phone connector (contract `cyclone.connector/1`):
  imports cloak profiles, binds them to Cyclone profiles, writes the module config over su.
- platform module (`android/module`) with per-app Build field + system property profile rendering driven by
  the bound profile, packaged as a platform module zip.
- Acceptance (issue #1): with a profile bound, a probe app reads the rendered fingerprint and
  props; unscoped apps still read the real device; callbacks apply once and do not stay resident
  beyond the SystemProperties binding.

## Phase 2 - Full profile and integrity

- Identifier callbacks per `docs/COMPAT_SURFACE.md`: telephony, SIM/carrier, MACs, ANDROID_ID,
  advertising and App Set IDs, Widevine, GSF ID, locale/timezone, WebView user agent.
- Integrity layer: per-profile pif.json generation, Tricky Store key-attestation path, verdict
  self-test, fingerprint rotation when one burns.
- Profile vault: seed storage, recycle (reseed) flow, health panel per profile.

## Phase 3 - Cyclone orchestration

- Loopback API replaced by the connector contract where possible: profile switching events drive
  per-profile re-binding automatically.
- Cyclone Core driver and profile selector integration: Cloak profiles appear as first-class
  Cyclone profiles.
- Fleet automation: warm-up schedules, pacing rules, per-profile proxy binding.

## Non-goals

- Virtual containers (VirtualApp-style engines). They are the most fragile pattern in the
  space; Cloak renders at the system level instead.
- A STRONG integrity guarantee on every banking app. Hardware key attestation depends on keybox
  supply; the cloak reports exactly what it can honestly guarantee per profile.
