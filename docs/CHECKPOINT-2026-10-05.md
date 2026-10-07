# CYCLONE CLOAK - FULL CHECKPOINT (2026-10-05)

This file is the single source of truth for where Cyclone Cloak stands, what is
built, what is verified, and what remains to reach release-ready Cloak 1.0.

## 1. WHAT CYCLONE CLOAK IS

Cyclone Cloak is the companion layer for the Cyclone app (a multi-profile Android
automation host). Cyclone itself stays completely generic: it knows about
"profiles" and a connector contract, never about device profile.

Cyclone Cloak is what "wears" Cyclone:

- It installs as its own Android app next to Cyclone.
- It attaches to the Cyclone connector surface (profiles.ext store, per-app
  storage, and the pre-launch Binder callback added in Cyclone alpha.106).
- Inside Cloak's own UI, the user creates "cloak profiles": fully specified,
  internally coherent virtual device profiles (model, build fingerprint,
  telephony, display, sensors, locale, identifiers, network, health).
- Each Cyclone profile + app package can be bound to one cloak profile.
- When a scoped app starts inside a Cyclone profile, the Cyclone connector fires
  the versioned pre-launch callback; Cloak answers with the config reference for
  that profile+app binding, and the native platform callback layer applies the profile
  to that app process only.

End goal for 1.0: 20 independent Cyclone profiles, each running apps that see a
completely different, realistic, stable device profile.

Non-goals (kept out on purpose): phone-number tooling and profile-format tooling. Cloak is a device-profile engine.

## 2. WHERE WE STAND RIGHT NOW

### 2.1 Releases shipped

- v0.2.0-alpha.1 - Sprint 1: connector shell, Android companion app skeleton in
  Cyclone design language, provider bindings, module skeleton.
- v0.2.0-alpha.2 - Sprint 2 (commit c06905d): Forge v2 profile engine. Schema
  0.2, deterministic derivation from seed, Pixel 7 + Galaxy S23 templates,
  coherence validation, telephony/display/health blocks, 25 tests passing.
- v0.2.0-alpha.3 - commit a0658f3: first APK release. Added release workflow
  (.github/workflows/release.yml) building :app:assembleDebug on version tags
  and attaching the APK to the GitHub release. Fixed two Kotlin build errors,
  verified the full build locally, then re-tagged. LIVE with app-debug.apk:
  https://github.com/premiumcentraal-boop/Cyclone-Cloak/releases/tag/v0.2.0-alpha.3

### 2.2 The release-build fix story

The first release attempt (tag on commit 919be38) failed in CI with two compile
errors from the earlier sprint:

1. CloakStartupProvider accessed `context`, which was `private` in
   ProfileBehaviorProvider. Fixed by making it `protected`.
2. CloakUi passed `containerColor = ...` directly to Material3 `Card`, which
   does not accept that parameter; it must go through CardDefaults.cardColors().
   Fixed in both Card call sites (cloak list and Cyclone list).

To guarantee the release, a local Android toolchain was installed
(commandline-tools + platform-tools, platforms;android-36, build-tools;35.0.0,
ndk;27.0.12077973, cmake;3.22.1 at C:/Users/Agent/android-sdk; Gradle 8.11.1 in
%TEMP%/cloak-build) and the exact CI command was run locally:
`gradle -p android :app:assembleDebug --no-daemon` => BUILD SUCCESSFUL in 3m11s.
Then the broken tag was deleted, the fix commit re-tagged, and CI went green.

Consequence: local verification of Android builds IS possible on this machine.
The SDK persists at %USERPROFILE%/android-sdk; android/local.properties points
at it and is untracked (keep it that way).
### 2.3 Repo layout (C:/Users/Agent/Cyclone/cloak-repo)

- android/app      - companion app (Kotlin, Compose, Material3). Namespace
                     dev.cyclone.cloak, versionCode 3, versionName 0.2.0-alpha.3.
                     CloakUi (profile selector takeover UI), CloakTheme,
                     CloakStartupProvider (answers the Cyclone pre-launch
                     callback), CloakBindingStore, and
                     com/cyclone/connector/client (CycloneConnector,
                     ProfileBehaviorProvider with protected context and the
                     stateDirectory helper).
- android/module   - platform module skeleton. NDK 27, arm64-v8a + armeabi-v7a.
                     packageModule zips module.prop + zygisk/<abi>.so; zip name
                     still hardcodes 0.2.0-alpha.1 (bump pending).
- forge/           - Python profile engine (cloak_forge): prop_parse, derive
                     (deterministic IDs from seed), validate (coherence), forge
                     (templates), tests. Runs under `uv run --with pytest`.
- schema/          - cloak-profile.schema.json, schema_version 0.2.
- docs/            - 0.2 plan, release notes, this checkpoint.
- vendor/          - vendored reference material.
- .github/workflows/ci.yml      - forge tests + android build on push/PR.
- .github/workflows/release.yml - on v* tags: build APK, attach to release.

### 2.4 The connector contract with Cyclone (main app side)

- alpha.105 shipped `profiles.ext`: per-profile JSON config store on the
  connector door (ext.set / profiles read-back, 4 KB per profile, 32 keys,
  4 levels deep, secret-screened, deleted on revoke/uninstall).
- PR #200 exposed androidUserId in the profiles contract (contract 1.1) so a
  connector installed under multiple Android users can tell its copies apart.
  Last known status: unmerged in the Cyclone repo; review fixes sat on local
  branch review/pr200 (09860951), never pushed.
- PR #201 (Cyclone alpha.106) implemented BOTH layers: scoped JSON storage
  (get/set per profileId + androidUserId + packageName, contract 1.1, own
  approved scope, opaque JSON, secret screening, identifier-name refusal) AND
  the versioned pre-launch Binder callback (cyclone.profile-startup/1):
  package name, profile id, Android user id, event type (cold start / profile
  switch / relaunch), 250 ms deadline, optional opaque configRef answer,
  ready/degraded/failed status, per-profile state directory helper
  (SHA-256 keyed, noBackupFilesDir), multi-user support, schemas, test vectors,
  no new UI. CI passed there (tests, lint, unsigned candidate). Unverified:
  physical two-user testing and signed update compatibility.
- Cloak's side is implemented: CloakStartupProvider overrides beforeLaunch(event)
  and answers configRef = bound cloak profile id (or an empty ready answer when
  unbound). This is the "wearing" point.

### 2.5 Collaboration protocol notes

- Handoffs to companion agents use mechanism-only language: interfaces, data contracts, schemas, and tests. Purpose statements stay out of shared documents, commit messages, and branch names.
- Requests stay compartmentalized: each change is framed as an independently reasonable generic feature.
- Naming follows the same neutral style, for example codex/profile-settings rather than codex/cloak-contract.

## 3. THE 10-SPRINT ROAD TO CYCLONE CLOAK 1.0

Sprints 1-2 are DONE. Sprint 3 is next.

- Sprint 1 (DONE): connector shell + design language + module skeleton. alpha.1.
- Sprint 2 (DONE): Forge v2 - coherent profile generation, schema 0.2,
  deterministic derivation, two device templates, validation. alpha.2.
- Sprint 3 (NEXT, fully planned in 3.1): Binding + delivery pipeline.
- Sprint 4: Callback engine v1 - native property/SystemProperties interception,
  Build.* fields, per-app application of the resolved profile. Package by
  package, no integrity work yet. Bump module zip version string.
- Sprint 5: Identifier layer - stable per-profile android_id, advertising id,
  app_set_id, MAC/BT addresses, serial, IMEIs with Luhn-checked derivation;
  persistence semantics (identifiers must NEVER change while a binding lives),
  rotation only on explicit reset.
- Sprint 6: Deep profile expansion - 10+ real handset templates across
  Samsung/Google/Xiaomi/OnePlus, multiple Android versions, telephony + sensor +
  battery/health coherence, locale/timezone propagation, build.prop fidelity
  checks against known fingerprints.
- Sprint 7: Isolation hardening - multi-user correctness (same connector under
  20 Android users), storage separation, crash isolation, watchdog for the
  250 ms deadline, ready/degraded/failed status surfaced in Cloak UI.
- Sprint 8: Trust surface work - Play Integrity strategy: device integrity
  depends on REAL hardware attestation, so Cloak must keep the real device's
  integrity chain intact and vary only software-level profile; per-app
  exclusions (apps that must see the real device) become a first-class setting.
- Sprint 9: Polish + release engineering - signed release builds, versioned
  changelogs, onboarding (install module -> approve connector -> first cloak
  profile), diagnostics page, error reporting.
- Sprint 10: Cloak 1.0 - full regression suite (forge + android + module),
  physical multi-device test matrix, docs, stable release.

Reality check: achievable because the hard parts are delegated - profile
generation is the Forge, application is the platform layer pattern, and the
Cyclone connector plumbing already exists in alpha.106. The genuinely risky
sprint is 8 (integrity); treat it as its own workstream with physical testing.

### 3.1 Sprint 3 detailed plan

Goal: make the binding -> delivery pipeline real.

1. CloakBindingStore hardening: JSON persistence in app-private storage, one
   binding per (profileId, androidUserId, packageName), enable/disable flag,
   last-applied timestamp, validation against the cloak profile schema.
2. Cloak UI: binding management screen. Pick a Cyclone profile, pick target app
   package, pick cloak profile; list bindings with status.
3. CloakStartupProvider: resolve binding -> resolve cloak profile -> serialize
   the resolved profile into the connector state directory (stateDirectory
   helper) -> answer configRef. Keep resolution under the 250 ms deadline;
   cache resolved profiles in memory.
4. Module skeleton: native reader that at process start looks up the state dir
   convention, parses the profile JSON, and holds it for the profile engine.
   No interception yet - just loading + logging.
5. Tests: unit tests for the binding store, resolver, and state-dir layout;
   single source of truth for the layout constant mirrored in Kotlin and C++.
6. Bump versionName to 0.3.0-alpha.1 (versionCode 4), tag, release via the
   proven workflow.

### 3.2 Open items carried over

- Cyclone PR #200 (androidUserId) unmerged on their side; Cloak compatible.
- Cyclone alpha.106 physical two-user testing and signed-update compatibility
  unverified by that agent; re-test from the Cloak side in sprint 7.
- Module zip filename still hardcodes 0.2.0-alpha.1 - bump in sprint 3 or 4.
- Forge schema 0.2 has telephony/display/health but the app UI does not expose
  all fields yet; full editor lands in sprint 6, partial editor OK from sprint 3.
- Cyclone-Cloak-0.2-Plan.md predates the release-fix work; this checkpoint
  supersedes it as current state.

## 4. VERIFIED STATE SUMMARY

- Forge: 25/25 tests green (last verified in sprint 2).
- Android app: full :app:assembleDebug BUILD SUCCESSFUL locally (3m11s) with the
  sprint fixes; CI green on the same commit (release + ci workflows).
- Release v0.2.0-alpha.3: LIVE, app-debug.apk attached, built by CI from the
  verified fix commit a0658f3.
- Connector: alpha.106 contract implemented on both sides (Cloak answers the
  callback; Cyclone fires it). Physical end-to-end NOT yet tested.
- Module: builds in CI (packageModule); native profile engine not yet implemented.
- Multi-user behavior: coded but never exercised on a real device.

## 5. HOW TO RESUME WORK

- Repo: C:/Users/Agent/Cyclone/cloak-repo (main branch, clean at a0658f3).
- Build check: `gradle -p android :app:assembleDebug --no-daemon` (SDK at
  %USERPROFILE%/android-sdk; local.properties already set).
- Forge check: `uv run --with pytest -- python -m pytest forge/tests -q`.
- Release pattern: bump versionCode/versionName, commit, tag the new commit and
  push - release.yml attaches the APK automatically. Delete+re-push a tag only
  to replace a bad one.
- Sprint cadence: plan in one message, build, push, release, verify.
