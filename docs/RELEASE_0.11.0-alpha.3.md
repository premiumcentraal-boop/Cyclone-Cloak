# Cyclone Cloak v0.11.0-alpha.3 — Engine integration

This prerelease integrates the local device-profile engine into generation,
import and identity diagnostics. It includes B4B and B5 through commit `e282564`.

- **Engine bundle import with strict validation:** rejects malformed JSON,
  duplicate keys, unsupported versions and inconsistent metadata. Profile
  validation and local uniqueness checks are recomputed before saving.
- **Deterministic uniqueness-aware generation:** normal new-profile actions retry
  derived seeds against the saved library. The chosen seed and attempt count are
  recorded without rewriting existing profiles. Direct generation remains available.
- **Local engine metadata sidecar:** engine version, profile schema version,
  template ID and source confidence are stored separately from the profile body
  and displayed in the identity UI.
- **Runtime evidence ingestion and coverage display:** locally loaded probe JSON
  maps to per-field verified, applied, pending, not_verified or failed states.
  Partial PASS results are not promoted to verified proof.
- **No module or connector behavior changes:** binding, synchronization,
  publication, runtime hooks and the connector contract are unchanged by this
  release. App/module packaging metadata advances together to version code 27.
- **Existing saved profiles remain compatible:** profile schemas 0.2 and 0.3
  remain supported. No migration, profile rewrite or storage-layout change is
  required. Existing profiles without sidecars remain readable.

## Upgrade and limitations

Install the signed APK as an update. The attached module ZIP uses matching release
metadata; its runtime behavior is unchanged from alpha.2. Back up important
profiles using the existing local backup action before upgrading. Existing backup
files do not include engine sidecars; restored profiles can show engine metadata
as not recorded. Loaded evidence is session-only and describes synthetic probe
fixtures on the measured binary, not the selected saved identity or the currently
installed module. Source confidence is not a device-verification claim.

No device tests, module installation or reboot were performed for this release.
Physical-device verification of the new app workflow remains unverified. Camera
checks still await the coordinated device pass with the newer module installed;
historical RC3 camera failures remain visible in loaded evidence.

## Local validation before release

B5 completed with 227 forge tests, 33 supplemental schema tests and 6 probe-runner
tests passing. Android: 293 app tests passed, 23 existing POSIX shell cases skipped
on Windows, and 12 module tests passed. Debug build passed. The release workflow
reruns forge and Android/module tests on Linux, builds the signed release APK and
module ZIP, verifies matching versions and the APK's embedded module, and attaches
both artifacts. See the associated Actions run for final release CI results.

Assets: `app-release.apk` and `cyclone-cloak-0.11.0-alpha.3.zip`.
