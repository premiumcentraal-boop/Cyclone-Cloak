# Updated diagnostics probe verification — 2026-10-16

Pixel 8, Android user 0. Companion and installed module: alpha.15 / code 39.
Only the side-by-side diagnostics APK was updated. No permission grants/revocations, Android-user switches, production installs, reboot, connector operations or production binding-store writes.

| Capture | Total | PASS | FAIL | BLOCKED | MISSING | OBSERVED / not covered | Warnings |
|---|---:|---:|---:|---:|---:|---:|---:|
| Fixture A | 89 | 77 | 0 | 0 | 0 | 12 | 0 |
| Fixture A repeat | 89 | 77 | 0 | 0 | 0 | 12 | 0 |
| Fixture B | 89 | 77 | 0 | 0 | 0 | 12 | 0 |

Aggregate: 267 row observations; 231 PASS, 36 OBSERVED; zero failures, warnings, blocked or missing rows. There are 12 distinct uncovered row IDs. Overall local summary: PASS. Duration: 155.35 seconds, including before/after integrity audits.

Focused rows in every capture:
- wifi.serviceMac: PASS / MATCH.
- bluetooth.address: PASS / MATCH.
- advertising.id: PASS / MATCH using the real SDK packaged in the measured APK.
- wifi.ssid, wifi.bssid, wifi.linkSpeed and bluetooth.name: OBSERVED / NO_PROFILE_EXPECTATION. Values were readable; no comparison expectation exists, so PASS is not claimed.

Every report label (a, a, b), report profile SHA-256 and corresponding temporary published profile file SHA-256 agree.

Production entries: 327 before and after. All 2 discovered production binding-store file hashes match. Module files, publisher shares, production profile bodies and semantic index match. Temporary probe binding and files removed.
Index raw bytes identical: True. The harness may normalize JSON formatting; the semantic index comparison is the production-record check.

Remaining gaps: denied-permission handling was not physically exercised because required grants already existed. Optional rows need profile-backed expectations before comparison PASS can be claimed. This run covers user 0 and bound A/repeat/B only; it does not establish unbound-control restoration proof, Android-user transition proof or any external platform verdict.

The SDK class was confirmed in DEX and the installed diagnostics APK SHA-256 matched the measured local APK. Reports redact actual and expected values while retaining comparison hashes. Historical evidence is unchanged.
