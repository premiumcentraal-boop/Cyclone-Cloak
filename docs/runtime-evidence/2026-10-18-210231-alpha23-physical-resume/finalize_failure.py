import json,hashlib,sys
from pathlib import Path
from collections import Counter
from zipfile import ZipFile, ZIP_DEFLATED
p=Path(sys.argv[1]); c=p/'lifecycle/cycle-001'
a=json.loads((p/'lifecycle/aggregate-summary.json').read_text());m=json.loads((c/'manifest.json').read_text());r=json.loads((c/'failed-linked-user-0-bound-cold.json').read_text());legacy=json.loads((c/'failed-user-0-bound-cold.json').read_text())
failures=[x for x in r['linked_parity']['rows'] if x['status']=='FAIL']
for x in failures:
 if x['id']=='reality.panel.values.refresh_hz':
  assert x['expected_sha256']==hashlib.sha256(b'90').hexdigest() and x['actual_sha256']==hashlib.sha256(b'60').hexdigest()
  x['expected_non_sensitive_value']=90;x['actual_non_sensitive_value']=60
 if x['id']=='linked.supportedModes':
  assert x['expected_sha256']==hashlib.sha256(b'true').hexdigest() and x['actual_sha256']==hashlib.sha256(b'false').hexdigest()
  x['expected_non_sensitive_value']=True;x['actual_non_sensitive_value']=False
record={'result':'FAIL','failed_cycle':1,'failed_phase':m['failed_phase'],'reason':'LINKED_PARITY_MISMATCH','linked_parity_counts':dict(Counter(x['status'] for x in r['linked_parity']['rows'])),'legacy_bound_counts':dict(Counter(x['status'] for x in legacy['rows'])),'failures':failures,'fixture':r['fixture'],'profile_sha256':r['profile_sha256'],'published_sha256':r['published_sha256'],'hashes_match':r['profile_sha256']==r['published_sha256'],'permission_delta':a['cycles'][0]['permission_delta'],'production_delta':a['cycles'][0]['production_delta'],'cleanup':json.loads((c/'cleanup-proof.json').read_text()),'installed_versions':m['installed_versions'],'additional_installations':0,'additional_reboots':0,'cycles_2_and_3':'NOT_RUN_STOP_ON_FAILURE','prior_run':'../2026-10-18-205729-alpha23-physical-validation/summary.md','local_parity_scope':'Implemented capability; measured failures remain explicit. No platform verdict.'}
(p/'result.json').write_text(json.dumps(record,indent=2)+'\n',encoding='utf8')
lines=['# Alpha.23 physical validation continuation','','Result: FAIL — stopped in cycle 1, user-0-bound-cold.','Companion/module: v0.11.0-alpha.23 / code 47; source 0d028a947b37013e0ad89a98d1455a79c33bfd3d.','Both users were RUNNING_UNLOCKED at the fresh baseline. No additional installation or reboot.','','| Bound user 0 section | PASS | FAIL | BLOCKED | OBSERVED |','|---|---:|---:|---:|---:|', '| linked_parity | 11 | 2 | 0 | 0 |']
ct=record['legacy_bound_counts']; lines.append('| legacy rows | '+' | '.join(str(ct.get(s,0)) for s in ['PASS','FAIL','BLOCKED','OBSERVED'])+' |')
lines+=['','Exact failures:','- reality.panel.values.refresh_hz / Display.getMode refresh: expected 90 Hz, actual 60 Hz (values confirmed against recorded SHA-256 hashes).','- linked.supportedModes: expected true (mode tuple present), actual false. Aggregate modes digest is preserved in the failed-linked report.','','Fixture A/profile hash and temporary published-body hash both: '+r['profile_sha256'], 'Other 11 linked rows passed: width, height, density, MCC, MNC, slots, language, country, timezone, SIM operator and active modem count.','Permission/AppOps delta: none. Production delta: none.','Cleanup passed for users 0 and 17: probe entries, body directories, expected-profile/report files and scratch removed.','Cycles 2 and 3, user-17 bound capture, cold restoration comparisons: NOT RUN after first bound failure.','Unbound baseline fixture mismatches are control observations; they are not classified as runtime regressions.','No production source, connector logic, bindings, permissions or release metadata edited. No commit, push or new release.','Local parity is an implemented capability, not a platform verdict.','','Evidence: result.json; lifecycle/aggregate-summary.json; lifecycle/cycle-001/failed-linked-user-0-bound-cold.json; failed-user-0-bound-cold.json; before/after audit and permission files; cleanup-proof.json; lifecycle-console.log.','APK/module verification and initial install/reboot records remain in the previous stopped evidence directory; alpha23 asset verification and diagnostics APK provenance are copied here.']
(p/'summary.md').write_text('\n'.join(lines)+'\n',encoding='utf8')
with ZipFile(p/'physical-evidence-bundle.zip','w',ZIP_DEFLATED) as z:
 for f in sorted(p.rglob('*')):
  if f.is_file() and f.name not in {'.gitignore','physical-evidence-bundle.zip'}:z.write(f,f.relative_to(p).as_posix())
print(json.dumps(record,indent=2)); print('BUNDLE_SHA256',hashlib.sha256((p/'physical-evidence-bundle.zip').read_bytes()).hexdigest())
