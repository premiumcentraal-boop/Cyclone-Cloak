# Companion UI redesign — information architecture

Front-end only. No storage, connector, publication, runtime, permission or networking changes; every
`CloakViewModel` call and every warning that existed before is still reachable.

## Before

```
[Phones] [Identities] [Profiles] [Health]      ← four peer tabs, app opened on Phones
```

- No single place said what needed attention; problems were spread across Profiles pills, binding cards and Health.
- Each phone row carried five wrapping text buttons; each identity carried nine.
- Identity creation stacked "one" and "fleet" forms, picked the phone from a wall of chips, and used +/- outlined buttons.
- Binding filter was a full-width outlined button; Profiles stacked four action buttons.
- Health mixed readiness, repair links and library review with no grouping.

## After

```
[Home] [Phones] [Identities] [Profiles] [Health]
  │
  ├─ Needs attention   Cyclone connection / approval, missing binding permissions, Root Doctor,
  │                    profiles needing attention, bound apps not ready, orphaned bindings,
  │                    shared identifiers, unreadable identities → tap goes to the screen that fixes it
  ├─ Next step         1 phone → 2 identity → 3 bind; the first unfinished step is marked "Up next"
  └─ Library           shortcuts with counts into the four working screens
```

- **Home** (new, start destination). Loading ("Waiting for Cyclone"), all-clear and problem states are explicit.
  Back from any other tab returns to Home.
- **Phones**: grouped dense rows; one primary *Make identity* action, Clone/Edit/Share/Delete in a ⋮ menu.
- **Identities**: *One identity | A fleet* segmented selector, phone dropdown, count stepper, and a "next step" hint.
  Each opened identity shows its binding state with *Bind to profile apps* as the primary action, other actions in ⋮,
  and diagnostics/evidence behind a **Diagnostics & evidence** disclosure kept apart from the saved values.
- **Profiles**: *Bind a device profile* is the primary action; Refresh/Bind all secondary; profiles render as one
  grouped list; the binding filter is a dropdown field. Binding cards put health beside the app name.
- **Health**: a local-diagnostics notice, then Readiness / Check and repair / Library review / Coverage sections.

## Design library

`ui/CloakDesign.kt` holds the shared building blocks every screen now uses: `CloakSectionHeader`, `CloakGroup`,
`CloakRow`, `CloakNotice`, `CloakSegmented`, `CloakSelectField`, `CloakStepper`, `CloakOverflowMenu`,
`CloakDisclosure`, `CloakIconBadge`, and the `CloakSpace` spacing scale. Tones come from the existing
`CloakStatusTone`; colors and shapes from the existing Cyclone V3.2 theme in `CloakTheme.kt`.

## Tests

- `HomeAttentionTest`: loading, all-clear, connector failure, permission-blocked, root/binding problems, next step.
- `ScreensTest`: Home renders attention items and navigates to the fixing screen; existing screen tests unchanged.
