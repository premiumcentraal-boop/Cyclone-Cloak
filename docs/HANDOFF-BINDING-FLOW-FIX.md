# Handoff: unify the binding flow (profiles and identities entry points)

## Context

Cyclone Cloak has two ways to create a binding, and they behave inconsistently. A user who wants to attach one device profile to a Cyclone profile gets stuck depending on where they start:

- **Profiles screen** (`ui/ProfilesScreen.kt`): the only visible action is "Bind an app" (`vm.startBinding()`). There is no obvious way to choose which device identity will be bound, and no "bind all apps" shortcut. The identity step exists in the flow but is easy to miss or reaches dead ends.
- **Identities screen** (`ui/IdentitiesScreen.kt`): "Bind to a profile" calls `vm.startBinding(id)` with the identity preselected, but the APP step of the flow only allows selecting **one** app. There is no multi-select and no "all apps" option, so the user cannot bind the identity to every app in the profile in one pass.

Net effect: neither entry point alone completes the common job ("bind this phone identity to this profile and all its apps") without friction.

## Goal

Make one binding flow that works correctly from **both** entry points:

1. **APP step becomes multi-select.** Replace the single-app picker with a checkbox list of the profile's apps, plus an "All apps" toggle that checks everything. The selected set is carried through REVIEW the same way a single app is today.
2. **Identity step is always resolvable.** When started from the Profiles screen, the flow must clearly present the IDENTITY step (same step order: PROFILE, APP, IDENTITY, REVIEW, RESULT) with the identity picker visible and required before REVIEW. When started from the Identities screen, the identity is preselected and the IDENTITY step can be skipped or shown as a confirmed summary.
3. **Bulk bind path stays intact.** The existing bulk-bind logic (`cyclone/BulkBind.kt`, and whatever the Profiles screen uses for its bulk action) must keep working; the new multi-select should reuse or feed into it rather than duplicating a second code path.
4. **Review shows the full selection.** The REVIEW step must list: profile, Android user, every selected app (or "All apps (n)"), and the chosen identity. The confirmation stamp logic in `BindingFlow.stamp` must cover the multi-select case.
5. **No schema or contract changes.** This is a UI + view-model fix only. `CloakBindingStore`, the connector contract, and the publish pipeline are untouched.

## Files likely involved

- `android/app/src/main/java/dev/cyclone/cloak/ui/BindingFlow.kt` (step state, validation, stamp)
- `android/app/src/main/java/dev/cyclone/cloak/ui/BindingFlowScreen.kt` (APP step UI: checkbox list)
- `android/app/src/main/java/dev/cyclone/cloak/ui/IdentitiesScreen.kt` (entry point wording)
- `android/app/src/main/java/dev/cyclone/cloak/ui/ProfilesScreen.kt` (entry point wording)
- `android/app/src/main/java/dev/cyclone/cloak/ui/CloakViewModel.kt` (`startBinding`, `selectBinding...`, submit path)
- `android/app/src/main/java/dev/cyclone/cloak/cyclone/BulkBind.kt` (reuse for multi-app submit)
- Existing tests to extend: `ui/BindingFlowTest.kt`, `ui/BindingFlowScreenTest.kt`, `ui/BindingReviewTest.kt`

## Definition of done

- From Profiles screen: start binding, pick profile, multi-select apps (including "All apps"), pick identity, review shows the full set, submit creates one binding per app.
- From Identities screen: start binding with identity preselected, pick profile, multi-select apps or "All apps", review, submit.
- Both paths produce the same stored binding records as the existing single-app flow produces today (same fields, same per-app records).
- Unit tests cover: multi-select state transitions, "All apps" toggle, review stamping with multiple apps, and submit producing one binding per selected app.
- `./gradlew :app:testDebugUnitTest` passes (or the repo's standard test task).
- No changes to the connector contract, storage schema, or native module.

## Notes

- Keep wording neutral: "app selection", "device profile", "binding". No behavior logic changes beyond the selection surface.
- If the APP step currently stores a single package string in the draft, widen it to a set and make sure `canAdvance` requires at least one selection.
- The flow is a modal sheet/screen; keep the existing design language (cards, step indicator, inline validation) unchanged.
