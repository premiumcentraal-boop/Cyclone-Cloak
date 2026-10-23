# Changelog

## v1.0.0-rc.1 — unreleased

Changes since `v0.11.0-alpha.28`:

- Added a Shamiko-style denylist opt-in through `cloak.denylist_enabled`, using
  the existing root companion to request Magisk denylist configuration. This
  does not establish that Shamiko is installed or that isolation was measured.
- Added the optional per-profile `pif` integrity configuration section and its
  runtime-envelope projection. Declared configuration is not an authenticated
  integrity response or proof of the physical boot state.
- Added three diagnostics self-visibility readers: `masked.package_list`,
  `masked.maps` and `masked.services`. RC device results are pending; restricted
  visibility, unavailable evidence and collection errors remain limitations.
- Removed a stale debug log line from `ProbeReportPolicy`.
- Updated coverage-ledger wording and documented the distinction between
  implemented capability and artifact-specific physical evidence. Historical
  measurements are not automatically promoted by a code change.
- Set matching app/module metadata to `1.0.0-rc.1` / code `60`.

Local verification: 784 app, 80 module, 148 standard probe, 148 diagnostics probe
and 192 schema tests passed. Debug/release APK builds and module packaging passed.
The forge command stopped with four collection errors because `cryptography` and
`jsonschema` were missing from its environment. The release gate remains blocked;
no RC tag, publication or RC physical smoke test has occurred. No platform verdict
or broader device compatibility is claimed.
