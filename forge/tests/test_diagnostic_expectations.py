"""Synthetic policy/transition checks; historical device rows are never relabelled."""
import copy
import json
from pathlib import Path
import sys
import pytest

sys.path.insert(0, str(Path(__file__).resolve().parents[2] / 'tools'))
import diagnostic_expectations as de
import profile_check as pc
from render_runtime_matrix import classify as matrix_classify

CASES = json.loads((pc.REPO/'android/probe/src/test/resources/expectation-cases.json').read_text())

@pytest.mark.parametrize('case', CASES, ids=lambda v: v['name'])
def test_shared_java_python_policy_cases(case):
    assert de.evaluate(case['rule'], case['actual']) == dict(status=case['status'], reason=case['reason'])


def policy_row(rule, actual, key='runtime.javaBridgeVisible'):
    checked = de.evaluate(rule, actual)
    return dict(id=key, source='synthetic', expected=rule.get('value'), actual=actual,
                has_expectation=True, expectation_policy=rule['policy'], policy_version=1,
                expectation_rule_sha256=pc.digest({k:v for k,v in rule.items() if k!='expected_change'}),
                expected_change=rule.get('expected_change', False),
                actual_available=actual is not None and actual != '', detail=checked['reason'],
                duration_ms=0, **checked)


def report(row, request='first', artifact='a'*64):
    return dict(rows=[row], request_id=request, artifact=artifact)


@pytest.mark.parametrize('left,right,change,status,reason', [
    (False,False,False,'PASS','STABLE_MATCH'),
    (False,True,False,'FAIL','UNEXPECTED_STABILITY_CHANGE'),
    (False,True,True,'PASS','EXPECTED_STABILITY_CHANGE'),
    (True,True,True,'FAIL','EXPECTED_STABILITY_CHANGE_MISSING'),
])
def test_stability_uses_two_captures_and_explicit_transition_expectation(left,right,change,status,reason):
    rule=dict(policy='STABLE_ACROSS_SWITCH',type='boolean')
    a=report(policy_row(rule,left))
    b=report(policy_row(dict(rule,expected_change=change),right),'second')
    raw=pc.compare(a,b)
    assert raw==pc.compare(pc.redact_report(a),pc.redact_report(b))
    assert raw[0]['status']==status and raw[0]['reason']==reason
    result=pc.classify([a,b],raw)
    assert result['result']==('PASS' if status=='PASS' else 'FAIL')
    assert a['rows'][0]['status']=='OBSERVED'  # Single captures never promoted.


@pytest.mark.parametrize('mutation', ['no_artifact','other_artifact','same_request','no_request','no_value_hash','other_rule'])
def test_stability_without_comparable_evidence_remains_pending(mutation):
    r=policy_row(dict(policy='STABLE_ACROSS_SWITCH',type='boolean'),False)
    a,b=report(r),report(copy.deepcopy(r),'second')
    if mutation=='no_artifact': a.pop('artifact')
    if mutation=='other_artifact': b['artifact']='b'*64
    if mutation=='same_request': b['request_id']=a['request_id']
    if mutation=='no_request': b.pop('request_id')
    if mutation=='no_value_hash': b['rows'][0]['actual']=None
    if mutation=='other_rule': b['rows'][0]['expectation_rule_sha256']='c'*64
    result=pc.compare(a,b)
    assert result[0]['status']=='OBSERVED' and result[0]['severity']=='WARNING'
    assert pc.classify([a,b],result)['result']=='DEGRADED'


def test_all_captures_need_stability_edges_and_duplicate_edges_do_not_fill_gaps():
    r=policy_row(dict(policy='STABLE_ACROSS_SWITCH',type='boolean'),False)
    a,b,c=[report(copy.deepcopy(r),s) for s in ['first','second','third']]
    first=pc.compare(a,b)
    assert pc.classify([a])['pending_rows']==1
    assert pc.classify([a,b,c],first*2)['pending_rows']==1
    assert pc.classify([a,b,c],first+pc.compare(b,c))['pending_rows']==0
    assert pc.classify([a,a],pc.compare(a,a))['result']=='DEGRADED'


def test_failure_is_not_masked_by_policy_diff():
    rule=dict(policy='STABLE_ACROSS_SWITCH',type='boolean')
    a,b=report(policy_row(rule,False)),report(policy_row(rule,'wrong'),'second')
    assert pc.compare(a,b)[0]['severity']=='FAIL'
    assert pc.classify([a,b],pc.compare(a,b))['result']=='FAIL'


@pytest.mark.parametrize('actual', [None,'<unknown ssid>','a real network'])
def test_external_state_is_explicit_never_pass_or_ordinary_warning(actual):
    rule=dict(policy='EXTERNAL_STATE',type='string',unavailable=[dict(value='<unknown ssid>',reason='SSID_REDACTED')])
    r=report(policy_row(rule,actual,'wifi.ssid'))
    assert r['rows'][0]['status']=='OBSERVED'
    result=pc.classify([r])
    assert result['warnings']==0 and result['not_covered_rows']==0 and result['external_state_rows']==1
    assert 'EXTERNAL_STATE_' in r['rows'][0]['reason']
    changed=report(policy_row(rule,'different network','wifi.ssid'),'second')
    assert pc.compare(r,changed)[0]['severity']=='INFO'


@pytest.mark.parametrize('policy', ['EXTERNAL_STATE','STABLE_ACROSS_SWITCH','TYPED','ENUM'])
def test_policy_only_rows_never_establish_runtime_verified(policy):
    r=dict(status='PASS',expectation_policy=policy)
    reads={s:copy.deepcopy(r) for s in ['bound-a','bound-a-repeat','bound-b','unbound-before','unbound-after']}
    assert matrix_classify(reads)=='POLICY_ONLY'
    reads['bound-b']['status']='FAIL'
    assert matrix_classify(reads)=='FAIL'


@pytest.mark.parametrize('key,value', [('policy_version',2),('policy_version',True),('expectation_policy','UNKNOWN'),('expectation_rule_sha256','bad'),('has_expectation',False),('actual_available','yes'),('expected_change','yes'),('status','PASS')])
def test_malformed_report_policy_or_unjustified_promotion_is_rejected(key,value):
    r=policy_row(dict(policy='STABLE_ACROSS_SWITCH',type='boolean'),False)
    r[key]=value
    with pytest.raises(ValueError): pc.parse_report(report(r))


def test_policy_redaction_and_stability_preserve_no_secret_values():
    r=report(policy_row(dict(policy='EXACT',value='PRIVATE-EXPECTED'),'PRIVATE-ACTUAL','diagnostic.test'))
    safe=pc.redact_report(r)
    assert 'PRIVATE-' not in json.dumps(safe)
    assert safe['rows'][0]['actual_sha256']!=safe['rows'][0]['expected_sha256']
    assert 'value' not in safe['rows'][0]


def test_legacy_redacted_evidence_is_not_promoted_or_re_evaluated():
    old=json.loads((pc.REPO/'docs/runtime-evidence/2026-10-16-diagnostics-quality-233115/bound-a.json').read_text())
    verdict=pc.classify([old])
    assert verdict['not_covered_rows']==12
    assert verdict['statuses']==dict(PASS=77,OBSERVED=12)
    assert not any(r.get('expectation_policy') for r in old['rows'])


@pytest.mark.parametrize('label', ['a','b'])
def test_every_uncovered_id_has_a_valid_sidecar_without_changing_profile_schema(label):
    root=pc.REPO/'android/probe/src/main/assets'
    sidecar=json.loads((root/f'expectations-{label}.json').read_text())
    rows=de.fixture(sidecar,label)
    old=json.loads((pc.REPO/'docs/runtime-evidence/2026-10-16-diagnostics-quality-233115/bound-a.json').read_text())
    assert set(rows)=={r['id'] for r in old['rows'] if r['status']=='OBSERVED'}
    assert len(rows)==12
    profile=json.loads((root/f'profile-{label}.json').read_text())
    assert profile['schema_version']=='0.3' and 'expectations' not in profile
    with pytest.raises(ValueError): de.fixture(sidecar,'b' if label=='a' else 'a')


@pytest.mark.parametrize('change', [dict(schema_version=2),dict(schema_version=True),dict(extra=1),dict(rows=[]),dict(rows={'x':{'policy':'UNKNOWN'}})])
def test_fixture_schema_rejects_malformed_extensions(change):
    fixture=dict(schema_version=1,fixture='a',rows={})
    fixture.update(change)
    with pytest.raises(ValueError): de.fixture(fixture,'a')

ROW_CASES = [
    ('telephony.simState',5,99,'OBSERVED'),
    ('telephony.phoneType',1,99,'PASS'),
    ('telephony.roaming',False,'false','PASS'),
    ('settings.unrelatedControl','0','enabled','OBSERVED'),
    ('wifi.bssid','12:34:56:78:9A:BC','bad','OBSERVED'),
    ('wifi.linkSpeed',54,-2,'OBSERVED'),
    ('wifi.ssid','example',[1],'OBSERVED'),
    ('bluetooth.name','Example',123,'OBSERVED'),
    ('sensors.list',[],[{}],'PASS'),
    ('packages.visible',['android'],['bad path'],'PASS'),
    ('runtime.loadedLibraries',['libpine.so'],['/private/libpine.so'],'PASS'),
    ('runtime.javaBridgeVisible',False,'false','OBSERVED'),
]

@pytest.mark.parametrize('key,valid,invalid,status',ROW_CASES)
def test_each_assigned_row_handles_valid_invalid_unavailable_and_malformed_policy(key,valid,invalid,status):
    sidecar=json.loads((pc.REPO/'android/probe/src/main/assets/expectations-a.json').read_text())
    rule=sidecar['rows'][key]
    assert de.evaluate(rule,valid)['status']==status
    assert de.evaluate(rule,invalid)['status']=='FAIL'
    unavailable=de.evaluate(rule,None)
    assert unavailable['status']==('OBSERVED' if rule['policy']=='EXTERNAL_STATE' else 'MISSING')
    assert de.evaluate(dict(rule,policy='MALFORMED'),valid)==dict(status='FAIL',reason='MALFORMED_EXPECTATION')


def test_permission_denial_is_not_hidden_as_optional_external_state():
    rule=dict(policy='EXTERNAL_STATE',type='string',unavailable=[])
    r=policy_row(rule,None,'wifi.ssid')
    r.update(status='BLOCKED',reason='PERMISSION_DENIED:android.permission.ACCESS_FINE_LOCATION')
    result=pc.classify([report(r)])
    assert result['blocked_rows']==1 and result['warnings']==1 and result['external_state_rows']==0


def test_profiles_keep_exact_canonical_hashes():
    for label,expected in [('a','e476b02fd308c62cf207f4fe60e97085bbd14ca6293b405c2cedb996c9555f53'),('b','2bb5d74138d14251f316038be1a679fb53b1aad0796e190a8d03e7bb8494482e')]:
        body=json.loads((pc.REPO/f'android/probe/src/main/assets/profile-{label}.json').read_text())
        assert pc.digest(body)==expected


def test_repeated_redaction_keeps_original_hashes_and_cannot_manufacture_stability():
    rule=dict(policy='STABLE_ACROSS_SWITCH',type='boolean')
    a=pc.redact_report(report(policy_row(rule,False)))
    b=pc.redact_report(report(policy_row(rule,True),'second'))
    assert pc.redact_report(a)==a and pc.redact_report(b)==b
    assert pc.compare(pc.redact_report(a),pc.redact_report(b))[0]['status']=='FAIL'


@pytest.mark.parametrize('key,value', [('actual_sha256','bad'),('expected_sha256',123),('expectation_policy',[]),('reason','NO_PROFILE_EXPECTATION')])
def test_malformed_hashes_or_policy_reason_are_not_evidence(key,value):
    row=policy_row(dict(policy='STABLE_ACROSS_SWITCH',type='boolean'),False)
    row[key]=value
    with pytest.raises(ValueError): pc.parse_report(report(row))


def test_legacy_runner_also_redacts_exact_expectations_and_is_idempotent():
    from run_runtime_probe import redact
    raw=dict(rows=[dict(id='test',actual='PRIVATE-ACTUAL',expected='PRIVATE-EXPECTED')])
    safe=redact(raw)
    assert 'PRIVATE-' not in json.dumps(safe)
    assert redact(safe)==safe
    assert safe['rows'][0]['actual_sha256']!=safe['rows'][0]['expected_sha256']
