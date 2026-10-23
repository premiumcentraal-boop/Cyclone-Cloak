# Alpha.24 display parity implementation and validation

Result: FAIL — cycle 1 / user-0-post-removal-warm / PROBE_REPORT_TIMEOUT.
Candidate commit: a93d8643c51463dff1d4fb3258a383432012f989; companion/module 0.11.0-alpha.24 / code 48.
Signed validation: https://github.com/premiumcentraal-boop/Cyclone-Cloak/actions/runs/39158498518 (PASS).
Normal CI: https://github.com/premiumcentraal-boop/Cyclone-Cloak/actions/runs/39158498702 (PASS).

## Implementation
Optional refresh/HDR metadata are captured independently with empty-array defaults.
Exact reviewed constructors are selected from the framework constructor list. Any rich constructor failure falls back to the simple constructor; outputs must match the requested tuple.
The temporary diagnostic showed exact constructor lookup throwing NoSuchMethodException despite those signatures appearing in enumeration. Temporary instrumentation was removed before candidate build.
Linked reports now include allowlisted raw active/supported screen tuples and Display.getRefreshRate(), retaining existing hashes.

## Measured results
| User / fixture | Legacy | Linked | Active tuple | getRefreshRate |
|---|---|---|---|---|
| 0 / A | 82 PASS, 7 OBSERVED | 13 PASS | 1080 x 2400 @ 90 Hz | 90 Hz |
| 17 / B | 81 PASS, 4 OBSERVED, 4 BLOCKED | 13 PASS | 1080 x 2340 @ 120 Hz | 120 Hz |
Both supported-mode lists contain the exact active profile tuple. Fixture/profile/published-body hashes match for both users. User-17 permission blocks remain limitations, not passes.

The first warm post-removal capture produced no completed report. The crash log identifies the same PID as user 0 bound capture (12996), thread cloak-probe-6, SIGSEGV at 0x38 / null pointer dereference. The native root cause is NOT established. No further runtime edits or retries were attempted.
Cold restoration and cycles 2/3 were not completed.

## Preservation and cleanup
Permission/AppOps delta: none. Production delta during lifecycle: none.
The installation audit separately records the authorized module change; all other production state and permissions were preserved.
Both users: temporary probe binding removed; body directory removed; expected/report/temp private files removed. Scratch removed.
Diagnostics installed once before cycles; exact matching APKs reused during cycle checks.
No alpha.24 tag or prerelease was created. CURRENT-STATE/RUNTIME-COVERAGE were not updated to claim success. The separate next wave was not started.

## Local gate
577 app + 35 module + 109 standard probe + 109 diagnostics probe + 594 forge + 69 schema + 7 probe-host = 1,500 passing tests; zero failures/skips.
Debug/release packaging, metadata, generated contract, diff checks and signed validation passed. No compiler warnings found.
The initial detached probe build lacked ANDROID_HOME; its log is preserved; setting the documented SDK path produced a green build.
Exact test/build commands: local-tests.json. Raw display results and failure details: result.json.
Module log: module-live-log.txt. Probe-only crash metadata: probe-crash-redacted.log.
Installation hashes: installation-provenance.json. All lifecycle captures, audits, permissions and cleanup proofs: lifecycle/.

Local parity is an implemented capability. These two successful bound measurements do not establish lifecycle reliability, broader device compatibility or any platform verdict.
