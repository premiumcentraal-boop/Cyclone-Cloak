"""Run-only evidence orchestration. No production source edits."""
from pathlib import Path
import sys,subprocess,json,hashlib,shlex,time,zipfile,re,uuid
O=Path(__file__).resolve().parent; R=O.parents[2];sys.path.insert(0,str(R/'tools'))
import profile_check as pc
import run_standalone_probe_validation as sp
from restoration_evidence import package_permissions,appop_modes
A='C:/Users/Agent/android-sdk/platform-tools/adb.exe'; S='3B171FDJH0061G'; P=pc.PACKAGE
sp.OUT=O

def save(n,v):pc.save(O/n,v)
def cmd(*args,check=True,timeout=45,binary=False):
 p=subprocess.run([A,'-s',S,*args],capture_output=True,timeout=timeout)
 journal=O/'commands.jsonl'
 with journal.open('a',encoding='utf8') as f:f.write(json.dumps(dict(at=time.time(),args=args,exit_code=p.returncode))+'\n')
 if check and p.returncode:raise RuntimeError('ADB_COMMAND_FAILED:'+str(args[:2]))
 return p.stdout if binary else p.stdout.decode('utf8','replace').replace('\r\n','\n')
def shell(c,**kw):return cmd('shell',c,**kw)
def root(c,**kw):return shell('su --mount-master -c '+shlex.quote('set -eu; '+c),**kw)
def snapshot(name):
 package=shell('dumpsys package dev.cyclone.cloak');grants,install=package_permissions(package);modes={}
 for u in (0,17):
  uid=re.search(r'^package:dev\.cyclone\.cloak uid:(\d+)$',shell(f'pm list packages --user {u} -U dev.cyclone.cloak'),re.M)[1]
  modes[str(u)]=appop_modes(shell(f'cmd appops get --user {u} dev.cyclone.cloak'),shell('cmd appops get '+uid))
 paths=shell('pm path dev.cyclone.cloak').strip().removeprefix('package:')
 files=root('find /data/adb/cyclone_cloak/state-v1 /data/adb/cyclone_cloak/publishers -type f -exec sha256sum {} +; for u in /data/user/[0-9]*; do if [ -d "$u/dev.cyclone.cloak" ]; then find "$u/dev.cyclone.cloak" -type f \\( -name "*binding*" -o -name "*index*" \\) -exec sha256sum {} +; fi; done')
 v=dict(apk_sha256=root('sha256sum '+shlex.quote(paths)).split()[0],grants=grants,install_grants=install,appops=modes,files=sorted(files.splitlines()))
 save(name,v);return v

def module_files():
 return root('find /data/adb/modules/cyclone_cloak -type f -exec sha256sum {} +')

def install():
 states={str(u):shell(f'am get-started-user-state {u}').strip() for u in (0,17)};save('preflight-users.json',states)
 if set(states.values())!={'RUNNING_UNLOCKED'}:raise RuntimeError('USERS_NOT_UNLOCKED')
 expected=json.loads((O/'artifact-agreement.json').read_text()); archive=O/'instrumented-module.zip'
 if hashlib.sha256(archive.read_bytes()).hexdigest()!=expected['selected_zip_sha256']:raise RuntimeError('ZIP_HASH_MISMATCH')
 before=snapshot('production-before.json')
 if before['apk_sha256']!=expected['installed_apk_sha256']:raise RuntimeError('PRODUCTION_APK_CHANGED')
 save('module-before.json',dict(files=module_files(),props=root('cat /data/adb/modules/cyclone_cloak/module.prop')))
 save('boot-before.json',dict(id=shell('cat /proc/sys/kernel/random/boot_id').strip(),at=time.time()))
 remote='/data/local/tmp/cloak-boot-gate-'+O.name+'.zip';save('module-staging.json',dict(path=remote))
 cmd('push',str(archive),remote)
 if root('sha256sum '+remote).split()[0]!=expected['selected_zip_sha256']:raise RuntimeError('STAGED_ZIP_MISMATCH')
 output=root('magisk --install-module '+remote,timeout=120);save('module-install.json',dict(output=output,completed_at=time.time()))
 root('rm -f '+remote)
 save('install-reboot-request.json',dict(at=time.time(),command='adb reboot'))
 cmd('reboot',timeout=15)
 print('INSTALL_REBOOT_REQUESTED',flush=True)

def bootcheck(label):
 boot=shell('getprop sys.boot_completed').strip();states={str(u):shell(f'am get-started-user-state {u}',check=False).strip() for u in (0,17)}
 v=dict(boot_completed=boot,current_user=shell('am get-current-user').strip(),users=states,boot_id=shell('cat /proc/sys/kernel/random/boot_id').strip(),at=time.time())
 save(label+'-boot.json',v);print(json.dumps(v),flush=True);return v


def inspect_loaded():
 with zipfile.ZipFile(O/'instrumented-module.zip') as z: expected={n:hashlib.sha256(z.read(n)).hexdigest() for n in z.namelist() if not n.endswith('/')}
 actual={p.removeprefix('/data/adb/modules/cyclone_cloak/'):h for h,p in (line.split(None,1) for line in module_files().splitlines())}
 logs=shell('logcat -d -t 500 -s CloakModule:I *:S',check=False)
 state=root('for f in disable remove update; do if [ -e /data/adb/modules/cyclone_cloak/$f ]; then echo "$f"; fi; done')
 save('installed-module-verification.json',dict(expected=expected,actual=actual,hashes_match=actual==expected,disable_remove_update_markers=state,log=logs,loaded_state_marker='No persistent loaded marker exists in this module; in-process bound probe evidence is required'))
 if any(actual.get(k)!=v for k,v in expected.items()) or set(actual)-set(expected)-{'load-records.jsonl'} or state.strip():raise RuntimeError('MODULE_INSTALLED_STATE_INVALID')
 print('MODULE_FILES_MATCH_APK_EMBEDDED_ZIP',flush=True)

def ensure_user(user):
 # Standard authorized switch only. No unlock command or credential operation.
 shell(f'am switch-user {user}')
 deadline=time.monotonic()+60
 states=[]
 while time.monotonic()<deadline:
  current=shell('am get-current-user').strip();state=shell(f'am get-started-user-state {user}',check=False).strip();states.append(dict(current=current,state=state))
  if current==str(user) and state=='RUNNING_UNLOCKED':save(f'switch-{user}-{int(time.time())}.json',states);return
  time.sleep(.5)
 save('switch-wait-failure.json',states);raise RuntimeError('USER_NOT_UNLOCKED')

def capture(user,phase):
 import jsonschema,collections
 request='bootgate-'+uuid.uuid4().hex
 sp.activate_user(user,sp.record_switch)
 launch=sp.launch_with_evidence(user,request);save(phase+'-launch.json',launch)
 report=None;deadline=time.monotonic()+60
 while time.monotonic()<deadline:
  candidate,visibility=sp.read_evidence(user,'report.json')
  if candidate and candidate.get('request_id')==request:report=candidate;break
  time.sleep(1)
 markers={}
 for part in ('start','end'):
  marker,seen=sp.read_evidence(user,'capture-'+part+'.json');markers[part]=sp.safe_marker(marker);markers[part+'_visibility']=seen
 save(phase+'-markers.json',markers);save(phase+'-launch.json',sp.finish_launch_evidence(user,launch))
 if report is None:raise RuntimeError('PROBE_REPORT_TIMEOUT:'+phase)
 safe=sp.redact(report);save(phase+'-report.json',safe)
 if report.get('status')=='FAIL' or report.get('error'):raise RuntimeError('PROBE_CAPTURE_FAILED:'+phase)
 if report['fixture']!=sp.fixture_for_user(user) or report['profile_sha256']!=launch['expected_profile']['profile_sha256'] or report['uid']//100000!=user:raise RuntimeError('CAPTURE_BINDING_MISMATCH')
 diagnosis=sp.marker_diagnosis(request,markers['start'],markers['end'])
 if diagnosis['reason']!='CAPTURE_ENDED_COMPLETED':raise RuntimeError('MARKER_FAILURE')
 ids=[x['id'] for x in report['rows']]
 if len(ids)!=89 or len(set(ids))!=89:raise RuntimeError('ROW_SET_FAILURE')
 jsonschema.validate(report['filesystem_observations'],json.loads((R/'schema/filesystem-observations.schema.json').read_text()))
 save(phase+'-verification.json',dict(user=user,fixture=report['fixture'],pid=report['pid'],profile_sha256=report['profile_sha256'],filesystem_schema=True,marker_diagnosis=diagnosis,counts=dict(collections.Counter(x['status'] for x in report['filesystem_observations']['rows']))))
 return report

def stage_canonical_fixture(user):
 fixture=sp.fixture_for_user(user)
 with zipfile.ZipFile(sp.APK) as z: body=json.loads(z.read(f'assets/profile-{fixture}.json'))
 encoded=pc.encoded(body);digest=hashlib.sha256(encoded).hexdigest()
 command=f'run-as {P} --user {user} sh -c '+shlex.quote('mkdir -p files; cat > files/expected-profile.json')
 result=sp.adb_result('shell',command,data=encoded.decode())
 if result['exit_code']:raise RuntimeError('EXPECTED_PROFILE_WRITE_FAILED')
 if sp.shell(f'run-as {P} --user {user} sha256sum files/expected-profile.json').split()[0]!=digest:raise RuntimeError('EXPECTED_PROFILE_HASH_MISMATCH')
 return dict(fixture=fixture,profile_sha256=digest,bytes=len(encoded),source='canonical encoding of diagnostics APK synthetic fixture')
sp.prepare_external_fixture=stage_canonical_fixture

def bound():
 inspect_loaded()
 diag=sp.APK;save('diagnostics-apk.json',dict(path=str(diag),sha256=hashlib.sha256(diag.read_bytes()).hexdigest()))
 output=cmd('install','--user','0','-r',str(diag),timeout=120);save('diagnostics-install.json',dict(output=output))
 if 'Success' not in output:raise RuntimeError('DIAGNOSTICS_INSTALL_FAILED')
 shell('cmd package install-existing --user 17 '+P)
 info=sp.package_info(P);save('diagnostics-installed.json',info)
 if any(info[str(u)]['apk_hashes']!=[hashlib.sha256(diag.read_bytes()).hexdigest()] for u in (0,17)):raise RuntimeError('DIAGNOSTICS_HASH_MISMATCH')
 run_id=uuid.uuid4().hex;m=dict(run_id=run_id,selections=[])
 for u,f in [(17,'b'),(0,'a')]:
  body=json.loads((R/f'android/probe/src/main/assets/profile-{f}.json').read_text());m['selections'].append(dict(kind='fixture',fixture=f,user=u,profile_id=body['id'],sha256=pc.digest(body)))
 d=pc.Device(A,S,45);h=pc.Harness(O,m,d,diag);h.checkpoint()
 try:
  save('overlay-add.json',h.overlay())
  for u in (17,0):
   ensure_user(u)
   _,index=d.index();entry=index['entries'][f'{u}/{P}'];published=root(f'sha256sum {pc.STATE}/{entry["key"]}/profile.json').split()[0]
   item=next(i for i in m['selections'] if i['user']==u)
   if published!=item['sha256']:raise RuntimeError('PUBLISHED_BODY_HASH_MISMATCH')
   report=capture(u,f'user-{u}-bound-cold')
   # Probe loads external fixture; authoritative linked publication digest uses canonical profile encoding.
   save(f'user-{u}-published-verification.json',dict(entry=entry,expected_canonical_sha256=item['sha256'],published_sha256=published,report_raw_asset_sha256=report['profile_sha256'],canonical_equals_published=published==item['sha256']))
   maps=root(f'cat /proc/{report["pid"]}/maps',check=False)
   relevant=[line for line in maps.splitlines() if any(x in line.lower() for x in ('cloak','zygisk','pine','shadowhook'))]
   log=shell('logcat -d -t 1000 -s CloakModule:I *:S',check=False)
   save(f'user-{u}-loaded-evidence.json',dict(pid=report['pid'],mapped_libraries=relevant,module_log=log))
   raw_records=root('cat /data/adb/modules/cyclone_cloak/load-records.jsonl',check=False)
   parsed=[]
   for line in raw_records.splitlines():
    try: parsed.append(json.loads(line))
    except ValueError: raise RuntimeError('LOAD_RECORD_MALFORMED')
   expected_digest=json.loads((O/'artifact-agreement.json').read_text())['selected_abi_sha256']['zygisk/arm64-v8a.so']
   marker_time=json.loads((O/f'user-{u}-bound-cold-markers.json').read_text())['start']['timestamp_ms']
   matched=[v for v in parsed if v.get('pid')==report['pid'] and v.get('target_package')==P and v.get('android_user_id')==u and v.get('module_artifact_sha256')==expected_digest and v.get('stage')=='preAppSpecialize_bound' and v.get('timestamp_ms',0)<=marker_time]
   save(f'user-{u}-durable-load-records.json',dict(matched=matched,all_records=parsed,bytes=len(raw_records.encode()),artifact_digest_expected=expected_digest))
   if len(raw_records.encode())>65536 or len(matched)!=1:raise RuntimeError('MODULE_DURABLE_LOAD_RECORD_ABSENT_OR_INVALID')
   if report['profile_sha256']!=published:raise RuntimeError('REPORT_PUBLISHED_HASH_MISMATCH')
 finally:
  if m.get('overlay_active'):save('overlay-remove.json',h.overlay(remove=True))
  for u in (0,17):
   shell(f'am force-stop --user {u} {P}',check=False)
  ensure_user(0)
 save('bound-phase-completed.json',dict(at=time.time()))
 print('BOUND_PHASE_COMPLETE',flush=True)

if __name__=='__main__':
 try:
  if sys.argv[1]=='install':install()
  elif sys.argv[1]=='bootcheck':bootcheck(sys.argv[2])
  elif sys.argv[1]=='bound':bound()
 except Exception as e:
  save('failure-'+sys.argv[1]+'.json',dict(phase=sys.argv[1],error_type=type(e).__name__,reason=str(e)[:200]));raise
