"""Strict, self-contained validation for the evidence-only R2 export.

The embedded schema avoids changing any product/profile schema. Raw capture files
are deliberately not prerequisites for a clean checkout's validation tests.
"""
import copy
from datetime import datetime
from pathlib import Path
import sys

import jsonschema
import pytest

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'forge'))
from cloak_forge.catalog_provenance import ImportRejected, load_json

HASH = {'type': 'string', 'pattern': '^[0-9a-f]{64}$'}
NULL_HASH = {'anyOf': [HASH, {'type': 'null'}]}
TEXT = {'type': 'string', 'minLength': 1, 'maxLength': 2048}
PATHS = {
    'reality.memory.values.free_storage_gib',
    'reality.sensors.values.inventory',
    *('reality.sensors.values.inventory[].' + key for key in
      ('max_delay_us', 'min_delay_us', 'name', 'range', 'resolution', 'type')),
    'reality.webview.values.user_agent',
}


def obj(properties):
    return {'type': 'object', 'properties': properties,
            'required': list(properties), 'additionalProperties': False}


OBSERVATION = obj({
    'capture_id': TEXT, 'probe_row_id': {'enum': [
        'envelope.sensors.inventory', 'envelope.storage.availableBytes',
        'envelope.webview.instanceUa']},
    'probe_status': {'const': 'MISSING'},
    'reason_code': {'enum': ['PROFILE_SURFACE_UNAVAILABLE', 'RuntimeException']},
    'actual_sha256': NULL_HASH, 'expected_sha256': NULL_HASH,
})
ROW = obj({
    'row_id': {'enum': sorted(PATHS)}, 'host_sha256': NULL_HASH,
    'fixture_expectation_sha256': HASH,
    'fixture_digest_projection': {'enum': ['direct_profile_field', 'ordered_inventory_field_projection']},
    'fixture_digest_algorithm': {'const': 'sha256/catalog_provenance.canonical-v1'},
    'classification': {'enum': ['PARTIAL', 'APPLIED', 'UNVERIFIED']},
    'reason_code': OBSERVATION['properties']['reason_code'],
    'evidence_scope': {'enum': ['unavailable_direct_reader', 'unavailable_aggregate_no_child_measurement']},
    'observations': {'type': 'array', 'minItems': 3, 'maxItems': 3, 'items': OBSERVATION},
})
SOURCE = obj({
    'capture_id': TEXT, 'android_user_id': {'const': 17},
    'captured_at': {'type': 'string', 'format': 'date-time'},
    'report_path': {'type': 'string', 'pattern': '^docs/runtime-evidence/[^.]+/user-17-capture-[123]-report\\.json$'},
    'report_sha256': HASH, 'markers_sha256': HASH,
})
APPLIED = obj({
    'row_id': TEXT,
    'probe_row_ids': {'type': 'array', 'minItems': 1, 'uniqueItems': True, 'items': TEXT},
    'regressed': {'type': 'boolean'}, 'source': {'const': 'archived_three_capture_replay'},
    'statuses': {'type': 'array', 'minItems': 3, 'maxItems': 3, 'items': {
        'type': 'array', 'minItems': 1, 'items': {'enum': ['PASS', 'FAIL', 'MISSING', 'BLOCKED', 'OBSERVED', 'UNMAPPED']}}},
})
SCHEMA = obj({
    'format': {'const': 1}, 'generated_at': {'type': 'string', 'format': 'date-time'},
    'source_commit': {'type': 'string', 'pattern': '^[0-9a-f]{40}$'},
    'source_tree_dirty': {'const': True},
    'measurement_mode': {'const': 'ARCHIVED_CAPTURE_REPLAY_NO_DEVICE_WRITES'},
    'artifact_digest': HASH,
    'artifact_digest_kind': {'const': 'installed_arm64_library_sha256_archived_load_record_match'},
    'artifact': obj({
        'version': {'const': '0.11.0-alpha.28'}, 'version_code': {'const': 52},
        'original_candidate_zip_sha256': HASH,
        'installed_files': obj({
            '/data/adb/modules/cyclone_cloak/zygisk/arm64-v8a.so': HASH,
            '/data/adb/modules/cyclone_cloak/zygisk/armeabi-v7a.so': HASH,
            '/data/adb/modules/cyclone_cloak/artifacts.sha256': HASH,
        }),
        'device_check': {'const': 'READ_ONLY_INSTALLED_FILE_HASHES_NOT_MEMORY_ATTESTATION'},
    }),
    'fixture_sha256': HASH,
    'sources': {'type': 'array', 'minItems': 3, 'maxItems': 3, 'items': SOURCE},
    'rows': {'type': 'array', 'minItems': 9, 'maxItems': 9, 'items': ROW},
    'applied_review': {'type': 'array', 'minItems': 8, 'maxItems': 8, 'items': APPLIED},
    'applied_regressions': {'type': 'array', 'uniqueItems': True, 'items': TEXT},
    'notes': {'type': 'array', 'minItems': 1, 'items': TEXT},
})


def validate(value):
    jsonschema.Draft202012Validator(SCHEMA, format_checker=jsonschema.FormatChecker()).validate(value)
    if [r['row_id'] for r in value['rows']] != sorted(PATHS):
        raise ValueError('INCOMPLETE_OR_DUPLICATE_PATHS')
    sources = [s['capture_id'] for s in value['sources']]
    if len(set(sources)) != 3:
        raise ValueError('DUPLICATE_CAPTURE')
    generated = datetime.fromisoformat(value['generated_at'].replace('Z', '+00:00'))
    captured = [datetime.fromisoformat(s['captured_at'].replace('Z', '+00:00'))
                for s in value['sources']]
    if generated.tzinfo is None or any(t.tzinfo is None for t in captured):
        raise ValueError('MISSING_TIMEZONE')
    if any(t > generated for t in captured):
        raise ValueError('INVALID_CAPTURE_TIME')
    if value['artifact_digest'] != value['artifact']['installed_files'][
            '/data/adb/modules/cyclone_cloak/zygisk/arm64-v8a.so']:
        raise ValueError('ARTIFACT_MISMATCH')
    for row in value['rows']:
        ua = row['row_id'].endswith('.user_agent')
        if row['classification'] != ('UNVERIFIED' if ua else 'PARTIAL'):
            raise ValueError('UNPROVEN_PROMOTION')
        if row['host_sha256'] is not None or any(o['actual_sha256'] is not None for o in row['observations']):
            raise ValueError('FABRICATED_MISSING_VALUE')
        if [o['capture_id'] for o in row['observations']] != sources:
            raise ValueError('CAPTURE_BINDING_MISMATCH')
        if any(o['reason_code'] != row['reason_code'] for o in row['observations']):
            raise ValueError('REASON_MISMATCH')
        expected_row = ('envelope.webview.instanceUa' if ua else
                        'envelope.sensors.inventory' if '.sensors.' in row['row_id'] else
                        'envelope.storage.availableBytes')
        if any(o['probe_row_id'] != expected_row for o in row['observations']):
            raise ValueError('WRONG_SURFACE')
        if row['reason_code'] != ('RuntimeException' if ua else 'PROFILE_SURFACE_UNAVAILABLE'):
            raise ValueError('INVENTED_REASON')
    reviews = value['applied_review']
    if len({r['row_id'] for r in reviews}) != len(reviews):
        raise ValueError('DUPLICATE_REVIEW')
    for review in reviews:
        if any(len(statuses) != len(review['probe_row_ids']) for statuses in review['statuses']):
            raise ValueError('INCOMPLETE_APPLIED_REVIEW')
        if review['regressed'] != any(s != 'PASS' for statuses in review['statuses'] for s in statuses):
            raise ValueError('REGRESSION_MISMATCH')
    if sorted(value['applied_regressions']) != sorted(r['row_id'] for r in reviews if r['regressed']):
        raise ValueError('MISSING_REGRESSION')


def report():
    return load_json((ROOT / 'docs/r2-measurement.json').read_bytes())


def test_measurement_validates_with_explicit_historical_provenance():
    jsonschema.Draft202012Validator.check_schema(SCHEMA)
    value = report()
    validate(value)
    assert len(value['rows']) == 9
    assert sum(r['classification'] == 'PARTIAL' for r in value['rows']) == 8
    assert sum(r['classification'] == 'UNVERIFIED' for r in value['rows']) == 1
    assert value['applied_regressions'] == []


@pytest.mark.parametrize('raw', [b'{"format":1,"format":1}', b'{"x":NaN}', b'{"x":Infinity}', b'{} trailing'])
def test_existing_strict_parser_rejects_ambiguous_json(raw):
    with pytest.raises(ImportRejected):
        load_json(raw)


@pytest.mark.parametrize('mutation', [
    'raw_value', 'hash', 'duplicate_path', 'unknown_path', 'missing_row',
    'promotion', 'host_fallback', 'artifact', 'foreign_capture', 'duplicate_capture',
    'reason', 'wrong_surface', 'regression', 'new_measurement_claim', 'timestamp',
])
def test_malformed_or_overclaimed_measurement_is_rejected(mutation):
    value = copy.deepcopy(report())
    row = value['rows'][0]
    if mutation == 'raw_value': row['actual'] = 'sensitive-value'
    elif mutation == 'hash': row['fixture_expectation_sha256'] = 'not-a-digest'
    elif mutation == 'duplicate_path': value['rows'][1] = copy.deepcopy(row)
    elif mutation == 'unknown_path': row['row_id'] = 'reality.unknown'
    elif mutation == 'missing_row': value['rows'].pop()
    elif mutation == 'promotion': row['classification'] = 'APPLIED'
    elif mutation == 'host_fallback': row['host_sha256'] = 'a' * 64
    elif mutation == 'artifact': value['artifact_digest'] = 'a' * 64
    elif mutation == 'foreign_capture': row['observations'][0]['capture_id'] = 'foreign'
    elif mutation == 'duplicate_capture': value['sources'][1] = copy.deepcopy(value['sources'][0])
    elif mutation == 'reason': row['reason_code'] = 'RuntimeException'
    elif mutation == 'wrong_surface': row['observations'][0]['probe_row_id'] = 'envelope.webview.instanceUa'
    elif mutation == 'regression': value['applied_review'][0]['regressed'] = True
    elif mutation == 'new_measurement_claim': value['measurement_mode'] = 'FRESH_DEVICE_CAPTURE'
    else: value['generated_at'] = 'not-a-time'
    with pytest.raises((ValueError, jsonschema.ValidationError)):
        validate(value)
