# Cyclone Cloak 0.11.0-alpha.23

Prerelease; app/module version code 47.

- Adds pure Kotlin/Python linked parity for ten panel, carrier and locale/timezone
  Reality paths. Existing base profile values are authoritative; mismatches are
  explicit and never override base fields. Density uses existing display.density.
- Runtime Contract format 2 records linked paths and parity. Only covered, present,
  exactly matching links are APPLIED. Format 1 contracts and old profiles remain readable.
- Adds scoped Display.getMode and getSupportedModes parity for base width, height
  and refresh rate. Existing display metrics, refresh, locale/timezone and telephony
  behavior is preserved. No physical display settings are changed.
- Adds 13 digest-only linked probe rows and coverage-card parity/mismatch details.
  Equivalent scalar JSON numbers (90 and 90.0) compare consistently; strings and
  booleans are never coerced. Historical evidence is unchanged.
- No binding, publication, connector, profile schema, storage-layout, permission,
  networking, credential or attestation changes. No platform verdict is claimed.

Physical verification is **PENDING**. Local tests verify framework return objects
on API 34/35 and existing locale/telephony callback logic, not ART hook installation.
The attached phone still runs alpha.18/code 42; no module update or reboot was
performed. Historical attached alpha.18 evidence does not verify this binary or the
new callbacks. API 26–33 constructor fallback remains physically unverified.

Upgrade: install the signed APK with the existing signer and use the matching
module ZIP through the normal update flow. Loading the changed module requires a
separately coordinated restart. Existing profiles and bindings are retained.
