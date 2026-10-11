# Profile validation

Python `forge/cloak_forge/validate.py` and Android
`forge/ProfileValidator.kt` apply the same rules. Profile schema 0.2, fields,
storage and identifier derivation are unchanged. Findings are diagnostics only:
`severity`, stable `code`, dotted `field` path and a user-readable `message`.
Python's new optional `Finding.field` also preserves three-argument construction.
The existing Android editor and import review display these field-specific messages.

| Check | Errors | Warnings |
| --- | --- | --- |
| Build | Malformed fingerprint, mismatched fingerprint/device parts, non-user build, non-ISO or impossible patch date, invalid Unix-seconds build timestamp | Development keys, future build/patch, patch newer than build |
| Model/manufacturer | Missing, non-text, blank or control-character device fields | Pixel/Samsung model families paired with an unexpected manufacturer; conflicting known brand/manufacturer families |
| API | SDK/first API must be JSON integers (not booleans, fractions or numeric strings), 26–36; first API cannot exceed SDK; known release must match SDK | Numeric release absent from the supported release map |
| Display | Dimensions, density and refresh rate must be positive JSON integers; valid screen class | Dimensions outside 120–8192 px, density outside 72–1000 dpi, refresh outside 24–240 Hz; aspect ratio above 4:1; shortest edge outside 120–1600 dp |
| Locale | Two lowercase language letters, two uppercase country letters; timezone path syntax or recognized legacy alias; no empty segments, traversal or whitespace | None |
| Identifiers | Exact complete-string formats for hex IDs, UUIDv4, serial, SIM serial, IMEI and MAC; IMEI Luhn checksum; MAC must be unicast | All-zero placeholders, duplicated primary/secondary IMEI, unknown identifier key |

These display and manufacturer ranges are heuristics, not a handset whitelist.
Warnings do not prevent saving; unknown manufacturers remain supported. Portrait
and landscape dimensions are equally valid. Timezone validation checks syntax,
not whether a region/location exists in a particular platform's timezone database.
Optional blocks remain optional; a present non-object block is a `BLOCK_TYPE` error.

Android 12 accepts API 31 and 32 because 12L reports release `12` with API 32.
Older Android 8.0/8.1/9/10 mappings and zero-suffixed releases are checked too.
See the [AOSP version table](https://source.android.com/docs/setup/reference/build-numbers).
Malformed fingerprints do not suppress independent API checks.

Example diagnostic:

```json
{"severity":"error","code":"RELEASE_SDK_MISMATCH","field":"device.sdk_int","message":"device.sdk_int must be 33 for Android 13; got 34"}
```

## Tests

```powershell
uv run --with pytest -- python -m pytest forge/tests -q
gradle -p android :app:testDebugUnitTest --no-daemon
gradle -p android :app:assembleDebug :module:packageModule --no-daemon
```

`forge/tests/test_validation_rules.py` has explicit expectations for valid,
suspicious and invalid inputs. Regenerate shared cases with
`python forge/tools/make_vectors.py`; Python and Kotlin compare severity, code,
field and message against `forge/tests/vectors/validation.json`.
Android `ProfileValidatorTest` tests malformed types, independent failures,
warnings, messages, optional blocks and unchanged valid templates.

Local verification: 109 Python tests and 44 JVM forge tests passed. The latter
compiled the repository's pure forge sources/tests using the installed Kotlin
compiler and ran JUnit directly with cached JSON/JUnit libraries. This does not
replace the full Android/Robolectric suite, whose required dependencies remain
uncached locally; see [CURRENT-STATE.md](CURRENT-STATE.md).
