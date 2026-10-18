"""Version 1 descriptive runtime contract. No runtime actions or profile mutation."""
import copy
import json
from pathlib import Path
from .reality import validate

TABLE = json.loads((Path(__file__).resolve().parents[2] / 'catalog/reality-runtime-fields.json').read_text(encoding='utf8'))
STATUSES = ('APPLIED', 'PARTIAL', 'PENDING', 'DIAGNOSTIC_ONLY', 'HOST_ONLY', 'UNSUPPORTED')


def present(value, path):
    if not path:
        return value is not None
    head, *tail = path
    if head.endswith('[]'):
        items = value.get(head[:-2]) if isinstance(value, dict) else None
        return isinstance(items, list) and any(present(item, tail) for item in items)
    return isinstance(value, dict) and head in value and present(value[head], tail)


def generate_contract(profile):
    schema = profile.get('schema_version')
    if schema not in ('0.2', '0.3', '0.4'):
        raise ValueError('UNSUPPORTED_PROFILE_SCHEMA')
    if 'reality' not in profile:
        return dict(format=1, schema_version=schema, presence='not present', fields=[])
    projected = copy.deepcopy(profile)
    if not isinstance(projected['reality'], dict):
        raise ValueError('INVALID_REALITY')
    known = {r['field_path'].split('.')[1] for r in TABLE['fields']}
    projected['reality'] = {k:v for k,v in projected['reality'].items() if k in known}
    if validate(projected):
        raise ValueError('INVALID_REALITY')
    return dict(format=1, schema_version=schema, presence='present', fields=[dict(r, present=present(projected, r['field_path'].split('.'))) for r in TABLE['fields']])


def validate_contract(contract):
    if not isinstance(contract, dict) or set(contract) != {'format', 'schema_version', 'presence', 'fields'}:
        raise ValueError('MALFORMED_CONTRACT')
    if type(contract['format']) is not int or contract['format'] != 1 or contract['schema_version'] not in ('0.2','0.3','0.4'):
        raise ValueError('UNSUPPORTED_CONTRACT')
    rows = contract['fields']
    if contract['presence'] == 'not present' and rows == []:
        return
    if contract['presence'] != 'present' or contract['schema_version'] != '0.4' or not isinstance(rows, list) or len(rows) != len(TABLE['fields']):
        raise ValueError('INCOMPLETE_CONTRACT')
    for row, expected in zip(rows, TABLE['fields']):
        if not isinstance(row, dict) or type(row.get('present')) is not bool or {k:v for k,v in row.items() if k != 'present'} != expected:
            raise ValueError('CONTRACT_TABLE_MISMATCH')
