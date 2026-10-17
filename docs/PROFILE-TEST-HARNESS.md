# Local profile-transition harness

The harness collects local Android observations, not a platform verdict. It does
not alter production bindings, publisher shares, connector state, or saved
profiles. No new permissions are declared or granted.

## Run

Build the two probe variants first:

```powershell
gradle -p android :probe:assembleStandardDebug :probe:assembleDiagnosticsDebug
./tools/run-profile-check.ps1 -Scenario bound-a -Phase environment
./tools/run-profile-check.ps1 -Scenario bound-a -Phase all
./tools/run-profile-check.ps1 -Scenario full-transition -Phase all -User 17 -TargetUser 0 -FullProfile
```

Use `-Serial` with multiple attached devices, `-Apk` for a diagnostics APK,
`-TimeoutSeconds` (default 90), and `-OutputDirectory` for a dedicated output
directory. The device must already allow shell root and the selected users must
be unlocked. The harness never handles credentials or approves dialogs.

The default permission-free APK remains
`android/probe/build/outputs/apk/debug/probe-debug.apk`. The side-by-side probe is
`android/probe/build/outputs/apk/diagnostics/debug/probe-diagnostics-debug.apk`.
Only the diagnostics package is accepted. Existing denied permissions produce
BLOCKED rows; this harness does not grant them.

## Phases and evidence

| Phase | Stable output | Action |
| --- | --- | --- |
| environment | phase-01-environment.json | User, API, installed module metadata/hash, index count |
| bind | phase-02-bind.json | Select fixture or audit existing full-profile selection |
| publish | phase-03-publish.json | Audit published bodies; install temporary probe-only overlay |
| launch | phase-04-launch.json | Install diagnostics probe for selected users; start selected user |
| probes | phase-05-probes.json | Before-switch snapshot |
| switch | phase-06-switch.json | Android user activation and unlocked-state check |
| post-switch-probes | phase-07-post-switch-probes.json | After-switch and probe cold-restart snapshots |
| diff | phase-08-diff.json | Stable-ID comparisons and conservative verdict |
| crash-log | phase-09-crash.log; phase-10-logcat.log | Bounded, scoped, redacted crash/event observations |
| summary | summary.md | Compact result, counts, duration, artifact references |

Every run also writes `manifest.json` and `evidence-bundle.zip` under
`docs/runtime-evidence/<date>-<scenario>/`. The manifest records phase status,
duration, artifact hashes, selection, cleanup, and original-user restoration.
Failed full runs retain completed snapshots and write NOT_RUN artifacts for
later phases. A completed full run cannot be overwritten: choose another name.

Each phase is independently invokable with `-Phase <name>`. Dependencies must
have completed and their evidence hashes must match. Repeating a phase makes
downstream results stale. Partial runs leave an INCOMPLETE summary until the
remaining phases run; their exit status reflects the selected phase. A full run
exits unsuccessfully for FAIL or INCOMPLETE. DEGRADED is completed evidence with
warnings, never a claim of complete coverage.

Full runs always attempt temporary-overlay cleanup and restore the original
foreground user. For an interrupted or manually phased run, repeat the same
configuration with `-Phase summary -Cleanup`. A cleanup failure remains visible;
do not delete the lock file or manually remove another publisher's records.

## Scope and safety

There is no existing command endpoint for the companion's guided binding and
publication flow. Consequently `bind` and `publish` audit production state and
exercise a **temporary diagnostics-probe index overlay**, not the normal app
save/reconcile/publish path. The overlay uses the existing kernel lock inode,
raw-byte compare-and-swap, and a same-filesystem index rename. It refuses foreign
probe bindings, preserves other entries, and retries concurrent index changes.
It does not modify publication scripts or publisher shares. Full-profile audits
check every selected published body using bounded command batches on Windows.

`-FullProfile` requires one unambiguous device-profile reference for the chosen
Android user. It audits all its published packages and runs the same profile
through the dedicated probe. It does not launch or introspect each production
app. Cross-user runs compare that source profile with fixture B in the target
user. Without `-FullProfile`, fixture A is used initially. Equal source/target
users provide a same-process repeat control. Explicit cold restart stops only
the diagnostics probe, never Cyclone or the companion.

Switching uses Android `am`, so it does not verify Cyclone bootstrap
acknowledgement or carried connector approval. The installed module file hash
is checked between snapshots; it does not attest to the in-process module image.
Loaded-library and Java-bridge rows are observations only. Package visibility
is the probe caller's filtered view, not an all-installed-packages claim.

Probe rows preserve legacy IDs and include `source`, `expected`, `actual`,
`status`, `detail`, and `duration_ms`. Missing values and permission failures
remain MISSING/BLOCKED. Explicit null expectations are distinguished from absent
expectations. Before collection, request ID, profile digest, package, process UID,
and foreground user prevent stale or wrong-user reports being accepted.

Host evidence hashes both actual and expected row values and omits their raw
values. No profile bodies or seeds are exported. The diagnostics probe temporarily
holds its expected profile and report in its private files directory; these are
test inputs, separate from companion storage. Output directories ignore their
contents in Git to avoid accidentally publishing device evidence. Hashes,
package lists, serials, and profile IDs are still local operational metadata;
review bundles before sharing.

Diffs distinguish expected changes observed, expected changes missing,
unexpected changes, unmapped changes, and blocked/missing rows. Counts in the
summary are distinct row IDs across snapshots, while raw status totals count
all observations. Unmapped, blocked, missing, and OBSERVED rows cannot silently
promote a run to PASS.

## Observational and permission-limited rows

The four unmapped changes in the saved user-17 → user-0 evidence already have
stable IDs. None needs renaming. No missing profile value can be recovered from
a redacted observation hash; learning an expectation from the observed device
would make the comparison circular.

| Stable row ID | Source API | Expected behavior | Why blocked or observational |
| --- | --- | --- | --- |
| build.BOOTLOADER | Build.BOOTLOADER | Compare device.bootloader when explicitly supplied | Source profile omitted it; no safe inferred expectation |
| build.getRadioVersion | Build.getRadioVersion() | Compare device.baseband when explicitly supplied | Source profile omitted it; radio availability varies |
| packages.visible | PackageManager.getInstalledPackages() | Record sorted caller-visible packages | User installs and Android visibility filtering vary; no exhaustive library expectation |
| settings.unrelatedControl | Settings.Secure.getString("accessibility_enabled") | Observe an unrelated provider key | User/device-dependent control; outside the selected profile contract |
| wifi.serviceMac | WifiManager / WifiInfo | Compare identifiers.mac | User 17 lacked fine-location access; no grant attempted |
| wifi.bssid | WifiInfo.getBSSID() | Observation only; access point dependent | Fine-location access denied in user 17 |
| wifi.ssid | WifiInfo.getSSID() | Observation only; network dependent | Fine-location access denied in user 17; otherwise observational here |
| bluetooth.name | BluetoothAdapter.getName() | Observation only; no profile-backed name expectation | BLUETOOTH_CONNECT denied in user 17 |
| advertising.id | Optional AdvertisingIdClient SDK | Compare identifiers.advertising_id when API exists | Optional SDK absent in this probe build in both users |

The before-switch bootloader and radio rows remain OBSERVED even though fixture
B supplies expectations after the switch. A one-sided expectation does not
establish an expected transition. Unmapped changes remain warnings. Blocked rows
are diagnostics limitations, not compatibility verdicts. Production permissions
and both probe manifests are unchanged by this follow-up.

## Real companion flow observer: deferred

The harness does not invoke the normal companion flow. That flow is owned by
the live ViewModel and requires a current reviewed selection plus acknowledgement
before `confirmBindingFlow()`. Retry uses retained state through
`retryBindingFlow()` / `retryRun()`. There is no existing non-UI invocation
endpoint. Adding an exported debug receiver or bypassing review to synthesize a
draft would introduce a new mutation surface, rather than neutral observation.

The existing `launchBindingWork` already captures a bounded RunEventCapture,
monotonic run duration, root/publication transitions, reconciliation summary,
interruption checkpoints, and final outcome in the existing run journal and
per-run debug bundle. These records remain usable, but are not proof of a new
debug-only observer or complete per-phase timings. A future debug instrumentation
test should attach to the live ViewModel, invoke the same reviewed public flow,
and correlate its existing run ID with diagnostic phase start/end events. It
must use explicitly selected test targets and test retry/interruption without
silently creating or replacing production bindings. This endpoint is explicitly
deferred in this release; no production flow or API is changed to simulate it.

## Repeated diagnostics reversibility cycles

`tools/run_probe_reversibility.py --cycles N` repeats the complete diagnostics
overlay lifecycle. The default is `--cycles 1`: existing capture names and the
root-level manifest, snapshots and summary remain unchanged. With multiple
cycles, each independent run writes into `cycle-001/`, `cycle-002/`, etc.
Every cycle captures users 0 and 17 in this order:

1. Unbound cold baselines for both users.
2. Temporary fixture A/user 0 and fixture B/user 17 binding, then cold captures.
3. Overlay removal, then warm captures of both still-running processes.
4. Cold post-removal captures compared with each user's own baseline.
5. Private-file and scratch cleanup, foreground-user restoration, production
   audit and permission/AppOps audit before the next cycle can begin.

Run from current main; keep the diagnostics APK and base harness pinned to a
clean detached `v0.11.0-alpha.18` / `fb1e3c6` checkout. For example:

```powershell
uv run -- python -X utf8 tools/run_probe_reversibility.py --cycles 3 --source-checkout "$env:TEMP/cloak-alpha18-reversibility-clean-1792277762" --adb C:/Users/Agent/android-sdk/platform-tools/adb.exe --serial 3B171FDJH0061G --secondary 17 --output "docs/runtime-evidence/<new-timestamp>-alpha18-lifecycle-3cycles"
```

Use a new output directory each time; existing directories are rejected. Both
users must already be running and unlocked. Before each cycle the runner reads
the diagnostics package path and hashes its APK for each user. Matching installed
APKs are reused without an install. Any different hash fails immediately with
`INSTALLED_APK_HASH_MISMATCH`; it is never replaced as a workaround. If the APK
is missing, a single diagnostics-only install is allowed for the missing user,
followed by verification of both users. Installation is attempted at most once
for the entire invocation, including when the 120-second install timeout expires.
If another user still lacks the APK after that attempt, the run fails rather
than provisioning users through repeated installs. No install retries are added.
No production APK/module installation,
permission reset, grant, AppOps reset, new user, unlock or reboot is performed.

Each cycle preserves all eight captures, restoration comparisons, before/after
permissions and production audits, cleanup proof, APK provenance and its original
evidence ZIP. The root `aggregate-summary.json` and `aggregate-summary.md` report
cycle pass/fail, restoration per user, cleanup, actual snapshot deltas and policy
results. `lifecycle-evidence-bundle.zip` contains the entire run and runner source.
`runner-provenance.json` records the base commit and exact script SHA-256; when
validated before committing, the base commit is the parent of the runner change.
The lifecycle summary records `apk_install_policy` as
`reuse_matching_install_missing_once`, `installation_skipped`, and
`install_attempts`. Each cycle's `apk_install` metadata contains the per-user
package paths, observed SHA-256 values, match/missing/mismatch states and whether
that cycle attempted or skipped installation. Missing successful hash evidence
cannot promote a cycle to PASS.

The first failure stops all subsequent cycles after the failed cycle's normal
cleanup path. Aggregate checks also reject missing evidence, missing cleanup,
incomplete captures and changes between cycle boundaries, even if an individual
manifest says success. No permission delta is normalized away in the summary.
Identical permission/AppOps snapshots pass. Only new diagnostics read records
`READ_DEVICE_IDENTIFIERS=deny` and
`USE_ICC_AUTH_WITH_DEVICE_IDENTIFIER=default` are permitted by the existing
classifier; removals, mode changes, unrelated additions, malformed entries and
duplicates still fail. All production audits must match the first baseline.

Raw unbound fixture FAIL rows are expected synthetic-fixture mismatches, not
restoration failures. Cold restoration uses paired actual-value hashes. Blocked,
unavailable and external-state rows remain limitations; warm retained values are
observations. This tests diagnostics reversibility rather than normal companion
binding/reconciliation, Cyclone bootstrap, or any platform verdict. AppOps audits
exclude volatile access timestamps/counters; only accessible private stores and
on-disk module files can be audited.
