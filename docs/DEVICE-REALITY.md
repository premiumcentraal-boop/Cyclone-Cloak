# Device Reality v1

Schema **0.4** adds an optional `reality` object. Existing 0.2/0.3 files remain
readable and are never rewritten on load. Adding Reality metadata is explicit;
there is no automatic migration. A 0.4 profile may also omit this object. New
identity and workshop generation request 0.4; direct Python/Kotlin forge and
engine calls retain their 0.3 default. Pass `schema_version="0.4"` in Python or
`schemaVersion = "0.4"` to Kotlin forge to opt in.

Each optional section has `availability`, `source`, `reason` and `values`.
Availability is `known`, `unknown` or `unavailable`. Known requires nonempty
values; unknown/unavailable require empty values. Partial known sections are
allowed: an omitted subfield makes no claim. Sources are `public reference`,
`approximated`, `needs verification` or `derived`. Unknown keys/private material
are rejected. See `schema/device-reality.schema.json` for exact types and bounds.

| Section | Descriptive data | Coherence checks |
| --- | --- | --- |
| compute | SoC, GPU family, core count, ABIs | Known SoC/API/hardware/manufacturer and GPU family |
| memory | RAM, storage SKU, free space in GiB | Positive bounded capacities; free ≤ total; known Pixel 7 SKU |
| panel | Size, density, refresh, technology, modes | Base display agreement; active mode must exist |
| cameras | Count, inventory/facing, level, megapixels | Count, unique IDs, base camera facing/count agreement |
| sensors | Type/name, sampling delays, range/resolution | Unique bounded entries; min ≤ max delay; resolution ≤ range |
| power | Typical design capacity, battery, charging, thermal | Known capacity, full/100%, charging/source agreement |
| drm | Security-level label only | L1/L2/L3; no private material or attestation assertion |
| radio | Configured names, Wi-Fi/Bluetooth state | Bounded safe names/states; local name uniqueness |
| carrier | SIM slots, MCC/MNC, country, state | Base telephony agreement; no ready SIM with zero slots |
| packages | Caller-visible package/version summaries | Package format, unique names; Android release agreement |
| webview | Provider, version, UA | Provider package/version and profile UA agreement |
| environment | Locale/timezone, keyboard/IME, accessibility | Base locale agreement; bounded formats/enums |

Generation copies catalog facts and explicit gaps. It derives a local
`inventory_id` and configured radio labels with domain-separated SHA-256 from
the seed. Supplied radio state and WebView provider/version facts are retained; a known WebView version is used in the generated UA. Explicitly unavailable radio/WebView sections remain unavailable. These are draft metadata, not physical identifiers or broadcasts.
Existing identifier derivation and uniqueness retries remain in use. The index
also checks those three new distinguishing fields; explicit regeneration edits
only the selected field in a draft. Shared hardware, package versions and
capabilities are intentionally not required to be unique.

## Catalog provenance

| Entries | Added facts | Confidence / source |
| --- | --- | --- |
| Pixel 7 / 7 Pro / 7a | Tensor G2; hardware family gs201 | Public reference: [Google Pixel specifications](https://support.google.com/pixelphone/answer/7158570) |
| Pixel 7 | 8 GiB RAM, selected 128 GiB SKU, typical 4355 mAh | Public reference: same Google specifications; SKU selection is deterministic, not live storage/battery state |
| Galaxy S23 | Snapdragon 8 Gen 2 for Galaxy; hardware family qcom | Public reference: [Samsung specification sheet](https://image-us.samsung.com/SamsungUS/samsungbusiness/pdf/spec-sheets/s23/att/hhp-s23-spec-att.pdf) |
| Every template | Panel size/density/refresh, carrier defaults and locale | Derived from existing template configuration; physical verification remains pending |
| Other hardware / live state / provider versions | Unknown with explicit reason | Needs verification; no GPU, camera, sensor, DRM or installed package facts invented |

Pixel 7's old gs101 and S23's exynos2200 hardware label were corrected. S23's old beyond1q device/product labels were also corrected to dm1q, following the [device-tree reference](https://blob.lineageos.org/preview/wiki/505004/devices/dm1q/). Firmware strings remain approximated, not a captured stock image.
Existing saved bodies are unchanged. Historical template/fixture evidence is not
rewritten. Workshop plans with old template fingerprints fail safely rather than
silently reproduce a different profile. The legacy base catalog contains
approximated compatibility values; the new metadata does not certify them.

## Read-only evidence and coverage

`catalog/reality-coverage.json` lists **43 fields** and the states `verified`,
`match-only`, `observed`, `blocked`, `missing`, `unmapped`. Checked-in states are
unmapped pending physical evidence. Existing 89 probe rows remain unchanged.
Reports may add `device_reality: {"format":1,"rows":[...]}`. Each row contains a
stable `reality.section.field` ID, source, expected/actual SHA-256 (or null),
status, fixed reason/error class, and duration. Raw values never enter this
extension. It is owned by the same capture session and atomic write gate.

No expected value means OBSERVED, never PASS. Unavailable APIs are MISSING,
permission denial is BLOCKED, unsupported mappings UNMAPPED. Matching hashes
are only match-only. A mismatch remains visible as a failed comparison (displayed
as missing matching evidence); it is never silently downgraded to a pass. The
importer validates IDs, uniqueness, hashes and comparison consistency. Aggregate
reports conservatively retain failure/block/missing states across captures.
Loaded coverage describes probe fixtures, not the selected saved identity.
This v1 importer never manufactures `verified`; that state is reserved for
separate reviewed device evidence.

GPU model, physical CPU count, advertised storage, panel technology, advertised
battery capacity, Wi-Fi device label, WebView UA and keyboard layout have no
reader in this extension. Existing legacy Wi-Fi SSID is a network name and is
not compared to a configured device label. Package visibility is caller-limited;
only a fixed Android/Google compatibility package list is queried, hashed as an
aggregate. Cameras/sensors are caller-visible inventories, not claims that new
hardware exists. Kernel RAM and free storage readings describe available physical
resources, which may differ from a marketed SKU. Exact array comparisons include
order and all described fields; partial inventories may mismatch and require
human review. Battery/thermal/radio/carrier/input state is dynamic.

Identities → Diagnostics & evidence → Reality coverage shows a **local** score
(100 minus 10 per validation error, floored at zero), missing sections, warning/
error count, allowlisted provenance and schema. Score is not completeness or a
platform verdict. Older profiles show not recorded. Default backups omit new
distinguishing markers/names; explicit identity backups retain them. Backup and
import confirmation behavior and profile storage layout remain unchanged.

All new physical-device checks are **UNVERIFIED**. No hooks, physical behavior,
permissions, networking, platform bypass, key material or account logic is added.

Rule parity check: `uv run -- python -X utf8 tools/generate_reality_rules.py --check`.
Regenerate the embedded Kotlin rules with the same command without `--check`.
