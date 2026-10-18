# Alpha.24 stabilization and integrated upgrade handoff

Released: [https://github.com/premiumcentraal-boop/Cyclone-Cloak/releases/tag/v0.11.0-alpha.24](https://github.com/premiumcentraal-boop/Cyclone-Cloak/releases/tag/v0.11.0-alpha.24) — prerelease, code 48.
Release commit: `93b77477742361c837ede790eb4de616b83e523d`. Measured code: `49564ce0a4d60af158da0e4f67b84583b06ea4c7`.

## Fix and included work

- Fixed the native warm-capture crash by retaining the embedded hook class loader with a strong JNI global reference. The previous loader was collectable while native trampolines still referenced its methods; the failed tombstone showed zeroed method storage immediately after compacting GC.
- Hook-engine SDK/debug mode comes from real host ART state before profile Build rewriting. Active/supported display modes preserve the same profile-backed tuple, with independent optional metadata and a simple-constructor fallback.
- Included original Device Factory commit `7f34ea2` as `6f9fa0c` and Profile Operations Console `5121b70` as `625e691`. Fixed standalone schema-test imports in `49564ce`. The factory stays opt-in Python; the console reuses existing app generation/review/storage/reconciliation paths.

## Physical result

- Twelve warm repetitions reused PID 8913 through compacting GC; all completed. Then three full cycles completed 24 captures across users 0 and 17. No unexpected crash.
- Each bound user-0/A capture: 82 legacy PASS, 7 OBSERVED, 0 BLOCKED; 13 linked PASS / 0 FAIL; 1080x2400 @ 90 Hz.
- Each bound user-17/B capture: 81 legacy PASS, 4 OBSERVED, 4 BLOCKED; 13 linked PASS / 0 FAIL; 1080x2340 @ 120 Hz.
- Supported lists contained the exact active tuple. User/fixture/report/published hashes matched. Warm and cold removal captures completed; both users restored cold without comparison failures.
- Production entries stayed 327. Permission/AppOps, production bodies/shares/accessible stores and per-cycle module audits were unchanged; all cleanup proofs passed. Zero APK installations by the harness; matching installed APKs were reused.
- Each changed native candidate required its normal module-update reboot; the failed host-configuration candidate and final loader-lifetime candidate each needed one. Stress/lifecycle harnesses performed no reboot. No data clearing, permission reset or production binding mutation.

[Evidence summary](runtime-evidence/2026-10-18-222138-alpha24-integrated-stabilization/summary.md), [bundle](runtime-evidence/2026-10-18-222138-alpha24-integrated-stabilization/evidence-bundle.zip), [exact commands](runtime-evidence/2026-10-18-222138-alpha24-integrated-stabilization/COMMANDS.md), [complete changed-file list](runtime-evidence/2026-10-18-222138-alpha24-integrated-stabilization/post-release/changed-files.txt).

## Gates and artifacts

- Local: **1,679 passed**, zero skips: app 598, module 40, probe 109 per flavor, forge 735, schema 81, host probe tools 7. Debug/release APK, module ZIP and both probes built. No compiler warnings.
- Final signed validation and release CI passed. APK signer is unchanged; app/module version/code agree; APK embeds the exact published module ZIP. Downloaded evidence/summary/provenance bytes match the tag.
- Published app code/resources and every module entry are byte-identical to the physically measured candidate. Archive container timestamps differ, so whole APK/ZIP digests differ; this is explicitly recorded rather than relabeling the measured package.

| Asset | Bytes | SHA-256 |
|---|---:|---|
| `app-release.apk` | 43786878 | `b8688135535f45edd295c01bd9b688d8d27886f687ecc582608c806e2d8646dc` |
| `cyclone-cloak-0.11.0-alpha.24.zip` | 1683427 | `73e03c3a7e453d8e5938cce413563e708e7311b39e85c2b1605ff339ef2cbbce` |
| `evidence-bundle.zip` | 1757549 | `71ff1712848e4c7ff2bc4c63464a20f889dceda651aebf694e1e5e403df99566` |

## Changed files and remaining limits

Runtime: `android/module/src/main/cpp/cloak.cpp`, `src/hook/java/dev/cyclone/cloak/hook/HookBridge.java`, `RuntimeSdkTest.java`; lifecycle failure classifier and tests; native loader lifetime guards. Factory: versioned catalog, pure factory, focused/schema tests and docs. Console: creation/readiness/history UI, binding review/view-model orchestration, additive journal metadata and three test files. Release docs/README and evidence asset paths updated; release verification gates are unchanged.

Four user-17 bound Wi-Fi/Bluetooth rows remain blocked by existing permission state. External/unavailable observations are not passes. Factory firmware/HAL approximations and the new console UI remain physically unverified. No platform verdict, broader device compatibility, profile/binding schema, connector protocol, permission or publication-script change is claimed. Existing action-version/cache CI warnings remain; no compiler warnings.

Prepared separate branch: `codex/next-compatibility-wave-alpha24`, from the released tag. No broader runtime work started. Future scope: concrete WebView provider, safe public graphics metadata, existing memory/storage descriptors, input-method coverage. Higher-risk native/system work needs a separate authorized device run.
