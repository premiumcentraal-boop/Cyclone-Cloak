# Cyclone Cloak 0.11.0-alpha.17 — diagnostics expectations

App/module packaging version code: **41**. Diagnostics-only change. No production
runtime, connector, binding storage, publication, profile schema, module behavior
or production permissions changed. Module changes are packaging metadata only.
No platform verdict is claimed.

## Changes

- Safe expectation policies for 12 previously uncovered diagnostic rows, in
  diagnostics-only fixture sidecars; existing profile bodies remain unchanged.
- Five local type/enum checks: phone type, roaming, sensor list, visible packages
  and loaded libraries. These check format/allowed values, not hook coverage.
- Six external-state rows remain observations by design: SIM state, unrelated
  settings control, Wi-Fi BSSID/link speed/SSID and Bluetooth name. Availability
  or denial has an explicit reason; insufficient evidence never becomes PASS.
- Java-bridge visibility requires paired captures with distinct capture IDs,
  matching installed-artifact digest/rule fingerprint and available value evidence.
- Redacted report handling separates external observations and pending stability;
  no policy-only check becomes VERIFIED runtime coverage.

## Physical validation (Run 3)

Pixel 8, Android user **0**, on 2026-10-17. Diagnostics commit:
`fbdc0faa5330bb0672894eb3110318307d738816`. Installed module:
**v0.11.0-alpha.16 / code 40**. Only the side-by-side diagnostics APK was installed.

| Capture | PASS | OBSERVED | FAIL | BLOCKED | MISSING |
| --- | ---: | ---: | ---: | ---: | ---: |
| Bound A | 82 | 7 | 0 | 0 | 0 |
| Bound A repeat | 82 | 7 | 0 | 0 | 0 |
| Bound B | 82 | 7 | 0 | 0 | 0 |

Paired classification: **246 PASS, 21 OBSERVED, 0 warnings, 0 blocked, 0 pending**;
zero critical failures or uncovered rows. Both stability comparisons passed
(`STABLE_MATCH`). Immutable captures retain the bridge row as OBSERVED until paired
comparison; single-capture pending state is not silently promoted. All six external
rows reported EXTERNAL_STATE_AVAILABLE and remained observations. Host-only
negative controls rejected duplicate capture IDs, mismatched/missing artifact,
mismatched rule fingerprint and missing value evidence.

Duration: **174.43 seconds** including audits. Fixture a/a/b labels and fixture,
report and temporary published profile hashes agreed. All **327 production index
entries**, 654 published body files, 1310 publisher-share files, 3 module files and
1 accessible private binding store matched before/after, including index bytes.
Other users' locked private stores are outside that file audit. Temporary probe
state was removed; permissions unchanged. No production APK/module install,
connector operation, user switch or reboot occurred.

## Evidence and provenance

The original Run 3 ZIP is checked in and attached unchanged:
`docs/runtime-evidence/2026-10-17-run3-expectations-022627/evidence-bundle.zip`.
SHA-256: `bfcd57e91a53f6452fd124cab48140ffc80c1943949939331693cfb32978da15`.
The attached `summary.md` and `build-provenance.json` also exist inside the ZIP.

Measured diagnostics APK SHA-256 (local and installed):
`03c16645995ca8fd3c8f9daa3caf0cf6aff9aa8f2618a26d82a737628ba6cbb7`.
The independent probe retains **1.0-diag/code 1**. It is not the production APK.
Historical alpha.16 evidence was not reclassified.

## Checks and limits

Local release gate: app 522, module 12, probe 79 per flavor, forge 411,
schema 33 and probe host 7 tests; debug/release assembly, module packaging and
artifact metadata/embedded ZIP checks. The existing signed-validation gate must
pass before tagging; release CI reruns checks and signs with the existing key.

This validates the new diagnostics expectation model only. The installed module
file digest is not proof of the in-process binary. Newly packaged alpha.17 app and
module binaries have no new physical verification. Permission grants were already
present, so denial was not physically re-exercised in Run 3; precise denial mapping
is covered by unit tests. Android-user transitions, unbound controls, arbitrary
apps, companion publication timing and external-service compatibility remain
outside this run. No broader device compatibility or platform verdict is claimed.

## Upgrade and assets

Update the signed companion through the normal install flow, retaining data.
No profile migration is required. Matching module metadata is alpha.17/code 41;
no module runtime change is included. The separate diagnostics build target is
`:probe:assembleDiagnosticsDebug`; diagnostics dependencies are not in the companion.

Assets: `app-release.apk`, `cyclone-cloak-0.11.0-alpha.17.zip`,
`evidence-bundle.zip`, `summary.md`, `build-provenance.json`.
