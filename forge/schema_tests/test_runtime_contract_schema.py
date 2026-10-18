import copy
import json
import sys
from pathlib import Path
import jsonschema
import pytest

ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'forge'))
from cloak_forge.runtime_contract import generate_contract

SCHEMA=json.loads((ROOT/'schema/reality-runtime.contract.json').read_text(encoding='utf8'))
PROFILE=json.loads((ROOT/'forge/tests/vectors/reality.json').read_text(encoding='utf8'))['profile']

def test_valid_and_absent_contracts():
    jsonschema.Draft202012Validator.check_schema(SCHEMA)
    jsonschema.validate(generate_contract(PROFILE),SCHEMA)
    for version in ['0.2','0.3','0.4']: jsonschema.validate(generate_contract({'schema_version':version}),SCHEMA)

@pytest.mark.parametrize('change',['unknown','duplicate','applied','host_applied','surface','reason','extra'])
def test_schema_rejects_invalid_capabilities(change):
    c=generate_contract(PROFILE); row=c['fields'][0]
    if change=='unknown': row['field_path']='reality.unknown'
    elif change=='duplicate': c['fields'][1]=dict(row,present=not row['present'])
    elif change in ['applied','host_applied']: row['status']='APPLIED'; row['surface']=None
    elif change=='surface': row['surface']='duplicated or unreviewed'
    elif change=='reason': row['reason']=''
    else: row['private']='secret'
    with pytest.raises(jsonschema.ValidationError): jsonschema.validate(c,SCHEMA)
