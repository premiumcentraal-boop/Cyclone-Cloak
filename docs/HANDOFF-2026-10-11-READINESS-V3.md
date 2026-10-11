# Readiness Doctor v3 handoff — 2026-10-11

## What changed

Upgraded the existing Health diagnostics card to Readiness Doctor v3. A single
ready/degraded/failed verdict uses the worst applicable check, with a compact
pass/warning/fail summary. Separate checks cover root/Zygisk, publication,
synchronization, app/module version agreement, permissions/provider, enabled
binding presence, each binding's enabled state, Cyclone profile/user/app
reference, local identity readability/coherence, stale references and conflicts.
Every non-pass state has a fixed user-readable reason and machine-readable code.

Missing identity files, unreadable JSON, blocking coherence errors and ID/file
reference mismatches are distinct. Duplicate binding targets, shared identifier
values, user-number changes and incomplete synchronization produce readable
warnings. Unreadable files are discovered by read-only inspection during the
existing library refresh, without deleting or rewriting them. Main-mirrored
identity files are explicitly unverified by this local library.

Disabled binding checks remain visible but are excluded from the verdict;
having no enabled bindings degrades readiness. Existing detailed binding health
and Cyclone health mappings, repair/reconnect actions, reconciliation, publication,
runtime module and connector contract are unchanged. Diagnostics never repairs
state automatically or triggers synchronization.

The copied JSON uses `cyclone-cloak.readiness-doctor/3` and a positive allowlist:
fixed check/status/reason enums and messages, binding ordinals/counts, applicability,
and grammar-checked release versions. Identity JSON, profile/app labels, package
names, seeds, full identifiers, keys, tokens, raw error/diagnostic strings and
user content are never exported. Privacy tests exercise raw input injection and
the complete state-to-report path.

## Changed files in this run

- `android/app/src/main/java/dev/cyclone/cloak/ui/HealthDiagnostics.kt`: v3 checks, verdict/summary and redaction.
- `android/app/src/main/java/dev/cyclone/cloak/ui/HealthDoctorCard.kt`: v3 title, warning labels and summary.
- `android/app/src/main/java/dev/cyclone/cloak/ui/HealthScreen.kt`: read-only fact adapter integration.
- `android/app/src/main/java/dev/cyclone/cloak/ui/ReadinessDoctorFacts.kt`: new pure snapshot adapter.
- `android/app/src/main/java/dev/cyclone/cloak/ui/CloakViewModel.kt`: local-library inventory/readability snapshot only.
- `android/app/src/test/java/dev/cyclone/cloak/ui/HealthDiagnosticsTest.kt`: verdict, mixed states, identity/conflict reasons and redaction.
- `android/app/src/test/java/dev/cyclone/cloak/ui/ReadinessDoctorFactsTest.kt`: new adapter, readability, conflict and privacy cases.
- `android/app/src/test/java/dev/cyclone/cloak/ui/HealthDoctorCardScreenTest.kt`: updated v3 labels/check counts and repeated-context selection.
- `android/app/src/test/java/dev/cyclone/cloak/ui/ScreensTest.kt`: v3 title assertion.
- `tools/run-local-profile-tests.ps1`: include the pure diagnostics and snapshot-adapter tests in supplemental JVM verification.
- `README.md`, `docs/ARCHITECTURE.md`: current v3 documentation.
- `docs/HANDOFF-2026-10-11-READINESS-V3.md`: this report.

Existing uncommitted coherence/generation/uniqueness/catalog edits and user-owned
brief/mission/report files were retained. The file list covers this run only.

## Exact commands run

From `C:\Users\Agent\Cyclone\cloak-repo`:

```powershell
$env:JAVA_HOME='C:/Program Files/Eclipse Adoptium/jdk-17.0.20.101-hotspot'
& "$env:TEMP/cloak-build/gradle-8.11.1/bin/gradle.bat" -p android :app:assembleDebug :module:packageModule --offline --no-daemon
& ./tools/run-local-profile-tests.ps1
uv run --with pytest -- python -m pytest forge/tests -q
& "$env:TEMP/cloak-build/gradle-8.11.1/bin/gradle.bat" -p android :app:testDebugUnitTest --offline --no-daemon
git -c core.safecrlf=false diff --check
```

## Verification

- Supplemental JVM: **91 tests passed**, including all existing doctor verdict/redaction cases, six new v3 model cases and three new adapter cases. Production model and adapter source are compiled directly alongside the tests; unchanged lower layers come from the local app build. This does not replace the full Gradle/Robolectric suite.
- Python: **185 passed** (unchanged shared forge regression suite).
- App APK and module ZIP: **passed**; final build 49 seconds, 74 tasks (15 executed, 59 up-to-date). No build/compiler warnings emitted.
- Full Android unit tests: **blocked**, failed at `:app:copyRobolectricRuntime` after 29 seconds because `org.robolectric:android-all-instrumented:14-robolectric-10818077-i7` / `android-all-instrumented-14-robolectric-10818077-i7.jar` is not cached for offline mode. The preceding run established that online downloads stall in this environment; no dependencies or tests were removed/bypassed. The definition-of-done item requiring all Android tests to pass remains outstanding.
- Compose tests are updated and retained, but require the blocked Robolectric runtime before screen execution can be claimed.
- Whitespace check passed.

## Assumptions and limitations

Readiness describes the last reported root/sync checks and current local snapshot,
not proof of runtime hooks on a device. Staleness means pending health or mismatched
profile/user/app/reference records; no arbitrary expiration interval was added.
Unused unreadable/conflicting identities warn globally; enabled bindings referencing
broken identities fail. A local install cannot independently prove readability of
Main's published identity, so mirrored identities warn rather than claim success.
A library directory that cannot be listed is marked unconfirmed rather than empty.

No device or reboot tests were performed; the existing deferred-device checklist
remains applicable. Release metadata remains **0.10.0-rc.1** for app and module.
HEAD includes published tag `v0.10.0-rc.1`; no commit, push, release or tag was made.
