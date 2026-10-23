# Companion UI redesign handoff

## Goal

Redesign the existing four-screen companion app so it feels like one coherent
product instead of four utility panels.

## Current screens

1. **Phones**
   - Source templates for local device configurations.
   - Supports generating a new configuration from a template, importing an
     existing configuration, cloning and editing.

2. **Identities**
   - Saved configurations derived from phones.
   - Shows the saved values, supports rename, delete, import/export and review
     before saving.

3. **Profiles**
   - Shows Cyclone profiles and which saved identities are associated with their
     apps.
   - Supports opening the related Cyclone profile and reviewing associations.

4. **Health**
   - Local readiness checks, compatibility evidence, duplicate review and
     diagnostics summaries.

## Redesign brief

Please redesign without changing the underlying behavior:

- Create one clear home/dashboard that answers “what needs attention?”.
- Keep Phones, Identities, Profiles and Health as secondary destinations.
- Use the existing Material 3 visual language, spacing and component patterns.
- Prefer dense, readable lists over decorative cards.
- Preserve all existing actions and warnings.
- Keep local-only diagnostics clearly separate from saved configuration data.
- Make empty, loading, error and permission-blocked states obvious.
- Improve the creation flow so the user always knows the next step.
- Do not remove advanced details; move them behind progressive disclosure.

## Constraints

- No storage, connector, publication or runtime behavior changes.
- No new permissions.
- No networking or telemetry.
- Preserve existing accessibility and test coverage.

## Deliverables

- Updated Compose screens and navigation.
- Updated tests for new navigation/state presentation.
- A short before/after summary of the information architecture.
