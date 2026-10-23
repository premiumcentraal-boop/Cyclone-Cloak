import ast,hashlib,json,sys,subprocess
from pathlib import Path
from zipfile import ZipFile
repo=Path.cwd(); out=Path(sys.argv[1]); sys.path.insert(0,str(repo/'tools'))
import profile_check as pc
import run_probe_reversibility as rr
release=json.loads((out/'published-release-api.json').read_text(encoding='utf-8-sig'))
proof={'tag':release['tag_name'],'commit':subprocess.check_output(['git','rev-parse','HEAD'],text=True).strip(),'assets':[]}
for a in release['assets']:
 p=out/'release-assets'/a['name']; h=hashlib.sha256(p.read_bytes()).hexdigest()
 assert a['digest']=='sha256:'+h and p.stat().st_size==a['size']
 proof['assets'].append({'name':p.name,'sha256':h,'size':p.stat().st_size})
apk=out/'release-assets/app-release.apk'; mod=out/'release-assets/cyclone-cloak-0.11.0-alpha.23.zip'
assert hashlib.sha256(apk.read_bytes()).hexdigest()=='8b605d985e97e31af32cce6263fc7cb4a94bb936f83b8b077d88be714d61c3cd'
with ZipFile(apk) as z: assert z.read('assets/cyclone-cloak-module.zip')==mod.read_bytes()
with ZipFile(mod) as z:
 props=dict(l.split('=',1) for l in z.read('module.prop').decode().splitlines() if '=' in l)
 assert props['version']=='v0.11.0-alpha.23' and props['versionCode']=='47'
proof['embedded_module_exact_match']=True
proof['published_provenance_scope']='Historical alpha18 evidence attachment; not alpha23 physical proof'
pc.save(out/'asset-verification.json',proof)
d=pc.Device('C:/Users/Agent/android-sdk/platform-tools/adb.exe','3B171FDJH0061G',90); users=[0,17]
space=dict(pc=pc,d=d,users=users,re=__import__('re'),hashlib=hashlib,PACKAGE=rr.PACKAGE)
tree=ast.parse((repo/'tools/run_probe_reversibility.py').read_text(encoding='utf8'))
cycle=next(n for n in tree.body if isinstance(n,ast.FunctionDef) and n.name=='run_cycle')
for n in cycle.body:
 if isinstance(n,ast.FunctionDef) and n.name in ('audit','permission_state'):
  exec(compile(ast.Module(body=[n],type_ignores=[]),'existing-harness-audit','exec'),space)
pc.save(out/'pre-update-production-audit.json',space['audit']())
pc.save(out/'pre-update-permissions.json',space['permission_state']())
app=d.shell('dumpsys package dev.cyclone.cloak').stdout
re=__import__('re')
env={'app_version':re.search(r'versionName=(\S+)',app)[1],'app_code':re.search(r'versionCode=(\d+)',app)[1],'module':d.root('cat /data/adb/modules/cyclone_cloak/module.prop').stdout,'users':d.shell('cmd user list -v').stdout,'user_states':{str(u):d.shell(f'am get-started-user-state {u}',check=False).stdout.strip() for u in users},'active_user':d.shell('am get-current-user').stdout.strip(),'boot_id':d.shell('cat /proc/sys/kernel/random/boot_id').stdout.strip()}
paths=d.shell('pm path dev.cyclone.cloak').stdout.strip().splitlines()
env['installed_app_sha256']=d.root('sha256sum '+__import__('shlex').quote(paths[0].removeprefix('package:'))).stdout.split()[0]
with ZipFile(mod) as z:
 env['installed_module_matches_release']={n:d.root('sha256sum '+__import__('shlex').quote('/data/adb/modules/cyclone_cloak/'+n)).stdout.split()[0]==hashlib.sha256(z.read(n)).hexdigest() for n in z.namelist() if n.startswith('zygisk/') and not n.endswith('/')}
pc.save(out/'pre-update-environment.json',env)
print(json.dumps({'assets_verified':True,'app':env['app_version'],'module':env['module'],'app_hash_matches':env['installed_app_sha256']==proof['assets'][0]['sha256'],'users':env['user_states'],'module_libraries':env['installed_module_matches_release']}))
