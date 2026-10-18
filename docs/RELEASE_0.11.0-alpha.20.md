# Cyclone Cloak 0.11.0-alpha.20 — Device Reality v1

App/module version code **44**. This prerelease adds local descriptive metadata
and read-only diagnostics. No platform verdict is claimed.

- Optional schema **0.4** sections describe compute, memory/storage, display
  panels/modes, camera summaries, sensors, power/thermal state, DRM level labels,
  radio names/state, carrier/SIM, package versions, WebView and input/environment.
  Values are typed, bounded and cross-checked against the base profile. Unknown
  and unavailable facts stay explicit; private/key material is not accepted.
- New identities and workshop drafts request 0.4. Generation copies conservative
  catalog facts and derives local inventory markers/configured labels from the
  seed. Uniqueness checks and explicit draft regeneration cover those fields.
  Existing direct engine/forge APIs retain their 0.3 default and accept explicit
  0.4 requests. Existing 0.2/0.3 profiles, imports and backups remain accepted;
  no saved profile is migrated or rewritten.
- Catalog provenance distinguishes public references from approximations and
  gaps. Corrected Pixel 7 SoC hardware family and S23 hardware/device family
  labels affect future generation only. Old template-fingerprint plans fail
  safely when their template has changed. No historical evidence is relabelled.
- Identities → Diagnostics & evidence → **Reality coverage** shows local coherence,
  missing optional sections, warnings, schema and provenance. A score of 100
  means no local coherence errors; it does not mean complete or verified hardware.
- An optional hashed `device_reality` probe extension covers 43 field mappings.
  The original 89 rows and capture-session write protection are preserved.
  Coverage distinguishes verified, match-only, observed, blocked, missing and
  unmapped; this importer never promotes a match to verified. Failed comparisons
  remain visible. Only digests, statuses and fixed reason classes are exported.

## Compatibility and limitations

No new runtime hooks, physical hardware behavior, module implementation,
connector protocol, binding logic, publication behavior, storage layout,
permissions or networking changes. No attestation bypass, credentials/keyboxes,
accounts, SMS or evasion logic. Module changes are packaging metadata only.

Camera/sensor inventories, GPU, DRM and installed package facts are not invented
when the catalog lacks a trustworthy source. Live battery, thermal, radio,
carrier and input state depends on the real device. Caller-visible packages may
be incomplete. GPU/CPU inventory, advertised storage/capacity, panel technology,
Wi-Fi device label, WebView UA and keyboard layout have unmapped observations.
Exact array comparisons include ordering; physical RAM differs from marketed
capacity. These limitations are documented in [Device Reality](DEVICE-REALITY.md).

Physical-device verification of this feature and the new probe extension is
**UNVERIFIED**. Historical alpha.18 evidence attached by the existing release
workflow (`evidence-bundle.zip`, `summary.md`, `build-provenance.json`) describes
its recorded artifact only; it does not verify alpha.20 or new fields.

## Local gate and upgrade

Install the signed APK through the existing update path; the APK embeds its
matching module ZIP. Existing identity files remain in place. Backups retain
their explicit sensitive-value choice; default export omits the new inventory
marker and configured radio names. Imports validate rather than infer facts.

Local gate: **546 app**, **12 module**, **96 probe per flavor**, **556 forge**,
**60 schema**, **7 probe host** tests. Shared vectors cover deterministic
generation and all twelve rule groups; focused checks cover legacy preservation,
backup redaction, uniqueness/regeneration, malformed metadata, read-only report
hashes, denial/error paths, coverage classification and Compose visibility.
Debug/release assembly, module packaging, metadata verification and diff checks
must pass before the existing signed-validation and release CI publish gate.
Exact commands and file list: [sprint handoff](HANDOFF-2026-10-18-DEVICE-REALITY.md).
