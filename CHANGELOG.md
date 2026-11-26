# Changelog

## v1.0.0-rc.3 — prerelease

- Calm-glass UI refresh across five existing destinations, including the home overview, profile gallery and detail sheets.
- Includes a diagnostics-only WebView user-agent reader change; the physical exception remains unresolved.
- App/module metadata: `1.0.0-rc.3` / code `62`. Local release gate passed; physical UI and RC3 runtime verification are not established. See [RC3 release notes](docs/RELEASE_1.0.0-rc.3.md) for exact test counts and limitations.

## v1.0.0-rc.2 — prerelease candidate

- Corrected package-read failures for missing declared package-version facts, retaining host information where unspecified.
- Extended the verification harness to accept an explicit version/code pair.
- App/module metadata: `1.0.0-rc.2` / code `61`. See [RC2 release notes](docs/RELEASE_1.0.0-rc.2.md) for local tests and evidence limits.

## v1.0.0-rc.1 — historical prerelease candidate

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
`jsonschema` were missing from its environment. This describes the initial local gate only. A subsequent dependency-complete run passed 1,134 forge tests; see [RC1 release notes](docs/RELEASE_1.0.0-rc.1.md). Signed artifact verification and physical results are separate. No platform verdict or broader device compatibility is claimed.
