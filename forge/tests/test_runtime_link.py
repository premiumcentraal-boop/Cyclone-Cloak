import copy
import json
from pathlib import Path
import pytest
from cloak_forge.runtime_link import LINKS, evaluate
from cloak_forge.runtime_contract import generate_contract, validate_contract, LEGACY_TABLE
P = json.loads((Path(__file__).parent/'vectors/reality.json').read_text(encoding='utf8'))['profile']


def test_mapping_exact_and_non_mutating():
    p=copy.deepcopy(P); assert len(LINKS)==10
    assert LINKS['reality.panel.values.density_dpi']=='display.density'
    assert all(r['parity_passed'] is True for r in evaluate(p))
    assert p==P


@pytest.mark.parametrize('path', LINKS)
def test_each_mismatch_is_reported_and_never_overrides_base(path):
    p=copy.deepcopy(P); _, section, _, key=path.split('.')
    value=p['reality'][section]['values'][key]
    p['reality'][section]['values'][key]=value+1 if isinstance(value,int) else 'different'
    before=copy.deepcopy(p)
    assert next(r for r in evaluate(p) if r['field_path']==path)['parity_state']=='MISMATCHED'
    assert p==before


def test_contract_mismatch_is_partial_not_applied():
    p=copy.deepcopy(P);p['reality']['panel']['values']['width']+=1
    c=generate_contract(p);validate_contract(c)
    row=next(r for r in c['fields'] if r['field_path']=='reality.panel.values.width')
    assert row['status']=='PARTIAL' and row['parity_passed'] is False
    row['status']='APPLIED'
    with pytest.raises(ValueError):validate_contract(c)


@pytest.mark.parametrize('schema',['0.2','0.3','0.4'])
def test_missing_reality_is_compatible(schema):
    assert all(r['parity_state']=='NOT_PRESENT' and r['parity_passed'] is None for r in evaluate({'schema_version':schema}))


def test_missing_base_and_unavailable_reality_are_not_passes():
    p=copy.deepcopy(P);del p['display']['width']
    assert next(r for r in evaluate(p) if r['field_path']=='reality.panel.values.width')['parity_state']=='BASE_MISSING'
    p['reality']['panel']['availability']='unavailable'
    assert all(r['parity_state']=='NOT_PRESENT' for r in evaluate(p) if r['field_path'].startswith('reality.panel.'))


def test_legacy_contract_is_readable():
    c=dict(format=1,schema_version='0.4',presence='present',fields=[dict(r,present=True) for r in LEGACY_TABLE['fields']])
    validate_contract(c)
