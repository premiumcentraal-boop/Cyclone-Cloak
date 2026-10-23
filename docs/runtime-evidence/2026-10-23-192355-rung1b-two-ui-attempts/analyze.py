"""Run-only evidence analysis; no raw identifiers retained in derived output."""
from pathlib import Path
import collections
import importlib.util
import json

O=Path(__file__).resolve().parent
spec=importlib.util.spec_from_file_location('ops',O/'run_ops.py')
o=importlib.util.module_from_spec(spec);spec.loader.exec_module(o)
reports=[json.loads((O/f'user-17-capture-{n}-report.json').read_text()) for n in (1,2,3)]
summary=[];comparisons=[]
sections=('rows','runtime_envelope','device_reality','linked_parity')
for n,r in enumerate(reports,1):
    summary.append(dict(capture=n,pid=r['pid'],uid=r['uid'],profile_sha256=r['profile_sha256'],
        counts={key:dict(collections.Counter(x['status'] for x in (r[key] if key=='rows' else r[key]['rows']))) for key in sections}))
for key in sections:
    tables=[{x['id']:x for x in (r[key] if key=='rows' else r[key]['rows'])} for r in reports]
    all_ids=set.union(*(set(t) for t in tables))
    status_changed=sorted(i for i in all_ids if len({t.get(i,{}).get('status') for t in tables})>1)
    passing=sorted(i for i in all_ids if all(t.get(i,{}).get('status')=='PASS' for t in tables))
    digest_ids=[i for i in passing if all(t[i].get('actual_sha256') for t in tables)]
    digest_changes=[i for i in digest_ids if len({t[i]['actual_sha256'] for t in tables})>1]
    exact_matches=[i for i in passing if all(t[i].get('reason')=='MATCH' for t in tables)]
    comparisons.append(dict(section=key,passing_rows=len(passing),passing_ids=passing,
        status_changed=status_changed,retained_digest_comparisons=len(digest_ids),digest_changed=digest_changes,
        exact_fixed_expectation_matches=len(exact_matches),
        limitation='Legacy raw actual values are redacted. MATCH in all three against the same fixed expected profile is evidence of exact comparison stability, not independent raw-value retention.' if key=='rows' else None))
o.b.save('capture-counts.json',summary)
o.b.save('determinism.json',dict(distinct_pids=len({r['pid'] for r in reports})==3,
    same_profile_hash=len({r['profile_sha256'] for r in reports})==1,sections=comparisons,
    user17_legacy_determinism='CONFIRMED post-grants',
    all_statuses_stable=all(not c['status_changed'] for c in comparisons),
    all_retained_PASS_digests_stable=all(not c['digest_changed'] for c in comparisons)))
promotions={
 'reality.carrier.values.sim_state':['envelope.telephony.simState','envelope.telephony.simStateSlot0'],
 'reality.compute.values.gpu':['envelope.compute.gpuRenderer'],
 'reality.packages.values.installed':['envelope.packages.visibleVersions'],
 'reality.packages.values.installed[].package':['envelope.packages.visibleVersions'],
 'reality.packages.values.installed[].version_code':['envelope.packages.visibleVersions'],
 'reality.packages.values.installed[].version_name':['envelope.packages.visibleVersions'],
 'reality.webview.values.provider':['envelope.webview.provider'],
 'reality.webview.values.version':['envelope.webview.version'],
}
envelopes=[{x['id']:x for x in r['runtime_envelope']['rows']} for r in reports]
for path,ids in promotions.items():
    assert all(e[i]['status']=='PASS' and e[i]['expected_sha256']==e[i]['actual_sha256'] for e in envelopes for i in ids)
table=json.loads((o.R/'catalog/reality-runtime-fields.json').read_text())
partials=[x['field_path'] for x in table['fields'] if x['status']=='PARTIAL']
remaining=[p for p in partials if p not in promotions]
o.b.save('evidence-ledger-promotions.json',dict(scope='Measured artifact/profile C only; no generated contract status changed; not verified runtime enforcement or independence.',
    APPLIED=[dict(path=p,rows=ids,new=p!='reality.carrier.values.sim_state') for p,ids in promotions.items()],
    remaining_PARTIAL=remaining,instance_UA_ledger='UNVERIFIED',VERIFIED=[],
    note='Package aggregate contains package names/version_code/version_name; full canonical aggregate hash passed. GPU maps to the separate concrete EGL/GLES envelope reader; original generic Reality row remains UNMAPPED. Host-authored equality alone does not establish distinct-value override or isolation.'))
assert len(promotions)==8 and len(remaining)==9
raw=o.b.root('cat /data/adb/modules/cyclone_cloak/load-records.jsonl',check=False)
records=[]
for line in raw.splitlines():
    try:
        v=json.loads(line)
        if v.get('package',v.get('target_package'))==o.sp.PACKAGE and str(v.get('pid')) in {str(r['pid']) for r in reports}:
            records.append(v)
    except ValueError:pass
o.b.save('own-capture-load-records.json',records)
print(json.dumps(dict(captures=summary,determinism=all(not c['status_changed'] and not c['digest_changed'] for c in comparisons),applied=8,remaining=9,load_records=len(records))))
