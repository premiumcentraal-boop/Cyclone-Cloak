# Cyclone companion cycle fix brief — 2026-10-16

## Where the run stopped

- Device: Pixel 8, Android 16, Android user 17.
- Installed companion build: `0.11.0-alpha.13`, version code 37.
- Main branch: `0636471` on top of release tag `v0.11.0-alpha.13`.
- The live binding run was interrupted while the debug build was being prepared. No new APK was built or released.
- Local tree currently has one code change plus this evidence/handoff material. The change is described below.

## What actually failed

### 1. The large binding run was not blocked by approval

The run journal and logcat show the elevated preflight completed quickly. The
failure happened later, during the publication step.

Evidence from the retry run:

```text
preflight: PRIVILEGE_GRANTED
publication: started 01:36:36.960
staging-validation: completed 01:36:37.480
publication result: DEGRADED / ROOT_TIMEOUT after 30026 ms
```

So the real failure is the 25-second publication budget expiring while the script
is still working. The UI wording made this look like a missing approval, but the
approval was not the blocker.

### 2. Why the publication step is slow

`mergePublishScript()` copies the staging tree once, then copies every per-app
key again into the merged temporary state tree. With about 327 enabled targets,
that means hundreds of directory copies and exceeds the current timeout.

Current implementation:

- [CloakRootDoctor.kt](/C:/Users/Agent/Cyclone/cloak-repo/android/app/src/main/java/dev/cyclone/cloak/root/CloakRootDoctor.kt)
- The live call uses `25_000` ms.
- The script performs a full staging copy, then another per-key copy during merge.

The local working tree already has this minimal mitigation:

```kotlin
runRootScript(publishScript(...), 120_000)
```

It has not been built, installed, tested, committed, or released yet.

### 3. Health screen crash

Opening the Health tab reproducibly killed the companion process with a native
`SIGSEGV`. The crash trace pointed at `DecorView.onMeasure` after about three
seconds.

A diagnostic test strongly suggests the companion app is being processed by its
own module runtime. When `17/dev.cyclone.cloak` was temporarily removed from
`/data/adb/cyclone_cloak/state-v1/index.json` and the app restarted, the Health
screen no longer crashed.

That manual state change is not a code fix. The companion package should be
excluded from its own runtime package set at the code level, and the change
should be covered by a test.

## Current on-device state

- The module state tree exists and contains user-17 bindings.
- The elevated preflight succeeds.
- Health currently reports degraded readiness, mostly because root/publication
  checks have not completed yet.
- Binding history reports one `needs-attention` run with `ROOT_TIMEOUT` and
  `INTERRUPTED`.
- Removing the companion self-entry from the module index was a diagnostic only;
  it should not be treated as a durable fix.

## Evidence captured

All of this is under
[docs/runtime-evidence/2026-10-16-alpha13-run](/C:/Users/Agent/Cyclone/cloak-repo/docs/runtime-evidence/2026-10-16-alpha13-run):

- `binding-run-last.json`
- `recent-diagnostics.jsonl`
- `tombstone-health-sigsegv-31.txt`
- `crash-logcat.txt`
- `module-index.json`
- `device-getprop.txt`
- `logcat-run.txt`
- `pending.json`

## Suggested release goal

Fix the two blockers and publish one stable companion/module update:

1. Keep the raised publication budget for now.
2. Reduce the publication work so hundreds of targets complete comfortably.
3. Exclude the companion app from being processed by its own module runtime.
4. Make the Health screen stable.
5. Add focused tests for both the timeout path and the self-exclusion behavior.
6. Release as the next companion/module prerelease only after app, module, and
   local schema tests pass.

## Worker prompt

Use the following wording when handing this to the delegated worker.

```text
We are close to a stable companion release, but two reliability issues showed up in live testing.

1. A large profile with about 327 app entries times out during the state publication step. Logs show the permission preflight passes quickly; the 25-second publication budget is what expires. The publish script copies the staging tree and then copies each per-app directory again, which is too slow at this scale. Please raise the publication budget to 120 seconds as an immediate mitigation, and if it is low-risk, reduce redundant copying in the merge path. Add a focused test that covers a many-entry publication payload.

2. The Health screen reproducibly crashes with a native SIGSEGV after about three seconds. Evidence strongly suggests the companion app is being processed by its own module runtime. Please add a code-level guard so the companion package is excluded from its own runtime package set, then verify that Health remains stable. Add a focused test for that behavior.

Please keep the changes tight and do not change schemas, storage formats, connector behavior, or public APIs. Preserve existing binding history. Add only the diagnostics needed to prove the fixes. Run the full Android/module test suites, build both app and module artifacts, and prepare the next prerelease notes. Do not publish until the usual test and packaging checks are green.

Repo: C:/Users/Agent/Cyclone/cloak-repo
Branch: main
Latest release: v0.11.0-alpha.13
```
