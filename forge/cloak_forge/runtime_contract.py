"""Version 2 linked runtime contract; format 1 remains readable. No runtime actions or profile mutation."""
import copy
import json
from pathlib import Path
from .reality import validate
from .runtime_link import LINKS, evaluate

TABLE = json.loads((Path(__file__).resolve().parents[2] / 'catalog/reality-runtime-fields.json').read_text(encoding='utf8'))
LEGACY_TABLE = json.loads((Path(__file__).resolve().parents[2] / 'catalog/reality-runtime-fields-v1.json').read_text(encoding='utf8'))
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
        return dict(format=2, schema_version=schema, presence='not present', fields=[])
    projected = copy.deepcopy(profile)
    if not isinstance(projected['reality'], dict):
        raise ValueError('INVALID_REALITY')
    known = {r['field_path'].split('.')[1] for r in TABLE['fields']}
    projected['reality'] = {k:v for k,v in projected['reality'].items() if k in known}
    if any(code != 'REALITY_LINK' or field not in LINKS for _, code, field, _ in validate(projected)):
        raise ValueError('INVALID_REALITY')
    parity = {r['field_path']: r for r in evaluate(projected)}
    rows = []
    for source in TABLE['fields']:
        row = dict(source, present=present(projected, source['field_path'].split('.')), parity_state='NOT_REQUIRED', parity_passed=None)
        if source['field_path'] in parity:
            link = parity[source['field_path']]
            row.update(parity_state=link['parity_state'], parity_passed=link['parity_passed'], present=link['reality_present'])
            row['status'] = 'APPLIED' if link['parity_passed'] is True else 'PARTIAL'
        rows.append(row)
    return dict(format=2, schema_version=schema, presence='present', fields=rows)


def validate_contract(contract):
    if not isinstance(contract, dict) or set(contract) != {'format', 'schema_version', 'presence', 'fields'}:
        raise ValueError('MALFORMED_CONTRACT')
    if type(contract['format']) is not int or contract['format'] not in (1, 2) or contract['schema_version'] not in ('0.2','0.3','0.4'):
        raise ValueError('UNSUPPORTED_CONTRACT')
    rows = contract['fields']
    if contract['presence'] == 'not present' and rows == []:
        return
    if contract['presence'] != 'present' or contract['schema_version'] != '0.4' or not isinstance(rows, list) or len(rows) != len(TABLE['fields']):
        raise ValueError('INCOMPLETE_CONTRACT')
    table = LEGACY_TABLE if contract['format'] == 1 else TABLE
    for row, expected in zip(rows, table['fields']):
        if not isinstance(row, dict) or type(row.get('present')) is not bool:
            raise ValueError('CONTRACT_TABLE_MISMATCH')
        actual = dict(row)
        actual.pop('present')
        if contract['format'] == 2:
            state, passed = actual.pop('parity_state', None), actual.pop('parity_passed', 'absent')
            if expected['linked_base_path']:
                if state not in ('MATCHED','MISMATCHED','NOT_PRESENT','BASE_MISSING') or passed is not (True if state == 'MATCHED' else False if state == 'MISMATCHED' else None):
                    raise ValueError('INVALID_PARITY')
                if row['present'] != (state != 'NOT_PRESENT') or row['status'] != ('APPLIED' if passed is True else 'PARTIAL'):
                    raise ValueError('UNPROVEN_APPLIED')
                actual['status'] = expected['status']
            elif state != 'NOT_REQUIRED' or passed is not None:
                raise ValueError('UNEXPECTED_PARITY')
        if actual != expected:
            raise ValueError('CONTRACT_TABLE_MISMATCH')
