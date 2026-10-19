# Play Surface Contract v1

This is a pure, local description and validation contract. It does not create
accounts, inspect Google services, alter packages, enforce isolation, or establish
a platform verdict. No new runtime, module, connector, publication or storage
behavior is introduced.

## Profile section

`play_surface` is optional on existing schema 0.2, 0.3 and 0.4 profiles. Absent
sections remain absent and produce no validation errors. There is no migration,
automatic persistence or generation. When present, format 1 requires explicit
`account` and `services` descriptions, including `unknown` where facts are not known.
Unknown future format versions, fields and enum values are rejected, rather than
silently given a known meaning. `unknown` is itself a valid declared value.

The source of field names, bounds and vocabularies is
[`play-surface.schema.json`](../schema/play-surface.schema.json).
`tools/generate_play_surface.py` embeds that same schema in Kotlin; `--check`
rejects drift. Shared vectors exercise both parsers, reason codes and messages.

| Section | Fields and interpretation |
| --- | --- |
| `account` | `mode`: none/dedicated/managed/unknown; `slot_id`: stable local opaque slot or null; `display_hint`: short label or null; `shared`: explicit sharing consent; `provisioning_state`: unprovisioned/ready/needs_review/blocked; `allowed_packages`: exact package scope; `last_reviewed_at`: UTC timestamp or null |
| `services.play_services` | `package_name` must be `com.google.android.gms`; `state`: enabled/disabled/missing/unknown; optional `version_name` |
| `services.play_store` | `package_name` must be `com.android.vending`; same state/version shape |
| `services.web_provider` | state plus optional package name/version; unspecified package is not an observation of a provider |
| `services.play_protect` | enabled/disabled/unavailable/unknown; expectation only, never certification |
| `services.google_settings_access` | accessible/blocked/unavailable/unknown |
| `services.install_origin` | play_store/system/sideload/managed/unknown |
| `services.backup_sync` | enabled/disabled/managed/unknown |

Dedicated and managed modes require a non-null slot. `none` requires null slot
and `shared=false`. Unknown mode may reserve a slot but claims no account setup.
Slots are local labels, not account identifiers, email addresses or credentials.
Hints must contain display labels only: do not put passwords, tokens or account
contents in them. Unknown credential fields are rejected; no parser can determine
whether an arbitrary user-provided label is secretly a password.

`allowed_packages` describes future scope; this sprint does not enforce it.
Provisioning state describes local setup, separately from evidence readiness.
No value is automatically inferred from the host or from a template.

## Readiness records and evidence binding

Records are separate values, not embedded in the profile whose body they hash.
[`play-readiness.schema.json`](../schema/play-readiness.schema.json) defines:

- `format=1`, `profile_id`, `state` (READY/DEGRADED/BLOCKED/UNKNOWN);
- `reason_codes`, `evidence_digest`, `report_timestamp`;
- `profile_hash`, `artifact_hash`, `provider_id`.

Digests are lowercase SHA-256. The caller supplies hashes of the exact profile,
artifact and evidence bytes, without implicit JSON normalization. Profile IDs
and provider IDs are bounded opaque local labels. UTC timestamps use
`YYYY-MM-DDTHH:MM:SSZ` and real calendar validation. JSON Schema consumers must
enable date-time format checking; the Kotlin/Python parsers enforce it directly.

Every non-UNKNOWN record requires complete evidence and artifact digests. READY
requires exactly `PS_LOCAL_CHECKS_PASSED`. All other states require one or more
non-success reasons. A provider cannot encode READY with missing evidence.

`bind_readiness` / `bindReadiness` checks a supplied record against the current
profile ID/body hash, artifact hash and evidence digest. Any mismatch or missing
evidence returns UNKNOWN with fixed reason codes. The original bindings remain
in the returned record, so foreign evidence is never relabelled as current. A
matching UNKNOWN remains UNKNOWN; the helper never promotes a state. It does not
compute a readiness verdict from expectations alone, authenticate a provider, or
verify report signatures. Providers, collection, persistence and UI are future work.

## Conflicts

`account_slot_conflicts` / `accountSlotConflicts` accepts explicit active/inactive
profile assignments. Slot reuse across active profiles conflicts unless every
assignment has `shared=true`. Inactive profiles do not reserve slots; malformed
assignments and duplicate profile IDs are rejected. The function returns paths
and reason codes without exposing slot contents or mutating the library.

`service_conflicts` / `serviceConflicts` compares expectations with a supplied
services-shaped local observation. Known package state, package name or declared
version disagreement produces `PS_PACKAGE_CONFLICT`; other known service
disagreement produces `PS_EXPECTATION_CONFLICT`. Unknown expectations or
observations stay `PS_EXPECTATION_UNKNOWN`; missing evidence produces
`PS_NO_EVIDENCE`. An empty conflict list is not a readiness record and never
implicitly becomes READY. These functions perform no device or Google queries.

## Public APIs

Python: `cloak_forge.play_surface` exports `FORMAT`, `ACCOUNT_MODES`,
`READINESS_STATES`, `REASON_CODES`, `Issue`, `ContractError`, `validate_surface`,
`validate_profile_section`, `validate_readiness`, `parse_surface`,
`parse_readiness`, `encode_surface`, `encode_readiness`, `account_slot_conflicts`,
`service_conflicts`, and `bind_readiness`.

Kotlin: `PlaySurfaceContract` exports equivalent constants and operations in
camelCase, plus `Assignment(profileId, active, profile)`, `Issue(field, code)` and
`ContractException(issues)`. JSON APIs use `JSONObject`; no Android context is
required. Existing profile validators validate only a present section.

Parsing is strict JSON with a 128 KiB UTF-8 limit and maximum nesting depth 32.
Duplicate keys, trailing input, non-finite numbers, extra fields and wrong types
are rejected. Findings contain fixed messages and trusted schema paths, never
rejected values or unknown key names. Serialization preserves contract data;
object key order is not part of the wire contract or an evidence hash convention.

The shared example in `forge/tests/vectors/play-surface.json` is synthetic local
metadata, not a provisioned account or verified Play state.
