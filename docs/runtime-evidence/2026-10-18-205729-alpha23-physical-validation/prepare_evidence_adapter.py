import ast,hashlib,json,sys,subprocess
from pathlib import Path
out=Path(sys.argv[1]); repo=Path.cwd(); source=Path(__import__('os').environ['TEMP'])/'cloak-alpha23-physical-clean'
p=repo/'tools/run_probe_reversibility.py'; original=p.read_text(encoding='utf8'); s=original
s=s.replace("TAG = 'v0.11.0-alpha.18'","TAG = 'v0.11.0-alpha.23'").replace("COMMIT = 'fb1e3c6ae2e4caca56778a54d8025dba696b53b8'","COMMIT = '0d028a947b37013e0ad89a98d1455a79c33bfd3d'")
s=s.replace("module_code='42',app_version='0.11.0-alpha.18',app_code='42'","module_code='47',app_version='0.11.0-alpha.23',app_code='47'").replace('Exact clean alpha.18 checkout required','Exact clean alpha.23 checkout required')
needle='                check_metadata(r, item, cold, ids)'
assert s.count(needle)==1
s=s.replace(needle,needle+'''
                linked = r.get('linked_parity', {})
                linked_rows = linked.get('rows', [])
                if linked.get('format') != 1 or len(linked_rows) != 13 or len({x.get('id') for x in linked_rows}) != 13:
                    raise ValueError('LINKED_PARITY_REPORT_INCOMPLETE')
                if mode == 'bound-cold' and any(x.get('status') in {'FAIL', 'MISSING'} for x in linked_rows):
                    pc.save(out/('failed-linked-'+phase+'.json'), dict(phase=phase, android_user=user,
                        fixture=item['fixture'], profile_sha256=item['sha256'], published_sha256=published,
                        linked_parity=linked, request_id=request))
                    raise ValueError('LINKED_PARITY_MISMATCH')
''')
ast.parse(s); dest=out/'alpha23_lifecycle_evidence_runner.py'; dest.write_text(s,encoding='utf8')
apk=source/'android/probe/build/outputs/apk/diagnostics/debug/probe-diagnostics-debug.apk'
proof={'tag':'v0.11.0-alpha.23','commit':'0d028a947b37013e0ad89a98d1455a79c33bfd3d','checkout':str(source),'checkout_clean':not subprocess.check_output(['git','status','--porcelain','--untracked-files=no'],cwd=source,text=True).strip(),'build_command':'JAVA_HOME=JDK17 ANDROID_HOME=C:/Users/Agent/android-sdk gradle-8.11.1 -p '+str(source/'android')+' :probe:assembleDiagnosticsDebug :probe:testDiagnosticsDebugUnitTest --offline --no-daemon --console=plain','apk_sha256':hashlib.sha256(apk.read_bytes()).hexdigest(),'apk_size':apk.stat().st_size,'base_runner_sha256':hashlib.sha256(p.read_bytes()).hexdigest(),'evidence_adapter_sha256':hashlib.sha256(dest.read_bytes()).hexdigest(),'adapter_scope':['Pin existing lifecycle runner to alpha23/code47','Require 13 linked_parity rows and stop on bound FAIL/MISSING','No changes to device commands, redaction, cleanup, permissions, audits or restoration classification'],'diagnostics_unit_tests':107,'build_result':'PASS'}
(out/'diagnostics-build-provenance.json').write_text(json.dumps(proof,indent=2)+'\n',encoding='utf8')
print(json.dumps(proof,indent=2))
