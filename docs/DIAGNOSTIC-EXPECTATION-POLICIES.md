# Diagnostics expectations — Run 2 (2026-10-17)

Scope: probe and local evidence tooling only. No production runtime, profile
schema, binding store, connector, module, permissions or release metadata change.
The twelve IDs below are the twelve OBSERVED rows in the alpha.16 evidence.
New policy behavior is locally tested; physical verification is **UNVERIFIED**.
Historical reports and device evidence are never rewritten or retroactively passed.

## Fixture model

`android/probe/src/main/assets/expectations-a.json` and `expectations-b.json` are
version-1 diagnostics fixture sidecars: `{schema_version: 1, fixture: a|b, rows: ...}`.
The existing profile-a/b bodies, schema 0.3 and canonical hashes remain unchanged.
A validated fixture label selects its sidecar. Arbitrary external profiles receive
no implicit fixture policies. Labels, version, rule keys, parameter types and
policy names fail closed on malformed input; no migrations are needed.

## Policy table (all 12 rows)

| Row ID | Policy | What is actually checked | Still depends on real device state |
|---|---|---|---|
| `telephony.simState` | EXTERNAL_STATE | Integer in 0–11; unknown/absent/not-ready/null have explicit unavailable reasons | SIM/subscription/lock state |
| `telephony.phoneType` | ENUM | Exact integer enum values 0, 1, 2, 3; booleans and numeric strings rejected | Valid enum does not prove a radio exists or matches the profile |
| `telephony.roaming` | TYPED | JSON boolean, including false | Roaming may legitimately change; no fixed roaming value asserted |
| `settings.unrelatedControl` | EXTERNAL_STATE | Accessibility control string 0 or 1; null explicitly unavailable | Per-user settings may differ across a user transition |
| `wifi.bssid` | EXTERNAL_STATE | MAC syntax; null/empty/zero/redacted BSSID explicitly unavailable | Association, location grant and Android redaction |
| `wifi.linkSpeed` | EXTERNAL_STATE | Integer 0–2147483647; -1 explicitly unknown, null unavailable | Link negotiation and connection state |
| `wifi.ssid` | EXTERNAL_STATE | String; null/empty/unknown-SSID sentinel explicitly unavailable | Association, location grant and Android redaction |
| `bluetooth.name` | EXTERNAL_STATE | String; null/empty explicitly unavailable | Adapter availability, name and Bluetooth grant |
| `sensors.list` | TYPED | Array of objects with positive signed-32-bit type and string name/vendor; empty array allowed | No exact hardware inventory is asserted |
| `packages.visible` | TYPED | Array of syntactically valid package names, including single-component android; empty allowed | Caller visibility and Android user; no fixed inventory asserted |
| `runtime.loadedLibraries` | TYPED | Array of library basenames ending .so, without paths; empty allowed | Loading time and instrumentation; no required library set asserted |
| `runtime.javaBridgeVisible` | STABLE_ACROSS_SWITCH | Boolean, then hash equality across comparable captures; false may be consistently absent | Needs distinct capture IDs, matching installed-artifact digest and rule fingerprint |

Telephony enum/state values follow the [Android API reference](https://developer.android.com/reference/android/telephony/TelephonyManager)
and [Android 16 framework source](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android16-qpr2-release/telephony/java/android/telephony/TelephonyManager.java).
The state policy accepts known framework states 10/11 conservatively as well.

## Meaning of results

- EXACT: deterministic equality only. An explicit null expectation can match null;
  otherwise absent or different values fail. Not assigned to any of these twelve
  volatile rows. Existing profile-value equality checks keep their old behavior.
- PRESENT: a value exists (false and empty arrays are present; null/empty string
  are missing). Available for future fixtures; none of these rows needs only this check.
- TYPED, RANGE and ENUM: PASS means only the stated constraint passed. RANGE uses
  signed-64-bit integers and inclusive bounds; booleans are not integers. RANGE
  is supported and tested but no extra arbitrary numeric bound is assigned to a volatile row.
- EXTERNAL_STATE: valid reads remain OBSERVED / EXTERNAL_STATE_AVAILABLE; allowed
  sentinels or missing values remain OBSERVED / EXTERNAL_STATE_UNAVAILABLE:<reason>.
  Invalid types/ranges/enums FAIL. Permission/API failures remain BLOCKED with
  their fixed reason; they are never swallowed as optional state or passed.
- STABLE_ACROSS_SWITCH: each capture remains OBSERVED / STABILITY_BASELINE_REQUIRED.
  The host compares actual-value hashes only when two distinct request IDs have
  the same artifact and rule fingerprint and available values. An explicit
  expected_change on the destination rule requires a change; unchanged then fails.
  The fingerprint excludes this edge flag. Missing or incomparable evidence remains
  pending/warning. Every capture must be connected by successful comparison edges;
  duplicate copies of one comparison cannot fill an untested gap.

`external_state_rows` and `pending_rows` accompany existing summary counts when
policy rows exist. `not_covered_rows` means there is still no expectation. Valid
external observations do not inflate warnings. Pending stability checks do.
A summary PASS means no failed/actionable checks; it does not mean every field
was compared to a profile or that external state is verified.

## Before/after examples (synthetic, not device results)

| Input | Before | After |
|---|---|---|
| phoneType integer 1 | OBSERVED / NO_PROFILE_EXPECTATION | PASS / ENUM_VALID |
| phoneType string "1" | OBSERVED | FAIL / EXPECTATION_NOT_IN_ENUM |
| roaming false | OBSERVED | PASS / TYPE_VALID |
| sensors with type=true | OBSERVED | FAIL / EXPECTATION_TYPE_MISMATCH |
| SSID unknown sentinel | OBSERVED / no explanation | OBSERVED / EXTERNAL_STATE_UNAVAILABLE:SSID_REDACTED |
| SSID read denied | BLOCKED / permission reason | Same BLOCKED / permission reason |
| bridge false, one capture | OBSERVED / NO_PROFILE_EXPECTATION | OBSERVED / STABILITY_BASELINE_REQUIRED; pending |
| bridge false → false, same artifact/rule, two distinct captures | Unmapped observation | Comparison PASS / STABLE_MATCH |
| bridge false → true without an expected change | Informational unmapped change | Comparison FAIL / UNEXPECTED_STABILITY_CHANGE |
| bridge values with missing/different artifact | Unmapped observation | Pending; never a stability pass |

With plausible synthetic values for all twelve, five local type/enum checks can
pass, six rows remain external-state observations, and one remains pending until
paired evidence exists. This is not a new device row-count claim.

## Redaction and proof limits

Rows add bounded metadata: expectation_policy, policy_version,
expectation_rule_sha256, actual_available, and (for stability) expected_change.
Rule values are not copied into extra report fields. EXACT uses the existing
expected field, which both host export paths redact, along with actual. Host
reports keep comparison hashes and booleans; redaction is idempotent. Existing
private probe reports retain their prior raw-value format inside the probe sandbox.

The matrix renderer labels policy rows POLICY_ONLY, never VERIFIED. Type checks,
presence checks, valid enum values, an empty valid inventory and stable false
bridge visibility do not demonstrate runtime hook coverage. The artifact is the
installed-file digest, not proof of the code loaded in memory. Manual external
state and the updated probe still need a separately authorized physical run.
