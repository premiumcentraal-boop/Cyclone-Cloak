from pathlib import Path
import os,sys,json,hashlib
p=Path(Path(os.environ['TEMP'],'cloak-alpha24-stabilization.txt').read_text().strip())
sys.path.insert(0,str(Path.cwd()/'tools'))
import profile_check as pc
D=pc.Device('C:/Users/Agent/android-sdk/platform-tools/adb.exe','3B171FDJH0061G',30)
raw=D.shell('dumpsys activity exit-info '+pc.PACKAGE).stdout
blocks=__import__('re').split(r'ApplicationExitInfo #\d+:',raw)[1:]
pc.save(p/'historical-exit.json',dict(pid=12996,matching_records=[s.strip() for s in blocks if 'pid=12996 ' in s],limitation='Process no longer exists; original maps and matching tombstone unavailable after rotation. Fresh reproduction PID 8984 is documented separately.'))
q=p/'repeated-warm';private=q/'private';private.mkdir(exist_ok=True);(private/'.gitignore').write_text('*\n')
pb=D.root('cat /data/tombstones/tombstone_06.pb',binary=True,check=False).stdout
if pb:
 (private/'matching-tombstone.pb').write_bytes(pb)
 pc.save(q/'tombstone-pb-provenance.json',dict(device_path='/data/tombstones/tombstone_06.pb',bytes=len(pb),sha256=hashlib.sha256(pb).hexdigest(),included_in_shareable_bundle=False,reason='Binary tombstone may contain raw memory; retained locally only. Text tombstone has matching PID/package/time.'))
for name in ('focused','repeated-warm'):
 q=p/name
 before=json.loads((q/'audit-before.json').read_text());after=json.loads((q/'audit-after.json').read_text())
 perm1=json.loads((q/'permissions-before.json').read_text());perm2=json.loads((q/'permissions-after.json').read_text())
 pc.save(q/'safety-proof.json',dict(production_delta=[] if before==after else ['AUDIT_CHANGED'],permission_delta=[] if perm1==perm2 else ['SNAPSHOT_DIFFERENCE_REQUIRES_POLICY_CHECK'],production_exact_equal=before==after,permissions_exact_equal=perm1==perm2,production_entries_before=before['production_entry_count'],production_entries_after=after['production_entry_count'],cleanup=json.loads((q/'cleanup-proof.json').read_text())))
text='''# Alpha.24 warm-capture stabilization — blocked

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
'''
(p/'summary.md').write_text(text,encoding='utf-8')
pc.save(p/'candidate-provenance.json',dict(commit='a93d8643c51463dff1d4fb3258a383432012f989',version='0.11.0-alpha.24',code=48,diagnostics_apk_sha256='2e43ed4ea4129ceb60e0abd5146d9ea33ec4688958a3fc500ef7d4926fe1a9b7',installed_module_arm64_sha256='8a529beae4f1e164405a8956581806cd85878b30a2c1b1336639acbb6178bfcc',prior_signed_validation=dict(url='https://github.com/premiumcentraal-boop/Cyclone-Cloak/actions/runs/39158498518',commit='a93d8643c51463dff1d4fb3258a383432012f989',conclusion='success'),limitation='Prior signed validation covers unchanged candidate product sources, not current uncommitted runner/tests. No new signed validation or release on failed device gate.'))
print(p)
