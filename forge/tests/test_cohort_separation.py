import copy
import json
from pathlib import Path

import pytest

from cloak_forge.cohort_separation import POLICY, analyze
from cloak_forge.forge import forge_profile

ROOT = Path(__file__).resolve().parents[2]


def profile(seed='ab', template='pixel_7', version='0.4'):
    return forge_profile('Local draft', seed * 32, template, schema_version=version)


def twin(p):
    q = copy.deepcopy(p)
    q['id'] = 'second'
    q['name'] = 'Second draft'
    return q


def test_identical_profiles_report_shared_combinations_without_modification():
    p = profile(); q = twin(p); before = copy.deepcopy([p, q])
    r = analyze([p, q])
    assert r['independence_score'] == 0
    assert len(r['identifier_findings']) == len(POLICY['categories']['stable_identifiers'])
    assert any(g['fields'] == ['device.model', 'device.fingerprint'] for g in r['shared_groups'])
    # Unknown sensor inventory must not create an invented complete-combination group.
    assert not any(len(g['fields']) == 10 for g in r['shared_groups'])
    assert 'HOMOGENEOUS_COHORT_NOT_HIGH_TRUST_EVIDENCE' in r['warnings']
    assert [p, q] == before


def test_near_duplicate_identifier_is_flagged_without_value():
    p = profile(); q = twin(p)
    value = q['identifiers']['android_id']
    q['identifiers']['android_id'] = ('0' if value[0] != '0' else '1') + value[1:]
    assert {'profiles': sorted([p['id'], q['id']]), 'field': 'identifiers.android_id',
            'kind': 'near_duplicate'} in analyze([p, q])['identifier_findings']


def test_distinct_profiles_separate_identifiers_but_do_not_invent_hardware_independence():
    p = profile(); q = profile('cd', 'pixel_8')
    r = analyze([p, q])
    assert not r['identifier_findings']
    scores = r['pairs'][0]['categories']
    assert scores['stable_identifiers'] == {'distance': 100, 'confidence': 100}
    assert scores['hardware_description']['distance'] > 0
    assert scores['play_google'] == {'distance': 0, 'confidence': 0}
    assert 0 < r['independence_score'] < 100


@pytest.mark.parametrize('version', ['0.2', '0.3', '0.4'])
def test_missing_reality_and_old_profiles_reduce_confidence(version):
    p = profile(version=version); p.pop('reality', None)
    r = analyze([p, twin(p)])
    assert len(r['missing_reality']) == 24
    assert r['confidence'] < 100


def test_unavailable_facts_are_not_compared_even_if_values_remain():
    p = profile(); q = twin(p)
    for row in (p, q):
        row['reality']['compute'] = {'availability': 'unavailable', 'source': 'needs verification',
                                     'reason': 'Unavailable.', 'values': {}}
    r = analyze([p, q])
    assert any(m['state'] == 'unavailable' for m in r['missing_reality'])
    assert next(f for f in r['weakest_fields'] if f['field'] == 'reality.compute.values')['known_profiles'] == 0


def test_empty_and_single_library_are_not_independence_evidence():
    for profiles in ([], [profile()]):
        r = analyze(profiles)
        assert r['independence_score'] is None and r['confidence'] == 0
        assert 'INSUFFICIENT_COHORT' in r['warnings']


@pytest.mark.parametrize('bad', [None, {}, [None], [{}], [{'id': 'x', 'name': 'secret'}], [profile()] * 2,
                                [profile()] * 201])
def test_malformed_input_fails_with_fixed_message(bad):
    with pytest.raises(ValueError, match='^COHORT_INPUT_INVALID$'):
        analyze(bad)


def test_stable_ordering_recomputation_and_no_identifier_or_seed_leakage():
    p = profile(); q = profile('cd'); rows = [p, q]
    r = analyze(rows)
    assert r == analyze(list(reversed(rows))) == analyze(copy.deepcopy(rows))
    text = json.dumps(r)
    for row in rows:
        for v in list(row['identifiers'].values()) + [row['seed'], row['reality']['inventory_id']]:
            assert v not in text
        assert row['device']['fingerprint'] not in text
    assert set(r['pairs'][0]['categories']) == set(POLICY['categories'])


def test_missing_facts_never_increase_distance_or_confidence():
    p = profile(); q = profile('cd', 'pixel_8')
    before = analyze([p, q]); p.pop('reality'); q.pop('reality')
    after = analyze([p, q])
    assert after['confidence'] < before['confidence']
    assert after['independence_score'] <= before['independence_score']


def test_policy_covers_every_existing_stable_identifier():
    from cloak_forge.engine import IDENTIFIER_FIELDS
    assert {'identifiers.' + f for f in IDENTIFIER_FIELDS} <= set(POLICY['categories']['stable_identifiers'])


def test_complete_correlated_combination_requires_all_facts():
    p = profile()
    vector = json.loads((ROOT / 'forge/tests/vectors/reality.json').read_text())
    p['reality']['sensors'] = next(c for c in vector['cases'] if c['name'] == 'valid sensors')['patch']['reality.sensors']
    assert any(len(g['fields']) == 10 for g in analyze([p, twin(p)])['shared_groups'])


def test_google_surface_is_separate_and_unknown_does_not_earn_confidence():
    p = profile()
    p['play_surface'] = json.loads((ROOT / 'forge/tests/vectors/play-surface.json').read_text())['surface']
    q = twin(p)
    q['play_surface']['account']['slot_id'] = 'different-local-slot'
    r = analyze([p, q])
    category = r['pairs'][0]['categories']['play_google']
    assert category == {'distance': 10, 'confidence': 90}
    text = json.dumps(r)
    for row in (p, q):
        assert row['play_surface']['account']['slot_id'] not in text
        assert row['play_surface']['account']['display_hint'] not in text


@pytest.mark.parametrize('mutation', ['label', 'schema', 'section', 'device'])
def test_malformed_profile_bodies_are_rejected_without_echoing(mutation):
    p = profile()
    if mutation == 'label': p['name'] = 'secret\nlabel'
    if mutation == 'schema': p['schema_version'] = 'secret-version'
    if mutation == 'section': p['reality']['memory'] = 'secret-body'
    if mutation == 'device': p['device'] = None
    with pytest.raises(ValueError, match='^COHORT_INPUT_INVALID$'):
        analyze([p])
