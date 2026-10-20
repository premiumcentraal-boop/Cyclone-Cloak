"""Read-only description separation, not statistical independence or a trust verdict."""
from __future__ import annotations

import json
import re
from itertools import combinations
from pathlib import Path

from .reality import SECTIONS, at, canonical
from .validate import validate_profile

POLICY = json.loads((Path(__file__).resolve().parents[2] / 'catalog/cohort-separation-v1.json').read_text())


def analyze(profiles: list[dict]) -> dict:
    """Fail closed with fixed messages. Never return values, digests, or validation snippets."""
    if not isinstance(profiles, list) or len(profiles) > 200:
        raise ValueError('COHORT_INPUT_INVALID')
    try:
        valid = all(isinstance(p, dict) and isinstance(p.get('id'), str) and
                    re.fullmatch(r'[A-Za-z0-9._:-]{1,96}', p['id']) and
                    isinstance(p.get('name'), str) and 1 <= len(p['name']) <= 160 and
                    not any(ord(c) < 32 or ord(c) == 127 for c in p['name']) and
                    not any(f.severity == 'error' for f in validate_profile(p)) for p in profiles)
        if not valid or len({p['id'] for p in profiles}) != len(profiles):
            raise ValueError('COHORT_INPUT_INVALID')
    except Exception:
        raise ValueError('COHORT_INPUT_INVALID') from None
    profiles = sorted(profiles, key=lambda p: p['id'])
    categories = POLICY['categories']
    paths = sorted({path for fields in categories.values() for path in fields})

    def value(p, path):
        if path.startswith('reality.') and '.values' in path:
            if at(p, '.'.join(path.split('.')[:2]) + '.availability') != 'known':
                return None
        v = at(p, path)
        if v is None or v == '' or v == {} or v == [] or v in ('unknown', 'unavailable'):
            return None
        if path in categories['stable_identifiers'] and isinstance(v, str):
            v = v.lower()
        return json.dumps(canonical(v), sort_keys=True, separators=(',', ':'), ensure_ascii=False)

    values = {p['id']: {path: value(p, path) for path in paths} for p in profiles}
    pairs, duplicates = [], []
    for a, b in combinations(profiles, 2):
        av, bv = values[a['id']], values[b['id']]
        scores = {}
        for category, fields in categories.items():
            known = [path for path in fields if av[path] is not None and bv[path] is not None]
            different = sum(av[path] != bv[path] for path in known)
            scores[category] = {'distance': different * 100 // len(fields),
                                'confidence': len(known) * 100 // len(fields)}
        pairs.append({'profiles': [a['id'], b['id']], 'categories': scores,
                      'distance': sum(v['distance'] for v in scores.values()) // len(scores),
                      'confidence': sum(v['confidence'] for v in scores.values()) // len(scores)})
        for path in categories['stable_identifiers']:
            x, y = av[path], bv[path]
            if x is None or y is None:
                continue
            # Format-aware conservative heuristic: equal-length alphanumerics, one substitution.
            nx, ny = re.sub('[^a-z0-9]', '', x), re.sub('[^a-z0-9]', '', y)
            if x == y or len(nx) == len(ny) >= 6 and sum(c != d for c, d in zip(nx, ny)) == 1:
                duplicates.append({'profiles': [a['id'], b['id']], 'field': path,
                                   'kind': 'duplicate' if x == y else 'near_duplicate'})
    shared = []
    for fields in [[p] for p in paths] + POLICY['combinations']:
        groups = {}
        for p in profiles:
            signature = tuple(values[p['id']][path] for path in fields)
            if all(v is not None for v in signature):
                groups.setdefault(signature, []).append(p['id'])
        for members in sorted(groups.values()):
            if len(members) > 1:
                shared.append({'fields': fields, 'profiles': members})
    weakest = []
    for path in paths:
        known = sum(values[p['id']][path] is not None for p in profiles)
        different = sum(values[a['id']][path] is not None and values[b['id']][path] is not None and
                        values[a['id']][path] != values[b['id']][path] for a, b in combinations(profiles, 2))
        weakest.append({'field': path, 'distance': different * 100 // max(1, len(pairs)),
                        'known_profiles': known})
    weakest.sort(key=lambda row: (row['distance'], row['field']))
    missing = []
    for p in profiles:
        for section in sorted(SECTIONS):
            availability = at(p, f'reality.{section}.availability')
            if availability != 'known' or not at(p, f'reality.{section}.values'):
                missing.append({'profile': p['id'], 'section': section,
                                'state': availability if availability in ('unknown', 'unavailable') else 'missing'})
    recommendations = [{'field': r['field'], 'action': 'regenerate_derived_identifier' if
                        r['field'] in categories['stable_identifiers'] else 'review_template_or_complete_known_facts'}
                       for r in weakest if profiles and r['distance'] < 50]
    score = sum(p['distance'] for p in pairs) // len(pairs) if pairs else None
    confidence = sum(p['confidence'] for p in pairs) // len(pairs) if pairs else 0
    warnings = []
    if not pairs:
        warnings.append('INSUFFICIENT_COHORT')
    if confidence < 80:
        warnings.append('INCOMPLETE_FACTS')
    if duplicates:
        warnings.append('IDENTIFIER_REUSE_OR_SIMILARITY')
    if score is not None and (score < 60 or any(p['distance'] < 30 for p in pairs)):
        warnings.append('HOMOGENEOUS_COHORT_NOT_HIGH_TRUST_EVIDENCE')
    return {'format': 1, 'policy': POLICY['version'],
            'profiles': [{'id': p['id'], 'label': p['name']} for p in profiles],
            'pairs': pairs, 'shared_groups': shared, 'identifier_findings': duplicates,
            'weakest_fields': weakest, 'missing_reality': missing, 'recommendations': recommendations,
            'independence_score': score, 'confidence': confidence, 'warnings': warnings}
