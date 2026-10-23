from pathlib import Path
import importlib.util,os,sys,json,hashlib,zipfile,shlex,time,uuid
R=Path('C:/Users/Agent/Cyclone/cloak-repo');O=Path(Path(os.environ['TEMP']+'/cloak-rung1a-c-out.txt').read_text(encoding='utf-8-sig').strip());P=R/'docs/runtime-evidence/2026-10-23-020207-alpha27-root-companion-cycle'
spec=importlib.util.spec_from_file_location('prior',P/'boot_gate_ops.py');b=importlib.util.module_from_spec(spec);spec.loader.exec_module(b);b.O=O;b.sp.OUT=O;pc=b.pc;sp=b.sp
C=R/'android/probe/src/main/assets/profile-c.json';A=sp.APK
sp.fixture_for_user=lambda u:'a' if u==0 else 'external'
def prep(u):
 with zipfile.ZipFile(A) as z:body=z.read('assets/profile-a.json' if u==0 else 'assets/profile-c.json')
 result=sp.adb_result('shell',f'run-as {sp.PACKAGE} --user {u} sh -c '+shlex.quote('mkdir -p files; cat > files/expected-profile.json'),data=body.decode())
 if result['exit_code']:raise RuntimeError('EXPECTED_PROFILE_WRITE_FAILED')
 h=hashlib.sha256(body).hexdigest()
 if sp.shell(f'run-as {sp.PACKAGE} --user {u} sha256sum files/expected-profile.json').split()[0]!=h:raise RuntimeError('EXPECTED_PROFILE_HASH_MISMATCH')
 return dict(fixture=sp.fixture_for_user(u),fixture_source='A' if u==0 else 'C',profile_sha256=h,bytes=len(body),source='exact packaged synthetic asset bytes')
sp.prepare_external_fixture=prep
original_launch=sp.lifecycle_launch_command
def launch(u,request):
 return original_launch(u,request).replace('--ez no_permission_prompt true','--ez no_permission_prompt '+('false' if u==17 and not (O/'ui-permissions-completed.json').exists() else 'true'))
sp.lifecycle_launch_command=launch

def overlay(remove=False):
 m=json.loads((O/'manifest.json').read_text());h=pc.Harness(O,m,pc.Device(b.A,b.S,45),A)
 h.body=lambda item:json.loads((R/'android/probe/src/main/assets'/('profile-a.json' if item['user']==0 else 'profile-c.json')).read_text())
 b.save('overlay-remove.json' if remove else 'overlay-add.json',h.overlay(remove))

def install():
 if hashlib.sha256(C.read_bytes()).hexdigest()!='2f7b201e99d58322efbf34663a91d30323f160e939b10882d133c2d3a441831a':raise RuntimeError('FIXTURE_C_SOURCE_HASH_MISMATCH')
 with zipfile.ZipFile(A) as z:
  if z.read('assets/profile-c.json')!=C.read_bytes():raise RuntimeError('PACKAGED_C_MISMATCH')
 zpath=R/'android/module/build/module/cyclone-cloak-0.11.0-alpha.28.zip'
 with zipfile.ZipFile(zpath) as z:expect={n:hashlib.sha256(z.read(n)).hexdigest() for n in z.namelist() if not n.endswith('/')}
 actual={p.removeprefix('/data/adb/modules/cyclone_cloak/'):h for h,p in (s.split(None,1) for s in b.module_files().splitlines())}
 proof=dict(zip_sha256=hashlib.sha256(zpath.read_bytes()).hexdigest(),installed_files=actual,packaged_files=expect,all_packaged_files_match=all(actual.get(k)==v for k,v in expect.items()),apk_sha256=hashlib.sha256(A.read_bytes()).hexdigest(),fixture_c_sha256=hashlib.sha256(C.read_bytes()).hexdigest())
 b.save('artifact-provenance.json',proof)
 if proof['zip_sha256']!='8fd58b4ad0ea60ec06460b0ecaa0b9cea8e56467437975103d04392958a68795' or not proof['all_packaged_files_match']:raise RuntimeError('MODULE_ARTIFACT_MISMATCH')
 b.snapshot('production-before.json');b.save('users-before.json',{str(u):b.shell(f'am get-started-user-state {u}').strip() for u in (0,17)})
 if any(b.shell(f'pm path --user {u} {sp.PACKAGE}',check=False).strip() for u in (0,17)):raise RuntimeError('PROBE_PREEXISTING_KEEP_STATE')
 output=b.cmd('install','--user','0','-r',str(A),timeout=120);b.save('diagnostics-install.json',dict(output=output))
 if 'Success' not in output:raise RuntimeError('INSTALL_FAILED')
 b.shell('cmd package install-existing --user 17 '+sp.PACKAGE)
 info=sp.package_info(sp.PACKAGE);b.save('diagnostics-installed.json',info)
 if any(info[str(u)]['apk_hashes']!=[proof['apk_sha256']] for u in (0,17)):raise RuntimeError('INSTALLED_APK_HASH_MISMATCH')
 _,idx=pc.Device(b.A,b.S,45).index()
 if any(f'{u}/{sp.PACKAGE}' in idx['entries'] for u in (0,17)):raise RuntimeError('EXISTING_PROBE_BINDING')
 m=dict(run_id=uuid.uuid4().hex,selections=[])
 for u in (0,17):
  body=json.loads((R/'android/probe/src/main/assets'/('profile-a.json' if u==0 else 'profile-c.json')).read_text());m['selections'].append(dict(kind='fixture',fixture='a' if u==0 else 'c',user=u,profile_id=body['id'],sha256=pc.digest(body)))
 b.save('manifest.json',m);overlay()
 print('INSTALL_HASHES_AND_OVERLAY_READY',flush=True)

def capture(u,n):
 report=b.capture(u,f'user-{u}-capture-{n}')
 _,idx=pc.Device(b.A,b.S,45).index();entry=idx['entries'][f'{u}/{sp.PACKAGE}'];published=b.root(f'sha256sum {pc.STATE}/{entry["key"]}/profile.json').split()[0]
 canonical=next(v for v in json.loads((O/'manifest.json').read_text())['selections'] if v['user']==u)['sha256']
 b.save(f'user-{u}-capture-{n}-publication.json',dict(published_sha256=published,canonical_expected_sha256=canonical,canonical_match=published==canonical,report_raw_profile_sha256=report['profile_sha256'],fixture_source='A' if u==0 else 'C',report_label=report['fixture']))
 if published!=canonical:raise RuntimeError('PUBLISHED_HASH_MISMATCH')
 b.save(f'user-{u}-permissions-after-capture-{n}.json',sp.package_info(sp.PACKAGE))
 if u==17:b.save('ui-permissions-completed.json',dict(at=time.time()))
 print('CAPTURE_COMPLETE',u,n,report['pid'],flush=True)

def cleanup():
 if (O/'manifest.json').exists() and json.loads((O/'manifest.json').read_text()).get('overlay_active'):overlay(True)
 sp.activate_user(0,sp.record_switch)
 out=b.cmd('uninstall',sp.PACKAGE,timeout=120)
 paths={str(u):b.shell(f'pm path --user {u} {sp.PACKAGE}',check=False).strip() for u in (0,17)}
 private=b.root('for u in 0 17; do for p in /data/user/$u/'+sp.PACKAGE+' /data/user_de/$u/'+sp.PACKAGE+' /storage/emulated/$u/Android/data/'+sp.PACKAGE+'; do if [ -e "$p" ]; then echo PRESENT:$p; else echo ABSENT:$p; fi; done; done')
 b.snapshot('production-after.json');left=json.loads((O/'production-before.json').read_text());right=json.loads((O/'production-after.json').read_text())
 b.save('audit-comparison.json',dict(equal=left==right,changed_fields=[k for k in left if left[k]!=right.get(k)]))
 b.save('cleanup.json',dict(uninstall_output=out,package_paths=paths,probe_directories=private,current_user=b.shell('am get-current-user').strip(),module_unchanged=True))
 print('CLEANUP_COMPLETE',left==right,flush=True)
if __name__=='__main__':
 try:
  if sys.argv[1]=='install':install()
  elif sys.argv[1]=='capture':capture(int(sys.argv[2]),int(sys.argv[3]))
  elif sys.argv[1]=='cleanup':cleanup()
 except Exception as e:b.save('failure-'+sys.argv[1]+'.json',dict(error=type(e).__name__,reason=str(e)[:240]));raise
