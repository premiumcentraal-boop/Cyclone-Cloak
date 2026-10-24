# RC2 candidate for the package-list fix — install gate and retry

Follow-up to `HANDOFF-2026-10-24-PACKAGE-LIST-FIX.md`. That handoff was correct and also
incomplete: rebuilding diagnostics could never deploy a module-side fix, so the S8 retry in
`2026-10-24-153604-rc1-smoke` was guaranteed to reproduce `DENIED:Unavailable`. This handoff
carries the module (and app) that actually contain the fix.

## What changed on top of `e2b8521`

- `android/module/module.prop` and `android/app/build.gradle`: candidate version
  `v1.0.0-rc.2` / code `61`, so the candidate is distinguishable from the published RC1 pair.
- `tools/run_probe_reversibility.py`: `--expected-version` now accepts an explicit
  `version:code` pair. A bare `v1.0.0-rc.1` still resolves to the RC1 pair, so existing
  invocations are unaffected. Without this, testing any candidate needed a code edit.

No runtime behaviour beyond the package fix, no schema change, no workflow change.

## Artifacts built locally from `main`

| Artifact | Path | SHA-256 |
|---|---|---|
| module zip | `android/module/build/module/cyclone-cloak-1.0.0-rc.2.zip` | `646402ea3a9d781c63399ab8fda67872bd40f98aecc60ec9eeea0a7fa6833027` |
| app (unsigned) | `android/app/build/outputs/apk/release/app-release-unsigned.apk` | `ef56c3c928297776cc24a982c7c12968511becbe75fcced542e416415188508e` |
| diagnostics probe | `android/probe/build/outputs/apk/diagnostics/debug/probe-diagnostics-debug.apk` | `0548e44fd16ae5791b3ddd4fc657c3882e2153d13f878fe1d097212709ce4a28` |

The probe APK is unchanged from the last run and still valid: the fix is not in the probe. The
app APK is unsigned locally, so install a CI-signed RC2 build, or sign it with the existing
signer before installing. Package weights: module zip 2,050,661 bytes, app 44,703,486 bytes.

## Install gate

1. Install the RC2 app and module. Module via `su --mount-master -c 'magisk --install-module <zip>'`.
2. Record installer exit code and output, then reboot once.
3. Confirm both users are unlocked and that installed module/app report `rc.2` / `61`.
4. Pin the **CI-built** arm64 zygisk hash from that signed run. The local build hash
   `45a2788d…` did not reproduce in CI (`5b4c6d01…`), so the local value is not authoritative;
   differences include both native libraries, not just ZIP timestamps. `--expected-module-hash`
   now enforces the pin in the runner instead of leaving it in prose. An installed-module file
   hash is still not in-process binary proof.

Only then rerun S8, with `--expected-version v1.0.0-rc.2:61 --expected-module-hash <CI arm64 sha256>`.

The version pair takes the tag with its `v`; the runner strips it for the app `versionName`, which
carries no prefix. The module `version` in `module.prop` does keep the `v`.

## Expected result

The bound package-list row should reach `NOT_PRESENT` for both users: the probe's own package is
exempted and the `Unavailable` exception no longer escapes the hook. `FOUND:` means a real
package sighting; `DENIED:` means an exception still escapes.

## Verification

forge 1139, app suite, module 80, diagnostics probe build — all green on this tree. Device
behaviour is still unproven until the gate and the run complete. Do not relax `NOT_PRESENT` and
do not promote any row to VERIFIED.
