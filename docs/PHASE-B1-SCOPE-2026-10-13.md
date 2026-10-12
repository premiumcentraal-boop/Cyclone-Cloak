# Phase B1 scope — RC3 runtime repair (2026-10-13)

## Where we stand

Phase A (RC2 device baseline) is complete and pushed (`05393f7`):

- Evidence: `docs/runtime-evidence/rc2-api36-baseline.json` (module arm64 SHA-256
  `2ec607f9...c5a528`, user 13, Pixel 8 / API 36).
- Matrix: **45 VERIFIED / 11 FAIL / 8 MATCH_ONLY / 3 BLOCKED / 3 UNMAPPED** out of 70 reads.
- Bound runs measure PASS=55/70; `bound-a` == `bound-a-repeat`; unbound controls restore exactly.
- The old alpha.1 baseline (13 verified) is superseded; no compatibility claim is inherited.

This scope defines the next release, **v0.10.0-rc.3 (code 24)**, as Phase B
batch 1 of the dirty-core plan: evidence-driven runtime repair in one focused
build, verified against the fresh RC2 baseline.

## Release composition

RC3 carries three workstreams, in this order:

1. **W1 — land the pending binding-flow work.** The working tree already holds
   the multi-app binding flow fix (BindingFlow, BindingFlowScreen, view model,
   both entry screens, tests, docs). Commit it as-is after running its local
   test script (`tools/run-local-binding-tests.ps1`). No further development.
2. **W2 — native property interception (5 FAILs).** The largest cluster.
3. **W3 — display metrics/refresh + DRM deviceUniqueId (2 clusters + 1 diagnosis).**

Out of scope for RC3: privileged probe variant for the 3 BLOCKED permission
gates (batch 2), camera/sensor schema (Phase D), engine extraction (Phase C),
posture adapter (Phase E), any Cyclone-side contract change.

## W2 — native property reads

**Problem.** The module hooks `android.os.SystemProperties` at the Java level
(RegisterNatives), so `properties.java.*` reads pass. The probe also reads
`__system_property_get` directly from its own JNI library (`probe.cpp`), and
those five rows — `ro.product.model`, `ro.build.version.sdk`,
`ro.build.fingerprint`, `ro.serialno`, `persist.sys.timezone` — still return
host values. Real apps read properties the same native way, so this is not a
probe artifact.

**Design.**

1. Add an inline-hook engine using the same embed pattern as Pine: declare a
   `hookEmbed` dependency on bytedance ShadowHook (MIT, arm64 + armv7,
   API 23+), extract `libshadowhook.so` per ABI in `prepareHookPine`-style
   tasks, embed both libs in the module zip next to `libpine.so`, and
   `dlopen` it from `cloak.cpp` at module init. Fail soft: if the library or
   symbol is unavailable, log and continue with the Java surface only.
2. At `preAppSpecialize`, after a profile binds (`g_active == true`), call
   `shadowhook_init` (unique mode) and hook `__system_property_get` in
   `libc.so`. The replacement calls the saved original pointer first, then —
   only while `g_active` — overwrites the buffer with the matching value from
   `g_props` and returns its length. The module's own `read_prop` must call
   the original pointer directly (no recursion).
3. Guard lengths: `PROP_VALUE_MAX` is 92 on modern Android. Values >= 92
   bytes are skipped by the hook; extend forge validation to reject prop-fed
   fields longer than 91 bytes so profiles never depend on truncation.
4. `__system_property_read_callback` (the API 30+ handle-based path) is a
   stretch goal behind its own evidence: ship it only if the plain-get hook
   verifies cleanly and the callback wrapper passes unit tests; otherwise it
   moves to batch 2 with an explicit unmapped reason.

**Verification.** The five failing rows must flip to PASS in the bound runs,
which upgrades them to VERIFIED in the rendered matrix (they already have
distinct A/B expectations and repeat/restore proof from the baseline run).

## W3a — display metrics and refresh

**Problem.** `kDisplayProps` maps only `density → ro.sf.lcd_density`. Width,
height and `refresh_rate_hz` exist in profiles but are never rendered. The
probe reads `Resources.getDisplayMetrics()`, `Display.getRealMetrics()` and
`Display.getRefreshRate()`. Profile A's height match was coincidental
(host 1080x2400 == fixture A); profile B (1080x2340 @ 120 Hz) fails height and
both profiles fail refresh.

**Design.**

1. C++: extend `kDisplayProps` with
   `{"width", "cloak.display.width"}, {"height", "cloak.display.height"},
   {"refresh_rate_hz", "cloak.display.refresh"}` so the profile's display
   block flows through the same property surface as every other block.
2. HookBridge: add `android.view.Display` hooks via the existing Pine
   plumbing — `getMetrics(DisplayMetrics)` and `getRealMetrics(DisplayMetrics)`
   in afterCall (overwrite widthPixels/heightPixels/densityDpi from the
   `cloak.display.*` props) and `getRefreshRate()` returning the profile value.
   Instance-method handling follows the established MediaDrm/WifiInfo pattern.
3. Honest expectation: if `Resources.getDisplayMetrics()` keeps serving a
   configuration-time copy, `display.resources.*` may still fail while
   `display.real.*` passes. In that case add the measured result to the ledger
   as-is and queue a `WindowMetrics`/resources-path hook for batch 2 rather
   than claiming coverage.

**Verification.** `display.real.height` and `display.refresh` must pass for
both fixtures; `display.resources.height` for B is the honest-open row.

## W3b — MediaDrm deviceUniqueId

**Diagnosis from code (no device needed):** the fixture carries
`identifiers.widevine_id` (64-hex). The probe obtains the byte array and
hex-encodes the **raw bytes**. `DeviceIdHook` currently returns
`hexBytes(prop(PROP_WIDEVINE))` — if that helper encodes the hex string as
ASCII bytes, the probe's re-encoding produces hex-of-ASCII (128 chars) and
mismatches. The fix is to decode the vault hex back into 32 raw bytes before
`setResult`, so probe-hex(bytes) == the stored identifier. Confirm with a
HookBridgeTest unit test, then with the device probe row.

If the hook never fires at runtime instead (logcat check during the probe run
settles this), record the finding and treat the row as blocked-with-reason
rather than forcing a workaround in the same batch.

## Build and release sequence

1. Commit W1 (binding flow) after its local tests; keep this commit separate.
2. Implement W2 (C++ + gradle embed + unit tests for prop-resolution edge
   cases: empty value, over-long value, unbound process, recursion guard).
3. Implement W3a + W3b (Java hooks + tests in `HookBridgeTest`).
4. Run: module + app unit tests, forge pytest, binding local tests.
5. Device (owner-approved install + reboot): install RC3 app + module on the
   test phone, confirm Root Doctor agreement, re-run the five-phase probe on
   user 13, render the matrix, update `RUNTIME-COVERAGE.md`.
6. Bump to `v0.10.0-rc.3` / code 24, build signed APK + module ZIP, publish
   the GitHub release with notes distinguishing measured-verifier from
   generated surface (no claims beyond probe evidence).

## Exit criteria

- 45 VERIFIED strictly increases; no regression on any previously verified row.
- Each repaired surface has device evidence from the RC3 binary itself.
- Every remaining row is PASS, FAIL-with-diagnosis, BLOCKED-with-reason or
  UNMAPPED-with-plan (batch 2 / Phase D).
- Two distinct profiles stable across cold starts; unbound controls restored.
- CI green; release artifacts attached; ledger and matrix updated in the same
  push.

## Risks

| Risk | Mitigation |
| --- | --- |
| New native-hook dependency destabilizes processes | Fail-soft init, unique mode, kill-switch via module.prop flag; probe run immediately after install |
| Inline hook vs future Android updates | ShadowHook is the maintained standard; isolate behind one wrapper translation unit |
| Resources DisplayMetrics is a config-time copy | Accept partial pass, queue WindowMetrics hook; never claim what the probe does not show |
| PROP_VALUE_MAX truncation | Forge validation cap at 91 bytes before release |
| Device work windows | Install/reboot only with owner approval; probe itself is non-destructive and self-cleaning |
