# Real application, one temporary binding

Result: **COMPLETE_WITH_LIMITATIONS**. Two cold launches of the already-installed Device Trust Scanner initial screen in Android user 17 rendered the same profile-backed Build values. No scan, attestation, biometric or server action was requested. This is rendered Java identity observation only, not a platform verdict or coverage/parity verification. No field promoted to VERIFIED.

## Scope and provenance

Repository main remains d4f0a5cb5f4d70bacaeb0b8ecdff184eff154150. Existing dirty files preserved. No product/module source edits, build, commit, tag or release. Only this run's evidence/orchestration files were added. Prior Settings selection preflight stopped before any install because Settings was already bound; that preflight is preserved separately at ../2026-10-23-021436-alpha27-real-app/. A pre-existing binding was never overwritten.

Reused exact prior instrumented alpha.27/code 51 ZIP: **b86db15b3a02598cac22985a4e93a2195f61b102403ab132d95f6a2228cb894b**. ARM64 digest 4425949ae374c0704721bae8b93278cd1b1476ab3918125a8ba870b0db03f002; ARMv7 7f476f9b3c47367c9eac6985a21a49a7ed509adf67457e7f318f13277897254b. Installed immutable files matched. Published alpha.27 unchanged. The retained original production APK hash df37f86da7ea617fcd1aad65504b23c95aaad56a1f9726ca3270846f8165cffc remained unchanged. Signature evidence for those exact original APK bytes is in the prior root-companion cycle archive.

Canonical synthetic fixture B published body SHA-256: **2bb5d74138d14251f316038be1a679fb53b1aad0796e190a8d03e7bb8494482e**. One temporary 17/com.devicetrust.scanner entry added by the existing kernel-lock/CAS temporary-overlay helper with package selection adapted in this run-only script. This was the diagnostics overlay path, not a normal companion UI binding or connector write. Published shares and companion binding stores were not written.

## Observations

| Field | Published | Cold 1 | Cold 2 | Result |
| --- | --- | --- | --- | --- |
| Build.MANUFACTURER | samsung | samsung | samsung | MATCH |
| Build.MODEL | SM-S911B | SM-S911B | SM-S911B | MATCH |
| Build.VERSION.RELEASE | 13 | 13 | 13 | MATCH |
| Build.VERSION.SDK_INT | 33 | 33 | 33 | MATCH |
| Remaining Build fields, telephony, display | see field-comparison.json | not displayed | not displayed | NOT_DISPLAYED |

Per capture: **4 MATCH / 0 MISMATCH / 22 NOT_DISPLAYED** across every fixture device, telephony and display field. Screen values manually transcribed from both archived PNGs; UIAutomator returned empty stdout, so no XML-based extraction is claimed. PIDs **8496** and **14586** are distinct. Both have matching durable companion load records for package/user/installed ABI digest and mapped hook-library evidence. Actual target-PID logs and launch stdout are retained. Logs include the existing API36 writable-library future-compatibility warning; no target crash was observed. No new functionality was tested beyond the visible four fields.

## Teardown and equality

Exactly one install reboot and one removal reboot. Temporary binding/body/scratch removed; production index returned from 328 to its original **327 entries**. Both module and pending-update directories absent after removal. Fresh post-reboot SystemUI processes have no module/hook mapping; target was intentionally not relaunched after removal. User 0 active at finish. No production APK install/launch, permission/AppOps writes, data clearing, account actions or user creation/removal.

Final persisted snapshots match exactly: production APK SHA-256, companion and selected-app permission grants/stable AppOps, all accessible companion binding/index files, published bodies and publisher shares. The first post-removal audit was taken while user17 was stopped and could not see its CE store. It is preserved as locked-user-audit-*; after a normal switch to user17/unlock availability, a complete snapshot matched. In-memory tuples versus JSON arrays also made the preliminary helper's AppOps equality flag false; persisted JSON snapshots have identical stable modes. No mode exception, reset, data rewrite or normalization was used.

**Cleanup limitation:** `/data/user/17/com.devicetrust.scanner/code_cache/cloak_hooks/libpine.so` remains as an inert module-extracted cache file (SHA-256 d3e415243b80b866d2c75408cc9a26ba4fcab0775f798442f9a622097d845e0c). Its preflight existence was not snapshotted, so provenance of that cache copy cannot be proven solely from this run. It was not deleted or treated as active runtime state. This run does not claim trace-free removal from the real app's private cache. Module directories, active fresh-process mappings and temporary publication artifacts are separately proven absent.

## Commands and checks

See exact-commands.txt, commands.jsonl and real_app_ops.py. No local test/build suites rerun because module/source bytes were unchanged. Both capture observations, exact immutable-file agreement, final persisted equality and temporary-overlay cleanup checked. git diff --check passed (existing Git LF/CRLF notices); staging index empty. No release, platform verdict or broader compatibility claim.
