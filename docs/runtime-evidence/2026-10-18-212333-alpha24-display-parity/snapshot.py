import ast,hashlib,json,re,sys
from pathlib import Path
sys.path.insert(0,str(Path.cwd()/'tools')); import profile_check as pc; import run_probe_reversibility as rr
out=Path(sys.argv[1]); label=sys.argv[2]; users=[0,17]; d=pc.Device('C:/Users/Agent/android-sdk/platform-tools/adb.exe','3B171FDJH0061G',90)
space=dict(pc=pc,d=d,users=users,re=re,hashlib=hashlib,PACKAGE=rr.PACKAGE)
tree=ast.parse(Path('tools/run_probe_reversibility.py').read_text());cycle=next(n for n in tree.body if isinstance(n,ast.FunctionDef) and n.name=='run_cycle')
for n in cycle.body:
 if isinstance(n,ast.FunctionDef) and n.name in ('audit','permission_state'):exec(compile(ast.Module(body=[n],type_ignores=[]),'existing-audit','exec'),space)
pc.save(out/(label+'-audit.json'),space['audit']());pc.save(out/(label+'-permissions.json'),space['permission_state']())
app=d.shell('dumpsys package dev.cyclone.cloak').stdout
pc.save(out/(label+'-environment.json'),dict(module=d.root('cat /data/adb/modules/cyclone_cloak/module.prop').stdout,app_version=re.search(r'versionName=(\S+)',app)[1],app_code=re.search(r'versionCode=(\d+)',app)[1],boot_id=d.shell('cat /proc/sys/kernel/random/boot_id').stdout.strip(),users={str(u):d.shell(f'am get-started-user-state {u}',check=False).stdout.strip() for u in users}))
print(label,'AUDIT_SAVED')
