from pathlib import Path
import ast,sys,json,re,shlex,hashlib,subprocess
sys.path.insert(0,str(Path.cwd()/'tools'))
import profile_check as pc
p=Path(__file__).parent
users=[0,17]
PACKAGE=pc.PACKAGE
d=pc.Device('C:/Users/Agent/android-sdk/platform-tools/adb.exe','3B171FDJH0061G',90)
tree=ast.parse((p/'alpha24_lifecycle_runner.py').read_text())
run=next(n for n in tree.body if isinstance(n,ast.FunctionDef) and n.name=='run_cycle')
for name in ('permission_state','audit'):
 node=next(n for n in run.body if isinstance(n,ast.FunctionDef) and n.name==name)
 exec(compile(ast.Module(body=[node],type_ignores=[]),str(p/'alpha24_lifecycle_runner.py'),'exec'))
mode=sys.argv[1]
if mode=='before':
 pc.save(p/'installation-audit-before.json',audit());pc.save(p/'installation-permissions-before.json',permission_state())
 pc.save(p/'installation-users-before.json',dict(current=d.shell('am get-current-user').stdout.strip(),states={str(u):d.shell(f'am get-started-user-state {u}').stdout.strip() for u in users}))
 print('BEFORE_AUDIT_SAVED')
elif mode=='install':
 art=p/'signed-artifacts'
 apks=list(art.rglob('app-release.apk'));zips=list(art.rglob('cyclone-cloak-0.11.0-alpha.24.zip'))
 if len(apks)!=1 or len(zips)!=1:raise RuntimeError('EXPECTED_SIGNED_ARTIFACT_PAIR')
 app=apks[0];module=zips[0]
 diag=Path.home()/'AppData/Local/Temp/cloak-alpha24-integrated-clean/android/probe/build/outputs/apk/diagnostics/debug/probe-diagnostics-debug.apk'
 remote='/data/local/tmp/cloak-alpha24-49564ce.zip'
 pc.save(p/'installation-artifacts.json',dict(app_sha256=hashlib.sha256(app.read_bytes()).hexdigest(),module_sha256=hashlib.sha256(module.read_bytes()).hexdigest(),diagnostics_sha256=hashlib.sha256(diag.read_bytes()).hexdigest(),commit='49564ce0a4d60af158da0e4f67b84583b06ea4c7',signed_validation_run=39163201635))
 for name,path,args in [('companion',app,[]),('diagnostics',diag,['--user','0'])]:
  output=d.run('install','-r',*args,str(path),timeout=120)
  (p/f'{name}-install.log').write_text(output.stdout+output.stderr,encoding='utf-8')
  if 'Success' not in output.stdout:raise RuntimeError('APK_INSTALL_FAILED')
 d.run('push',str(module),remote,timeout=120)
 output=d.root('magisk --install-module '+shlex.quote(remote),timeout=120)
 (p/'module-install.log').write_text(output.stdout+output.stderr,encoding='utf-8')
 d.root('rm -f -- '+remote)
 pc.save(p/'module-update.json',dict(installed=True,reboot_required=True,reboot_reason='Magisk stages the replacement module for next boot; one reboot is necessary to load its native library.'))
 print('MATCHING_ARTIFACTS_INSTALLED_REBOOT_REQUIRED')
elif mode=='after':
 a=audit();b=json.loads((p/'installation-audit-before.json').read_text())
 delta=[k for k in b if k!='module_files' and b[k]!=a[k]]
 perms=json.loads(json.dumps(permission_state()));old=json.loads((p/'installation-permissions-before.json').read_text())
 pc.save(p/'installation-audit-after.json',a);pc.save(p/'installation-permissions-after.json',perms)
 from zipfile import ZipFile
 z=next((p/'signed-artifacts').rglob('cyclone-cloak-0.11.0-alpha.24.zip'))
 with ZipFile(z) as archive:
  libraries={abi:dict(expected=hashlib.sha256(archive.read('zygisk/'+abi+'.so')).hexdigest(),installed=d.root('sha256sum /data/adb/modules/cyclone_cloak/zygisk/'+abi+'.so').stdout.split()[0]) for abi in ['arm64-v8a','armeabi-v7a']}
 proof=dict(non_module_production_delta=delta,permission_delta=[] if old==perms else ['PERMISSION_STATE_CHANGED'],libraries=libraries,users={str(u):d.shell(f'am get-started-user-state {u}').stdout.strip() for u in users})
 pc.save(p/'installation-preservation-proof.json',proof)
 if delta or old!=perms or any(v['expected']!=v['installed'] for v in libraries.values()):raise RuntimeError('INSTALLATION_PRESERVATION_FAILED')
 print('INSTALLATION_AUDIT_PASS')
