import hashlib,json,sys,shlex,subprocess,time
from pathlib import Path
from zipfile import ZipFile,ZIP_DEFLATED
out=Path(sys.argv[1]);sys.path.insert(0,str(Path.cwd()/'tools'));import profile_check as pc;import run_probe_reversibility as rr
d=pc.Device('C:/Users/Agent/android-sdk/platform-tools/adb.exe','3B171FDJH0061G',90)
metadata={}
for u in [0,17]:
 path=f'/data/user/{u}/dev.cyclone.cloak/no_backup/cloak-bindings-v1.json'
 r=d.root('if [ -f '+shlex.quote(path)+' ]; then stat -c "%Y %s" '+shlex.quote(path)+'; sha256sum '+shlex.quote(path)+'; fi')
 metadata[str(u)]={'path_sha256':hashlib.sha256(path.encode()).hexdigest(),'mtime_size_and_content_sha256':r.stdout.strip()}
pc.save(out/'binding-store-visibility.json',metadata)
_,idx=d.index()
proof={'probe_captures_started':False,'temporary_bindings_created':False,'temporary_profile_bodies_created':False,'temporary_private_files_created':False,'diagnostics_entries_absent':{str(u):f'{u}/{rr.PACKAGE}' not in idx['entries'] for u in [0,17]},'module_install_scratch_removed':d.root('test ! -e /data/local/tmp/cloak-alpha23-validation.zip',check=False).returncode==0,'reboot_count_requested':1}
pc.save(out/'cleanup-proof.json',proof)
pre=json.loads((out/'pre-update-production-audit.json').read_text());post=json.loads((out/'post-update-production-audit.json').read_text());perm=json.loads((out/'update-preservation-proof.json').read_text())
result={'result':'STOPPED','failed_phase':'post-update-production-preservation-audit','reason':'NEWLY_ACCESSIBLE_BINDING_STORE_NOT_IN_BEFORE_AUDIT','production_delta':rr.snapshot_delta(pre,post),'permission_delta':perm['permission_delta'],'unchanged_components':[k for k in pre if pre[k]==post[k]],'binding_store_metadata':metadata,'cleanup':proof,'physical_parity':'NOT_RUN','lifecycle_cycles_completed':0,'limitation':'User 17 was stopped during before-audit. Its credential-encrypted binding store became accessible after unlock; no before content hash exists, so unchanged contents cannot be proved. No binding writer or temporary overlay was run.','platform_verdict':False}
pc.save(out/'result.json',result)
lines=['# Alpha.23 physical validation — stopped','','Tag: v0.11.0-alpha.23; commit: 0d028a947b37013e0ad89a98d1455a79c33bfd3d','Installed companion/module: 0.11.0-alpha.23 / 47','APK SHA-256: 8b605d985e97e31af32cce6263fc7cb4a94bb936f83b8b077d88be714d61c3cd','Module ZIP SHA-256: 7bde12d4780c7398175a9c57819ed0831ae25ca0d4e9b1c17a26e6f067ac5ba0','','Result: STOPPED before probe bindings/captures.','Failing phase: post-update production-preservation audit.','Exact delta: accessible binding stores increased from 1 to 2 after Android user 17 started/unlocked. The new file has path hash '+metadata['17']['path_sha256']+'.','Existing published entries, bodies, publisher shares, module files, and previously accessible store hashes are unchanged. Permissions/AppOps delta: none.','The before-audit saw user 17 stopped; its store was inaccessible. Its unchanged contents cannot be proved from that baseline. This is an evidence completeness failure, not a confirmed binding mutation.','','Actions completed: exact release download/hash/signature/module verification; companion update; normal Magisk module update; one reboot; diagnostics build (107 tests pass); diagnostics-only APK update.','No probe capture, temporary binding, profile-body overlay, connector call, permission change, production binding write, or lifecycle cycle was run.','Cleanup: install scratch removed; no temporary probe entries/bodies/private files created.','Physical linked parity and three-cycle validation remain NOT RUN. No platform verdict.','','Release attachments named build-provenance.json / evidence-bundle.zip / summary.md are historical alpha18 evidence; they are not alpha23 physical results.','No CURRENT-STATE or RUNTIME-COVERAGE success claim, commit, push or release was made.']
(out/'summary.md').write_text('\n'.join(lines)+'\n',encoding='utf8')
with ZipFile(out/'physical-evidence-bundle.zip','w',ZIP_DEFLATED) as z:
 for p in sorted(out.rglob('*')):
  if p.is_file() and p.name!='physical-evidence-bundle.zip' and 'release-assets' not in p.parts:z.write(p,p.relative_to(out).as_posix())
print(json.dumps(result,indent=2));print('BUNDLE_SHA256',hashlib.sha256((out/'physical-evidence-bundle.zip').read_bytes()).hexdigest())
