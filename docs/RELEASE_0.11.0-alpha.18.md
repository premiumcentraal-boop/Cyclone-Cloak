# Cyclone Cloak 0.11.0-alpha.18 — diagnostics capture sessions

App/module packaging version code: **42**. Diagnostics-only fix; production
runtime, connector behavior, binding storage, publication, profile schemas,
module behavior and permissions are unchanged. No platform verdict is claimed.

## Fix

- Fixed overlapping diagnostics capture sessions: each session owns its request
  metadata, profile, expectations and rows. New intents cannot reset an active
  reader's data or change its request ID.
- Superseded workers cannot write reports or update UI. Only the current
  generation may perform the final atomic report write.
- Successful reports require exactly 89 known, non-empty, unique row IDs and
  complete rows. Malformed captures produce structured failures.
- The runner preserves a bounded redacted failure record before cleanup, with
  reason codes, known row IDs, hashes, request metadata and error type only.

## Physical evidence

Validated the five-phase user 0 ↔ user 17 transition on Pixel 8, 2026-10-17.
Diagnostics commit: `8e7ab1407c5ff18a2a50aa6e727b34c52739396c`.
Installed companion/module: **alpha.17 / code 41**. Only diagnostics was installed.

| Capture | User / fixture | PASS | OBSERVED | BLOCKED | FAIL | MISSING |
| --- | --- | ---: | ---: | ---: | ---: | ---: |
| Initial | 0 / A | 82 | 7 | 0 | 0 | 0 |
| Secondary warm | 17 / B | 81 | 4 | 4 | 0 | 0 |
| Secondary cold | 17 / B | 81 | 4 | 4 | 0 | 0 |
| Return warm | 0 / A | 82 | 7 | 0 | 0 | 0 |
| Return cold | 0 / A | 82 | 7 | 0 | 0 | 0 |

Duration: **37.2 seconds**. Fixture A and B always matched the correct Android
user; report profile hashes and temporary published-body hashes agreed in all
captures and retained primers. Warm captures proved process reuse; cold captures
proved process replacement. No duplicate or incomplete reports occurred,
including the previously failing owner-return warm phase.

Aggregate: **408 PASS, 29 OBSERVED, 8 BLOCKED occurrences, zero FAIL/MISSING**.
Paired classification remains DEGRADED with four warnings, four distinct blocked
rows and zero pending stability checks. Four user-17 rows remain blocked by the
expected existing permission state: `wifi.bssid` and `wifi.ssid` require fine
location; `bluetooth.address` and `bluetooth.name` require Bluetooth connect.
External-state observations remain observations, never silently promoted.

Production state and permissions were unchanged: 327 index entries, 654 published
files, 1310 publisher-share files, 3 module files and 2 accessible binding stores
matched before/after, including index bytes. Permission grants/flags and app-op
modes matched. Temporary probe bindings, published bodies and private files were
removed. No reboot, user creation/removal, production installation or permission
change occurred. Other locked users' private stores remain outside the audit.

## Evidence and provenance

Source: `docs/runtime-evidence/2026-10-17-capture-lifecycle-rerun-031422/`.
The unchanged evidence ZIP, summary and build provenance are attached.

Evidence ZIP SHA-256:
`e0bf5b6bd0d6eedccf60c4fbe5fd69a7e39663b5021e768ad178028870969ce2`.

Measured diagnostics APK SHA-256, matched to the installed APK for both users:
`e4b5ebf6a6b1a44ea9eb882b8935ab417088a8cec68d1f2651b222b2184071bd`.
The probe retains its independent version 1.0-diag/code 1. Evidence predates
alpha.18 packaging and does not physically verify the newly packaged binaries.
Installed module file hashes do not prove in-process module identity. This run
does not establish arbitrary-app compatibility or a platform verdict.

## Release checks and upgrade

Local gate: app 522, module 12, probe 91 per flavor, forge 438, schema 33 and
probe host 7 tests, plus debug/release/probe builds and artifact verification.
The existing signed-validation gate must pass before tagging. Release CI reruns
the gate and signs with the existing release key; no signer changes.

Update through the normal signed companion install flow, retaining data.
No migration is required. Matching module alpha.18/code 42 contains packaging
metadata changes only. No new device run is part of this release operation.

Assets: `app-release.apk`, `cyclone-cloak-0.11.0-alpha.18.zip`,
`evidence-bundle.zip`, `summary.md`, `build-provenance.json`.
