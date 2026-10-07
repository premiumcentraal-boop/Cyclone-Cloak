# Cyclone Cloak 0.5.0-alpha.1

Sprint 5: isolation. Published state moves to a root-only directory.

## What landed

- The module-facing state tree now lives at `/data/adb/cyclone_cloak/state-v1`,
  root-owned and mode 700. The companion publishes it through a single atomic
  `su` step (stage, copy, chmod, swap), so no other app can read published
  profiles or the index - including everything sprint 4 left world-readable at
  known paths.
- The companion builds its publish tree in its own private cache first; only the
  root-owned copy is ever visible outside.
- Legacy world-readable state from pre-0.5 releases is purged on every publish.
- Failure handling: when the `su` publish fails, the binding is marked
  `degraded` in the binding list so it is visible instead of silent. The key
  derivation, index shape, and profile JSON contract are unchanged from v1.
- The index carries `schemaVersion: 2`.

## Notes for installers

- The first publish may surface a `su` grant prompt; grant it once for the
  Cyclone Cloak companion. Later publishes reuse the grant silently.
- The key derivation and layout contract remain compatible with the forge test
  vectors; `STATE_LAYOUT.md` documents the v2 layout.

## Known limits

- IMEI/SIM serial, screen geometry, refresh rate and locale still reach apps
  through the pending Java-level hook sprint (tracked in the coverage ledger).
- Physical two-user verification is still pending (sprint 6 runbook).