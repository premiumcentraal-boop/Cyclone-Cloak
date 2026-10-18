# Catalog Import Bridge v1

This is an offline, opt-in Python import path on `codex/catalog-import-bridge-v1`.
It adds no Android code, runtime surfaces, permissions, connector changes, profile
storage changes or release changes. It does not fetch URLs, run downloaded code,
install catalogs, bind apps or save profiles. Existing generators and the frozen
twelve-model Device Factory catalog are unchanged.

## Pipeline and approval boundary

1. Obtain a permitted source artifact locally and review its actual file license
   and notice. Record the exact bytes' SHA-256, optional revision, approved URL or
   local source label, observation date, source format and role.
2. `parse_hardware_xml(bytes, source, device_id=None)` creates a provenance draft.
   Multiple device definitions require explicit selection. Hardware XML alone
   cannot become a candidate. It supplies no firmware, identifiers, launch API,
   carrier defaults, WebView versions or certification claims.
3. `attach_build_properties(draft, bytes, source)` uses the existing
   `prop_parse.parse_build_prop()` parser. Manufacturer and model must match
   exactly. The bridge rejects duplicate keys and conflicting aliases before
   their values can be lost by the parser's existing last-wins behavior.
   Primary brand/product/device properties must actually exist; model fallback
   is not used. Absent `first_api_level` remains **unknown**, even when SDK exists.
4. `attach_review(draft, bytes, source)` adds explicitly supplied, plain JSON
   facts. Every fact identifies its canonical path, availability, value,
   confidence, source, evidence locator and any conflicting fact IDs it resolves.
   No completion defaults are pulled from another phone.
5. `resolve(draft)` selects reviewed facts before matching build properties,
   then hardware definitions, then reference facts. Any concrete disagreement
   still blocks promotion. A reviewed correction must explicitly list the IDs
   of **all** disagreeing facts in `resolves`; priority alone is insufficient.
   Earlier facts and their provenance remain available for review.
6. `completion(draft)` returns `complete`, fixed reason codes, sorted missing
   paths, conflicts and a tentative template. All contributing source licenses
   need explicit `license_reviewed: true`. Unknown/unavailable required values,
   missing build/review sources, malformed firmware, contradictory capabilities
   or invalid generated profiles prevent promotion.
7. `promote(draft, reviewer="...")` makes a digest-bound candidate. This is an
   explicit **local review assertion**, not a cryptographic signature or remote
   approval. Only reviewed candidates can enter
   `DeviceFactory.from_imported_candidates([candidate])`. That method replays
   completion from the provenance and rejects body/manifest tampering.
8. The normal factory then derives per-instance identifiers, seeds and inventory
   markers, validates the existing schema, checks uniqueness against the supplied
   library, and returns its existing profile/quality bundle. `batch()` retains its
   existing all-or-nothing in-memory acceptance. Callers still decide whether to
   save; this bridge never writes an app-private profile or binding.

## Supported hardware XML subset

The parser accepts a `devices/device` collection or single `device` in the AOSP
device namespaces 1–6, or unnamespaced self-authored XML. It is bounded to 256,000
bytes, 4,096 elements and 16 levels. UTF-8 only; DTD/entity declarations,
unknown elements/attributes, unexpected mixed content, duplicate singleton
elements, repeated camera locations, ambiguous selection and malformed values
are rejected with fixed reason codes. No external entity resolution, file reads,
script interpretation, makefile execution or network access takes place.

| XML declaration | Stored fact and interpretation |
|---|---|
| name, manufacturer | Naming hints mapped to model/manufacturer; AVD names are not necessarily shipping models. Correct explicitly before attaching a different matching build. |
| dimensions/x-dimension, y-dimension | Pixel width/height; positive integral dimensions, not dp. |
| pixel-density | Logical density from an explicit standard bucket or numeric `420dpi` form. `nodpi`/`anydpi` is unavailable. |
| xdpi, ydpi, diagonal-length | Separate descriptive physical DPI/inches; never used as logical density or to invent a refresh rate. |
| cpu, gpu | Declared platform labels; exact compatible completed Reality facts must agree. No hardware ID guessed from the label. |
| ram, internal-storage, removable-storage | Byte hints, with explicit binary B/KiB/MiB/GiB and decimal KB/MB/GB normalization. No physical free-space claim. |
| camera | Declared front/back/external presence, autofocus/flash booleans. No camera ID, megapixels or HAL level inferred. Completed inventory must retain declared facing coverage. |
| sensors, networking, abi, dock | Declared token lists; absent declarations stay absent/unknown. Sensor names/ranges and carrier facts are not fabricated. |
| mic, keyboard, nav, touch/mechanism, power-type | Descriptive validation hints. No runtime behavior changes. |

Known software/state/icon/frame metadata is accepted inertly; paths are never
opened. AVD software API ranges are **not** shipping Android releases or launch
API levels. Other formats/extensions (including foldable-region XML) fail closed
in v1 rather than being guessed. A larger document containing any unsupported
extension must be curated into a licensed, hash-recorded supported input first.

## Formats, partialness and trust

`schema/catalog-provenance.v1.schema.json` describes provenance interchange.
`catalog/import-fields-v1.json` is the frozen allowlist and required complete
Factory shape. Arrays are atomic evidence values. JSON is bounded to 2 MB;
duplicate object keys, non-finite numbers and unknown canonical field paths fail.
Sources and facts have independent stable IDs; fact hashes and source references
are checked by the runtime validator in addition to JSON Schema validation.

A fact's availability is `known`, `unknown` or `unavailable`. The latter two
require null values. Missing paths are reported as missing/unknown, never given
defaults. Confidence is `declared`, `public-reference`, `reviewed` or
`approximated`; none means physically verified. A correction retains the chosen
fact's own confidence rather than raising it automatically.

A review document contains `{"facts": [...]}`; each row has `path`, `value`,
`availability`, `confidence`, `evidence` and `resolves` (an array, normally empty).
Sources have format `hardware-xml`, `build-prop`, `review-json` or `reference`,
and role `hardware`, `build`, `review` or `reference`.

Candidates contain format 1, kind `catalog-import-candidate`, the template,
provenance manifest, reviewer, `platform_trust: "unknown"`, and canonical SHA-256
digest. A fresh factory defensively copies candidates and does not alter the
canonical catalog or its stricter model-specific checks. Source metadata stays
in this separate candidate, not in generated profile storage. Keep candidates
beside their original permitted source artifacts; a digest is not authentication.

The current Factory needs a complete set of its existing capability sections;
therefore this v1 gate conservatively keeps incomplete hardware drafts out of
generation. Unknown optional descriptive hardware hints can remain unknown.
Imported Play-services certification and attestation metadata must both stay
`unknown`; generated integrity is also unknown. No platform verdict is inferred.

## Approved seed set and limitations

`catalog/import-sources-v1.json` records the approved reference set, observation
date (2026-10-18), SHA-256 of each observed **reference page**, license/notice
context and scope of confidence. These hashes do not identify downloadable
hardware artifacts. Individual files need their own pinned source records and
license review; approval of a source URL is not redistribution approval.
No third-party device records or licensed source bodies are vendored here.

| Source | Role/confidence boundary | License/notice handling |
|---|---|---|
| [AOSP canonical definitions](https://android.googlesource.com/platform/tools/base/+/eac8f291215dd282f4439078298a287449af2d59) | Pinned revision; declared emulator hardware, not physical proof. | Record exact file notice; do not infer rights from repository name alone. |
| [Official AVD workflow](https://developer.android.com/studio/run/managing-avds) | Workflow reference only. | Google documentation terms; no catalog facts copied. |
| [Sony Open Devices](https://developer.sony.com/develop/drivers/) | Artifact/model-specific reference. | Download licenses vary; review actual artifact. |
| [AndroidDeviceNames](https://github.com/jaredrummler/AndroidDeviceNames) | Manufacturer/model name cross-check, public reference. | Apache-2.0 project; inspect database-specific attribution. |
| [Matomo DeviceDetector](https://github.com/matomo-org/device-detector) | Name/UA cross-check, not complete hardware or firmware. | LGPL-3.0-or-later; no code/data copied. |
| [Play supported-device docs](https://support.google.com/googleplay/android-developer/answer/9859371?hl=en) | Catalog cross-check only. | Documentation/catalog terms require separate review. |
| [Firebase Test Lab](https://firebase.google.com/docs/test-lab/android/available-testing-devices) | Lab inventory cross-check only. | Documentation terms; no runtime/certification conclusion. |
| [reDroid](https://github.com/remote-android/redroid-doc) | Backend/reference only; not physical-device facts. | Component-specific Apache/GPL/third-party notices; never execute it during import. |

Only XML and build-property adapters are implemented in v1. The other approved
sources are bounded references for explicit review, not automated scrapers or
downloaders. Real hardware/firmware/SKU review and physical validation remain
future work. No Android UI exposes this Python bridge yet.

## Reproduce the synthetic example

The self-authored fixture set is under `forge/tests/fixtures/catalog-import/`:
`hardware.xml`, `matching.build.prop`, `synthetic-completion.json`, `sources.json`
and `synthetic-candidate.json`. Every file says synthetic either in its contents
or source notice. Compatibility approximations are explicit, not measured facts.
Local Git attributes pin LF bytes so the provenance hashes survive Windows/Unix.

With `forge` on `PYTHONPATH`:

```python
import json
from pathlib import Path
from cloak_forge.hardware_xml import (
    parse_hardware_xml, attach_build_properties, attach_review, promote,
)
from cloak_forge.device_factory import DeviceFactory

p = Path("forge/tests/fixtures/catalog-import")
sources = json.loads((p / "sources.json").read_text())
draft = parse_hardware_xml((p / "hardware.xml").read_bytes(), sources[0])
draft = attach_build_properties(draft, (p / "matching.build.prop").read_bytes(), sources[1])
draft = attach_review(draft, (p / "synthetic-completion.json").read_bytes(), sources[2])
candidate = promote(draft, reviewer="Synthetic example review only")
factory = DeviceFactory.from_imported_candidates([candidate])
bundles = factory.batch("synthetic_pixel7_import", "local-batch-seed", 10, existing=[])
# Profiles/quality reports are caller-owned; no automatic save or binding occurs.
```

Pass the full existing library to `generate()` or `batch()` at acceptance time.
Never log generated seeds/identifiers or raw build-property dumps. Errors contain
fixed reason codes and canonical field paths only. Unrecognized dump properties
(including private material) are not retained; source records retain only a hash.
