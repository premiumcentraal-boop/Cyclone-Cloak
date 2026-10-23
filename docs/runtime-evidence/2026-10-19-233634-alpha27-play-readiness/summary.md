# Alpha.27 Play/Google device test — 2026-10-20 (Europe/Berlin)

Result: diagnostics capture, validated production import/save and four rejection checks passed. Local readiness is UNKNOWN, not a platform verdict.

- Measured user: 17; companion/module alpha.27, code 51.
- Published signed diagnostics APK installed side by side; installed SHA-256 matches release.
- Capture: 3 PASS, 8 OBSERVED, 2 MISSING, 3 NOT_RUN, 0 FAIL, 0 BLOCKED (16 rows).
- Play Services availability passed. Play Store and current Google WebView are visible/enabled.
- Missing: alternate com.android.webview package (not visible or absent); Google settings action not resolvable.
- Account association remains unknown: no Google account is visible to this zero-permission caller; this does not prove no account exists.
- Play Protect not measured. Optional Integrity request was not made; no decoded verdict exists.
- User, saved-file hash and artifact binding agree with the production UI. Valid import previewed UNKNOWN and saved UNKNOWN.
- Production UI rejected foreign profile hash, foreign artifact, stale timestamp and incomplete rows. Saved readiness bytes were unchanged by these negative tests.
- Production bindings (327 in the profile copy), saved profiles, publication tree, publisher shares and module files match before/after hashes. Permission grants/flags and AppOps modes are identical.
- Only the intended local readiness evidence was saved. No accounts, bindings, connector, module or permission configuration was changed.
- Request-specific diagnostics input/report files and four negative-test Downloads were removed. The valid redacted report remains in user 17 Downloads for review.
- All 2,015 local tests passed; git diff --check passed. No source code or release metadata changed.

## Limitations

The stock device runner uses run-as and cannot access this non-debuggable signed diagnostics APK. This run used scoped root access to diagnostics-private staging/report files. Initial manual staging created a root-owned files parent, causing REPORT_WRITE_FAILED; ownership and SELinux label were corrected only for that diagnostics directory before the successful retry. See setup-notes.json.

Connector UI remained Reconnecting with existing Magisk approval-timeout binding warnings. This test does not claim the connector, root repair, binding reconciliation or broader runtime surfaces are healthy. No root repair/publication was triggered. Account-slot conflict consent was covered by local tests, not a new on-device shared assignment. No platform certification or permanent trust is claimed.

Artifact binding identifies the installed companion APK and its bundled module ZIP; it does not independently attest the loaded root module. Audit hashes cover accessible production paths only. Timestamps inside captures use UTC; this report's date uses the user's Europe/Berlin date.
