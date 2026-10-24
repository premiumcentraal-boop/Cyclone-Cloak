"""Run-only RC1 evidence/update operations; no repository source changes."""
import hashlib,json,os,re,shlex,subprocess,sys,time
from pathlib import Path
from zipfile import ZipFile
R=Path('C:/Users/Agent/Cyclone/cloak-repo');sys.path.insert(0,str(R/'tools'))
import profile_check as pc
from restoration_evidence import package_permissions,appop_modes
O=Path(sys.argv[2]); A='C:/Users/Agent/android-sdk/platform-tools/adb.exe';S='3B171FDJH0061G'
D=pc.Device(A,S,90); P=Path(os.environ['TEMP'])/'cloak-rc1-published-1da4542'
def save(name,value):pc.save(O/name,value)
def adb(*args,check=True,timeout=90):
 p=D.run(*args,check=False,timeout=timeout)
 with (O/'commands.jsonl').open('a') as f:f.write(json.dumps(dict(at=pc.utc(),args=list(args),exit_code=p.returncode,stdout_sha256=hashlib.sha256(p.stdout.encode()).hexdigest()))+'\n')
 if check and p.returncode:raise RuntimeError('ADB_COMMAND_FAILED')
 return p.stdout
def shell(command,**kw):return adb('shell',command,**kw)
def root(command,**kw):return shell('su --mount-master -c '+shlex.quote('set -eu; '+command),**kw)
def states():return {str(u):shell(f'am get-started-user-state {u}',check=False).strip() for u in (0,17)}
def hashes(command):
 return sorted(root(command).splitlines())
def snapshot(name):
 packs={}
 for pkg in ('dev.cyclone.cloak','dev.cyclone.cloak.probe.diag'):
  text=shell('dumpsys package '+pkg)
  if not shell('pm path '+pkg,check=False).strip():
   packs[pkg]=dict(present=False,grants=None,install_grants=None,modes=None)
   continue
  grants,install=package_permissions(text); modes={}
  for u in (0,17):
   lookup=shell(f'pm list packages --user {u} -U {pkg}')
   match=re.search(r'^package:'+re.escape(pkg)+r' uid:(\d+)$',lookup,re.M)
   modes[str(u)]=appop_modes(shell(f'cmd appops get --user {u} {pkg}',check=False),shell('cmd appops get '+match[1],check=False)) if match else {'package_not_installed':True}
  packs[pkg]=dict(present=True,grants=grants,install_grants=install,modes=modes)
 raw=root('cat /data/adb/cyclone_cloak/state-v1/index.json'); index=json.loads(raw)
 v=dict(packages=packs,index_sha256=hashlib.sha256(raw.encode()).hexdigest(),index_semantic_sha256=pc.digest(index),entry_count=len(index['entries']),entries={hashlib.sha256(k.encode()).hexdigest():pc.digest(v) for k,v in index['entries'].items()},production_files=hashes('find /data/adb/cyclone_cloak/state-v1 /data/adb/cyclone_cloak/publishers -type f -exec sha256sum {} +; for u in /data/user/[0-9]*; do f="$u/dev.cyclone.cloak/no_backup/cloak-bindings-v1.json"; if [ -f "$f" ]; then sha256sum "$f"; fi; done'),module_files=hashes('find /data/adb/modules/cyclone_cloak -type f -exec sha256sum {} +'))
 save(name,v);return v
def preflight():
 if adb('get-state').strip()!='device':raise RuntimeError('DEVICE_OFFLINE')
 users=states();save('pre-install-users.json',users)
 if set(users.values())!={'RUNNING_UNLOCKED'}:raise RuntimeError('USER_UNAVAILABLE_OR_LOCKED')
 verification=json.loads((P/'published-verification.json').read_text())
 for asset in verification['assets']:
  if hashlib.sha256((P/asset['name']).read_bytes()).hexdigest()!=asset['sha256']:raise RuntimeError('PUBLISHED_ASSET_HASH_MISMATCH')
 save('published-verification.json',verification)
 path=shell('pm path --user 0 dev.cyclone.cloak').strip().removeprefix('package:')
 adb('pull',path,str(O/'pre-install-base.apk'))
 env=dict(os.environ,JAVA_HOME='C:/Program Files/Eclipse Adoptium/jdk-17.0.20.101-hotspot')
 cert=subprocess.run(['C:/Users/Agent/android-sdk/build-tools/35.0.0/apksigner.bat','verify','--print-certs',str(O/'pre-install-base.apk')],capture_output=True,text=True,env=env)
 (O/'pre-install-signature.txt').write_text(cert.stdout+cert.stderr)
 if cert.returncode or '17ca3617b0cbaf250d2ef03e945b319e57695de6d977c652ded243ad5656f553' not in cert.stdout:raise RuntimeError('INSTALLED_SIGNER_MISMATCH')
 snapshot('pre-install-audit.json')
 save('pre-install-versions.json',dict(app=[x.strip() for x in shell('dumpsys package dev.cyclone.cloak').splitlines() if 'versionName=' in x or 'versionCode=' in x],module=root('cat /data/adb/modules/cyclone_cloak/module.prop'),boot_id=shell('cat /proc/sys/kernel/random/boot_id').strip()))
 apk=R/'android/probe/build/outputs/apk/diagnostics/debug/probe-diagnostics-debug.apk'
 save('diagnostics-build-provenance.json',dict(source_commit=subprocess.check_output(['git','rev-parse','HEAD'],cwd=R,text=True).strip(),apk_sha256=hashlib.sha256(apk.read_bytes()).hexdigest(),apk_path=str(apk),build_command='gradle -p android :probe:assembleDiagnosticsDebug :probe:testDiagnosticsDebugUnitTest --offline --no-daemon --console=plain',runner_sha256=hashlib.sha256((R/'tools/run_probe_reversibility.py').read_bytes()).hexdigest()))
 print('PREFLIGHT_PASSED',flush=True)
def update():
 if not (O/'pre-install-audit.json').is_file():raise RuntimeError('MISSING_PREINSTALL_AUDIT')
 save('app-install.json',dict(output=adb('install','-r','--user','0',str(P/'app-release.apk'),timeout=120)))
 for u in (0,17):
  if not shell(f'pm path --user {u} dev.cyclone.cloak').strip():
   save(f'app-install-user-{u}.json',dict(output=shell(f'cmd package install-existing --user {u} dev.cyclone.cloak')))
  path=shell(f'pm path --user {u} dev.cyclone.cloak').strip().removeprefix('package:')
  digest=root('sha256sum '+shlex.quote(path)).split()[0]
  save(f'installed-app-user-{u}.json',dict(path=path,sha256=digest))
  if digest!='bec08a2f3dd572d262c5df4aed46106197754df8ea9f3501c307ba3876715b17':raise RuntimeError('INSTALLED_APK_HASH_MISMATCH')
 remote='/data/local/tmp/cloak-rc1-s8-'+O.name+'.zip'
 adb('push',str(P/'cyclone-cloak-1.0.0-rc.1.zip'),remote)
 if root('sha256sum '+shlex.quote(remote)).split()[0]!='08c200f90decc52fa734d8bffd7a21f8e7ab62d5844f8ae563f1cd81214fc566':raise RuntimeError('STAGED_ZIP_HASH_MISMATCH')
 save('module-install.json',dict(output=root('magisk --install-module '+shlex.quote(remote),timeout=120)))
 root('rm -f '+shlex.quote(remote))
 save('reboot-request.json',dict(at=pc.utc(),reason='NORMAL_MODULE_UPDATE_ACTIVATION'))
 adb('reboot',timeout=20);print('MODULE_UPDATE_REBOOT_REQUESTED',flush=True)
def bootcheck():
 save('post-update-boot.json',dict(boot_completed=shell('getprop sys.boot_completed').strip(),users=states(),current_user=shell('am get-current-user').strip(),boot_id=shell('cat /proc/sys/kernel/random/boot_id').strip()))
 with ZipFile(P/'cyclone-cloak-1.0.0-rc.1.zip') as z:
  expected={n:hashlib.sha256(z.read(n)).hexdigest() for n in z.namelist() if not n.endswith('/')}
 actual={p.removeprefix('/data/adb/modules/cyclone_cloak/'):h for h,p in (line.split(None,1) for line in hashes('find /data/adb/modules/cyclone_cloak -type f -exec sha256sum {} +'))}
 save('installed-module-verification.json',dict(expected=expected,actual=actual,immutable_match=all(actual.get(k)==v for k,v in expected.items())))
 if any(actual.get(k)!=v for k,v in expected.items()):raise RuntimeError('INSTALLED_MODULE_HASH_MISMATCH')
 after=snapshot('post-install-audit.json');before=json.loads((O/'pre-install-audit.json').read_text())
 check=dict(production_files_unchanged=before['production_files']==after['production_files'],entries_unchanged=before['entries']==after['entries'],permissions_appops_unchanged=before['packages']==after['packages'])
 def norm(v):
  if isinstance(v,(list,tuple)):return [norm(x) for x in v]
  if isinstance(v,dict):return {k:norm(x) for k,x in v.items()}
  return v
  if not all(check.values()) or norm(before['packages'])!=norm(after['packages']):raise RuntimeError('INSTALLATION_AUDIT_CHANGED')
  if not all(check.values()) or norm(before['packages'])!=norm(after['packages']):raise RuntimeError('INSTALLATION_AUDIT_CHANGED')
 print('BOOT_AND_INSTALLATION_AUDIT_PASSED',flush=True)
try:
 {'preflight':preflight,'update':update,'bootcheck':bootcheck}[sys.argv[1]]()
except Exception as e:
 save('operation-failure.json',dict(phase=sys.argv[1],error_type=type(e).__name__,reason=str(e)))
 print('STOP',type(e).__name__,str(e),flush=True);raise
