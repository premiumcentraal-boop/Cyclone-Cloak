# Built-in phone sources and confidence

Reviewed 2026-10-11. The shared catalog contains 15 templates and retains
`schemaVersion: 1`. Existing device blocks are unchanged. These are coherent
configuration examples, not captured firmware or device compatibility evidence.

## Confidence metadata

Each catalog entry has an optional `confidence` string:

- `public reference`: all supplied values have direct reference evidence.
- `approximated`: public specifications combined with representative configuration defaults.
- `needs verification`: legacy fixture awaiting a source audit.

All eight additions use `approximated`. Model, panel resolution, refresh mode,
and camera grouping come from Google's [hardware specifications][specs];
selected release/build/patch combinations come from the [AOSP build table][builds].
API mapping follows [Android release constants][apis]. Device/platform names use
[AOSP device sources][devices], including [Pixel 9 sources][caimito] and
[Pixel 8a sources][akita]. This does not establish that every property matches
stock firmware. No new template is device-verified.

| Entry | Device classes | Android / API (first API) | Panel pixels | Hardware / camera group | Overall confidence |
| --- | --- | --- | --- | --- | --- |
| `pixel_4` | Legacy | 13 / 33 (29) | Existing | Existing | needs verification |
| `pixel_7` | Legacy | 13 / 33 (33) | Existing | Existing | needs verification |
| `galaxy_s23` | Legacy | 13 / 33 (33) | Existing | Existing | needs verification |
| `pixel_6` | Mainstream | 14 / 34 (31) | Existing | Existing | approximated |
| `pixel_6a` | Midrange | 14 / 34 (31) | Existing | Existing | approximated |
| `pixel_7a` | Midrange | 14 / 34 (33) | Existing | Existing | approximated |
| `pixel_8` | Mainstream | 14 / 34 (34) | Existing | Existing | approximated |
| `pixel_6_pro` | Older, large-display | 14 / 34 (31) | 1440 × 3120 | Tensor; triple rear + front | approximated |
| `pixel_7_pro` | Large-display | 14 / 34 (33) | 1440 × 3120 | Tensor G2; triple rear + front | approximated |
| `pixel_8_pro` | Large-display | 14 / 34 (34) | 1344 × 2992 | Tensor G3; triple rear + front | approximated |
| `pixel_8a` | Compact, midrange | 14 / 34 (34) | 1080 × 2400 | Tensor G3; dual rear + front | approximated |
| `pixel_9` | Mainstream, newer | 14 / 34 (34) | 1080 × 2424 | Tensor G4; dual rear + front | approximated |
| `pixel_9_pro` | Compact, newer | 14 / 34 (34) | 1280 × 2856 | Tensor G4; triple rear + front | approximated |
| `pixel_9_pro_xl` | Large-display, newer | 14 / 34 (34) | 1344 × 2992 | Tensor G4; triple rear + front | approximated |
| `pixel_9a` | Midrange, newer | 15 / 35 (35) | 1080 × 2424 | Tensor G4; dual rear + front | approximated |

## Conservative defaults and metadata boundary

Optional `classification` describes device classes, display class, hardware
feature group and camera class. These are coarse catalog annotations. `compact`
is relative to this catalog, not an Android resource-size qualifier. All new
phones retain the schema's `display.screen_size_class: normal`. Hardware groups
mean platform family plus 5G/NFC, optionally UWB; they are not complete feature
lists. Camera classes describe rear-group/front presence, not Camera2 IDs,
sensor dimensions or hook coverage.

Both readers retain raw catalog annotations; Kotlin phone import/export also
round-trips them. Both profile forges copy only existing device/telephony/network/
display/locale blocks, so annotations never extend the profile storage schema or
runtime reader format. Legacy phone files without annotations still load.
Editing through the existing phone builder reconstructs a custom template and
drops reference metadata, avoiding attribution of modified values to stock specs.

New entries use US T-Mobile (`310`/`260`), two SIM slots, 5G, `us-east`, and
`en`/`US`/`America/New_York`. These are chosen configuration defaults, not observed
carrier provisioning. Logical densities of 420/480 dpi are approximated, not
physical panel PPI. Refresh selects a supported 120 Hz mode.

Incrementals `catalog.YYYYMMDD`, composed fingerprints and build timestamps are
synthetic; bootloader/baseband remain empty. Pixel 9a's build-table patch is
2025-02-05 for `BD4A.250405.003`; its later synthetic timestamp is intentional.
Older/newer means generation relative to this catalog and support by its existing
Android validator. It does not promise current manufacturer update eligibility.
The three legacy fixtures still need firmware/hardware/network auditing.

The full-catalog Python and Kotlin tests require zero validation findings for
every entry and verify unique IDs, requested classes, metadata compatibility,
and unchanged generated profile blocks. See the [run handoff][handoff] for exact
commands and current local test/build limitations.

[specs]: https://support.google.com/pixelphone/answer/7158570?hl=en
[builds]: https://source.android.com/docs/setup/reference/build-numbers
[apis]: https://developer.android.com/reference/android/os/Build.VERSION_CODES
[devices]: https://android.googlesource.com/device/google/
[caimito]: https://android.googlesource.com/device/google/caimito/+/refs/heads/main/
[akita]: https://android.googlesource.com/device/google/akita/+/refs/heads/main/
[handoff]: HANDOFF-2026-10-11-CATALOG.md
