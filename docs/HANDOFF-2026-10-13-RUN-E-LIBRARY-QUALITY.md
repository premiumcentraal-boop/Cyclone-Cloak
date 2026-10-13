# Delegated run E: local profile-library quality

## Starting point

Work on `main` at `6dfa287`. This is a local app-quality sprint only. Do not
create a release, change versions, run device actions, or touch existing data,
validation, binding, posture, connector, publication, module or runtime behavior.
If a change outside the summary/UI seems necessary, stop and report instead of
making it.

## Goal

Add a neutral **Library quality** summary so the owner can see the overall health
of saved local profiles without opening each one. It must be read-only. It must
not repair, mutate, delete, rebind, publish or interpret anything. It should use
the same local design language as the existing Health cards.

## Required data

The summary should report, at minimum:

- Total readable saved profiles.
- Unreadable saved profiles, shown only when the identity inventory is known.
- Duplicate identifier groups.
- Profiles with validation errors.
- Profiles with validation warnings.
- Profiles with missing local compatibility records.
- Profiles with stale or attention-state local compatibility records.
- Profiles bound to at least one app.
- Profiles with no bindings.

Do not display raw identifier values. Display affected profile names, counts and
short local states. Profile navigation may open the existing review flow.

## Existing integration points

Use these existing values and helpers; do not add new storage or indexes:

- `CloakState.identities` is `List<Pair<String, JSONObject>>`.
- `CloakState.identityInventoryKnown` and `CloakState.unreadableIdentityIds`
  describe inventory reliability.
- `CloakState.uniqueness` is an existing derived `ProfileUniqueness`.
  Its `duplicates` list already contains field names and profile IDs, not values.
- `ProfileValidator.validateProfileResult(profile).findings` gives local
  validation errors and warnings.
- `CloakState.postures` contains existing `PostureRecord` values.
- `EngineMetadataStore.digest(profile)` gives the saved-profile body digest.
- `CloakState.currentArtifactDigest` is the current bundled-artifact digest.
- `PostureSummary.freshnessWarnings(record, profileId, profileDigest,
  artifactDigest, now)` reports profile/artifact mismatch and freshness issues.
- `CloakState.bindingsOf(profileId)` reports existing app bindings.

For time-dependent checks, pass `now` into the pure assessment function so unit
tests can use a fixed clock. Do not read the clock during assessment.

## Suggested shape

Add a pure assessment model, for example `LibraryQualitySummary`, plus a small
pure builder. Keep it independent of Compose and persistence. It may return rows
or lists of affected profile IDs; the UI translates IDs into profile names for
display.

Do not put raw identifier values, diagnostic bodies, posture reports, provider
artifacts or secret-looking strings in the summary.

## UI

Add the summary to the Health screen below the existing local identifier
duplicates card. Use the current Cyclone-style card layout and concise rows.

Each actionable row should let the owner review an affected profile using the
existing review action. Disable actions while `state.busy != null`. Empty
libraries should render a clear neutral state rather than an error.

## Scope guards

- No networking, analytics, new permissions or file exports.
- No profile storage, binding, publication, connector, module or runtime changes.
- No automatic regeneration, duplicate repair, reset, reseed or deletion.
- No new posture decisions and no platform verdicts.
- No change to existing duplicate detection.

## Tests

Add focused unit tests for the pure summary:

- empty library;
- readable library with no findings;
- one duplicate group with two profiles;
- separate counts for error and warning profiles;
- missing compatibility record;
- stale compatibility record;
- compatibility record in an attention state;
- bound and unbound profiles;
- unreadable profiles only counted when inventory is known;
- summary does not include raw identifier values.

Add Compose tests for:

- visible summary rows and counts;
- affected-profile navigation;
- disabled navigation while busy;
- empty-library state.

## Verification

```powershell
$env:JAVA_HOME='C:/Program Files/Eclipse Adoptium/jdk-17.0.20.101-hotspot'
& "$env:TEMP/cloak-build/gradle-8.11.1/bin/gradle.bat" -p android :app:testDebugUnitTest --offline --no-daemon
uv run --with pytest --with jsonschema -- python -m pytest forge/schema_tests -q
git diff --check
```

When all checks pass, commit to `main` with a concise neutral message such as
`feat(library): add local quality summary`. Report changed files, exact test
counts and build status. Do not publish a release.
