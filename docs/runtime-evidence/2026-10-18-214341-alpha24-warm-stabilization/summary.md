# Alpha.24 warm-capture stabilization — blocked

Candidate: `a93d8643c51463dff1d4fb3258a383432012f989`, 0.11.0-alpha.24 / code 48. No tag or release created. No product/runtime changes, installations or reboot in this run.

## Physical result

The first focused bound -> warm-removal capture completed in the same PID 6878 (legacy 82 PASS / 7 OBSERVED, linked 13 PASS). A repeated warm-only reproduction in PID 8984 completed the bound capture and six warm captures, then failed at `user-0-post-removal-warm-repeat-7` with `PROBE_CRASHED`. This is a failed stabilization gate, not a successful lifecycle proof. The complete three-cycle run was intentionally not started.

Crash: 2026-10-18 21:47:08 +0200, PID/TID 8984/8984, diagnostics package, SIGSEGV / SEGV_MAPERR, null read 0x38. Matching tombstone: `/data/tombstones/tombstone_06`. This reproduction faults on the main thread; the prior PID 12996 failure named a probe worker. They share the signal/address signature but identical root cause is not established.

Native stack: `art::ArtMethod::PrettyMethod(bool)+80` -> `art::StackVisitor::WalkStack` -> `art::Thread::DumpJavaStack` -> `art::Runtime::Abort` -> `art::ArtMethod::GetOatQuickMethodHeader` -> `art::NullPointerHandler::IsValidReturnPc` -> `art::NullPointerHandler::Action` -> `[vdso]` -> `/memfd:jit-cache` at 0x249e368. The complete 18-frame stack and memory maps are in `repeated-warm/matching-tombstone.log`. This is an ART fatal/stack-dump path; the initiating hook/method remains unresolved. Pine and ShadowHook are loaded, but this stack does not prove either library caused it.

## Scope investigation

- Native property callback: the wrapper passes a stack-owned callback context through the original property read call; no asynchronous retention or overlay-removal mutation was found in this source path. No evidence establishes it as the crash cause.
- Profile properties and Java bridge are initialized at process specialization, not removed/reinstalled when the temporary overlay is removed. Warm in-memory profile values are therefore expected observations.
- Display supported-mode handling invokes the hooked active-mode reader; construction itself creates a new mode and does not mutate DisplayInfo. No faulting display method is identified by this native stack.
- Probe captures own their session state and reject superseded writes. Repeated workers finish internally before the harness sees their report, but activity recreation/configuration and ART/Pine interaction remain investigation leads.
- No speculative runtime guard or global compiler setting was applied. No warm force-stop fallback was used.

## Preservation and safety

`repeated-warm/` contains bounded full PID-filtered logcat, the matching text tombstone (raw memory dump/ASCII removed), before/after process maps and thread status, module/library hashes, phase markers, precise request metadata, redacted reports, audits, permissions/AppOps snapshots, and cleanup proof. Original text and binary tombstones are retained locally under ignored `private/`, excluded from the shareable bundle. Historical PID 12996 no longer exists and its tombstone has rotated; its retained ApplicationExitInfo record is preserved in `historical-exit.json`.

Both focused runs preserved all 327 production entries and all audited publisher shares, published bodies, module files and accessible binding stores. Permission/AppOps snapshots are identical. Temporary bindings, profile body directories, private report/expectation files and scratch files were removed for users 0 and 17. See each `safety-proof.json` and `cleanup-proof.json`.

## Changed tracked files

- `tools/run_probe_reversibility.py`: fresh package/user/PID-correlated exit classification; `PROBE_CRASHED` versus report timeout, with bounded metadata only.
- `forge/tests/test_probe_reversibility.py`: ten focused classification cases covering native/Java crashes, stale/unrelated exits and malformed evidence.

No runtime code changed. No platform verdict. No release or broader compatibility branch prepared because the physical release gate failed.

## Local verification

All 1,510 tests passed: app 577, module 35, probe standard 109, probe diagnostics 109, forge 604, schema 69, probe host 7. Debug/release APK builds, module packaging, probe builds and release metadata checks passed. No compiler warnings. Both diff checks passed (Git emitted only existing LF-to-CRLF conversion notices). Exact commands: `commands.txt`; results: `local-test-results.json`.

Existing signed validation for unchanged candidate a93d864 passed: https://github.com/premiumcentraal-boop/Cyclone-Cloak/actions/runs/39158498518 . No new signed workflow, commit, push, tag or release after the failed device gate. Runner and focused tests remain uncommitted.
