# Profile validation

Python `forge/cloak_forge/validate.py` and Android
`forge/ProfileValidator.kt` apply the same rules. Profile schema 0.2, fields,
storage and identifier derivation are unchanged. Findings are diagnostics only:
`severity`, stable `code`, dotted `field` path and a user-readable `message`.
Python's new optional `Finding.field` also preserves three-argument construction.
The existing Android editor and import review display these field-specific messages.

## Coherence result contract (2026-10-11)

The existing list APIs remain available: Python `validate_profile` and Kotlin
`validateProfile` / `validatePhone`. They now return findings ordered by severity
(`error`, `warning`, `info`), then dotted field, then code and message as tie-breakers.
A missing field sorts as an empty string. Ordering is independent of rule execution
and input object key order, including structural-error early returns.

Python `validate_profile_result` / `summarize` and Kotlin `validateProfileResult` /
`validatePhoneResult` / `summarize` expose a `ValidationResult` containing immutable
sorted findings, severity counts and an error flag. Python count names are
`error_count`, `warning_count`, `info_count`, `has_errors`; Kotlin uses
`errorCount`, `warningCount`, `infoCount`, `hasErrors`.
Finding constructors reject unknown severities, malformed reason codes and blank
explanations. Existing reason codes remain unchanged. Results are diagnostics only;
they are not persisted in profile JSON and require no migration.

Only errors block acceptance. Warnings express uncertainty; info gives context.
The editor labels info as a note and does not count it as a warning.

| Check | Errors | Warnings |
| --- | --- | --- |
| Build | Malformed fingerprint, mismatched fingerprint/device parts, non-user build, non-ISO or impossible patch date, invalid Unix-seconds build timestamp | Development keys, future build/patch, patch newer than build |
| Model/manufacturer | Missing, non-text, blank or control-character device fields | Pixel/Samsung model families paired with an unexpected manufacturer; conflicting known brand/manufacturer families |
| API | SDK/first API must be JSON integers (not booleans, fractions or numeric strings), 26–36; first API cannot exceed SDK; known release must match SDK | Numeric release absent from the supported release map |
| Display | Dimensions, density and refresh rate must be positive JSON integers; valid screen class | Dimensions outside 120–8192 px, density outside 72–1000 dpi, refresh outside 24–240 Hz; aspect ratio above 4:1; shortest edge outside 120–1600 dp |
| Locale | Two lowercase language letters, two uppercase country letters; timezone path syntax or recognized legacy alias; no empty segments, traversal or whitespace | None |
| Identifiers | Exact complete-string formats for hex IDs, UUIDv4, serial, SIM serial, IMEI and MAC; IMEI Luhn checksum; MAC must be unicast | All-zero placeholders, duplicated primary/secondary IMEI, unknown identifier key |
| Carrier/network | Carrier must be nonblank text without control characters; MCC/MNC must match the entire ASCII-digit value; supported network type and SIM slots; egress hint must be text of at most 128 Unicode code points without control characters | None; an all-zero MCC generates an info note |
| Hardware consistency | Present bootloader/baseband must be text without control characters | Explicit unspecified modem (`none`, `unknown`, `n/a`) with declared cellular service; 5G with first API below 29 prompts confirmation, without asserting impossibility |

Additional stable reason codes:

| Code | Severity | Field | Explanation |
| --- | --- | --- | --- |
| `TELEPHONY_CARRIER_FORMAT` | error | `telephony.carrier_name` | Carrier contains control characters |
| `TELEPHONY_PLACEHOLDER_OPERATOR` | info | `telephony.mcc` | All-zero operator value should be intentional |
| `NETWORK_HINT_FORMAT` | error | `network.egress_hint` | Wrong type, excessive length or control characters |
| `HARDWARE_FIELD_FORMAT` | error | `device.bootloader` or `device.baseband` | Wrong type or control characters |
| `HARDWARE_MODEM_UNSPECIFIED` | warning | `device.baseband` | An explicitly unspecified modem accompanies cellular service |
| `HARDWARE_NETWORK_GENERATION` | warning | `telephony.network_type` | Older launch API with 5G needs confirmation |
| `HARDWARE_UNSPECIFIED` | info | `device.hardware` | `unknown`/`generic` cannot establish platform capabilities |

Hardware coverage uses existing hardware, modem, network and launch-API fields.
The storage schema has no camera/sensor feature flags; validation does not invent
them or infer supported features from a manufacturer's name. Empty/absent optional
modem fields remain supported, as do generated secondary identifiers on single-SIM
templates. The seven catalog entries and runtime fixtures remain valid and clean.
Egress hints are opaque labels: validation does not interpret them as addresses,
perform networking, or require country/carrier/timezone agreement when roaming.

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

Coherence sprint verification: **176 Python tests and 50 JVM forge tests passed**,
including **108 shared validation vectors** with exact severity/code/field/message
parity. JVM tests compiled the current production forge sources and all forge tests
with Kotlin/JUnit and cached JSON libraries. The full local Gradle unit/screen suite
remains blocked at `:app:copyRobolectricRuntime` by the uncached API34 framework JAR.
This is not a claim that screen tests ran in this sprint. See
[the completion handoff](HANDOFF-2026-10-11-COHERENCE.md) for exact commands/results.
