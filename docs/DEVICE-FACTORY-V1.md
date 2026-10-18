# Device Factory v1

The opt-in [Catalog Import Bridge v1](CATALOG-IMPORT-BRIDGE-V1.md) can now admit
explicitly reviewed, complete provenance-bound candidates through
`DeviceFactory.from_imported_candidates()`. It reuses factory derivation and
uniqueness checks without replacing the canonical catalog or saved profiles.
Hardware-only drafts remain incomplete; imported platform trust stays unknown.

`forge/cloak_forge/device_factory.py` is an opt-in, pure Python factory. It does
not change the Android app's existing generator, profile imports, persistence,
bindings, publication, module, hooks, connector transport, evidence system or
release workflow. Generated bodies use the unmodified profile schema 0.4;
existing Forge callers and schemas 0.2/0.3 remain unchanged.

## Canonical catalog

`catalog/device-factory-v1.json` contains twelve fixed hardware/SKU references:
Pixel 6, 6a, 6 Pro, 7, 7a, 7 Pro, 8, 8a, 8 Pro, 9, 9 Pro and Galaxy S23
(SM-S911B). Each includes device/build fields, a fingerprint-to-patch binding,
bootloader/baseband examples and patterns, display modes and size class, camera
inventory and capability class, sensors, RAM/storage, battery, carrier/region,
locale/timezone, WebView/browser, graphics and Play-services expectations.

Hardware classes build on the existing catalog and manufacturer references:
[Google specifications](https://support.google.com/pixelphone/answer/7158570),
[Samsung specifications](https://www.samsung.com/ae/smartphones/galaxy-s/galaxy-s23-phantom-black-128gb-sm-s911bzkbmea/).
These are compatibility fixtures, not captured device dumps. Bootloader and
baseband examples, firmware details, camera HAL levels/IDs and rounded sensor
dimensions are explicitly approximated. Camera inventories represent physical
lens classes, not a promise about Camera2 logical/physical ID enumeration.
Display modes describe the supported compatibility class at one resolution;
they do not enumerate every adaptive refresh step or alternate resolution.
Sensor names describe inventory classes, not vendor sensor strings.
Play-services package expectations describe neither installed versions nor
certification. No keybox, DRM provisioning or attestation evidence is generated.

The factory loads a defensive catalog snapshot, validates every template before
use and returns defensive copies. The catalog is separate from `phones.json`,
so it does not silently alter existing Android or Python generation. Treat this
v1 snapshot as frozen: changed hardware/firmware facts require a new versioned
catalog/factory revision. Every quality report records its template digest.

## API

Run Python with `forge` on its import path, as the existing Forge tools do:

```python
from cloak_forge.device_factory import DeviceFactory, InstanceRequest

factory = DeviceFactory()
request = InstanceRequest(
    template_id="pixel_7", seed="private factory seed", ordinal=0, revision=0,
    label="Device 001",
)
bundle = factory.generate(request, existing=library_snapshot)
profile = bundle["profile"]
quality = bundle["quality"]
```

`library_snapshot` is a caller-supplied list of existing profile bodies; use `[]`
for an empty library. The factory has no hidden registry or filesystem writes.
At every persistence boundary, callers must revalidate against the complete,
current library under their own storage transaction/lock. Two independent callers
using stale or incomplete libraries cannot establish global uniqueness.

SHA-256 of a versioned, canonical JSON tuple `(templateId, seed, ordinal,
revision)` becomes the existing HMAC identifier derivation's seed. The original
seed is 1–1024 characters, and ordinal/revision are nonnegative 31-bit integers.
Labels do not affect identity. Use a private high-entropy seed when unpredictable
identifiers matter; determinism is not an entropy guarantee. Profile IDs are
deterministic UUIDv5 values; advertising/app-set IDs have UUIDv4 format and variant
bits, with their contents deterministically derived. Revising any tuple component
changes all instance identifiers. Generation never silently retries/reseeds a
collision; an identical request remains an identical identity.

The ICCID uses telecom prefix `89`, US issuer class `8901260`, subscriber digits
and Luhn. IMEIs are 15-digit, Luhn-valid synthetic numbers with **TAC allocation unverified**;
they do not claim a GSMA device allocation. Wi-Fi and Bluetooth MAC-style values
are locally administered unicast tokens. GSF and Widevine IDs are nonzero local
hex tokens of the schema's lengths, not provisioned service identities.

`validate(profile, template_id, existing)` returns findings and rejects changed
canonical device/build/display/camera/carrier/region fields, incompatible Reality
capabilities, missing or malformed identifiers, bad checksums, multicast/global
MACs, placeholders and collisions. Collision checks include same-profile-ID
records and cross-field values (including primary/secondary IMEIs). The existing
legacy validator's warning/edit semantics are preserved outside this API.

`quality_report(profile, request, existing, created_at=...)` recomputes validation
and exact derivation rather than trusting a supplied report. `generate` returns
only a valid bundle, otherwise raises `FactoryError` with structured findings.
Reports include validation state, completeness percentage (presence of required
device/identifier/capability fields, not evidence confidence), failed checks,
warnings, template ID/digest, seed fingerprint, ordinal, revision, factory version
and a timezone-aware creation timestamp. The report has no raw generated
identifiers or seeds. Timestamp changes do not change the profile body; pass the
same `created_at` to reproduce an entire bundle byte-for-byte after serialization.

## Batches

```python
batch = factory.batch(
    "pixel_7", "private batch seed", count=20,
    label_prefix="Fleet", seed_policy="shared", revision=0,
    start_ordinal=0, existing=library_snapshot,
)

# Explicit per-item seeds, still separated by template/ordinal/revision:
batch = factory.batch(
    "pixel_7", "", count=2, seed_policy="per-item",
    item_seeds=["private seed A", "private seed B"],
    existing=library_snapshot,
)
```

Counts are 1–1000 per call. Batches share one report creation timestamp and label
items with a prefix plus ordinal (e.g. `Fleet 001`). Shared policy uses the same
root seed; per-item policy requires exactly one explicit seed per item. Seed
selection is never implicit. Keep the same request, catalog version and item
seeds for regeneration; use `start_ordinal` for deterministic chunking.

All drafts are validated against the supplied library and earlier drafts before
the complete list is returned. Any failure raises `FactoryError`, returns no
partial list, and leaves caller inputs unchanged. Persistence belongs to the
caller. There is no public profile or plan export/import API in v1. Applications
may display/share the identifier-free quality reports; profile bodies and seeds
are private reproduction material and should not be included in public exports.

## Verification

```powershell
uv run --with pytest --with jsonschema -- python -m pytest forge/tests forge/schema_tests
```

Tests exercise every template against the existing JSON schema, deterministic
vectors, every derivation axis, identifier formats/checksums/locality, coherence
mutations, invalid catalogs/requests, cross-field/library collisions, both seed
policies, all-or-nothing failures and unchanged legacy generation.

Internal validation is separate from runtime evidence. This factory makes no
platform compatibility, runtime enforcement, certification or integrity claim.
