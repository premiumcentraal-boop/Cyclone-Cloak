# Cohort separation v1

This pure, local analysis compares saved profile **descriptions**. It does not
measure actual device independence, runtime enforcement, certification, or a
platform verdict. Distinct identifiers alone cannot establish high-trust
multi-instance compatibility. No profile, binding, store, publisher, connector,
runtime, or account is changed. There is no UI integration or automatic repair.

## Entry points

Python: `cloak_forge.cohort_separation.analyze(profiles: list[dict]) -> dict`.
Supply decoded saved profile bodies, not engine envelopes or storage wrappers.

Kotlin: `CohortSeparation(policyJson: String).analyze(input: List<JSONObject>): JSONObject`.
Supply the trusted bundled `cohort-separation-v1.json` asset as `policyJson`.
The catalog asset is already included by the existing app asset source set;
no new storage or Android API is required. Both implementations use that same
checked-in version-1 policy table.

Existing coherence validators reject malformed profiles. Schema 0.2 and 0.3
remain accepted without Reality. Duplicate profile IDs, invalid ID/label shapes,
and libraries over 200 profiles fail with the fixed `COHORT_INPUT_INVALID` error.
No validation exception or rejected value is returned. Callers must resolve
unreadable/malformed files explicitly rather than silently dropping them from a
cohort. Input objects are never mutated.

## Privacy and format

The JSON report has `format: 1` and `policy: 1`. `profiles` contains only IDs and
labels. All other rows identify field paths, section names, profile IDs, counts,
scores, actions, or fixed reason codes. Groups have **no shared values or value
hashes**; low-entropy carrier/account/region facts cannot be recovered from a
published group digest. No seeds, identifiers, account hints, firmware strings,
profile bodies, or private metadata are copied into the output. IDs/labels are
explicitly allowed display metadata; users should keep their labels non-sensitive.

- `pairs`: per-pair distance/confidence and seven category breakdowns.
- `shared_groups`: repeated individual fields and configured correlated tuples;
  each lists only field paths and member profile IDs.
- `identifier_findings`: exact duplicates and conservative near-duplicate flags.
- `weakest_fields`: all scored paths, weakest first, with known-profile counts.
- `missing_reality`: missing, unknown, or unavailable sections per profile.
- `recommendations`: field paths and review/regeneration actions only.
- `independence_score`, `confidence`, `warnings`: deterministic cohort summary.

## Scoring and limitations

The seven equally weighted categories are stable identifiers, hardware
descriptions, carrier/region, software/packages, display/camera/sensors,
network/locale/timezone, and Play/Google expectations. The table lists exact paths.
Play expectations remain descriptions, not authenticated service evidence.

For each pair/category, distance is `floor(100 * different_known_fields /
configured_fields)` and confidence is `floor(100 * mutually_known_fields /
configured_fields)`. Pair summaries are the integer mean of category scores;
cohort summaries are the integer mean of pair scores. Missing facts earn **zero**
distance and reduce confidence; they never count as different devices.
Reality values are comparable only when their envelope is `known`. Null, empty,
`unknown`, and `unavailable` facts are not comparable. Array order is significant;
object key order is not. Stable identifiers use case-insensitive comparison.

Empty/single-profile cohorts have no pair evidence: score is null, confidence
zero, and `INSUFFICIENT_COHORT` is recorded. Confidence under 80 emits
`INCOMPLETE_FACTS`. Any duplicate/near-duplicate emits
`IDENTIFIER_REUSE_OR_SIMILARITY`. Score under 60 or any pair under 30 emits
`HOMOGENEOUS_COHORT_NOT_HIGH_TRUST_EVIDENCE`. These are conservative policy
thresholds, not empirically calibrated trust probabilities. A higher score
does not confer trust. Correlated facts and repeated base/Reality descriptions
are intentionally visible; categories are not statistically independent.

Near-duplicate detection is deliberately limited: equal-length normalized
alphanumeric identifiers of at least six characters differing by exactly one
substitution. It is a review heuristic, not evidence that identifiers are linked.
It does not detect every edit, prefix pattern, or cross-field reuse.

The complete correlated tuple includes model, firmware, carrier, language,
country, timezone, display, camera, sensors, and storage/memory. Smaller firmware,
region, display/camera, and compute/storage tuples remain visible when the full
tuple is incomplete. A missing sensor inventory never becomes a fabricated
shared hardware fact. Recommendations to regenerate apply to derived identity
values only, and require separate explicit user action. Hardware differences
require supported catalog choices or real evidence, not invented specifications.
Unavailable facts must remain unavailable.

## Examples

Two copies of one body with different saved IDs: distance zero, identifier-reuse
warning, shared hardware/software groups. Different seeds on one template:
identifier distance can be 100 while hardware distance remains zero. Different
templates with unknown Play/Reality facts: distance may improve, but confidence
stays below 100. None of these reports is a platform verdict.

Processing is bounded to 200 profiles, uses pairwise comparisons, and produces a
potentially large report at that limit. This version is a library/API only;
library UI/export integration is future work. Tests compare complete Python and
Kotlin reports, including malformed inputs and older schemas, without device use.
