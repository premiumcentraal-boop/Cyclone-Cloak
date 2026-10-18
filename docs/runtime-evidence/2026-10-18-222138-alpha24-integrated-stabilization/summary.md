# Alpha.24 physical stabilization evidence

Result: PASS with documented permission/external-state limitations.
Code commit: `49564ce0a4d60af158da0e4f67b84583b06ea4c7`. Signed validation: [39163201635](https://github.com/premiumcentraal-boop/Cyclone-Cloak/actions/runs/39163201635).

12 warm repetitions stayed in PID 8913 through a compacting GC; 13 focused captures completed. Three full lifecycle cycles completed 24 captures. No unexpected probe crash.

| Cycle | User | Fixture | Legacy PASS / OBSERVED / BLOCKED | Linked PASS / FAIL | Active mode |
|---|---:|---|---|---|---|
| 1 | 0 | a | 82 / 7 / 0 | 13 / 0 | 1080x2400 @ 90 Hz |
| 1 | 17 | b | 81 / 4 / 4 | 13 / 0 | 1080x2340 @ 120 Hz |
| 2 | 0 | a | 82 / 7 / 0 | 13 / 0 | 1080x2400 @ 90 Hz |
| 2 | 17 | b | 81 / 4 / 4 | 13 / 0 | 1080x2340 @ 120 Hz |
| 3 | 0 | a | 82 / 7 / 0 | 13 / 0 | 1080x2400 @ 90 Hz |
| 3 | 17 | b | 81 / 4 / 4 | 13 / 0 | 1080x2340 @ 120 Hz |

Active tuples were present in each supported-mode list. Fixtures, Android users, report hashes and temporary published-body hashes matched. Both users restored cold in each cycle, with zero restoration FAIL rows. The full cycle reports retain unbound fixture mismatches: those are host-versus-synthetic-fixture controls, not suppressed failures. Warm retained values are explicitly observations.

Production entries remained 327 before/after. Published bodies, publisher shares, accessible binding stores and module files remained unchanged during every cycle. Permission/grant/AppOps snapshots were identical, including cycle boundaries. All temporary entries, bodies, private reports, expected files and scratch cleanup proofs passed. Each lifecycle cycle reused the installed diagnostics APK; zero harness installations.

Four user-17 bound rows remain BLOCKED by unchanged permissions. Dynamic external-state and unavailable observations remain limitations, never promoted to PASS. This measures display/linked parity and capture lifetime on the recorded binary; no platform verdict or broader device compatibility is claimed. Device Factory firmware/HAL approximations and the new operations UI are not physically verified by this run.

Root cause: the native bridge released its last local class-loader reference while installed native trampolines retained code addresses. A preceding failed run crashed after compacting GC with zeroed method storage. A strong JNI global reference now retains the loader for the process lifetime, including partially installed callbacks. Host API/debug mode are also taken from the real ART process before profile rewriting. The preceding failed evidence is included as clearly labelled redacted prior-failure material.

Local gate: app 598, module 40, each probe flavor 109, forge 735, schema 81, probe host tools 7: 1,679 passed, zero skips, no compiler warnings. Debug/release APK and module packaging passed. CI has action-version/cache warnings; these are not compiler warnings.

Assets and source provenance: `build-provenance.json`, `signed-artifact-verification.json`, `installation-artifacts.json`, `installation-preservation-proof.json`. Raw private tombstone memory and APK binaries are excluded from this evidence bundle.
