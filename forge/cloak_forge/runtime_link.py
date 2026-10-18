"""Pure, value-free parity findings. Base fields are authoritative."""
import json
from pathlib import Path
TABLE = json.loads((Path(__file__).resolve().parents[2] / 'catalog/reality-runtime-fields.json').read_text(encoding='utf8'))
LINKS = {r['field_path']: r['linked_base_path'] for r in TABLE['fields'] if r.get('linked_base_path')}


def at(profile, path):
    value = profile
    for part in path.split('.'):
        value = value.get(part) if isinstance(value, dict) else None
    return value


def evaluate(profile):
    rows = []
    for path, base in LINKS.items():
        section = at(profile, '.'.join(path.split('.')[:2]))
        known = isinstance(section, dict) and section.get('availability') == 'known'
        reality, authoritative = at(profile, path), at(profile, base)
        present = known and reality is not None
        # JSON numeric values compare numerically; booleans never equal numbers.
        equal = type(reality) is type(authoritative) and reality == authoritative
        if isinstance(reality, (int, float)) and not isinstance(reality, bool) and isinstance(authoritative, (int, float)) and not isinstance(authoritative, bool):
            equal = reality == authoritative
        state = 'NOT_PRESENT' if not present else 'BASE_MISSING' if authoritative is None else 'MATCHED' if equal else 'MISMATCHED'
        rows.append(dict(field_path=path, linked_base_path=base, parity_requirement='EXACT', reality_present=present,
                         parity_state=state, parity_passed=state == 'MATCHED' if present and authoritative is not None else None))
    return rows
