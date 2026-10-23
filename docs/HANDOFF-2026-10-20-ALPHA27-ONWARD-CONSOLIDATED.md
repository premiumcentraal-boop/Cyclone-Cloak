# Cyclone Cloak — alpha.27 and all subsequent repair work

Prepared: 2026-10-20. Scope: the first published alpha.27, its device setup and
Play evidence tests, subsequent runner/connector/Health repairs, the Cyclone
capacity dependency, installed signed candidates, evidence and unfinished work.
This is a consolidated handoff, not a new release or a new device test.

## Current position

- Cloak repository: `C:/Users/Agent/Cyclone/cloak-repo`, branch `main`, recorded
  HEAD `cb60012a121743c8374dda5cf164349dfe3b0b28` before this document.
- Published prerelease remains **v0.11.0-alpha.27 / code 51**, release commit
  `79c2f86dba420cf46233a48d92889f6d6e8babfb`.
- Later fixes are committed/pushed, signed and installed as **test candidates**.
  They retain alpha.27/code 51; version text alone cannot identify the binary.
- Final installed Cloak candidate source:
  `c6371704791166376e0c48e6fa19b402597c3f2d`.
- Separate Cyclone mobile candidate source:
  `7e001f4ee80dbc329464b04b8a730df5c4762793`, branch
  `codex/connector-capacity-reliability`, worktree
  `C:/Users/Agent/Cyclone-connector-reliability`.
  Installed version: **5.0.0-alpha.144.dev2 / code 299**. It is not a published
  Cyclone release and is not merged into its development release branch.
- Device validation is **PARTIAL**. ADB disconnected at approximately 12:42
  Europe/Berlin on October 20; subsequent checks still found no device.
  Final profile-17 checks and the final after-audit remain unfinished.
- No later Cloak release/tag, module update, reboot, data clearing, permission
  reset, account write or new signing identity was performed in the repair run.

## 1. What alpha.27 delivered

[Published release](https://github.com/premiumcentraal-boop/Cyclone-Cloak/releases/tag/v0.11.0-alpha.27).

The release consolidated the metadata-only Google surface isolation layer,
conservative readiness evaluator/store, separate Play diagnostics APK and first
production readiness import/review UI. Profiles shows source/time, service and
account observations, stale/mismatch reasons, evidence identity selection and
explicit shared-account-slot confirmation.

Import validates the complete strict envelope and its readiness projection,
exact saved profile bytes, Android user, current app/bundled-module artifact
digest and freshness. Malformed, foreign, incomplete or stale reports cannot
replace accepted evidence. Missing required observations stay UNKNOWN.
Existing profile schema 0.2/0.3/0.4 files remain compatible.

The separate `dev.cyclone.cloak.playevidence` APK declares **zero permissions**.
Collection requires an explicit tap. Optional public Integrity requests require
separate confirmation; no raw token or fabricated decoded verdict is retained.
No Integrity request was made during the device runs described here.

Private stores retain existing `google-surface-isolation` and
`play-readiness-evaluation` formats. Assignment and readiness writes are each
atomic, but not a combined transaction; partial-save errors are explicit.
No production module/runtime behavior, publication scripts, connector protocol,
binding storage layout or production permissions changed in this release.

Implementation lineage included in alpha.27:

| Commit | Purpose |
| --- | --- |
| `287f8c1` | Google surface isolation/state |
| `fa7a2fd` | Evidence-bound readiness evaluation/storage |
| `947693d` | Separate user-initiated Play evidence harness |
| `c362b81` | Production import and readiness/sharing review UI |
| `28bf118` | Alpha.27 release metadata and notes |
| `79c2f86` | Add missing CI JSON-schema dependency; release tag target |
| `a51c6d1` | Published artifact verification receipts |

Initial release gate: **2,015 tests passed**. Legacy probe lint was not green:
9 errors / 12 warnings per flavor. The owner explicitly accepted these and
absent physical evidence for that prerelease, conditional on signed CI passing.
The later repair fixed those lint errors; do not retroactively describe the
original release gate as fully green.

Release CI passed:
[normal CI](https://github.com/premiumcentraal-boop/Cyclone-Cloak/actions/runs/39254225924),
[signed validation](https://github.com/premiumcentraal-boop/Cyclone-Cloak/actions/runs/39254226690),
[release](https://github.com/premiumcentraal-boop/Cyclone-Cloak/actions/runs/39255232998).

| Published asset | Bytes | SHA-256 |
| --- | ---: | --- |
| `app-release.apk` | 43,986,455 | `2fd83e2cbaf031ea563990e8a6e54154067351f51f074d25d9f4592c013d282e` |
| `cyclone-cloak-0.11.0-alpha.27.zip` | 1,683,427 | `fc10038d69a1c4d23e7bc1ee1c9f3b47fc3a5c497c58804814840bd21a0a2b59` |
| `playEvidence-release.apk` | 3,437,173 | `5e58b57e7d394ecccb4d5bb589df3e8130022df5b5abd10537dabbfcd0c08868` |

All 11 published assets matched GitHub sizes/digests. Both APK signatures passed
with existing certificate SHA-256
`17ca3617b0cbaf250d2ef03e945b319e57695de6d977c652ded243ad5656f553`.
The companion embeds the exact published module ZIP; app/module code is 51.
The separate diagnostics app uses `1.0-diagnostics` / code 1.
Full receipts: [published verification](RELEASE-VERIFICATION-ALPHA27.md).

## 2. First physical Play/Google setup and import

The device was made available, user 17 was opened/unlocked normally and the
published diagnostics APK was installed side by side. The authorized operator
performed the Collect local evidence tap. The selected saved identity was
`192f47b0-2c97-5d5e-8e59-461e9dc53abb`; Cyclone profile
`Cyclone_2ca4cf9432e64fbd` maps to Android user 17.

Evidence: `docs/runtime-evidence/2026-10-19-233634-alpha27-play-readiness/`.
The folder timestamp uses the capture's naming convention; its summary uses
October 20 Europe/Berlin. Capture timestamps themselves use UTC.

All 16 rows completed: **3 PASS / 8 OBSERVED / 2 MISSING / 3 NOT_RUN /
0 FAIL / 0 BLOCKED**. Play Services availability passed. Play Store and current
Google WebView were visible/enabled. Alternate `com.android.webview` was
absent/not visible and the Google settings action did not resolve.

Valid import previewed and explicitly saved **UNKNOWN**. Production UI rejected
four negative cases: wrong profile hash, wrong artifact hash, stale timestamp,
and incomplete rows. Saved readiness bytes remained unchanged after rejection.
Production binding/profile/publication/module hashes and permission/AppOps
snapshots matched before/after; only intended local readiness evidence changed.

Account association is not visible to the zero-permission caller; this does not
prove account absence. Play Protect and actual Google settings access are not
measured. No decoded Integrity verdict exists. UNKNOWN is the correct result,
not a failure of import and not evidence of platform certification.

Initial staging required manual repair because the stock runner assumed run-as
access to a non-debuggable signed APK. That led to the dedicated runner fix.

## 3. Play runner transport repair and successful automatic rerun

Commit **`2eb52a2`** fixed diagnostics-only transport:

- Explicit opt-in `--private-access root`; no silent privileged fallback.
- Verify the diagnostics UID and restrict staging/report commands.
- Correct per-user SELinux categories using forced recursive relabeling scoped
  to diagnostics inputs; chown/non-recursive relabeling alone was insufficient.
- Checked remote exit markers; non-PTY shell-v2 EOF forwarding for exact writes.
- Fresh-intent flags preserve new request extras on reused activities.
- `--saved-identity` reads only the selected profile into RAM rather than
  exporting its private body to the PC.

Changed: `tools/run_play_evidence.py`,
`forge/tests/test_play_evidence_harness.py`, harness documentation and handoff.
Seven regression tests were added. Gate: **2,022 passed**, debug build passed.

Earlier failed/manual-repair attempts remain separate:
`2026-10-20-000008-alpha27-play-runner-fix`,
`2026-10-20-000207-alpha27-play-runner-fixed`, and
`2026-10-20-000610-alpha27-play-runner-verified` under runtime-evidence.
Only the final `000729` run proves automatic staging/launch without manual repair.

Final evidence: `docs/runtime-evidence/2026-10-20-000729-alpha27-play-runner-verified/`.
Bundle SHA-256:
`f19d0ef340ae2c5f17aaee33b09c64c8f4aefce05439032c9266b2326a0d2f04`.
Counts remained **3 PASS / 8 OBSERVED / 2 MISSING / 3 NOT_RUN / 0 FAIL /
0 BLOCKED**. Import/review/save succeeded and remained UNKNOWN.
Request cleanup and production/permission audits passed.
The valid redacted import remains in user 17 Downloads.

Selected profile hash:
`945be161ca131ba888c649059aac897986911861627efb83219a408ad376219d`.
Measured original-release artifact-set digest:
`7e8803535fcd011750a5c07c283bc83e10ed335860965ddecbb05883e88d2a62`.
This evidence does **not** bind to the later installed repair candidate.

These older Play evidence directories remain local/untracked; they were not
silently included in subsequent evidence-only commits. Preserve them explicitly
when transferring the handoff to another machine.

## 4. Connector, capacity and Health repair

The Play tests exposed a separate connection problem: 327 enabled bindings,
126 remote config records in Main and a saturated 256 in user 17. Installed
Cyclone/source imposed a 256-record quota per connector installation. Cloak
also withheld its Connected state until lengthy reconciliation completed.

This establishes capacity pressure, not the cause of every historical BAD_REQUEST
or Magisk timeout. Existing permanent grants were present; old timeout labels
did not prove a new denial.

Cloak commit **`3781806`**:

- Publishes a live approved handshake/registry immediately, with reconciliation
  still running separately. Cached state cannot establish a live connection.
- Keeps single-flight reconnect and Connected during a coalesced follow-up;
  a later real failure remains visible.
- Replaces misleading BAD_REQUEST-as-missing-app wording with honest rejection
  reasons while retaining local records.
- Moves Check & repair near the top of Health and paginates checks by 20.
  Overall verdict/counts and copied diagnostics still include all checks.
- Adds narrow intentional permission-analysis suppressions and API-30 guards
  for the diagnostic probe's API-29 minimum, without adding permissions.

Primary files: `ConnectionLifecycle.kt`, `CycloneBridge.kt`, `SyncEngine.kt`,
`ReconnectCoordinator.kt`, `Reconcile.kt` in the app cyclone package;
`HealthScreen.kt`, `HealthDoctorCard.kt`, `HealthDiagnostics.kt`,
`ReadinessDoctorFacts.kt`; probe `ProbeActivity.java`, `RealityProbe.java`;
coordinator/sync/reconciliation/Health regression tests.

Separate Cyclone commit **`7e001f4e`** raises the bounded quota to **8,192**,
preserving existing files, validation, namespace isolation and wire errors.
Files: `ProfileConfigCapacity.kt`, `ProfileConfigStore.kt`,
`ProfileConfigCapacityTest.kt`; Windows CRLF normalization in `CyTest.kt`;
connector SDK spec, candidate version metadata and owner-test notes.

Cloak follow-up **`c637170`** scopes first-run discovery cleanup to profiles
represented by local bindings, including disabled bindings. Previously tracked
tuple cleanup remains. Unknown unrelated records are neither scanned nor
deleted. Tests cover unrelated profiles, an empty library, disabled cleanup and
existing same-profile stale cleanup.

## 5. Signed candidates actually installed

| Candidate | Source | Bytes | APK SHA-256 |
| --- | --- | ---: | --- |
| First Cloak repair | `3781806` | 44,002,843 | `3994c7b37a68db3603e327032523fe6a681d27d9effb9326d66d31fdc75b0afa` |
| Final Cloak repair | `c637170` | 44,002,839 | `2bb80cf73a1e33e7d262a9bab63998f62a3bb847c80da64e5a0875cc23e6932c` |
| Cyclone owner test | `7e001f4e` | 66,441,537 | `1c0ed0e107a5351a3a6ecc187aab9aabedb4bff0f969f1bcc1fc936a06ba94a4` |

Both updates used established signing lanes and preserved app data. Installed
hashes matched on users 0 and 17. Cloak remains code 51; Cyclone candidate code
299. No module ZIP was installed during this repair run.

Final Cloak bundled ZIP SHA-256:
`79220b5418ec9b98392174c769312a5502d215d52c697107e5a6dbea9bec63a6`.
The embedded ZIP matched its signed CI artifact. This is not a claim that the
installed root module was replaced by that bundled ZIP.

Signed CI:
[first Cloak candidate](https://github.com/premiumcentraal-boop/Cyclone-Cloak/actions/runs/39352373058),
[final Cloak candidate](https://github.com/premiumcentraal-boop/Cyclone-Cloak/actions/runs/39406349015),
[Cyclone owner test](https://github.com/premiumcentraal-boop/Cyclone/actions/runs/39352440392).
All passed; none published a new release. Cyclone certificate SHA-256:
`e78c6e0b32d66da05839243c65e9987ba54722d402543f00408b57b1585dbf60`.

## 6. Device observations and preservation boundary

Serial `3B171FDJH0061G`; actual hardware Pixel 8. The profile's Pixel 7 Pro label
is not hardware identification. Existing users 0 and 17 were used normally.

- Normal Health repair completed publication on both copies during candidate
  validation. On final Cloak, user 0 connected immediately on launch.
- Home's root attention item correctly navigated to the visible Health control.
- Final user-0 publication: **COMPLETED**, all phases completed, **2,143 ms**.
- Final user-0 Health: **Ready — 1,644 pass, 0 warning, 0 fail**.
- Both connector stores reached **327 records / 327 ready**, with no mismatch
  against their current local assignments.
- Main publisher share was unchanged; all 327 original Main entries remained
  in the merged index, none changed or missing.
- Latest completed intermediate audit preserved 3 module files, 2 saved
  identities, 654 effective published body files and 655 Main publisher-share
  files, plus permissions and AppOps modes.
- In-app diagnostics export succeeded on the first repair candidate: 256
  redacted events, zero recorded failures and zero persistence failures.

**Important normal-flow binding change:** user 17 originally selected identity
`192f47b0-2c97-5d5e-8e59-461e9dc53abb`; Main already published
`e8d45c1b-a4ff-5e8e-904f-f27d9861f0ca` for the same 327 targets.
Normal existing Main-wins mirroring changed all 327 local user-17 assignments
to Main's identity. The original saved identity remains intact. Effective
published bodies already used Main and did not change. Local binding files and
publisher 17's redundant share are **not** claimed byte-identical. No new
binding policy was introduced by these repairs.

Android killed background Cyclone processes during user switches and APK
updates; transient DeadObjectException was recorded. These are not classified
as native crashes. Full 327-record consistency passes still take roughly
**3–4 minutes**, and startup/repair can queue several serialized passes.
Connected is now visible during work; full-library latency is not solved.

The phone disappeared from ADB during final settling. Last active user: 0;
user 17 had been RUNNING_UNLOCKED. No reset/reboot was attempted in response.
The intermediate audit is **not** the missing final after-audit.

## 7. Evidence and repository state

Committed partial evidence:
`docs/runtime-evidence/2026-10-20-011343-connection-reliability/`.
Bundle: [evidence-bundle.zip](runtime-evidence/2026-10-20-011343-connection-reliability/evidence-bundle.zip).
Size **767,892 bytes**, SHA-256:
`ecc3cc0c7b1b55ee1f783d012eee1120c9949ac92a1c10130f89465f1329ef45`.
Manifest result is `PARTIAL_DEVICE_DISCONNECTED`, not PASS.

It retains provenance, installed hashes, binding/config state snapshots,
production and permission audits, publication events, diagnostics export,
test output, Main-entry proof, UI observations and disconnect boundary.
Evidence directory `.gitattributes` preserves captured bytes/line endings.
Raw build stdout is preserved inside `gradle-followup.log.zip`; do not normalize
its bytes or overwrite the partial bundle when resuming.

Post-feature documentation commits:
`643679e` preserves the partial evidence; `cb60012` preserves byte-level manifest
integrity and archives raw build output. An initial raw-log whitespace issue was
corrected by the latter; final whitespace checks passed.

Older untracked handoffs/evidence remain untouched. Do not stage them wholesale.
Some top-level release prose still says physical Play import is UNVERIFIED;
that describes the release-time state. Later evidence establishes the limited
capture/import result above, not broader compatibility or final candidate proof.

## 8. Tests and exact repeatable local commands

Counts below are successive overlapping gates; **do not add them together**.

| Gate | Passed | Notes |
| --- | ---: | --- |
| Published alpha.27 baseline | 2,015 | Legacy probe lint errors disclosed |
| Play runner repair | 2,022 | Seven added Python regressions |
| Final Cloak repair | 2,033 | Full local gate green; lint zero errors |
| Separate Cyclone mobile candidate | 2,909 | Separate repository/suite |

Final Cloak breakdown: app 703; module 40; Play diagnostics 20; standard probe
109; diagnostics probe 109; forge/schema 1,045; host probe 7.
Debug assembly passed. No new Cloak compiler warnings. Existing lint warnings:
app 19 and each legacy probe flavor 12. Cyclone debug build/metadata guards
passed with existing Kotlin deprecation warnings. Its first attempt lacked
ANDROID_HOME; the corrected full rerun passed.

Executed Cloak final gate, from the Cloak repository:

```powershell
$env:JAVA_HOME='C:/Program Files/Eclipse Adoptium/jdk-17.0.20.101-hotspot'
$env:ANDROID_HOME='C:/Users/Agent/android-sdk'
$env:CLOAK_TEST_WSL='1'
& "$env:TEMP/cloak-build/gradle-8.11.1/bin/gradle.bat" -p android :app:testDebugUnitTest :module:testDebugUnitTest :playEvidence:testDebugUnitTest :probe:testStandardDebugUnitTest :probe:testDiagnosticsDebugUnitTest :app:assembleDebug :app:lintDebug :playEvidence:lintDebug :probe:lintStandardDebug :probe:lintDiagnosticsDebug --offline --no-daemon --console=plain
uv run --offline --with pytest --with jsonschema -- python -X utf8 -m pytest forge/tests forge/schema_tests -q
uv run --offline -- python -X utf8 -m unittest discover -s android/probe/tests -v
git diff --check
git diff --cached --check
```

Full earlier release/integration commands:
[integration handoff](HANDOFF-2026-10-19-PLAY-READINESS-INTEGRATION-V1.md),
[release receipts](RELEASE-VERIFICATION-ALPHA27.md).
Runner reproduction command and detailed diagnostics limits:
[Play runner handoff](HANDOFF-2026-10-20-PLAY-RUNNER-FIX.md).
Signed build/run provenance and raw logs are retained in the partial evidence.
This handoff-writing run did not rerun builds or tests or operate the device.

## 9. Remaining work, in order

1. Reconnect the same device and verify its actual ADB/user state. Do not infer
   connectivity or unlock from a prior message. Do not reinstall if APK hashes
   still match the candidates above.
2. Create a fresh continuation evidence directory referencing the original
   baseline; preserve the committed partial ZIP and all prior failed attempts.
3. Finish the final user-0 settle observation without repeatedly queuing repair.
4. Switch normally to existing user 17, leave it active during checks, verify
   early connection and run normal Health repair once on the final candidate.
   Preserve genuine mirrored-identity warnings; do not rewrite identities merely
   to turn warnings into passes.
5. If finishing Play workflow validation, collect/import fresh evidence bound to
   the final candidate. The successful original-release report is not current
   artifact evidence. Use the independent evidence identity selection; do not
   change bindings to make an import match. Do not request optional Integrity.
6. Capture the final production/module/profile/publisher/permission/AppOps audits
   against the original baseline, acknowledging the normal mirroring deltas.
   Export current diagnostics and leave user 17 active.
7. Commit only intended continuation evidence/docs. A future release needs its
   own explicit version/release gate; none was performed after alpha.27 here.
8. Separate follow-up: reduce redundant full-library passes/remote reads without
   losing required event replay or single-flight behavior. No scheduling change
   is claimed by this work.
9. Required unmeasured Play surfaces and real decoded/authenticated Integrity
   evidence remain separate future work. Package visibility and local coherence
   cannot substitute for those observations.

No full reliability guarantee, platform verdict, certification, permanent trust
or broader device compatibility is established. The confirmed claim is bounded
local evidence and the measured candidate behavior recorded above.
