# Cyclone Cloak 0.8.0-alpha.1 (Fleet mode)

This release is about scale: forge a fleet of coherent phone identities on the
device, bind them across your Cyclone profiles in one step, and manage them
from a single screen.

- **On-device forge.** The full identity forge now runs inside the companion
  app, bit-for-bit parity-tested against the desktop forge, so the same seed
  produces the same phone everywhere.
- **Batch generation.** Pick a device template and a count (1 to 99); the
  fleet is auto-named Vault 01, Vault 02, ... and every identity is unique,
  coherent, and schema-validated before it is stored.
- **Fleet bind.** One tap assigns unused identities to every ready Cyclone
  profile that has no bindings yet, writes the config, and publishes once.
  Already-bound profiles are never touched and identities are never handed
  out twice.
- **Fleet export/import.** The whole identity set travels as one versioned
  JSON file with validation on the way in, so a fleet can be backed up,
  restored, or shared between devices.
- **Fleet dashboard.** The bindings list remains the single source of truth:
  per-app state, enable/disable, and removal stay one tap away.

No module schema change; the module zip is rebuilt for version alignment.
Identifiers, hooks, and pif.json behavior are unchanged from 0.7.0.

## First setup or app update

1. Install or update the companion APK and approve the Cyclone connector.
2. Use Fleet forge to generate the number of identities you need.
3. Tap Bind fleet; every ready Cyclone profile without bindings gets its own
   identity, then publishing runs once.
4. Watch the bindings list: any binding that is not "ready" tells you why.
