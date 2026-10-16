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
