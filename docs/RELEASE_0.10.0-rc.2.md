# Cyclone Cloak 0.10.0-rc.2

Published on 2026-10-12 (Berlin; 2026-10-11 UTC) as a
[prerelease](https://github.com/premiumcentraal-boop/Cyclone-Cloak/releases/tag/v0.10.0-rc.2) from `b9d8606`.
[Release workflow](https://github.com/premiumcentraal-boop/Cyclone-Cloak/actions/runs/38180047567) passed full Android/module tests, **185 Python tests**,
signed APK/module builds and artifact verification before upload.
Main CI also passed. Physical-device behavior remains **unverified**.
Local Android/Compose execution remains dependency-blocked; CI supplied the full
suite verification. This is not a claim of complete device compatibility.

## Versions and packaging

- App `dev.cyclone.cloak`: `0.10.0-rc.2`, versionCode **23**, minimum API 29,
  target/compile API 36.
- Module `cyclone_cloak`: `v0.10.0-rc.2`, versionCode **23**, ARM64 and ARMv7,
  minimum API 26. Runtime behavior is unchanged in this release-preparation run.
- Profile schema **0.2**, binding schema **2**, module reader format and
  `cyclone.connector/1.3` remain compatible.
- Release workflow runs Python and Android/module tests, builds a signed APK and
  `cyclone-cloak-0.10.0-rc.2.zip`, verifies metadata/ARM payloads and identical
  embedded module bytes, then attaches both. This test-gated workflow completed successfully for RC2.
  Local release builds are unsigned; signing remains a CI credential requirement.

## Major improvements

- Coherence findings have stable severity, reason codes, field paths and readable
  explanations; Kotlin/Python shared rules cover build, version, manufacturer,
  display, locale/timezone, identifiers, carrier and hardware consistency.
- Fifteen catalog templates with documented source confidence, including eight
  additional compact, mainstream, large-display, midrange, older/newer examples.
- Phones generates validated profiles with stable stored seeds. The builder
  groups fields, shows inline findings and blocks saving invalid configurations.
- Local distinguishing-value checks flag duplicates without migrating or
  rewriting existing profiles; explicit per-field regeneration is available.
- Guided single-app binding adds selection, actionable warnings, final review
  and retry. Existing bulk binding and connector contract are retained.
- Profile-only local backups support one profile or the library, previewed
  validated batch imports, duplicate detection and schema-mismatch rejection.
  Default exports omit seeds/identifiers; explicit inclusion is optional.

## Diagnostics and reliability

Readiness Doctor v3 shows ready/degraded/failed and individual reasons for
versions, bindings, profile references, readability, publication, synchronization
and conflicting records. Copy diagnostics uses fixed allowlisted reasons and
omits names, seeds, identifiers, keys, tokens, raw errors and user content.
The bundled coverage matrix distinguishes source implementation from evidence.

Publication holds a process-safe kernel lock through staging and share merge.
Swap failures retain the previous complete tree; interrupted attempts recover
under the lock. Locked/completed/failed/recovered and stale-marker details are
explicit. Ambiguous removed/trashed profiles, missing apps, duplicate records and
transient connector failures preserve local records and report warnings.
Validated profile-library imports stage and commit the whole batch with rollback.

## Migration and upgrade

No profile or binding migration is required. Existing identities remain readable
and keep their values; coherence/uniqueness warnings may newly flag them.
The backup envelope is a portable export format, not a new internal profile
storage schema. Default backup restore creates fresh identity values and excludes
bindings, Cyclone identifiers and publication state. Advanced full-state backup
is not implemented. Follow [UPGRADE_0.10.0-rc.2.md](UPGRADE_0.10.0-rc.2.md).

## Known limitations and evidence

- **RC2 physical-device checks are unverified.** Signed upgrades, module activation
  after reboot, multi-user publication, guided binding and Downloads provider
  behavior need device checks. Reboot-dependent checks remain deferred in
  [DEFERRED-DEVICE-CHECKS.md](DEFERRED-DEVICE-CHECKS.md).
- Older runtime evidence records 13 verified reads of 70 on the recorded binary;
  it does not verify RC2. Many identifier reads fail; display geometry,
  camera/sensor metadata and App Set SDK coverage remain incomplete.
- Ready is reported readiness, not full inspection coverage or Play Integrity.
  Keybox management, automatic attestation/rotation and reset reseeding are absent.
- Publication uses two renames and can briefly leave the reader path absent;
  failed rollback requires the next recovery attempt. Power-loss durability and
  old/noncooperating writers are not covered. Upgrade all publishers together.
- Profile-library atomicity coordinates this process's storage callers; it does
  not promise process-safe or power-loss transactions. Downloads and the private
  export are separate complete artifacts; failed public copies retain the private
  backup. Private backups disappear on data clear/uninstall.
- Catalog values are conservative references/approximations, not captured
  firmware or physical verification. Local duplicate checks are per Android user.

Exact verification commands and results: [CURRENT-STATE.md](CURRENT-STATE.md)
and [RC2 handoff](HANDOFF-2026-10-12-RC2.md).
