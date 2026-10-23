# Candidate runtime validation — stopped at cycle 1

Result: NOT VALIDATED. Eight captures completed; cold restoration and raw index-byte audit failed. Cycles 2–3 not run.

Candidate source: `1c9ce900930b7c52d3d7130b8ca8a741e183b73a`. Retained metadata: alpha.27/code 51; this is not the published alpha.27 binary. No release or tag.

| Capture | Scenario | Legacy P/F/O/B | Linked P/F | Envelope P/O/M |
|---|---|---|---|---|
| user-0-baseline-cold | A | 32/37/12/8 | 6/7 | 5/7/0 |
| user-0-bound-cold | A | 76/0/11/2 | 13/0 | 4/3/8 |
| user-0-post-removal-cold | A | 32/37/12/8 | 6/7 | 5/7/0 |
| user-0-post-removal-warm | A | 76/0/11/2 | 13/0 | 4/3/8 |
| user-17-baseline-cold | B | 22/46/9/12 | 6/7 | 4/7/0 |
| user-17-bound-cold | B | 75/0/8/6 | 13/0 | 4/3/8 |
| user-17-post-removal-cold | B | 22/46/9/12 | 6/7 | 4/7/0 |
| user-17-post-removal-warm | B | 75/0/8/6 | 13/0 | 4/3/8 |

Unbound fixture-comparison FAIL counts are expected. Restoration is a separate comparison of same-user cold actual hashes. Observations, blocks and unavailable values are not passes.

Failures:
- User 0: packages.visible changed at cold restoration.
- User 17: packages.visible and settings.unrelatedControl changed at cold restoration.
- Published index raw SHA-256 changed from c7d97e4fa4264caeedce207e2786349f1e8045a287b96f06cac729eb21aa80e6 to 409d6d5386be07a6b14b888872cb8467feeb1bf5c21647e716c90bd8a57ba99c. Canonical index, all 327 entries, bodies, shares and binding stores match. The existing temporary overlay path reserializes JSON; byte preservation was not proven. No repair or normalization performed afterward.

Safety:
- Both users’ temporary binding entries, bodies, private reports/expectations and scratch files removed; cleanup proof passes.
- Saved profile files unchanged across the whole run.
- Module file changes are the expressly authorized candidate update; native library hashes match the signed module ZIP after the single authorized reboot.
- Serialized permissions/AppOps snapshots are identical before/after; no grant/reset commands issued. However the existing snapshot parser overwrites runtime grant dictionaries when later dumpsys User sections repeat. The independent current snapshot records four grants for user 0 and four denials for user 17; complete before/after grant-flag preservation is therefore NOT PROVEN by these snapshots.
- No optional Integrity request, account operation, data clearing, uninstall, release or tag.

Limitations:
- Bound envelope: GPU renderer, display real-size and two SIM state rows match on both users; 3 observations and 8 missing rows per user. Sensors, camera, free storage, package versions, WebView and DRM are not proven by these fixtures. WebView default-UA returned AndroidRuntimeException.
- Current fixture source is Device Factory pixel_8/pixel_7 schema 0.4; catalog approximations remain approximations. A/B are scenario names; report fixture is correctly external.
- Initial attempt in runtime-lifecycle failed before rows because the evidence runner incorrectly requested built-in fixture labels for custom Factory profiles. Preserved unchanged; corrected evidence runner in runtime-lifecycle-external. No product change.
- Bounded fixed-reason module-log collection returned no matching lines; module installation plus scoped observed effects do not prove every callback loaded.
- Further cycles, repeat-cold proof, Play evidence capture and service-isolation device UI checks were not attempted after the failing lifecycle gate. Existing local tests pass; they do not replace device evidence.

No platform certification, permanent trust, hardware equivalence or broader compatibility is claimed.
