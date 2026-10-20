import copy
import json
import subprocess
import sys
from pathlib import Path

import pytest

from cloak_forge import instance_realism as realism
from cloak_forge.device_factory import DeviceFactory, FactoryError, InstanceRequest
from cloak_forge.engine import generate, generate_unique
from cloak_forge.forge import forge_profile
from cloak_forge.reality import at
from cloak_forge.validate import validate_profile

ROOT = Path(__file__).resolve().parents[2]
STAMP = '2026-10-20T12:00:00+00:00'
RULES = realism.catalog()


def request(**extra):
    return dict(name='Instance', seed='ab' * 32, template='pixel_7', schema_version='0.4', instance_realism=True, **extra)


def invalid(p):
    return any(f.severity == 'error' for f in validate_profile(p))


@pytest.mark.parametrize('source_id', sorted(RULES['sources']))
def test_every_approved_tuple_is_coherent_deterministic_and_provenanced(source_id):
    source = RULES['sources'][source_id]
    factory = DeviceFactory()
    for option in source['tuples']:
        if source_id.startswith('factory.'):
            r = InstanceRequest(source['template_id'], 'realism-test', instance_realism=True, realism_tuple=option['id'])
            a = factory.generate(r, created_at=STAMP)
            assert a == factory.generate(r, created_at=STAMP)
            p = a['profile']
            assert not factory.validate(p, source['template_id'])
        else:
            r = {**request(), 'template': source['template_id'], 'realism_tuple': option['id']}
            a = generate(r)
            assert a == generate(r)
            p = a['profile']
        assert not invalid(p)
        record = p['meta']['instance_realism']
        assert record['catalog_digest'] == realism.digest(RULES)
        assert set(option['changes']) <= set(record['provenance'])
        assert all(at(p, path) == fact['value'] for path, fact in option['changes'].items())
        assert p['integrity'] == {'verdict': 'unknown', 'checked_at': None, 'keybox_tag': None}


def test_seeds_and_tuples_generate_distinct_identifiers_and_actual_supported_diversity():
    rows = [generate({**request(), 'seed': f'{i:064x}'})['profile'] for i in range(64)]
    assert len({p['meta']['instance_realism']['tuple_id'] for p in rows}) == 8
    assert {at(p, 'reality.memory.values.storage_gib') for p in rows} == {128, 256}
    assert {at(p, 'reality.memory.values.ram_gib') for p in rows} == {8}
    assert {p['locale']['timezone'] for p in rows} == {'America/New_York', 'America/Los_Angeles'}
    for path in ['id', 'seed', 'reality.inventory_id', 'reality.radio.values.wifi_name', 'reality.radio.values.bluetooth_name']:
        assert len({at(p, path) for p in rows}) == len(rows)
    for field in rows[0]['identifiers']:
        assert len({p['identifiers'][field] for p in rows}) == len(rows)
    a = generate({**request(), 'realism_tuple': 'sku-8-128-new_york'})['profile']
    b = generate({**request(), 'realism_tuple': 'sku-8-256-new_york'})['profile']
    assert all(a['identifiers'][k] != b['identifiers'][k] for k in a['identifiers'])


@pytest.mark.parametrize('path,value', [('device.hardware', 'unsupported-chip'), ('display.width', 1440),
    ('camera.cameras', []), ('reality.memory.values.ram_gib', 16), ('reality.memory.values.storage_gib', 999),
    ('reality.drm.values.security_level', 'L1'), ('telephony.mnc', '99'), ('device.bootloader', 'invented-000'),
    ('device.fingerprint', 'invented/firmware'), ('reality.webview.values.version', '999.0.0.1')])
def test_illegal_hardware_firmware_carrier_and_software_edits_are_rejected(path, value):
    p = generate(request())['profile']; realism.set_path(p, path, value)
    assert invalid(p)


@pytest.mark.parametrize('path,value', [('identifiers.android_id', '0123456789abcdef'),
    ('identifiers.serial', 'VALIDSERIAL1'), ('reality.radio.values.wifi_name', 'Cloak-not-derived'),
    ('reality.radio.values.bluetooth_name', 'Cloak-not-derived'), ('reality.inventory_id', '0' * 64)])
def test_syntactically_valid_but_wrong_derivations_are_rejected(path, value):
    p = generate(request())['profile']; realism.set_path(p, path, value)
    assert invalid(p)


@pytest.mark.parametrize('field,value', [('format', 2), ('tuple_id', 'foreign'), ('source_id', 'phones.pixel_8'),
    ('catalog_digest', '0' * 64), ('axes', {}), ('provenance', {})])
def test_tampered_or_incomplete_provenance_fails_closed(field, value):
    p = generate(request())['profile']; p['meta']['instance_realism'][field] = value
    assert realism.validate(p) and invalid(p)


def test_baseline_mismatch_does_not_silently_use_other_template_or_host():
    selection = realism.plan('phones.pixel_7', 'ab' * 32)
    p = forge_profile('Instance', selection['seed'], 'pixel_7', '0.4')
    p['display']['width'] = 1440
    with pytest.raises(ValueError, match='^REALISM_BASELINE_MISMATCH$'):
        realism.apply(p, selection)


def test_uniqueness_retries_cross_slot_and_replays_chosen_input():
    r = request(); a = generate_unique(r, [])
    saved = copy.deepcopy(a['profile']); saved['id'] = 'another'
    saved['identifiers'] = {'serial': a['profile']['identifiers']['android_id']}
    before = copy.deepcopy(saved)
    b = generate_unique(r, [saved])
    assert b['metadata']['attempt_count'] == 2 and saved == before
    assert b['metadata']['chosen_seed'] == b['profile']['seed']
    replay = generate({**r, 'seed': b['metadata']['realism_request_seed'],
                       'realism_tuple': b['metadata']['instance_realism']['tuple_id']})
    assert replay['profile'] == b['profile']


def test_factory_batch_is_atomic_in_memory_and_rejects_existing_collisions():
    f = DeviceFactory()
    a = f.batch('pixel_7', 'batch', 8, instance_realism=True, created_at=STAMP)
    assert len({b['profile']['id'] for b in a}) == 8
    original = copy.deepcopy(a[0]['profile'])
    with pytest.raises(FactoryError):
        f.batch('pixel_7', 'batch', 8, instance_realism=True, existing=[original], created_at=STAMP)
    assert original == a[0]['profile']


@pytest.mark.parametrize('version', ['0.2', '0.3', '0.4'])
def test_existing_profiles_and_default_generation_are_unchanged(version):
    old = forge_profile('Old', 'ab' * 32, 'pixel_7', version); before = copy.deepcopy(old)
    assert not invalid(old) and old == before
    assert generate(dict(name='Old', seed='ab'*32, template='pixel_7', schema_version=version))['profile'] == old
    if version != '0.4':
        with pytest.raises(ValueError, match='^REALISM_REQUIRES_SCHEMA_04$'):
            generate({**request(), 'schema_version': version})


def test_unknown_hardware_and_software_are_never_filled_from_host():
    p = generate(request())['profile']
    baseline = forge_profile('Instance', p['seed'], 'pixel_7', '0.4')
    for section in ('compute', 'panel', 'cameras', 'sensors', 'power', 'drm', 'packages'):
        assert p['reality'][section] == baseline['reality'][section]
    for key in ('manufacturer', 'model', 'hardware', 'first_api_level'):
        assert p['device'][key] == baseline['device'][key]
    before = copy.deepcopy(baseline['reality']['webview']); after = copy.deepcopy(p['reality']['webview'])
    before['values'].pop('user_agent', None); after['values'].pop('user_agent', None)
    assert before == after
    assert p['camera'] == baseline['camera']


@pytest.mark.parametrize('extra,code', [({'instance_realism': 1}, 'REALISM_REQUEST_INVALID'),
    ({'realism_tuple': 'invented'}, 'REALISM_TUPLE_UNSUPPORTED'),
    ({'instance_realism': False, 'realism_tuple': 'anything'}, 'REALISM_REQUEST_INVALID')])
def test_invalid_requests_are_explicit(extra, code):
    with pytest.raises(ValueError, match='^' + code + '$'):
        generate({**request(), **extra})


def test_checked_in_rules_are_reproducible_and_provenance_has_no_identifier_values():
    subprocess.run([sys.executable, str(ROOT / 'tools/generate_instance_realism.py'), '--check'], check=True)
    bundle = generate(request()); p = bundle['profile']
    text = json.dumps(p['meta']['instance_realism'])
    assert p['seed'] not in text
    assert all(v not in text for v in p['identifiers'].values())
    assert not any('keybox' in path or path.startswith('integrity') for path in RULES['allowed_paths'])


def test_firmware_and_modem_are_atomic_existing_catalog_bundles_and_ua_tracks_release():
    p = generate(request(realism_tuple='sku-8-128-new_york-firmware-peer'))['profile']
    factory = DeviceFactory().catalog()
    reference = next(t for t in factory if t['id'] == 'pixel_7')['device']
    fields = ['fingerprint', 'version_release', 'sdk_int', 'security_patch', 'build_id',
              'version_incremental', 'build_date_utc', 'bootloader', 'baseband']
    assert all(p['device'][f] == reference[f] for f in fields)
    assert f"Android {p['device']['version_release']}; {p['device']['model']}" in p['ua']['value']
    assert all(p['meta']['instance_realism']['provenance']['device.' + f]['confidence'] == 'approximated' for f in fields)
    assert not invalid(p)
