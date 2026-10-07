# Cyclone Cloak 0.4.0-alpha.1

Sprint 4: coverage and tightening.

## What landed

- Telephony identity in the applied set: carrier name (network and SIM), the
  combined MCC+MNC operator numeric, and network type (5G is reported as NR,
  the Android convention).
- Display density in the applied set via `ro.sf.lcd_density`.
- Device identity now covers the bootloader and baseband props as well.
- Coverage ledger: every Forge-emitted field is marked applied, pending, or
  internal, and tests enforce the ledger against both the forge output and the
  module source. Nothing can silently skip the applied set again.
- State tightening: binding directories are traversable but not listable for
  other apps. The module resolves known paths, so discovery of profile data
  from other apps no longer works.

## Known limits

- IMEI/SIM serial, screen geometry, refresh rate and locale reach apps only
  through the deeper Java-level hook sprint (tracked as pending in the
  coverage ledger).
- Physical two-user verification is still pending.