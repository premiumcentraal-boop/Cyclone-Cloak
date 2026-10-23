import hashlib,json,os,shlex,subprocess,sys
from pathlib import Path
from zipfile import ZipFile
p=Path(sys.argv[1]);sys.path.insert(0,str(Path.cwd()/'tools'));import profile_check as pc
apks=list((p/'signed-artifacts').rglob('app-release.apk'));mods=list((p/'signed-artifacts').rglob('cyclone-cloak-0.11.0-alpha.24.zip'))
assert len(apks)==len(mods)==1
apk,mod=apks[0],mods[0]; diag=Path(os.environ['TEMP'])/'cloak-alpha24-physical-clean/android/probe/build/outputs/apk/diagnostics/debug/probe-diagnostics-debug.apk'
with ZipFile(apk) as z: assert z.read('assets/cyclone-cloak-module.zip')==mod.read_bytes()
with ZipFile(mod) as z:
 props=dict(l.split('=',1) for l in z.read('module.prop').decode().splitlines() if '=' in l)
 assert props['version']=='v0.11.0-alpha.24' and props['versionCode']=='48'
 libs={n:hashlib.sha256(z.read(n)).hexdigest() for n in z.namelist() if n.startswith('zygisk/') and not n.endswith('/')}
proof=dict(signed_validation_run=39158498518,apk_sha256=hashlib.sha256(apk.read_bytes()).hexdigest(),module_sha256=hashlib.sha256(mod.read_bytes()).hexdigest(),module_libraries=libs,diagnostics_apk_sha256=hashlib.sha256(diag.read_bytes()).hexdigest(),embedded_module_exact_match=True,diagnostics_install_attempts=0,companion_install_attempts=1)
d=pc.Device('C:/Users/Agent/android-sdk/platform-tools/adb.exe','3B171FDJH0061G',120)
for u in (0,17):assert d.shell(f'am get-started-user-state {u}').stdout.strip()=='RUNNING_UNLOCKED'
result=d.run('install','-r',apk);(p/'companion-install.log').write_text(result.stdout+result.stderr);assert 'Success' in result.stdout
# Install the new probe at most once; subsequent cycles require an exact installed hash.
checks={}
for u in (0,17):
 path=d.shell(f'pm path --user {u} dev.cyclone.cloak.probe.diag').stdout.strip().removeprefix('package:')
 checks[str(u)]=d.root('sha256sum '+shlex.quote(path)).stdout.split()[0] if path else None
if set(checks.values())!={proof['diagnostics_apk_sha256']}:
 proof['diagnostics_install_attempts']=1
 result=d.run('install','-r',diag);(p/'diagnostics-install.log').write_text(result.stdout+result.stderr);assert 'Success' in result.stdout
proof['diagnostics_hashes_before_install']=checks
proof['diagnostics_hashes_after_install']={}
for u in (0,17):
 path=d.shell(f'pm path --user {u} dev.cyclone.cloak.probe.diag').stdout.strip().removeprefix('package:')
 actual=d.root('sha256sum '+shlex.quote(path)).stdout.split()[0];assert actual==proof['diagnostics_apk_sha256']
 proof['diagnostics_hashes_after_install'][str(u)]=actual
pc.save(p/'installation-provenance.json',proof)
d.run('push',mod,'/data/local/tmp/cloak-alpha24-validation.zip')
result=d.root('magisk --install-module /data/local/tmp/cloak-alpha24-validation.zip');(p/'module-install.log').write_text(result.stdout+result.stderr)
d.root('rm -f /data/local/tmp/cloak-alpha24-validation.zip')
print(json.dumps(proof,indent=2)); print('MODULE_INSTALLED_REBOOT_REQUIRED')
