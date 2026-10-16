# Cyclone Cloak 0.11.0-alpha.16 — diagnostics report quality

App and module packaging version code: **40**. Diagnostics-only changes;
production runtime, connector, binding, publication, storage schemas and module
behavior are unchanged. The module change is version metadata only. No new
production permissions or dependencies. No platform verdict is claimed.

## Changes

- Package the real Play Services advertising-identifier SDK in the independent
  diagnostics probe only, with its diagnostics-only AD_ID declaration. The
  requested location, Wi-Fi and Bluetooth permissions already existed in that
  flavor. The standard probe remains permission-free and retains its APK path.
- Use precise, API-aware permission checks and fixed, redacted failure reasons.
- Carry fixture labels through launch, validate complete fixture bodies, and
  require report labels and profile hashes to agree. External profiles no longer
  silently receive the fixture-a label.
- Separate OBSERVED/no-expectation rows into `not_covered_rows`. Such observations
  and unmapped changes with no expectation on either side are informational;
  missing/blocked reads, mismatches and one-sided expectations remain actionable.
  No observation is promoted to VERIFIED.
- Add focused probe/harness tests. Signed validation and release CI also run
  probe Java tests and existing probe host checks before packaging.

## Exactly what was measured

Evidence: `docs/runtime-evidence/2026-10-16-diagnostics-quality-233115/`.
The original redacted `evidence-bundle.zip` is attached unchanged, SHA-256:
`d80f3be6c014e611a266bd44f3a96751f86ad2295f3f83928ad356649a52138b`.
Read the attached `PROVENANCE-NOTE.md` with it.

On 2026-10-16, Pixel 8, Android user **0**, the locally built updated diagnostics
APK installed and ran against the installed **alpha.15/code 39** companion/module.
The installed probe APK hash matched the local APK containing the real SDK.
Fixture A, A repeat and B were collected after probe-process cold starts:

| Capture | Total rows | PASS | OBSERVED / not covered | FAIL | BLOCKED | MISSING | Warnings |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| A | 89 | 77 | 12 | 0 | 0 | 0 | 0 |
| A repeat | 89 | 77 | 12 | 0 | 0 | 0 | 0 |
| B | 89 | 77 | 12 | 0 | 0 | 0 | 0 |

Aggregate: **231 PASS, 36 OBSERVED**, zero failures, warnings, blocked or missing
rows; **12 distinct uncovered IDs**. Duration **155.35 seconds**, including
before/after preservation audits. Overall local summary: PASS.

- Advertising ID, Wi-Fi service MAC and Bluetooth address: PASS/MATCH each time.
- SSID, BSSID, link speed and Bluetooth name: readable, OBSERVED with
  NO_PROFILE_EXPECTATION; comparison PASS is not claimed for these rows.
- Labels a/a/b matched report and temporary bound-profile SHA-256 in every report.
- All **327 production index entries**, both discovered binding-store files,
  publisher shares, published profile bodies and module files matched before and
  after; the index also matched byte-for-byte. Temporary probe state was removed.
- No production app/module install, permission grant/revocation, user switch,
  reboot, connector operation or production binding-store write occurred.

## Open limits

This is evidence for the recorded probe APK and installed alpha.15 module file,
not physical verification of newly packaged alpha.16 binaries. The module digest
is an installed-file hash, not proof of the in-process binary. No new device run
is performed for release packaging. Denial handling was not exercised on-device;
the permission map in the manifest is unscoped across Android users and must not
be interpreted as a user-0 grant record. The focused rows were actually readable.

Optional rows still need profile-backed expectations. This run covers bound
A/repeat/B in user 0 only: no unbound-control proof, multi-user transition proof,
normal companion publication timing proof, arbitrary-app coverage or external
service compatibility is claimed. Prior historical evidence remains separate.
The real SDK can still return a blocked/API error on other environments.

## Checks and update

Local gate: **522 app, 12 module, 15 probe tests per flavor, 293 forge, 33 schema,
7 probe host tests**, plus debug/release packaging and artifact metadata checks.
Protected signing uses the existing release key; signed validation must pass
before the tag, and release CI verifies the embedded module exact match.

Install the signed companion APK through the normal update flow, retaining data.
Matching module metadata is alpha.16/code 40; no module runtime change is included.
The diagnostic SDK is not embedded in the companion: build the separate
`:probe:assembleDiagnosticsDebug` target to reproduce probe measurements.

Assets: `app-release.apk`, `cyclone-cloak-0.11.0-alpha.16.zip`,
`evidence-bundle.zip`, `PROVENANCE-NOTE.md`.
