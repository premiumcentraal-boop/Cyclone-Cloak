# Instance realism v1

Unreleased, opt-in, pure generation. This adds no runtime surface, UI action,
storage layout, permission, connector or publication change. Old schema 0.2,
0.3 and 0.4 profiles remain readable without migration; default generation stays
unchanged. Realism generation requires schema 0.4 and an approved catalog source.

## Approved variations

`catalog/instance-realism-v1.json` contains 134 complete tuples for 24 source
baselines across 12 model families. Sources distinguish the phone catalog from
Device Factory. Twenty-two sources have a second catalog-backed firmware bundle.
Selection always chooses a whole reviewed tuple, rather than independently
combining hardware options.

| Axis | Current support and limits |
| --- | --- |
| RAM/storage | Reviewed US nominal SKU pairs for Pixel 6/6 Pro, 7/7 Pro, 8/8 Pro and 8a. RAM is fixed within each supported model; storage varies only among listed pairs. Existing `_gib` fields retain the catalog's nominal capacity convention, not measured usable capacity. |
| Region/carrier | Existing issuer, MCC/MNC, SIM count and country remain fixed. No additional carrier facts are invented. |
| Locale/timezone | US templates may select New York or Los Angeles as configured preferences. These are not observations of location or network service. Other locales remain fixed. |
| Firmware/modem | Exact counterpart catalog bundles may vary only for the same manufacturer, brand, model, product, device, hardware and launch API. Fingerprint, release, SDK, patch, build ID, incremental, build time, bootloader and baseband move together. Inherited catalog strings are labelled approximated, not verified stock firmware. |
| Identifiers/radio labels | Existing deterministic derivation is reused, with cross-slot collision checks. These are local compatibility identifiers and labels, not proof of issued identities or provisioned DRM. |
| Cameras/sensors | Baseline inventories stay fixed or unknown/unavailable. The tuple format can represent reviewed alternatives, but none are fabricated for this release of the catalog. |
| Packages/WebView/browser | Known package/provider facts stay fixed; unknown facts stay unknown. The configured user-agent follows the selected firmware and existing declared provider information. No installed-package or browser observation is claimed. |

SKU source: [Google Pixel hardware specifications](https://support.google.com/pixelphone/answer/7158570?hl=en),
reviewed 2026-10-20. Registry evidence records source labels/URLs, source catalog
digests, dates and confidence. This does not add physical-device verification.

## API and reproducibility

Python engine example:

```python
from cloak_forge.engine import generate_unique

bundle = generate_unique({
    "name": "Local draft", "template": "pixel_7", "seed": "1" * 64,
    "schema_version": "0.4", "instance_realism": True,
}, existingProfiles=[])
```

Python Factory accepts `InstanceRequest(..., instance_realism=True,
realism_tuple=None)` and batch accepts the corresponding optional flags. Kotlin
`IdentityEngine.GenerationRequest` has matching defaulted options. The pure
`InstanceRealism.plan`, `apply`, `validate` and `conflicts` helpers implement the
same selection and checks in both languages.

Selection hashes source ID and input seed into a sorted tuple list, then derives
the final profile seed from source, input seed and tuple ID. A caller may pin
`realism_tuple`. Engine `metadata.chosen_seed` is always the final profile seed;
`metadata.realism_request_seed` retains the selection input for replay. Retry
metadata also reports the attempt count. Replay uses that request seed and pinned
tuple. Factory callers retain their original request, ordinal and revision.
All uniqueness callers must supply a complete, current library snapshot. Factory
batch rejects collisions; engine generation retries deterministically up to 128
attempts. Neither helper writes or binds profiles.

## Provenance and validation

Optional `meta.instance_realism` format 1 records catalog digest, source ID, tuple
ID, axis policy and field-path provenance (source, confidence, availability).
Changed values are in the normal profile body; provenance contains neither raw
identifier values nor seeds. Engine replay metadata does contain a seed and must
be treated as private identity data, not diagnostics output.

Validation checks the exact approved baseline, complete tuple, source binding,
provenance, identifiers/radio derivation and existing coherence rules. Unsupported
templates, edited hardware, mismatched firmware, fabricated metadata or invalid
derivation fail explicitly with fixed reason codes. There is no host fallback.
Keep the shipped v1 registry immutable; future catalog revisions must retain v1
resolution so stored provenance does not become invalid or require migration.

Full identity backups preserve allowlisted realism metadata and the existing
network hint needed for exact round-trip validation. Default redacted backups
omit the derivation claim along with private identity material. Existing backup
formats and profile storage paths are unchanged.

No attestation, credential, keybox, certification or platform verdict is generated.
Coherent local data is not proof of physical hardware behavior.
