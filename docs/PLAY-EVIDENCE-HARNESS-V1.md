# Play/Google Evidence Harness v1

Development-only APK `dev.cyclone.cloak.playevidence`, separate from Cloak and
its runtime probe. It does not depend on the production app or module. Nothing
runs on launch: select a profile JSON, enter its Cyclone profile ID and measured
artifact digest, then tap **Collect local evidence**. The app has no declared
permissions, account writes, package mutations, service, upload or background job.
The fixed package visibility queries are not permissions.

## What is measured

Format 1 has exactly 16 unique check IDs. Each row contains `id`, `status`,
`reason`, allowlisted `metadata` and bounded `duration_ms`.

| IDs | Evidence and limits |
| --- | --- |
| `package.<name>` (six rows) | GMS, Store, GSF, Google/AOSP WebView and Chrome: package/version/enabled state and installing package where visible. Missing means not visible **or** missing, not proven global absence. Installer identity is an observation, not provenance authentication. |
| `play_services.availability` | Real `GoogleApiAvailabilityLight` result code. Zero is local API availability PASS, other codes FAIL; denial/error BLOCKED. |
| `play_store.availability` | Visible/enabled package OBSERVED. Does not prove purchases, login or Store service health. |
| `play_protect.state` | NOT_RUN / NO_PUBLIC_OBSERVATION. No supported reader is connected; no legacy SafetyNet or invented global-setting inference. |
| `web_provider.state` | Current WebView provider package/version; disabled is BLOCKED, absent is MISSING. No WebView is instantiated. |
| `google_settings.access` | Resolution of the Google settings action is OBSERVED / RESOLVABLE_NOT_ACCESS_PROOF; no settings activity launched and no actual access claimed. |
| `account.association` | Boolean caller-visible Google account presence only; never names, emails, account counts, tokens or contents. No visible account does not prove no account. |
| `process.android_user` | Actual process UID-derived Android user, checked against requested user. |
| `profile.binding` | Exact selected file bytes SHA-256 compared to requested hash. Does not mutate or verify a production binding. |
| `integrity.api` | NOT_RUN until a separate confirmed user request; then actual token-receipt digest/time/request hash or a fixed API error code. |
| `integrity.verdict` | NOT_RUN / BACKEND_VERIFICATION_REQUIRED, including after successful token receipt. |

The six package names are `com.google.android.gms`, `com.android.vending`,
`com.google.android.gsf`, `com.google.android.webview`, `com.android.webview`,
and `com.android.chrome`. Queries reflect the caller's Android-user boundary.
Security exceptions become BLOCKED / PERMISSION_DENIED. Arbitrary exception
messages are discarded. No check requests a permission or an account chooser.

## Integrity boundary

**Collect + request Play Integrity** requires a positive caller-supplied Cloud
project number and explicit confirmation. It uses the public Standard Integrity
API once, with a request hash bound to this request/profile/user/artifact. SDK
preparation and request each have a 90-second timeout. A project number is public
configuration, not a credential. The request contacts Google through Play's
service; local collection does not initiate that request.

The public response is an encrypted token. Default collection hashes it immediately
and discards it; ordinary reports keep actual receipt metadata only. The separate
[verification exchange](INTEGRITY-VERIFICATION-EXCHANGE-V1.md) adds explicit opt-in
private retention and document-picker export, plus an owner-operated local backend.
The diagnostics app never decodes, logs or automatically forwards tokens. No
credentials are included. API/package availability and token receipt are never a
platform verdict or permanent proof. Physical API success remains UNVERIFIED.

References: [Standard Integrity API](https://developer.android.com/google/play/integrity/standard),
[package visibility](https://developer.android.com/training/package-visibility/declaring),
[caller-visible accounts](https://developer.android.com/reference/android/accounts/AccountManager).

## Report contract and privacy

`schema/play-evidence-report.schema.json` is a closed format-1 schema. The Kotlin
`EvidenceReport` and Python runner also enforce semantic reason/status pairs,
row completeness, unique IDs, per-reader metadata allowlists, exact binding,
warning consistency and readiness projection consistency. Unknown keys, tokens,
account contents, duplicate JSON keys, oversized reports and silent promotions
are rejected. Reports are capped at 128 KiB. Timestamps are UTC whole seconds.

The envelope includes `request_id`, `profile_id`, `profile_hash`, `artifact_hash`,
`android_user_id`, `timestamp`, `rows`, `warnings`, and `readiness_report`.
The selected profile is read-only and only its exact byte hash enters the report;
use the same byte-hash convention in an evaluation context. The artifact digest
is supplied by the operator, **not attested or independently measured by this
collector**. For app/module pairs use an agreed manifest of both artifact hashes.
The runner separately records the installed **diagnostics** APK hash.

`readiness_report` is consumable by the existing strict Play Readiness Evaluation
parser. Play Protect and actual Google settings access remain unknown, so this
harness cannot promote the existing required-evidence evaluator to READY. There
is no automatic import into production readiness storage. The development
[integration flow](PLAY-READINESS-INTEGRATION-V1.md) adds explicit production
import, preview and save of the original full envelope.

Only the current capture generation may atomically write its private report;
new intents invalidate old workers. The latest 20 reports are retained. No profile
body, raw token, arbitrary log text, account name, credential or production store
is exported in normal evidence reports. The explicitly opted-in verification bundle
is a separate sensitive transport file. Package/version/installer metadata, opaque profile IDs and SHA-256
digests are intentionally retained for debugging.

## Build and run

```powershell
$env:JAVA_HOME='C:/Program Files/Eclipse Adoptium/jdk-17.0.20.101-hotspot'
& "$env:TEMP/cloak-build/gradle-8.11.1/bin/gradle.bat" -p android :playEvidence:testDebugUnitTest :playEvidence:assembleDebug :playEvidence:lintDebug --offline --no-daemon --console=plain
```

Output: `android/playEvidence/build/outputs/apk/debug/playEvidence-debug.apk`.
Install this separate diagnostics app explicitly through your normal development
process; the runner never installs anything. Select an already running/unlocked
user where it is installed. The runner never starts/switches/unlocks users.

Alpha.27 also publishes `playEvidence-release.apk`, signed with the existing
release identity. It is a separate optional developer app (version 1.0-diagnostics,
code 1), not the production companion. A locally debug-signed copy can have a
different certificate and may not accept an in-place update. Preserve reports
before any separately approved replacement; this release run does not change a
device or clear data.

```powershell
uv run --with jsonschema -- python -X utf8 tools/run_play_evidence.py --adb C:/Users/Agent/android-sdk/platform-tools/adb.exe --serial 3B171FDJH0061G --user 17 --profile C:/local/approved-profile.json --profile-id profile-17 --artifact-digest <64-lowercase-hex>
```

The runner stages the supplied file **only in the diagnostics UID's private
directory**, opens the app and waits for your tap. `--wait-integrity` waits for
an explicit on-device API request; it does not initiate one. Default timeout 240s,
configurable up to 900s. Default `--private-access run-as` uses no root. No production binding, package install/removal,
permission/AppOps change, account write or reboot command is used. Input and
report scratch files for that request are removed in `finally`; cleanup success
is recorded. Failure or inability to clean up makes the run fail.

### Signed APKs

The published signed diagnostics APK is not debuggable, so Android correctly
rejects `run-as`. On an already rooted development device, explicitly select
`--private-access root`. This never falls back silently or installs a debug APK.
It checks the diagnostics directory's per-user UID, repairs only that package's
private staging ownership/SELinux label, preserves exact input bytes, and checks
the remote exit status independently of ADB transport success. Root commands for
staging/report access are allowlisted; cleanup remains request-specific.
Android's per-user MLS categories require recursive forced relabeling of the
diagnostics input directory (`restorecon -RF`); ownership alone is insufficient.
The launch explicitly delivers a fresh intent to an existing collection screen,
so a completed older request cannot be mistaken for the newly staged request.

To avoid exporting a saved profile body to the PC, use `--saved-identity <id>`
instead of `--profile`. This reads only that identity's file in the selected
user's Cloak copy into memory; it never invokes a production writer. Neither
profile bodies nor root command output are included in evidence. Provenance
records the explicit transport and profile source.

```powershell
uv run --with jsonschema -- python -X utf8 tools/run_play_evidence.py --adb C:/Users/Agent/android-sdk/platform-tools/adb.exe --serial 3B171FDJH0061G --user 17 --private-access root --saved-identity <saved-id> --profile-id <Cyclone-profile-id> --artifact-digest <64-lowercase-hex>
```

The user must still initiate local collection. This option does not request
Integrity, grant permissions, approve Magisk access for the companion, repair
publication, or convert unknown readiness into READY.

Offline validation/export, requiring no device:

```powershell
uv run --with jsonschema -- python -X utf8 tools/run_play_evidence.py --report C:/local/report.json
```

Default output is a fresh timestamped `docs/runtime-evidence/<UTC>-play-evidence-<suffix>/`.
`--output` may select a new directory; existing output is never overwritten.
The ZIP includes `report.json`, `readiness-report.json`, `provenance.json`,
`cleanup.json`, and `summary.md`, plus an adjacent SHA-256 file. Device runs also
retain `capture-attempt.json`. Offline export explicitly has no independently
verified device binding. Valid reports with FAIL rows are preserved and return
exit 1. Invalid inputs retain a fixed-code failure record only, never the rejected
raw payload. BLOCKED/MISSING/NOT_RUN remain visible limitations; exit 0 means a
valid capture, not readiness or platform verification.

## Test boundaries

Kotlin fake readers test real classification paths without accessing accounts or
services. Shared synthetic JSON is checked by both Kotlin and Python; runner
tests cover binding mismatches, schema rejection, redaction, private-file cleanup,
bundle layout and explicit user initiation. Device/API behavior, OEM settings
resolvability, account visibility and real Integrity project configuration need a
separate authorized device run. No release is published by this sprint.
