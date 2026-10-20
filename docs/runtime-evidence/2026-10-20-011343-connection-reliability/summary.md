# Connection reliability — signed candidate verification

Result: **PARTIAL — ADB device disconnected during final verification.**
This is not a claim of complete device validation or a platform verdict.

## Implemented and installed

- Cloak `c6371704791166376e0c48e6fa19b402597c3f2d` on main: live connection
  progress before reconciliation; honest BAD_REQUEST reasons; reachable Health
  repair controls; paginated checks; first-run cleanup scoped to locally managed
  profiles, preserving unrelated records. Includes prior commit `3781806`.
- Cyclone `7e001f4ee80dbc329464b04b8a730df5c4762793` on
  `codex/connector-capacity-reliability`: bounded configuration quota 8,192,
  replacing the 256-record ceiling that prevented this 327-binding library from
  completing. Existing files and wire error envelopes are preserved.
- Both signed APK updates installed successfully using existing signing lanes.
  Installed hashes matched on users 0 and 17. No release was published and no
  module was installed.

| Candidate | Version/code | APK SHA-256 |
|---|---|---|
| Cloak final | 0.11.0-alpha.27 / 51 | `2bb80cf73a1e33e7d262a9bab63998f62a3bb847c80da64e5a0875cc23e6932c` |
| Cyclone owner test | 5.0.0-alpha.144.dev2 / 299 | `1c0ed0e107a5351a3a6ecc187aab9aabedb4bff0f969f1bcc1fc936a06ba94a4` |

## Verified observations

- Final Cloak candidate connected immediately on launch in user 0. Home's
  Root setup attention item reached Health's visible Check & repair control.
- User 0 publication: **COMPLETED**, all phases completed, 2,143 ms.
- User 0 Health: **Ready — 1,644 pass, 0 warning, 0 fail**.
- Connector stores: **327 records / 327 ready in each copy**. Zero mismatches
  against current local assignments. Baseline counts were 126 and 256.
- Main's original publisher share is unchanged; all 327 original Main entries
  remain in the effective merged index with no changed or missing entries.
- Last completed preservation audit: permissions/AppOps, saved identity files,
  module files and effective profile bodies were unchanged. This is an
  intermediate audit, not the missing final after-audit.
- In-app Save diagnostics bundle succeeded. The retained user-0 export is from
  the first signed Cloak candidate `3781806`, before the cleanup follow-up:
  256 redacted events, zero recorded failures, zero persistence failures.

## Normal-flow changes and limits

User 17's earlier normal repair applied the existing Main-wins mirror policy to
327 local assignments. Its old saved identity file remains intact. Main already
published the effective identity before this run, so effective profile bodies
did not change. Local binding files and publisher 17's redundant share are not
claimed byte-identical. See the binding snapshots and main-entry proof.

Each complete 327-record check takes roughly 3–4 minutes. Startup and repair can
queue follow-up checks. The connection stays visible during this work, but this
run does not establish a latency improvement for full-library verification.
Earlier user switches caused Android background-process kills and transient
DeadObjectException; they are not classified as native crashes.

## Interruption and resume

At approximately 12:42 Europe/Berlin, ADB no longer listed the device. The last
active Android user was 0; user 17 had been RUNNING_UNLOCKED. The host settle
observer stopped with ADB_DEVICE_NOT_FOUND. No phone restart or cleanup was
attempted in response.

Still pending:

1. Reconfirm installed hashes and finish the final user-0 settle observation.
2. Return to existing user 17; verify reconnection and normal Health repair on
   the final candidate. Preserve genuine mirrored-identity warnings.
3. Collect/import fresh Play evidence for the final artifact if continuing that
   workflow. Required unknown observations must remain UNKNOWN.
4. Collect final permissions, production and module audits; export final
   diagnostics. Do not substitute an intermediate snapshot for this step.
5. Leave user 17 active when finished.

No data clearing, permission reset, module update or reboot was performed.

## Gates

Cloak: **2,033 tests passed** (app 703, module 40, Play diagnostics 20,
standard probe 109, diagnostics probe 109, forge/schema 1,045, host probe 7).
Debug build passed. Lint: zero errors; existing warnings app 19, each probe 12.
No new compiler warnings. Cyclone mobile candidate: **2,909 tests passed** and
debug assembly passed; existing deprecation warnings remain.

Signed Cloak validation:
https://github.com/premiumcentraal-boop/Cyclone-Cloak/actions/runs/39406349015

Signed Cyclone owner-test validation:
https://github.com/premiumcentraal-boop/Cyclone/actions/runs/39352440392

Exact local test/build commands are in
`docs/HANDOFF-2026-10-20-CONNECTION-RELIABILITY.md`. Artifact provenance, installed
hashes, intermediate audits, publication events and the disconnection boundary
are retained beside this summary. No production profile bodies or credentials
are included.

Raw Gradle stdout is retained byte-for-byte in `gradle-followup.log.zip`, including
its original trailing whitespace. The evidence directory preserves captured
line endings so manifest hashes remain valid across checkouts.
