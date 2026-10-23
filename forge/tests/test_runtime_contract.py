import copy
import json
import subprocess
import sys
from pathlib import Path
import pytest
from cloak_forge.runtime_contract import TABLE, STATUSES, generate_contract, validate_contract

ROOT = Path(__file__).resolve().parents[2]
PROFILE = json.loads((ROOT/'forge/tests/vectors/reality.json').read_text(encoding='utf8'))['profile']


def test_deterministic_private_and_complete_schema_coverage():
    before = copy.deepcopy(PROFILE)
    result = generate_contract(PROFILE)
    validate_contract(result)
    assert result == generate_contract(PROFILE) and PROFILE == before
    def paths(rule, path):
        if rule['type'] == 'object':
            return sum((paths(v, path+'.'+k) for k,v in sorted(rule['properties'].items())), [])
        return [path] + (paths(rule['items'], path+'[]') if rule['type'] == 'array' else [])
    schema=json.loads((ROOT/'schema/device-reality.schema.json').read_text(encoding='utf8'))
    assert [r['field_path'] for r in result['fields']] == paths(schema,'reality')
    assert all(r['reason'] and (r['surface'] or r['status'] != 'APPLIED') for r in result['fields'])
    assert len({r['surface'] for r in result['fields'] if r['surface']}) == sum(bool(r['surface']) for r in result['fields'])
    assert PROFILE['seed'] not in json.dumps(result)
    for row in result['fields']:
        if row['field_path'].startswith(('reality.drm.values.', 'reality.power.values.')):
            assert row['metadata_only'] and not row['requires_future_runtime_work']
            assert not row['safe_representation']


@pytest.mark.parametrize('status', STATUSES)
def test_unreviewed_status_transition_rejected(status):
    result=generate_contract(PROFILE)
    row=next(r for r in result['fields'] if r['status'] != status)
    row['status']=status
    with pytest.raises(ValueError): validate_contract(result)


@pytest.mark.parametrize('mutation', ['unknown','duplicate','surface','reason','present','extra','missing'])
def test_malformed_contract(mutation):
    result=generate_contract(PROFILE)
    if mutation=='unknown': result['fields'][0]['field_path']='reality.secret'
    elif mutation=='duplicate': result['fields'][1]=result['fields'][0].copy()
    elif mutation=='surface': result['fields'][0]['surface']='invented'
    elif mutation=='reason': result['fields'][0]['reason']=''
    elif mutation=='present': result['fields'][0]['present']=1
    elif mutation=='extra': result['raw']='private'
    else: result['fields'].pop()
    with pytest.raises(ValueError): validate_contract(result)


@pytest.mark.parametrize('version',['0.2','0.3','0.4'])
def test_old_without_reality(version):
    profile=dict(schema_version=version)
    result=generate_contract(profile)
    assert result['presence']=='not present' and result['fields']==[]
    validate_contract(result)


def test_unknown_future_sections_ignored_known_invalid_rejected():
    profile=copy.deepcopy(PROFILE); profile['reality']['future']={'secret':'never exported'}
    assert generate_contract(profile)==generate_contract(PROFILE)
    profile['reality']['panel']['values']['width']=-1
    with pytest.raises(ValueError): generate_contract(profile)


def test_generated_consumers_are_current():
    subprocess.run([sys.executable,str(ROOT/'tools/generate_runtime_contract.py'),'--check'],check=True)
    assert json.loads((ROOT/'android/probe/src/main/assets/reality-runtime-fields.json').read_text())==TABLE


def test_filesystem_observations_are_separate_unverified_capabilities():
    assert len(TABLE['fields']) == 97
    assert len(TABLE['observation_surfaces']) == 4
    for row in TABLE['observation_surfaces']:
        assert row['field_path'].startswith('diagnostics.filesystem.')
        assert row['status'] == 'DIAGNOSTIC_ONLY'
        assert row['runtime_covered'] is False
        assert row['linked_base_path'] is None
    contract = generate_contract(PROFILE)
    assert len(contract['fields']) == 97
    assert all(r['field_path'].startswith('reality.') for r in contract['fields'])


@pytest.mark.parametrize('mutation', [None, 'pass', 'raw', 'duplicate', 'unknown', 'digest', 'denial'])
def test_filesystem_observation_schema(mutation):
    import jsonschema
    schema = json.loads((ROOT/'schema/filesystem-observations.schema.json').read_text())
    rows = [dict(id=r['field_path'], source=r['surface'], status='OBSERVED',
                 reason='OBSERVATION_ONLY_NO_EXPECTATION', sha256='a'*64,
                 byte_count=12, line_count=1, duration_ms=0) for r in TABLE['observation_surfaces']]
    if mutation == 'pass': rows[0]['status'] = 'PASS'
    elif mutation == 'raw': rows[0]['content'] = 'private'
    elif mutation == 'duplicate': rows[1] = rows[0].copy()
    elif mutation == 'unknown': rows[0]['id'] = 'diagnostics.filesystem.unknown'
    elif mutation == 'digest': rows[0]['sha256'] = None
    elif mutation == 'denial': rows[0]['status'] = 'BLOCKED'
    report = dict(format=1, rows=rows)
    if mutation:
        with pytest.raises(jsonschema.ValidationError): jsonschema.validate(report, schema)
    else:
        jsonschema.validate(report, schema)
        for row in rows:
            row.update(status='BLOCKED', reason='PERMISSION_DENIED', sha256=None, byte_count=None, line_count=None)
        jsonschema.validate(report, schema)
