# Cyclone Cloak 0.11.0-alpha.25

Signed prerelease; matching app/module version code **49**.

## Catalog Import Bridge v1

- Offline, opt-in forge/catalog import of a bounded Android hardware-profile XML
  subset: dimensions, logical density, declared camera/sensor presence, platform
  labels, memory/storage units and input/network feature hints.
- Separate versioned provenance manifest: source URL/local label, SHA-256,
  optional revision, license/notice and explicit license review, observation date,
  field evidence, confidence and known/unknown/unavailable states.
- Matching build-property attachment uses the existing parser. Duplicate keys,
  conflicting aliases and mismatched manufacturer/model are rejected. Missing
  `first_api_level` remains unknown instead of defaulting to the current SDK.
- Reviewed corrections take precedence, but concrete disagreement blocks promotion
  until the reviewer explicitly names the conflicting evidence. Hardware-only or
  incomplete drafts cannot become candidates.
- Approved candidates enter `DeviceFactory.from_imported_candidates()`, reusing
  deterministic per-instance identifier derivation, validation, supplied-library
  uniqueness and all-or-nothing in-memory batch generation. No automatic save,
  binding or replacement of the canonical catalog occurs.

## Verification and limits

Local gate: **1,767 tests passed**, zero skips: app 598; module 40; standard and
diagnostics probe 109 each; forge 810; schema 94; host probe checks 7. Debug and
release assembly/module packaging and artifact metadata checks pass. The normal
signed-validation and release workflows rerun the gate and sign using the existing
release identity. See the [implementation handoff](HANDOFF-2026-10-18-CATALOG-IMPORT-BRIDGE-V1.md)
and [pipeline documentation](CATALOG-IMPORT-BRIDGE-V1.md).

The complete fixture is **synthetic**, not a physical device capture. The eight
approved upstream sources are recorded references; their page hashes do not pin
downloadable hardware artifacts. Actual files still require artifact-specific
license/notice, model/SKU and firmware review. Only XML, build-property and explicit
review JSON adapters are implemented. Unsupported XML extensions fail closed.
There is no new Android import screen or automatic source downloader.

Imported certification, attestation and platform trust stay **unknown**.
No platform verdict or broader device coverage is claimed. No device tests were
run for alpha.25. Prior alpha.24 physical evidence remains specific to its measured
artifact; it is not relabelled or attached as new alpha.25 verification.

## Upgrade and compatibility

Use the normal signed APK and matching module ZIP. Existing profile schemas
0.2/0.3/0.4 remain readable; no migration or data clearing is required. This release
does not change production runtime/module behavior, connector protocol, binding
logic/storage, publication scripts, profile storage layout or permissions.
There are no new dependencies in the importer, network calls, credentials, key
material or platform-attestation behavior. App/module packaging metadata changes
together to alpha.25/code 49.

Release assets include `app-release.apk`, `cyclone-cloak-0.11.0-alpha.25.zip`,
the synthetic candidate, provenance schema, approved-source register and importer
handoff. The automatically generated source archive includes the forge API and
self-authored XML/build-property/review fixtures. Previous device evidence remains
linked from the runtime coverage ledger.
