# Standalone diagnostics probe validation — INCOMPLETE

APK SHA-256: `81706b5ddc386881dfff7391cc344351806d8946f230d269f21d6105f815bda4`.
Local APK installed successfully; installed package SHA-256 matched for users 0 and 17.
Source: main `d4f0a5c` with uncommitted diagnostics observation work; not a release artifact.

| User | Capture | Result | Valid filesystem rows |
| --- | --- | --- | --- |
| 0 | first, initial attempt | CAPTURE_FAILED / NullPointerException | 0 |
| 0 | first, repeat attempt | CAPTURE_FAILED / NullPointerException | 0 |
| 0 | consecutive second | PROBE_REPORT_TIMEOUT (60 seconds) | 0 |
| 17 | first | PROBE_REPORT_TIMEOUT (60 seconds) | 0 |
| 17 | second | Not run after timeout | 0 |

User 0 failure envelope correctly reports UID 10273 (Android user 0), package,
request ID and profile digest. No failing row ID or stack is present in that
structured failure envelope. Do not assign the exception to a specific reader
without further evidence. No valid filesystem_observations section was produced;
schema conformance, filesystem counts and digest determinism remain NOT VALIDATED.
Do not interpret the absence of measured rows as zero failures or successful coverage.

Both users were RUNNING_UNLOCKED at preflight. User 0 remained active. User 17
launch was accepted by am start, but no report arrived. This is a collection
limitation, not a measured filesystem denial. No user switch was attempted.

Every attempt's cleanup reports successful diagnostics uninstall and no package
paths for users 0/17. Reports remain in the evidence archives. The probe existed
before testing; its global uninstall and loss of its private data were explicitly
requested by the handoff. No raw filesystem content is retained.

Production APK hashes, version and stable permission snapshots matched before and
after every attempt. No root shell, module update/reboot, production app install,
production launch, binding overlay, permission grant/reset or AppOps edit occurred.
Private production stores/module bytes were not independently audited. The
installed module's claimed alpha.28 provenance was not established.

No product code changed in this validation run. No tag, release, parity verdict,
VERIFIED promotion, platform verdict or broader coverage claim was made.

Next step: a separately authorized diagnostics-code investigation of the structured
NullPointerException and missing repeated/cross-user reports; preserve these attempts.
