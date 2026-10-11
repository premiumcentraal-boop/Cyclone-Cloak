# Built-in phone sources

Reviewed 2026-10-10. `catalog/phones.json` remains schemaVersion 1. The four
additions are representative Android 14 configurations, not certified factory
property dumps. Confidence applies to the facts named below, not every field.

| Entry | Release / API (first API) | Display / hardware group | Source type and confidence |
| --- | --- | --- | --- |
| `pixel_4` | 13 / 33 (29) | Existing display and hardware values | Legacy release fixture; low confidence in full configuration, source audit pending |
| `pixel_7` | 13 / 33 (33) | Existing display and hardware values | Legacy release fixture; low confidence in full configuration, source audit pending |
| `galaxy_s23` | 13 / 33 (33) | Existing display and hardware values | Legacy release fixture; low confidence in full configuration, source audit pending |
| `pixel_6` | 14 / 34 (31) | 1080 x 2400, 90 Hz, normal; Tensor / `gs101` | Google [specifications][specs], AOSP [build table][builds] and [device source][raviole]; high for model, launch OS, resolution, refresh and Tensor family; medium for representative defaults |
| `pixel_6a` | 14 / 34 (31) | 1080 x 2400, 60 Hz, normal; Tensor / `gs101` | Google [specifications][specs], AOSP [build table][builds] and [device source][bluejay]; high for model, launch OS, resolution, refresh and Tensor family; medium for representative defaults |
| `pixel_7a` | 14 / 34 (33) | 1080 x 2400, 90 Hz, normal; Tensor G2 / `gs201` | Google [specifications][specs], AOSP [build table][builds] and [device source][lynx]; high for model, launch OS, resolution, refresh and Tensor family; medium for representative defaults |
| `pixel_8` | 14 / 34 (34) | 1080 x 2400, 120 Hz, normal; Tensor G3 / `zuma` | Google [specifications][specs], AOSP [build table][builds] and [device source][shusky]; high for model, launch OS, resolution, refresh and Tensor family; medium for representative defaults |

Google is the manufacturer for all additions. API values follow Android's
[release constants][apis]: Android 12 = 31, Android 13 = 33, Android 14 = 34.
First API describes the launch OS; the template's current OS is Android 14.
The build table lists `UP1A.231005.007` for Pixel 6/6a/7a and
`UD1A.230803.022.A5` for Pixel 8, with the selected security patch dates.

Representative defaults are explicit: logical density 420 dpi (not physical
panel PPI), ordinary phone size class `normal`, two SIM slots (nano-SIM + eSIM),
and a US T-Mobile/English locale configuration. `hardware` is the existing
platform-family field; no feature-group field or feature claims were added.
Refresh rate selects a supported mode, not a claim of constant panel refresh.
Carrier and egress values are configuration choices, not manufacturer specs.

Incrementals `catalog.YYYYMMDD`, fingerprints composed from the matching fields,
and build timestamp 2023-10-12 UTC are synthetic coherence defaults. They must
not be presented as captured stock firmware or proof of integrity. Unverified
bootloader/baseband strings are empty in the new entries, matching the existing
phone editor's representation of unspecified optional values.

All seven entries pass the existing Python profile validator and Kotlin phone
and profile validators with zero findings. Validation establishes internal
coherence, not external accuracy; the older three entries are preserved and
still need a separate audit of firmware, hardware and network values.

Run `uv run --with pytest -- python -m pytest forge/tests/test_catalog.py -q`
and `gradle -p android :app:testDebugUnitTest` (includes `PhoneCatalogTest`).
Both suites iterate every catalog row; uniqueness checks prevent dictionary
loading from hiding duplicate IDs.

[specs]: https://support.google.com/pixelphone/answer/7158570?hl=en
[builds]: https://source.android.com/docs/setup/reference/build-numbers
[apis]: https://developer.android.com/reference/android/os/Build.VERSION_CODES
[raviole]: https://android.googlesource.com/device/google/raviole/
[bluejay]: https://android.googlesource.com/device/google/bluejay/
[lynx]: https://android.googlesource.com/device/google/lynx/
[shusky]: https://android.googlesource.com/device/google/shusky/
