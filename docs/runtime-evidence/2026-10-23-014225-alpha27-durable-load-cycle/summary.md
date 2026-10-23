# Durable module load evidence — NOT VALIDATED (stop gate)

Result: MODULE_DURABLE_LOAD_RECORD_ABSENT_OR_INVALID after user-17 bound cold capture. No second fix/retest attempted.

## Artifact provenance

Owner approved a new instrumented ZIP; the original published alpha.27 release remains untouched. Source is an isolated archive of v0.11.0-alpha.27 (28bf118437cd0f96fee54b8fc2e07e551fe1cc31) with only the attached load-record/ABI-manifest patch. Metadata remains alpha.27/code 51. Main's unrelated runtime changes were not used for the device build.

- Original installed APK SHA-256: `df37f86da7ea617fcd1aad65504b23c95aaad56a1f9726ca3270846f8165cffc`
- Original embedded module ZIP SHA-256: `53649be46759c3b8ba461d122a20d214727f310ceb34ee9fa296a4a2e7454b3f`
- Instrumented ZIP SHA-256: `e9e805523c2eaab098d795bb4b419d15f25a5ee056963e4ddb831e5d3c9e4f04`
- Instrumented arm64 library: `0b2e9884b3f75adc5d2038f1684c928d17092bfe0e4fdf979ce812bb30ba57c1`
- Instrumented ARMv7 library: `dd175d53738cb053c2782edb990f9b7531a514f1d50179618b656ae367bd4c33`

The new manifest hashes match both packaged ABI libraries; ELF checks pass. After boot, installed immutable files match the instrumented ZIP. Digest provenance is packaged ABI metadata verified by the host, not in-memory attestation. Hook-library path in a load record is explicitly a planned path before post-specialization. PID binds a fork to its later capture; request ID is unavailable at pre-specialization.

## Device result

Magisk install succeeded; one reboot observed complete within 37.027 seconds (polling upper bound). User 0 unlocked; normal switch started/unlocked existing user 17. User-17 fixture B cold capture PID 9073 completed: 89 unique legacy rows, matching STARTED/COMPLETED markers, valid filesystem schema, 2 OBSERVED / 2 BLOCKED filesystem rows. Report and published body SHA-256 both equal `2bb5d74138d14251f316038be1a679fb53b1aad0796e190a8d03e7bb8494482e` (same canonical synthetic fixture bytes).

No durable record exists. Module log reports `load record unavailable`; boot/audit evidence records SELinux `avc: denied { write }` for directory `cyclone_cloak`, source `u:r:zygote:s0`, target `u:object_r:system_file:s0`, permissive=0. This proves the attempted direct writer lacks permission in this context; it does not establish a module load failure. No SELinux, permission or AppOps mutation was attempted. No fresh logcat retry can substitute for the missing durable record.

Stopped as instructed. User-0 bound capture, module removal, second reboot, post-removal captures and restoration comparisons are NOT RUN. The instrumented module remains installed, not marked for removal. Possible separate authorized follow-up: request the write through the already-root companion, without weakening SELinux. No implementation of that follow-up was attempted.

## Cleanup and final audit

Temporary diagnostics overlay removed; both temporary profile bodies and scratch directory absent. Diagnostics APK uninstalled for users 0 and 17. Active user is 0. Exact persisted before/after production APK, permission grants, stable AppOps, binding index/store, published-body and publisher-share hash snapshots compare equal. No production APK install/launch or profile-store rewrite. Module artifact replacement is intentional and excluded from production-state equality. Prior standalone reports are preserved as baselines, but no post-removal comparison was performed.

## Changed implementation files

- android/module/src/main/cpp/cloak.cpp — bound pre-specialization record call.
- android/module/src/main/cpp/module_load_record.hpp — bounded JSONL writer: O_APPEND, one write, inode flock, 64 KB truncation cap, fdatasync; evidence failure preserves hook decisions.
- android/module/build.gradle — manifest of finished ABI SHA-256 hashes.

## Local commands and results

```powershell
$env:JAVA_HOME='C:/Program Files/Eclipse Adoptium/jdk-17.0.20.101-hotspot'
$env:CLOAK_TEST_WSL='1'
& "$env:TEMP/cloak-build/gradle-8.11.1/bin/gradle.bat" -p android :module:testDebugUnitTest :module:packageModule --offline --no-daemon --console=plain
# Same tasks against the isolated alpha.27 source archive:
& "$env:TEMP/cloak-build/gradle-8.11.1/bin/gradle.bat" -p "C:\Users\Agent\AppData\Local\Temp\cloak-alpha27-load-record-20261023-014115/android" :module:testDebugUnitTest :module:packageModule --offline --no-daemon --console=plain
git diff --check
git diff --cached --check
```

Current-main module tests: 80 passed. Isolated alpha.27 module tests: 40 passed. Both ABI builds/packaging passed. No compiler warning/error lines found in either build log; Git emitted existing LF/CRLF notices. Dedicated native concurrency/cap tests were not executed, so those properties are implementation-level, not physically validated.

Device orchestration: `uv run --offline -- python -X utf8 <evidence>/boot_gate_ops.py install`, `... bootcheck instrumented`, `uv run --offline --with jsonschema -- python -X utf8 <evidence>/boot_gate_ops.py bound`. Exact top-level device commands/exit codes are in commands.jsonl; helper commands are defined in the preserved run-only orchestration and existing runner, with launch stdout/markers/report evidence saved. This journal is not a claim that every inherited helper subprocess was individually logged. Root denial/absence-check command and outputs are preserved separately.

No release, tag, version bump, commit, VERIFIED promotion, parity or platform verdict claim. All prior evidence preserved.
