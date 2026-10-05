# Cyclone Cloak 0.2.0-alpha.1

This is the first 0.2 connector shell. It upgrades Cloak to the Cyclone connector
surface from Cyclone 5.0.0-alpha.106/107 and establishes the binding chain that the
rest of the identity engine will build on.

## What is new

- Vendored the Cyclone Connector SDK from `alpha.106`/`107`.
- Added support for `profiles.config` and `profiles.startup`.
- Added a per-profile, per-package binding store.
- Added a startup provider shell that receives Cyclone pre-launch events.
- Added a Cyclone-style Compose UI shell.
- Removed the old root-copy apply flow.

## Notes

- The startup provider replies with the bound identity reference, but the hook engine
  itself is still the next sprint.
- This alpha is intentionally small: it proves the connector path first.
