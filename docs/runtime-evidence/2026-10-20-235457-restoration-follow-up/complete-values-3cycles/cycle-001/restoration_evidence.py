"""Bounded, non-secret comparison evidence for the diagnostics lifecycle only."""
import copy
import difflib
import hashlib
import json
import re


def canonical_digest(value):
    return hashlib.sha256(json.dumps(value, sort_keys=True, separators=(',', ':'),
                                    ensure_ascii=True).encode()).hexdigest()


def comparable_values(raw, redacted):
    """Keep complete package-name/control values, never arbitrary identity readings."""
    source = {r['id']: r for r in raw['rows']}
    for row in redacted['rows']:
        value = source[row['id']].get('actual')
        valid = False
        if row['id'] == 'packages.visible':
            valid = (isinstance(value, list) and len(value) <= 4096
                     and all(isinstance(v, str) and (v == 'android' or re.fullmatch(r'[A-Za-z0-9_]+(?:\.[A-Za-z0-9_]+)+', v))
                             for v in value) and len(set(value)) == len(value))
            if not valid and source[row['id']].get('status') not in ('BLOCKED', 'MISSING'):
                raise ValueError('COMPARABLE_PACKAGE_LIST_INVALID')
        elif row['id'] == 'settings.unrelatedControl':
            valid = value is None or value in ('0', '1')
        if valid:
            if canonical_digest(value) != row.get('actual_sha256'):
                raise ValueError('COMPARABLE_HASH_MISMATCH')
            row['comparable_value'] = copy.deepcopy(value)
    return redacted


def value_difference(before, after):
    result = dict(before_sha256=before.get('actual_sha256') if before else None,
                  after_sha256=after.get('actual_sha256') if after else None)
    if before and after and 'comparable_value' in before and 'comparable_value' in after:
        left, right = before['comparable_value'], after['comparable_value']
        result.update(before_value=left, after_value=right, values_available=True)
        if before['id'] == 'packages.visible':
            result.update(added=sorted(set(right) - set(left)), removed=sorted(set(left) - set(right)),
                          line_diff=list(difflib.unified_diff(sorted(left), sorted(right),
                                         fromfile='before', tofile='after', lineterm='')))
    else:
        result.update(values_available=False, limitation='PRIOR_VALUE_NOT_RETAINED_OR_PRIVATE')
    return result


def confirmed_host_control(before, after):
    """A changed control is external only with independent same-user host evidence."""
    for report in (before, after):
        row = next((r for r in report['rows'] if r['id'] == 'settings.unrelatedControl'), None)
        context = report.get('host_control')
        if (not row or row.get('comparable_value') not in ('0', '1') or not isinstance(context, dict)
                or context.get('android_user') != report['android_user']
                or context.get('request_id') != report['request_id']
                or context.get('source') != 'root_cli_settings_secure'
                or context.get('before') not in ('0', '1') or context.get('after') not in ('0', '1')
                or row['comparable_value'] not in (context['before'], context['after'])):
            return False
    return True


def semantic_audit(value):
    """Only serialization observations are non-gating; all other fields still gate."""
    if value is None:
        return None
    return {k: v for k, v in value.items() if k not in ('index_bytes_sha256', 'index_bytes_size')}


def audit_comparison(before, after):
    return dict(semantic_unchanged=before is not None and semantic_audit(before) == semantic_audit(after),
                raw_index_before={k: before.get(k) for k in ('index_bytes_sha256', 'index_bytes_size')} if before else None,
                raw_index_after={k: after.get(k) for k in ('index_bytes_sha256', 'index_bytes_size')} if after else None,
                raw_index_changed=bool(before and after and any(before.get(k) != after.get(k)
                    for k in ('index_bytes_sha256', 'index_bytes_size'))))


def package_permissions(text):
    """Only installed package User blocks; later visibility User blocks must not overwrite grants."""
    grants, current, runtime = {}, None, False
    for line in text.splitlines():
        match = re.match(r'^    User (\d+):.*\binstalled=', line)
        if match:
            current, runtime = match[1], False
            if current in grants:
                raise ValueError('DUPLICATE_PERMISSION_USER')
            grants[current] = {}
        elif re.match(r'^    User \d+:', line) or (line and not line[0].isspace()):
            current, runtime = None, False
        if current is not None and line.strip() == 'runtime permissions:':
            runtime = True
        if runtime:
            match = re.fullmatch(r'\s*(android\.permission\.[A-Z_]+): granted=(true|false), flags=\[(.*)\]', line)
            if match:
                name = match[1]
                if name in grants[current]:
                    raise ValueError('DUPLICATE_PERMISSION_RECORD')
                grants[current][name] = dict(granted=match[2] == 'true',
                    flags=sorted(x.strip() for x in match[3].split('|') if x.strip()))
            elif re.match(r'\s*android\.permission\.', line):
                raise ValueError('MALFORMED_PERMISSION_RECORD')
    if not grants:
        raise ValueError('PERMISSION_USERS_MISSING')
    install = dict(re.findall(r'(android\.permission\.[A-Z_]+): granted=(true|false)',
                             text.split('    User ')[0]))
    return grants, install


def appop_modes(text, uid_text=None):
    """Retain stable UID/package mode distinction, reject duplicate or malformed mode lines."""
    if uid_text is not None:
        # Android prints a multi-line UID prefix, then package modes without a heading.
        # Query the numeric UID separately instead of guessing where that prefix ends.
        uid_lines = [line for line in uid_text.splitlines() if line.strip()]
        lines = [line for line in text.splitlines() if line.strip()]
        if uid_lines == ['No operations.']:
            uid_lines = []
        if lines[:len(uid_lines)] != uid_lines:
            raise ValueError('APPOP_UID_PREFIX_CHANGED')
        uid = appop_modes('\n'.join(uid_lines).replace('Uid mode: ', ''))
        package = appop_modes('\n'.join(lines[len(uid_lines):]))
        return sorted([('Uid mode: ' + name, mode) for name, mode in uid] + package)
    result = []
    for line in text.splitlines():
        match = re.match(r'^\s*((?:Uid mode: )?[A-Z_]+):\s*([a-z]+)(?:;|$)', line.strip())
        if match:
            name, mode = match.groups()
            if mode not in {'allow', 'ignore', 'deny', 'default', 'foreground', 'errored'}:
                raise ValueError('INVALID_APPOP_MODE')
            if any(name == old[0] for old in result):
                raise ValueError('DUPLICATE_APPOP_RECORD')
            result.append((name, mode))
        elif re.match(r'^\s*(?:Uid mode: )?[A-Z_]+:', line):
            raise ValueError('MALFORMED_APPOP_RECORD')
    return sorted(result)
