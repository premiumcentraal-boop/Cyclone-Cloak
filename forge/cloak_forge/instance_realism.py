"""Approved complete instance tuples; never probes the host or invents capabilities."""
from __future__ import annotations

import copy
import hashlib
import json
import re
from pathlib import Path

from .reality import at, canonical

ROOT = Path(__file__).resolve().parents[2]
CATALOG_PATH = ROOT / 'catalog/instance-realism-v1.json'


def digest(value):
    return hashlib.sha256(json.dumps(canonical(value), sort_keys=True, separators=(',', ':'),
                                     ensure_ascii=False, allow_nan=False).encode('utf8')).hexdigest()


def anchor(profile):
    facts = {k: copy.deepcopy(profile[k]) for k in
             ('device', 'display', 'camera', 'telephony', 'locale', 'network', 'reality') if k in profile}
    reality = facts.get('reality', {})
    reality.pop('inventory_id', None)
    # Wholly derived from device and webview facts, so it must not invalidate the anchor.
    reality.pop('packages', None)
    for section, keys in (('radio', ('wifi_name', 'bluetooth_name')), ('webview', ('user_agent',))):
        for key in keys:
            reality.get(section, {}).get('values', {}).pop(key, None)
    return digest(facts)


def catalog():
    return json.loads(CATALOG_PATH.read_text(encoding='utf8'))


def set_path(profile, path, value):
    keys = path.split('.')
    parent = profile
    for key in keys[:-1]:
        if key not in parent:
            if value is None:
                return
            parent[key] = {}
        if not isinstance(parent[key], dict):
            raise ValueError('REALISM_PROFILE_INVALID')
        parent = parent[key]
    if value is None:
        parent.pop(keys[-1], None)
    else:
        parent[keys[-1]] = copy.deepcopy(value)


def plan(source_id, seed, tuple_id=None):
    rules = catalog()
    if not isinstance(seed, str) or not re.fullmatch('[0-9a-f]{64}', seed):
        raise ValueError('REALISM_SEED_INVALID')
    source = rules['sources'].get(source_id)
    if source is None:
        raise ValueError('REALISM_TEMPLATE_UNSUPPORTED')
    options = sorted(source['tuples'], key=lambda t: t['id'])
    if tuple_id is None:
        index = int(hashlib.sha256(f'cloak-realism-select-v1:{source_id}:{seed}'.encode()).hexdigest()[:8], 16) % len(options)
        selected = options[index]
    else:
        selected = next((t for t in options if t['id'] == tuple_id), None)
        if selected is None:
            raise ValueError('REALISM_TUPLE_UNSUPPORTED')
    chosen_seed = hashlib.sha256(f"cloak-realism-seed-v1:{source_id}:{seed}:{selected['id']}".encode()).hexdigest()
    return {'source_id': source_id, 'tuple_id': selected['id'], 'seed': chosen_seed}


def _selected(source_id, tuple_id):
    rules = catalog()
    source = rules['sources'][source_id]
    selected = next(t for t in source['tuples'] if t['id'] == tuple_id)
    return rules, source, selected


def record(profile, source_id, tuple_id):
    rules, source, selected = _selected(source_id, tuple_id)
    derived = ['id', 'seed', 'ua.value', 'reality.webview.values.user_agent', 'reality.inventory_id',
               'reality.radio.values.wifi_name', 'reality.radio.values.bluetooth_name']
    derived += ['identifiers.' + key for key in profile.get('identifiers', {})]
    provenance = {}
    for path in derived:
        if at(profile, path) is not None:
            provenance[path] = {'source': 'deterministic-derivation-v1', 'confidence': 'derived', 'availability': 'known'}
    for path, fact in selected['changes'].items():
        provenance[path] = {k: fact[k] for k in ('source', 'confidence', 'availability')}
    return {'format': 1, 'catalog_digest': digest(rules), 'source_id': source_id,
            'tuple_id': tuple_id, 'provenance': dict(sorted(provenance.items())),
            'axes': source['axes']}


def apply(profile, selection):
    """Generation-only transform of a fresh baseline. Always returns a caller-owned copy."""
    rules, source, selected = _selected(selection['source_id'], selection['tuple_id'])
    if profile.get('schema_version') != '0.4' or anchor(profile) != source['anchor']:
        raise ValueError('REALISM_BASELINE_MISMATCH')
    if profile.get('seed') != selection['seed']:
        raise ValueError('REALISM_SEED_MISMATCH')
    result = copy.deepcopy(profile)
    for path, fact in selected['changes'].items():
        if path not in rules['allowed_paths'] or path not in source['originals']:
            raise ValueError('REALISM_PATH_UNSUPPORTED')
        set_path(result, path, fact['value'])
    # Configured browser string follows the selected release/model and any declared
    # provider version. An unknown installed version is never filled from the host.
    agent = at(result, 'ua.value')
    if isinstance(agent, str):
        device = result['device']
        agent = re.sub(r'Linux; Android [^;]+; [^)]*',
                       f"Linux; Android {device['version_release']}; {device['model']}", agent)
        from .reality import user_agent
        agent = user_agent(result.get('reality', {}), agent)
        result['ua']['value'] = agent
        if at(result, 'reality.webview.availability') == 'known':
            result['reality']['webview']['values']['user_agent'] = agent
    result['meta']['instance_realism'] = record(result, selection['source_id'], selection['tuple_id'])
    from .validate import validate_profile
    if any(f.severity == 'error' for f in validate_profile(result)):
        raise ValueError('REALISM_COHERENCE_ERROR')
    return result


def validate(profile):
    """Optional metadata is checked without migration, mutation or recursive validation."""
    meta = profile.get('meta')
    if not isinstance(meta, dict) or 'instance_realism' not in meta:
        return []
    from .validate import Finding
    try:
        supplied = meta['instance_realism']
        rules, source, selected = _selected(supplied['source_id'], supplied['tuple_id'])
        if profile.get('schema_version') != '0.4' or meta.get('template') != source['template_id']:
            raise ValueError()
        if supplied != record(profile, supplied['source_id'], supplied['tuple_id']):
            raise ValueError()
        restored = copy.deepcopy(profile)
        for path, fact in selected['changes'].items():
            if path not in rules['allowed_paths'] or at(profile, path) != fact['value']:
                raise ValueError()
            set_path(restored, path, source['originals'][path])
        if anchor(restored) != source['anchor']:
            raise ValueError()
        from .derive import derive_identifier_bundle, luhn_check_digit
        expected_ids = derive_identifier_bundle(profile['seed'])
        if supplied['source_id'].startswith('factory.'):
            prefix = source['iccid_prefix']
            digits = prefix + str(int(digest([profile['seed'], 'iccid']), 16) % 10**(18-len(prefix))).zfill(18-len(prefix))
            expected_ids['sim_serial'] = digits + luhn_check_digit(digits)
            if profile['telephony']['sim_slot_count'] == 1:
                expected_ids.pop('imei_secondary')
        if profile['identifiers'] != expected_ids:
            raise ValueError()
        from .reality import token, user_agent
        if at(profile, 'reality.inventory_id') != token(profile['seed'], 'inventory'):
            raise ValueError()
        if at(profile, 'reality.radio.availability') == 'known' and any(
            at(profile, 'reality.radio.values.' + key) != 'Cloak-' + token(profile['seed'], field)[:12]
            for key, field in [('wifi_name', 'wifi'), ('bluetooth_name', 'bluetooth')]):
            raise ValueError()
        from .forge import build_ua
        expected_ua = user_agent(profile['reality'], build_ua(profile['device'], profile['display']['screen_size_class'], profile['seed']))
        if at(profile, 'ua.value') != expected_ua or (at(profile, 'reality.webview.availability') == 'known' and
                at(profile, 'reality.webview.values.user_agent') != expected_ua):
            raise ValueError()
        ids = [re.sub('[:-]', '', v.lower()) for v in profile['identifiers'].values()]
        if len(ids) != len(set(ids)):
            raise ValueError()
    except (KeyError, TypeError, ValueError, StopIteration, AttributeError, OverflowError):
        return [Finding('error', 'INSTANCE_REALISM_INVALID',
                        'meta.instance_realism: approved tuple, provenance or baseline binding failed.', 'meta.instance_realism')]
    return []


def conflicts(profile, library):
    """Cross-slot identifier collisions plus the existing Reality uniqueness rules."""
    from .engine import check_uniqueness
    ours = {re.sub('[:-]', '', v.lower()) for v in profile['identifiers'].values()}
    return any(profile['id'] == p.get('id') or ours.intersection(
        re.sub('[:-]', '', v.lower()) for v in p.get('identifiers', {}).values() if isinstance(v, str))
        for p in library) or bool(check_uniqueness(profile, [{**p, 'id': f'saved-{i}'} for i, p in enumerate(library)]))
