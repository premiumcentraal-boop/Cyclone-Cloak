# Cyclone Cloak Roadmap

## Working pattern

- **Schema first.** `schema/cloak-profile.schema.json` is the contract. Code that reads or writes
  profiles validates against it, and `forge/` is the reference implementation of those rules.
- **Every surface earns its way in.** Nothing goes into the hook engine that is not listed in
  `docs/HOOK_SURFACE.md` with a phase.
- **Coherence gates everything.** A profile that fails `forge` validation cannot be applied.
  Coherence rules ship with tests and are the definition of "done" for every generator change.
- **One milestone, one PR series.** Each phase below lands through reviewed PRs against `main`,
  tracked by milestone issues.

## Phase 0 - Foundation (this commit)

- Profile Forge prototype: build.prop dump parsing, coherence validator, stable identifier
  derivation (Luhn-valid IMEIs, UUIDv4 advertising IDs, MAC and serial generators).
- Profile schema v0.1, hook-surface inventory, architecture and integration contract.

## Phase 1 - Android scaffold

- Companion app (`app/`) and Zygisk module (`module/`) in one Android Studio project; the module
  loads under Magisk, KernelSU, and APatch via Zygisk Next.
- Per-app Build field and system property spoofing driven by a Cloak profile.
- Acceptance: with a profile applied, a probe app reads the spoofed fingerprint and props;
  unscoped apps still read the real device; hooks are absent from the app's memory after startup.

## Phase 2 - Full identity and integrity

- Identifier hooks per `docs/HOOK_SURFACE.md`: telephony, SIM/carrier, MACs, ANDROID_ID,
  advertising and App Set IDs, Widevine, GSF ID, locale/timezone, WebView user agent.
- Integrity layer: per-profile pif.json generation, Tricky Store key-attestation path, verdict
  self-test, fingerprint rotation when one burns.
- Identity vault: seed storage, recycle (reseed) flow, health panel per profile.

## Phase 3 - Cyclone orchestration

- Loopback HTTPS API in the companion app: apply, recycle, health, rotate.
- Cyclone Core driver and profile selector integration: Cloak profiles appear as first-class
  Cyclone profiles.
- Fleet automation: warm-up schedules, pacing rules, per-profile proxy binding.

## Non-goals

- Virtual containers (VirtualApp-style engines). They are the most detectable pattern in the
  space; Cloak hooks at the system level instead.
- A STRONG integrity guarantee on every banking app. Hardware key attestation depends on keybox
  supply; the cloak reports exactly what it can honestly guarantee per profile.
