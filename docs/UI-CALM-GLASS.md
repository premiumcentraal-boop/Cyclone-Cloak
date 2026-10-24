# Cloak calm-glass redesign

This is a UI update over published **v1.0.0-rc.2 / code 61**, developed on
`codex/cloak-calm-glass-redesign` and prepared for **v1.0.0-rc.3 / code 62**.
Publication uses the existing signed-validation and release workflows. No device install
or physical review was performed for this redesign.

The owner's reference describes Cyclone's R6 profile UI. Cloak adopts its visual
language: the still five-stop blue backdrop, white ink, rounded groups, profile
faces, large page titles, capsule actions, and status dots. It does not adopt
Cyclone's Android-user creation, removal, capacity controls, or Carry machinery.

## Navigation and behavior

- The five existing destinations remain Home, Phones, Identities, Profiles and Health.
- Home adds a horizontal profile overview and a prominent next setup action.
  Existing attention items still navigate to the screen where they can be fixed.
- Profiles uses three-column profile faces. Tapping a face opens a scrollable
  detail sheet with the existing readiness checks, binding actions, evidence
  review, account-slot review, run history and Open in Cyclone action.
- Recently deleted profiles are grouped separately for inspection. No profile or
  binding is removed. Unresolved bindings remain visible in the binding list.
- Readiness details are expandable. Existing error reasons remain available;
  the readiness calculation is unchanged.
- Binding confirmation and navigation stay below the scrolling content, above
  the keyboard. Existing selection, warning acknowledgement, confirmation stamp,
  busy-state gates and retry callbacks are unchanged.
- Names and avatar colors are presentation only. Colors are derived consistently
  from the existing ID; no appearance preferences or new storage are introduced.
- The existing identity rename/delete confirmations and evidence import paths
  remain. Raw identifiers are not added to the gallery or Home overview.

The theme also covers the phone editor, batch workshop, import dialogs and
compatibility detail cards. System bars use the dark palette. Groups use tinted
surfaces and hairlines; there is no animated backdrop or blur dependency.

## Verification

Run from the repository root in PowerShell:

```powershell
$env:JAVA_HOME='C:/Program Files/Eclipse Adoptium/jdk-17.0.20.101-hotspot'
$env:ANDROID_HOME='C:/Users/Agent/android-sdk'
$env:CLOAK_TEST_WSL='1'
& "$env:TEMP/cloak-build/gradle-8.11.1/bin/gradle.bat" -p android :app:testDebugUnitTest :app:assembleDebug --offline --no-daemon --console=plain
git diff --check
```

`CalmGlassScreenTest` covers correct profile selection, current-versus-selected
state, incomplete-profile inspection, empty lists, Unicode initials, expandable
readiness, disabled actions and compact-width labels. Existing binding tests now
assert the fixed footer is visible instead of scrolling to it. `ScreensTest`
renders all tabs and opens a profile sheet to inspect its root status.

The complete app suite passed **791 tests: 0 failures, 0 errors, 0 skipped**,
including seven new redesign tests. The debug APK build passed. A final rendering
check after changing the secondary Phones action uses the same environment:

```powershell
& "$env:TEMP/cloak-build/gradle-8.11.1/bin/gradle.bat" -p android :app:testDebugUnitTest --tests dev.cyclone.cloak.ui.ScreensTest --tests dev.cyclone.cloak.ui.CalmGlassScreenTest :app:assembleDebug --offline --no-daemon --console=plain
```

That final check passed **14 tests**, with zero failures, errors or skipped tests,
and rebuilt `android/app/build/outputs/apk/debug/app-debug.apk` successfully.

No compiler warnings appeared in the final full gate. Git may print the existing
checkout's LF-to-CRLF normalization notice; `git diff --check` passes. The initial
presentation gate exercised the app; the subsequent full local release gate,
including forge, schema, module and probe suites, is recorded in
[the RC3 release notes](RELEASE_1.0.0-rc.3.md). No physical device suite was run.
Existing app tests include the saved-profile and binding regressions.

## Changed files

All Kotlin paths below are inside `android/app/src/main/java/dev/cyclone/cloak/ui/`:

- Design and shell: `CloakTheme.kt`, `CloakDesign.kt`, `CloakApp.kt`,
  `ConnectionStatusChip.kt`, new `CloakProfileGallery.kt`.
- Destinations: `HomeScreen.kt`, `PhonesScreen.kt`, `IdentitiesScreen.kt`,
  `ProfilesScreen.kt`, `HealthScreen.kt`.
- Existing flows and cards: `BindingFlowScreen.kt`, `PhoneEditorScreen.kt`,
  `CompatibleDeviceScreen.kt`, `ProfileReadinessCard.kt`, `Cards.kt`.
- Android system colors: `android/app/src/main/res/values/themes.xml`.
- App UI tests: new `CalmGlassScreenTest.kt`; updated `ScreensTest.kt`,
  `BindingFlowScreenTest.kt`, `ProfileOperationsScreenTest.kt`.
- Documentation: `README.md`, `docs/ARCHITECTURE.md`, `docs/CURRENT-STATE.md`,
  and this file. Stale RC1 header text is corrected for RC3 preparation;
  historical measured test entries are retained.

Local renders are written to `android/app/build/screenshots/` and
`android/app/build/reports/calm-glass/profiles-compact.png`. They use synthetic
test state, not physical-device evidence. Phone, TalkBack and large-font checks
remain unverified until a separate device review. No device installation was
performed. Permissions, connector, runtime, module behavior and storage are unchanged.
Only app/module packaging version metadata is updated for RC3.
