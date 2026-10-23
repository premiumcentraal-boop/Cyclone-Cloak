from pathlib import Path
import importlib.util,sys,json,hashlib,shlex,time,uuid,zipfile,subprocess
R=Path('C:/Users/Agent/Cyclone/cloak-repo'); PRIOR=R/'docs/runtime-evidence/2026-10-23-020207-alpha27-root-companion-cycle'
O=Path(__file__).resolve().parent
spec=importlib.util.spec_from_file_location('prior_ops',PRIOR/'boot_gate_ops.py');b=importlib.util.module_from_spec(spec);spec.loader.exec_module(b)
b.O=O;b.sp.OUT=O
pc=b.pc; TARGET='com.android.settings'; COMPONENT='com.android.settings/.Settings$MyDeviceInfoActivity'
save=b.save;shell=b.shell;root=b.root;cmd=b.cmd

def target_state():
 from restoration_evidence import package_permissions,appop_modes
 p=shell('dumpsys package '+TARGET);grants,install=package_permissions(p);modes={}
 for u in (0,17):
  import re
  uid=re.search(r'^package:com\.android\.settings uid:(\d+)$',shell(f'pm list packages --user {u} -U {TARGET}'),re.M)[1]
  modes[str(u)]=appop_modes(shell(f'cmd appops get --user {u} {TARGET}'),shell('cmd appops get '+uid))
 path=shell('pm path --user 17 '+TARGET).strip().removeprefix('package:')
 return dict(package=TARGET,apk_sha256=root('sha256sum '+shlex.quote(path)).split()[0],grants=grants,install_grants=install,appops=modes)

def install():
 states={str(u):shell(f'am get-started-user-state {u}').strip() for u in (0,17)};save('preflight-users.json',dict(states=states,current=shell('am get-current-user').strip(),devices=cmd('devices','-l')))
 if set(states.values())!={'RUNNING_UNLOCKED'}:raise RuntimeError('USERS_NOT_UNLOCKED')
 info=json.loads((PRIOR/'artifact-agreement.json').read_text());archive=PRIOR/'instrumented-module.zip'
 if hashlib.sha256(archive.read_bytes()).hexdigest()!=info['selected_zip_sha256']:raise RuntimeError('ZIP_HASH_MISMATCH')
 save('artifact-agreement.json',info)
 (O/'instrumented-module.zip').write_bytes(archive.read_bytes())
 save('production-before.json',b.snapshot('production-before.json'));save('target-before.json',target_state())
 if json.loads((O/'production-before.json').read_text())['apk_sha256']!=info['installed_apk_sha256']:raise RuntimeError('PRODUCTION_APK_CHANGED')
 raw,index=pc.Device(b.A,b.S,45).index()
 if f'17/{TARGET}' in index['entries']:raise RuntimeError('TARGET_ALREADY_BOUND')
 save('index-before.json',index);(O/'index-before.bytes').write_bytes(raw.encode())
 save('preflight-package.json',dict(component=shell('cmd package resolve-activity --brief --user 17 -a android.settings.DEVICE_INFO_SETTINGS'),version=shell('dumpsys package dev.cyclone.cloak').split('versionCode=')[1].splitlines()[0],selected_app=TARGET))
 save('module-before.json',dict(paths=root('for p in /data/adb/modules/cyclone_cloak /data/adb/modules_update/cyclone_cloak; do if [ -e "$p" ]; then echo PRESENT:$p; else echo ABSENT:$p; fi; done')))
 remote='/data/local/tmp/cloak-realapp-'+O.name+'.zip';save('module-staging.json',dict(path=remote))
 cmd('push',str(archive),remote)
 if root('sha256sum '+remote).split()[0]!=info['selected_zip_sha256']:raise RuntimeError('STAGED_ZIP_MISMATCH')
 save('module-install.json',dict(output=root('magisk --install-module '+remote,timeout=120),at=time.time()));root('rm -f '+remote)
 save('boot-before.json',dict(id=shell('cat /proc/sys/kernel/random/boot_id').strip(),at=time.time()))
 cmd('reboot',timeout=15);print('INSTALL_REBOOT_REQUESTED',flush=True)

def overlay(remove=False):
 pc.PACKAGE=TARGET
 manifest=O/'manifest.json'
 if manifest.exists():m=json.loads(manifest.read_text())
 else:
  body=json.loads((R/'android/probe/src/main/assets/profile-b.json').read_text());m=dict(run_id=uuid.uuid4().hex,selections=[dict(kind='fixture',fixture='b',user=17,profile_id=body['id'],sha256=pc.digest(body))],target_package=TARGET)
  save('manifest.json',m)
 h=pc.Harness(O,m,pc.Device(b.A,b.S,45),PRIOR/'original-installed.apk')
 save('overlay-remove.json' if remove else 'overlay-add.json',h.overlay(remove=remove))
 if not remove:
  _,idx=h.d.index();entry=idx['entries'][f'17/{TARGET}'];body=root(f'cat {pc.STATE}/{entry["key"]}/profile.json')
  digest=hashlib.sha256(body.encode()).hexdigest()
  save('published-body-verification.json',dict(entry=entry,canonical_sha256=m['selections'][0]['sha256'],published_sha256=digest))
  if digest!=m['selections'][0]['sha256']:raise RuntimeError('PUBLISHED_BODY_HASH_MISMATCH')

def observe(n):
 b.ensure_user(17)
 before=shell('pidof '+TARGET,check=False).strip();shell(f'am force-stop --user 17 {TARGET}')
 start=time.time();launch=cmd('shell',f'am start --user 17 -n '+shlex.quote(COMPONENT)+' --activity-clear-top --activity-single-top',check=False)
 time.sleep(3)
 pid=shell('pidof '+TARGET,check=False).strip();save(f'capture-{n}-launch.json',dict(at=start,component=COMPONENT,user=17,pid_before=before,pid_after=pid,stdout=launch))
 if not pid:raise RuntimeError('REAL_APP_NO_PROCESS')
 pids=pid.split();p=pids[0]
 log=shell(f'logcat -d --pid={p} -t 1000 -v threadtime',check=False);(O/f'capture-{n}-logcat.log').write_text(log,encoding='utf8')
 ui=shell('uiautomator dump /dev/tty',check=False,timeout=35);(O/f'capture-{n}-ui.xml').write_text(ui,encoding='utf8')
 (O/f'capture-{n}-screen.png').write_bytes(cmd('exec-out','screencap','-p',binary=True))
 records=root('cat /data/adb/modules/cyclone_cloak/load-records.jsonl',check=False);all_records=[json.loads(s) for s in records.splitlines() if s.strip()]
 expected=json.loads((O/'artifact-agreement.json').read_text())['selected_abi_sha256']['zygisk/arm64-v8a.so']
 matched=[v for v in all_records if v.get('target_package')==TARGET and v.get('android_user_id')==17 and str(v.get('pid')) in pids and v.get('module_artifact_sha256')==expected]
 save(f'capture-{n}-load-record.json',dict(matched=matched,all_records=all_records))
 save(f'capture-{n}-maps.json',dict(pid=p,mappings=[s for s in root(f'cat /proc/{p}/maps',check=False).splitlines() if any(v in s.lower() for v in ('cloak','zygisk','pine','shadowhook'))]))
 if not matched:raise RuntimeError('DURABLE_RECORD_MISSING')
 print('CAPTURE_COMPLETE',n,pid,flush=True)

def remove():
 overlay(True);shell(f'am force-stop --user 17 {TARGET}');b.ensure_user(0)
 root('test -d /data/adb/modules/cyclone_cloak; touch /data/adb/modules/cyclone_cloak/remove')
 save('module-removal-request.json',dict(at=time.time(),method='standard Magisk remove marker'))
 cmd('reboot',timeout=15);print('REMOVAL_REBOOT_REQUESTED',flush=True)

def final():
 b.bootcheck('removed');b.ensure_user(0)
 paths=root('for p in /data/adb/modules/cyclone_cloak /data/adb/modules_update/cyclone_cloak; do if [ -e "$p" ]; then echo PRESENT:$p; else echo ABSENT:$p; fi; done')
 save('module-removal-proof.json',dict(paths=paths))
 after=b.snapshot('production-after.json');before=json.loads((O/'production-before.json').read_text())
 ta=target_state();save('target-after.json',ta);tb=json.loads((O/'target-before.json').read_text())
 save('production-comparison.json',dict(exact_equal=before==after,changed_fields=[k for k in before if before[k]!=after.get(k)],target_permissions_appops_equal=ta==tb,target_changed_fields=[k for k in tb if tb[k]!=ta.get(k)]))
 m=json.loads((O/'manifest.json').read_text());key=pc.digest([m['run_id'],17]);_,idx=pc.Device(b.A,b.S,45).index()
 cleanup=dict(binding_absent=f'17/{TARGET}' not in idx['entries'],body_absent=root(f'if [ ! -e {pc.STATE}/{key} ]; then echo ABSENT; fi').strip()=='ABSENT',scratch_absent=shell(f'if [ ! -e /data/local/tmp/cloak-profile-check-{m["run_id"]} ]; then echo ABSENT; fi').strip()=='ABSENT',module_absent='PRESENT:' not in paths,current_user=shell('am get-current-user').strip())
 save('cleanup.json',cleanup)
 if before!=after or ta!=tb:raise RuntimeError('FINAL_AUDIT_CHANGED')
 if not all(v for k,v in cleanup.items() if k!='current_user'):raise RuntimeError('CLEANUP_INCOMPLETE')
 print('FINAL_AUDIT_EQUAL_CLEANUP_COMPLETE',flush=True)

if __name__=='__main__':
 try:
  mode=sys.argv[1]
  if mode=='install':install()
  elif mode=='boot':b.bootcheck('installed');b.inspect_loaded()
  elif mode=='bind':overlay()
  elif mode=='capture':observe(int(sys.argv[2]))
  elif mode=='remove':remove()
  elif mode=='final':final()
 except Exception as e:
  save('failure-'+sys.argv[1]+'.json',dict(error=type(e).__name__,reason=str(e)[:200],at=time.time()));raise
