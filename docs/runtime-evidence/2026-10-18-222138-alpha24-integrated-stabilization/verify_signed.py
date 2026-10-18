from pathlib import Path
import subprocess,hashlib,json,re,sys
from zipfile import ZipFile
p=Path(__file__).parent
art=p/('signed-artifacts' if len(sys.argv)==1 else sys.argv[1])
a=next(art.rglob('app-release.apk'));z=next(art.rglob('cyclone-cloak-0.11.0-alpha.24.zip'))
sdk=Path('C:/Users/Agent/android-sdk/build-tools/35.0.0')
r=subprocess.run([str(sdk/'apksigner.bat'),'verify','--verbose','--print-certs',str(a)],capture_output=True,text=True,check=True)
assert '17ca3617b0cbaf250d2ef03e945b319e57695de6d977c652ded243ad5656f553' in r.stdout
b=subprocess.check_output([str(sdk/'aapt.exe'),'dump','badging',str(a)],text=True)
assert "versionCode='48' versionName='0.11.0-alpha.24'" in b
with ZipFile(a) as f: assert f.read('assets/cyclone-cloak-module.zip')==z.read_bytes()
with ZipFile(z) as f:
 prop=f.read('module.prop').decode();assert 'version=v0.11.0-alpha.24' in prop and 'versionCode=48' in prop
 libraries={n:hashlib.sha256(f.read(n)).hexdigest() for n in f.namelist() if n.startswith('zygisk/') and n.endswith('.so')}
result=dict(signature_verified=True,signer_sha256='17ca3617b0cbaf250d2ef03e945b319e57695de6d977c652ded243ad5656f553',embedded_module_exact_match=True,version='0.11.0-alpha.24',code=48,libraries=libraries,assets={x.name:dict(size=x.stat().st_size,sha256=hashlib.sha256(x.read_bytes()).hexdigest()) for x in [a,z]})
(p/('signed-artifact-verification.json' if len(sys.argv)==1 else 'release-artifact-verification.json')).write_text(json.dumps(result,indent=2)+'\n')
(p/'signed-apk-verification.log').write_text(r.stdout)
print(json.dumps(result,indent=2))
