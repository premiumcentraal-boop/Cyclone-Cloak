# Cyclone Cloak 0.3.0-alpha.1

Sprint 3: the binding and delivery pipeline.

## What is new

- Resolved profiles now reach the module through the per-app state directory
  (see docs/STATE_LAYOUT.md), with a rebuilt module index on every binding change.
- Binding management in the app: list, enable/disable, and remove bindings.
- Resolution is cached and answers within the connector deadline.
- Module reader consumes the state index instead of the legacy config file.
- Layout contract documented with cross-language test vectors.

## Verification

- Forge suite: 27 tests passing (25 existing + 2 new layout vectors).
- JVM unit tests for the state layout pass locally.
- Release APK builds and is signed with the stable release key.