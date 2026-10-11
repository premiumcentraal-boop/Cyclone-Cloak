# Cyclone Cloak 0.10.0-rc.1

Release candidate prepared on 2026-10-11 from `v0.10.0-alpha.1`. This is a
prerelease, not a declaration of full device compatibility. The RC has not been
tagged, published, installed or tested on a phone.

## Versions and artifacts

- App: `dev.cyclone.cloak`, version `0.10.0-rc.1`, version code **22**; minimum API 29, target API 36.
- Magisk/Zygisk module: `cyclone_cloak`, version `v0.10.0-rc.1`, version code **22**; ARM64 and ARMv7, minimum API 26.
- Release workflow builds the release APK and `cyclone-cloak-0.10.0-rc.1.zip`,
  embeds that exact ZIP in the APK, checks both artifacts and attaches both.
  CI signs the APK with the configured release keystore; the local release APK
  is unsigned. The workflow has not been dispatched for this RC.

## Major fixes and diagnostics

- Stronger compatible profile validation: build/release/SDK coherence, manufacturer/model
  relationships, display sanity, locale/timezone and identifiers; field-specific,
  machine-readable reasons. Profile schema remains **0.2**.
- Four additional conservative phone templates: Pixel 6, 6a, 7a and 8, with
  catalog-wide validation and documented source confidence.
- Phone builder sections, inline feedback, blocking save validation and live summary;
  binding filters, clearer active/disabled health and missing-profile warnings.
- Health Doctor v2: one readiness verdict, pass/warn/fail checks, release matching
  and an allowlisted redacted diagnostics clipboard report. Private identities
  and raw errors are excluded.
- Health compatibility matrix generated from checked-in facts, with implementation,
  test status, verification dates and evidence references across 13 areas.
- Process-safe shared publication lock, isolated staging, interrupted share/tree
  recovery, preserved rollback backups and explicit publication outcomes.
- Reconciliation retains ambiguous missing/trashed records and duplicate timestamp
  ties, protects deferred remote tuples, reports failed clears, and tests user moves,
  confirmed removals, retries and strict read-back. Connector contract and binding
  schema remain unchanged.
- Local runtime corrections for Build.VERSION.INCREMENTAL, typed telephony returns,
  guarded Android ID/settings callbacks, advertising-ID class loading and
  locale/resource configuration. **These changes are unverified on a device.**

## Validation and known limitations

- 61 reconciliation-related JVM tests and 40 publication/diagnostics JVM tests
  passed via direct compilation of production Kotlin sources; five module callback
  tests remain passing from the Gradle cache. 148 Python tests
  passed, including 10 release artifact gates; see [CURRENT-STATE.md](CURRENT-STATE.md).
- Local debug/release APK and module builds passed; artifact verification confirms
  RC/22 metadata, both ARM ABIs and byte-identical embedding. No build warnings.
- Full Android unit/screen tests are **not verified locally**: Gradle is blocked
  by an uncached Robolectric framework. The release workflow requires these tests
  to pass before building/publishing; the local subset is not a substitute.
- The 70-read API36 device baseline belongs to an older installed binary:
  **13 verified, 39 failed, 7 blocked, 8 coincidental matches, 3 unmapped**.
  Its module version label does not establish byte identity with this RC.
  [Runtime evidence](RUNTIME-COVERAGE.md) remains scoped to that measured hash.
- **Unverified:** RC hooks on ART/Pine, cross-user approval/revocation, signed
  upgrade, publication under Android Toybox concurrency, device read-back after
  module restart, restore/reconciliation and reboot behavior.
- Camera/sensor metadata hooks and display geometry are incomplete; many identifier,
  property, network and locale surfaces still need device proof. Permission-blocked
  reads remain blocked. App Set ID SDK callbacks and uptime offsets are absent.
- Publishing `pif.json` does not verify Play Integrity or implement keybox management.
- Older publishers and direct external writers do not honor the new lock. Upgrade
  all publisher installs before concurrency checks. Two-rename publication can
  temporarily leave the reader path absent; failed rollback retains the previous
  tree for a later recovery attempt. No power-loss durability claim is made.
- Three legacy catalog templates still need a source audit. Ambiguous orphan
  bindings can persist if a removal event was missed; review them explicitly.

Follow [UPGRADE_0.10.0-rc.1.md](UPGRADE_0.10.0-rc.1.md). Reboot-dependent checks
remain deferred in [DEFERRED-DEVICE-CHECKS.md](DEFERRED-DEVICE-CHECKS.md).
