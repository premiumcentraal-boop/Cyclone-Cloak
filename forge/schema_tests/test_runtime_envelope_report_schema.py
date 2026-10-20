"""Optional extension is strict and never a verification or platform verdict."""
import copy
import json
from pathlib import Path

import jsonschema
import pytest

ROOT = Path(__file__).resolve().parents[2]
SCHEMA = json.loads((ROOT / 'schema/runtime-envelope-report.schema.json').read_text())


def report():
    return {'format': 1, 'verification': 'NOT_VERIFIED', 'limitations': [], 'rows': [{
        'id': 'envelope.compute.gpuRenderer', 'source': 'Android read-only API',
        'expected_sha256': 'a' * 64, 'actual_sha256': 'a' * 64,
        'status': 'PASS', 'reason': 'HASH_MATCH_ONLY', 'duration_ms': 0}]}


def test_valid_extension_and_missing_evidence_are_not_verified():
    value = report()
    jsonschema.validate(value, SCHEMA)
    row = value['rows'][0]
    row.update(status='OBSERVED', expected_sha256=None, reason='NO_EXPECTATION')
    jsonschema.validate(value, SCHEMA)
    assert value['verification'] == 'NOT_VERIFIED'


@pytest.mark.parametrize('mutation', ['raw', 'verified', 'unknown', 'hash', 'reason', 'missing', 'version'])
def test_malformed_or_sensitive_extensions_rejected(mutation):
    value = copy.deepcopy(report())
    if mutation == 'raw': value['rows'][0]['actual'] = 'private-value'
    elif mutation == 'verified': value['verification'] = 'VERIFIED'
    elif mutation == 'unknown': value['rows'][0]['id'] = 'envelope.unknown'
    elif mutation == 'hash': value['rows'][0]['actual_sha256'] = 'value'
    elif mutation == 'reason': value['rows'][0]['reason'] = 'private contents'
    elif mutation == 'missing': value['rows'][0]['expected_sha256'] = None
    else: value['format'] = 2
    with pytest.raises(jsonschema.ValidationError): jsonschema.validate(value, SCHEMA)


def test_contract_never_promotes_phase_two_to_applied_and_drm_is_not_elevated():
    table = json.loads((ROOT / 'catalog/reality-runtime-fields.json').read_text())
    partial = [r for r in table['fields'] if 'Phase 2 implementation' in r['reason']]
    assert len(partial) == 17
    assert all(r['status'] == 'PARTIAL' and r['requires_future_runtime_work'] for r in partial)
    drm = next(r for r in table['fields'] if r['field_path'] == 'reality.drm.values.security_level')
    assert drm['metadata_only'] and not drm['safe_representation']
    assert 'never substituted or elevated' in drm['reason']
