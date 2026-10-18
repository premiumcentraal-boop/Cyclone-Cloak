# Profile operations console

Branch: `codex/profile-operations-console`, based on `a93d8643c51463dff1d4fb3258a383432012f989`.

## User flow

- Identities has one primary **New compatible device** action. Choose one real template, 1–20 profiles and an optional prefix. Preview local coherence, uniqueness and warnings; approve individual profiles before saving. Repeated creation chooses an unused prefix. Saved results remain available if another item fails.
- After saving, choose a saved device and Cyclone profile in the same flow. Review assignment selects all current Cyclone apps and uses the existing binding review and confirmation. Saved devices survive cancellation.
- Each non-Main Cyclone profile has a readiness card, **Bind all apps**, **Replace device profile**, and **Run history**. The previous fleet-wide action is labelled **Assign fleet** to distinguish its scope.
- Bind all apps reuses the current device when exactly one identity is assigned and there are no errors, shared-identity warnings, replacement warnings or duplicate identifiers. Otherwise it asks for device selection/review. It never chooses an arbitrary library device.
- Replacement starts from every currently bound app, including disabled bindings. The review lists old devices/apps, the new device, the Cyclone profile and Android user. App selection changes only through the explicit change-selection action. Confirmation preserves profile ID, user, binding revision, origin and enabled states; unrelated records remain. Changed user mappings or binding records invalidate the replacement. Mirrors managed by Main remain protected.
- Saved-operation retry publishes and reconciles existing records. It never reinstates historical bindings. Changed targets require a new review.
- Per-profile history contains timestamp, operation, profile/user, apps, before/after references, result and fixed failure reasons. Diagnostic ZIPs save through the Android system document picker; cancellation, storage failure and missing picker provide concrete retry guidance.

## Evidence and compatibility

Readiness is an observation of current saved profiles, connection state, the last app-wide Root Doctor result and bounded operation history. Compatibility posture retains its report freshness context. A past successful binding run is not runtime proof. The connector supplies no timestamped successful lifecycle evidence, so the lifecycle row explicitly says **Not recorded**; binding checks are listed separately.

Profile JSON, binding JSON, connector IDs, permissions and contracts are unchanged. Existing stores, validators, generation engine, publication and reconciliation paths are reused. Run-history schema 1 gets an optional `previous_device_profiles` map per target and a `REPLACE` UI operation. Old records without before-state remain readable. Before/after references use the existing diagnostic redaction policy; no seeds, profile bodies or user labels enter bundles.

No module, native hooks, runtime behavior, forge internals, release versioning or release workflow source changes are included. The app build's existing dependency packages the unchanged module.

## Changed code

- `ui/CompatibleDeviceScreen.kt`: compact creation, quality review, per-item approval and assignment.
- `ui/ProfileOperations.kt`: UI plans, replacement guards, one-tap eligibility and label allocation.
- `ui/ProfileReadinessCard.kt`: conservative per-profile evidence and next steps.
- `ui/IdentitiesScreen.kt`, `ProfilesScreen.kt`, `CloakApp.kt`: entry points and navigation.
- `ui/CloakViewModel.kt`, `BindingFlow.kt`, `BindingFlowScreen.kt`: orchestration, explicit replacement review and retained retry.
- `ui/BindingRunsSection.kt`, `diagnostics/BindingRunJournal.kt`: filtered history, additive redacted before/after evidence, document-picker export.
- `ProfileOperationsTest`, `ProfileOperationsIntegrationTest`, `ProfileOperationsScreenTest`: pure eligibility/readiness tests, real-store integration and Robolectric Compose interaction tests.

## Verification

Focused regression command:

```powershell
gradle -p android :app:testDebugUnitTest --tests '*ProfileOperations*' --tests '*BindingFlow*' --tests '*BindingRun*' --tests '*BatchIdentity*' --tests '*BindingList*'
```

Full verification command:

```powershell
gradle -p android :app:testDebugUnitTest :app:assembleDebug
```

Full result: **598 tests, 568 passed, 30 skipped, 0 failures/errors**. All 21 new console tests passed. The existing Windows skips are 11 `CloakPublishMergeTest` cases, 18 `PublicationRecoveryTest` cases and one `PrivilegedSessionTest` case requiring a POSIX shell/WSL environment. Debug APK assembly and unchanged-module packaging passed without build warnings. After a background-only visual correction, all **28 console and screen tests passed** and APK assembly passed again. Creation and profile-readiness screenshots were visually inspected. `git diff --check` passes.

No physical phone installation, root operation, profile mutation on a device, reboot or release was performed. Device-level connector approval, lifecycle evidence and the system file picker still need physical verification.
