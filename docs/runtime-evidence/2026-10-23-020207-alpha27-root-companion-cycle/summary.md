# Root-companion durable load record and module cycle

Result: COMPLETE_WITH_LIMITATIONS. One instrumented reinstall, one boot reboot, two bound cold captures, standard Magisk removal, one removal reboot and two post-removal cold captures completed. No second fix/retest cycle. **Final device state: module REMOVED; diagnostics APK uninstalled; active user 0.** Production APK is unchanged.

## Fix and trust limits

Pre-specialization assembles a bounded record and sends a private CLK2 request over the existing Zygisk root-companion channel. The companion validates package/user, a currently published binding, exact allowlisted fields, and packaged ABI digest. Only the root companion writes the fixed JSONL destination using the existing one-write/flock/64 KB cap/fdatasync path. The pre-specialization caller waits for acknowledgement after persistence. Fixed result codes: 0 OK, 1 INVALID, 2 DIRECTORY, 3 DIGEST, 4 WRITE, 5 TRANSPORT. Evidence failure does not change hook activation or profile replies. No SELinux change. Public connector protocol and state reader format are unchanged.

The record proves bound pre-specialization was reached and persisted before capture completion, not that all hooks or platform services passed. The digest is independently checked packaging metadata, not in-memory attestation. Hook path is labeled planned before post-specialization. No VERIFIED promotion, coverage/parity claim or platform verdict.

## Artifact provenance

Isolated build source: alpha.27 tag (28bf118437cd0f96fee54b8fc2e07e551fe1cc31) plus attached instrumentation-alpha27.patch and header. Only durable-load instrumentation/ABI manifest added; unrelated main runtime changes excluded. Metadata remains alpha.27/code 51.

- Original APK: `df37f86da7ea617fcd1aad65504b23c95aaad56a1f9726ca3270846f8165cffc`
- Original embedded module ZIP: `53649be46759c3b8ba461d122a20d214727f310ceb34ee9fa296a4a2e7454b3f`
- New root-companion instrumented ZIP: `b86db15b3a02598cac22985a4e93a2195f61b102403ab132d95f6a2228cb894b`
- New ARM64 library: `4425949ae374c0704721bae8b93278cd1b1476ab3918125a8ba870b0db03f002`
- New ARMv7 library: `7f476f9b3c47367c9eac6985a21a49a7ed509adf67457e7f318f13277897254b`

Both ABI ELF files match their generated manifest. Installed immutable files matched the selected instrumented ZIP after boot. The published release was not modified or retagged; original archived APK/ZIP hashes rechecked unchanged. Installed APK signature evidence is carried from the identical pulled APK; final APK SHA-256 matches the preflight snapshot.

## Captures

| User | Phase | Fixture | PID | FS OBSERVED/BLOCKED/MISSING | Durable record |
| --- | --- | --- | --- | --- | --- |
| 17 | bound-cold | B | 10610 | 2/2/0 | matched |
| 17 | post-removal-cold | B | 6123 | 2/2/0 | module absent |
| 0 | bound-cold | A | 12668 | 2/2/0 | matched |
| 0 | post-removal-cold | A | 8269 | 2/2/0 | module absent |

Every capture completed with 89 unique legacy rows, matching STARTED/COMPLETED markers, valid filesystem schema and correct Android user/fixture. For bound captures the report hash equals the canonical temporary published body hash; durable PID/package/user/artifact matches are preserved. Raw legacy FAIL comparisons are retained in reports and capture-table.json: unbound captures compare host values against synthetic fixture expectations and are not runtime-parity verdicts.

After removal, both module and pending-update directories are absent. Both new probe processes have no module/hook mapping. For both users proc/cpuinfo SHA-256 matches the archived same-APK unbound baseline. build.prop and proc/version stay BLOCKED/PERMISSION_DENIED and NOT_COMPARABLE; maps stay volatile OBSERVED. The filesystem observations are not promoted to PASS or VERIFIED by these restoration comparisons. Publication state is intentionally retained to preserve production bindings; absence of the module does not require deleting production state.

## Final audit and cleanup

Exact persisted before/after snapshots match: production APK SHA-256, runtime/install permission grants, stable UID/package AppOps, binding-store/index bytes (by SHA-256), published profile bodies and publisher shares. No allowed-mode exceptions, resets or semantic normalization. Module replacement/removal is intentional and excluded from production data comparison. Both temporary diagnostics binding entries, body directories, module staging ZIP and scratch files are absent. Diagnostics package globally uninstalled, removing private/external probe state for both users. Phone returned to user 0. No accounts, user creation/removal, permission/AppOps writes or production APK launch/install.

## Changed files and local checks

- android/module/src/main/cpp/cloak.cpp — new private record request, root-companion dispatch/write/ack and pre-specialization send.
- android/module/src/main/cpp/module_load_record.hpp — split assembly from root-only validation/persistence; existing bounded writer retained.
- forge/tests/test_module_load_record.py — three host ownership/ordering integration guards.
- android/module/build.gradle ABI-manifest generation is a preserved prerequisite from the preceding run.

Module unit suites: 80 current-main tests and 40 isolated-alpha.27 tests passed. Three new host integration guards passed. Both ABI builds and manifest/ELF verification passed. No compiler warning/error lines found in build logs; Git emitted existing LF/CRLF notices. Native IPC/write behavior was measured on device; no standalone host-native concurrency/cap test was executed.

```powershell
$env:JAVA_HOME='C:/Program Files/Eclipse Adoptium/jdk-17.0.20.101-hotspot'
$env:CLOAK_TEST_WSL='1'
& "$env:TEMP/cloak-build/gradle-8.11.1/bin/gradle.bat" -p android :module:testDebugUnitTest --offline --no-daemon --console=plain
& "$env:TEMP/cloak-build/gradle-8.11.1/bin/gradle.bat" -p "C:\Users\Agent\AppData\Local\Temp\cloak-alpha27-companion-record-20261023-020127/android" :module:testDebugUnitTest :module:packageModule --offline --no-daemon --console=plain
uv run --offline --with pytest -- python -X utf8 -m pytest forge/tests/test_module_load_record.py -q
git diff --check
git diff --cached --check
```

Exact device argv/exit codes, including inherited probe/overlay helpers, are in commands.jsonl (outputs hashed where they could contain private data). Launch stdout, markers, redacted reports, module records, cleanup and audits are preserved separately. Run-only orchestration modes: install, bootcheck instrumented, bound, remove, bootcheck removed, restoration. Install uses `magisk --install-module <staged ZIP>`; removal uses only the standard module `remove` marker followed by reboot. No production source edits beyond the authorized module instrumentation; nothing staged/committed, no release/tag/version bump. Prior evidence preserved.
