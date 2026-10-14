# Handoff — Companion UI redesign (2026-10-14)

PR: https://github.com/premiumcentraal-boop/Cyclone-Cloak/pull/13 · branch `claude/quirky-feynman-n8y709` · CI green.
Front end only. Nothing changed in the ViewModel, storage, connector, runtime, permissions or release version.

## What changed

- **One design library** in `ui/CloakDesign.kt`: section header, grouped list, row, notice, segmented selector,
  dropdown field, stepper, ⋮ menu and expandable section, plus a spacing scale. All screens use these, with the
  existing Cyclone colours and shapes.
- **New Home tab** (the app now opens here):
  - "Needs attention" lists the problems; tapping one opens the screen that fixes it.
  - "Next step" shows phone → identity → bind.
  - Shortcuts open the other four tabs.
- **Phones**: compact rows. Each phone has one *Make identity* button; Clone, Edit, Share and Delete are in ⋮.
- **Identities**:
  - Creating: a *One identity / A fleet* switch, a phone dropdown (instead of chips) and a −/+ counter.
  - Each identity: *Bind to profile apps* is the main button; the other actions are in ⋮.
  - Diagnostics are behind a "Diagnostics & evidence" section.
- **Profiles**: *Bind a device profile* is the main button. Profiles show as one list, the binding filter is a
  dropdown, and the binding cards are tidier.
- **Health**: a "local diagnostics" note at the top, then sections: Readiness, Check and repair, Library review,
  Coverage.
- **Tests**: added `HomeAttentionTest` and Home tests in `ScreensTest`. Existing tests are unchanged and pass.
- **Docs**: `docs/UI-REDESIGN-2026-10-13.md` describes the layout before and after.

## Issues

1. The bottom bar now has 5 tabs. Labels may get tight on very narrow phones; check on a real device.
2. Binding cards still use *On/Off* text buttons rather than a switch. This keeps the existing wording and tests.
3. While Cyclone is being opened, the "Open … in Cyclone" row ignores taps but doesn't look disabled.
4. Leftovers I didn't remove: `CloakRootDoctorCard` (already unused before this change), and the `selected` and
   `onClick` parameters of `CloakProfileCard` (never used before either).
5. The cloud container has no Android SDK, so the tests were verified only by GitHub CI. Screenshots of each
   screen are in the CI run's `screens` artifact.

## Questions

1. Should binding On/Off become a switch? That changes test selectors but not the stored states.
2. Is the note under "Diagnostics & evidence" ("kept apart from the saved identity values") accurate for
   posture records? Please confirm or give the wording you want.
3. Do you want a dark theme to match Cyclone? Not added, to keep the change small.
4. Should a release be cut with this? Versioning was not touched.
