"""Diagnostics fixture policies, independent of profile schemas and runtime state.

EXTERNAL_STATE remains an observation. STABLE_ACROSS_SWITCH needs a paired
capture with the same rule and installed artifact; neither is a hook verdict.
"""
import json
import re

POLICIES = {'EXACT', 'STABLE_ACROSS_SWITCH', 'PRESENT', 'TYPED', 'RANGE', 'ENUM', 'EXTERNAL_STATE'}
TYPES = {'boolean', 'integer', 'string', 'mac', 'packages', 'libraries', 'sensors'}


def integer(value):
    return type(value) is int and -(2**63) <= value < 2**63


def same(a, b):
    return json.dumps(a, sort_keys=True, ensure_ascii=False, separators=(',', ':')) == json.dumps(b, sort_keys=True, ensure_ascii=False, separators=(',', ':'))


def scalar(value):
    return value is None or isinstance(value, str) or type(value) is bool or integer(value)


def typed(kind, value):
    if kind == 'boolean':
        return type(value) is bool
    if kind == 'integer':
        return integer(value)
    if kind == 'string':
        return isinstance(value, str)
    if kind == 'mac':
        return isinstance(value, str) and re.fullmatch(r'[0-9a-fA-F]{2}(:[0-9a-fA-F]{2}){5}', value) is not None
    if not isinstance(value, list):
        return False
    if kind in ('packages', 'libraries'):
        pattern = r'[A-Za-z_][A-Za-z0-9_]*(\.[A-Za-z_][A-Za-z0-9_]*)*' if kind == 'packages' else r'[A-Za-z0-9_.+-]+\.so'
        return all(isinstance(v, str) and re.fullmatch(pattern, v) is not None for v in value)
    if kind == 'sensors':
        return all(isinstance(v, dict) and integer(v.get('type')) and 0 < v['type'] <= 2147483647
                   and isinstance(v.get('name'), str) and isinstance(v.get('vendor'), str) for v in value)
    return False


def validate(rule):
    try:
        json.dumps(rule, allow_nan=False)
    except (ValueError, TypeError):
        raise ValueError('MALFORMED_EXPECTATION') from None
    if not isinstance(rule, dict) or not isinstance(rule.get('policy'), str) or rule['policy'] not in POLICIES:
        raise ValueError('MALFORMED_EXPECTATION')
    policy = rule['policy']
    allowed = {'policy'}
    if policy == 'EXACT':
        allowed.add('value')
        if 'value' not in rule:
            raise ValueError('MALFORMED_EXPECTATION')
    if policy in {'TYPED', 'STABLE_ACROSS_SWITCH', 'EXTERNAL_STATE'}:
        allowed.add('type')
        if not isinstance(rule.get('type'), str) or rule['type'] not in TYPES:
            raise ValueError('MALFORMED_EXPECTATION')
    if policy == 'STABLE_ACROSS_SWITCH':
        allowed.add('expected_change')
        if 'expected_change' in rule and type(rule['expected_change']) is not bool:
            raise ValueError('MALFORMED_EXPECTATION')
    if policy in {'RANGE', 'EXTERNAL_STATE'}:
        allowed.update(('min', 'max'))
        if policy == 'RANGE' and not {'min', 'max'} <= rule.keys():
            raise ValueError('MALFORMED_EXPECTATION')
        if any(k in rule and not integer(rule[k]) for k in ('min', 'max')):
            raise ValueError('MALFORMED_EXPECTATION')
        if 'min' in rule and 'max' in rule and rule['min'] > rule['max']:
            raise ValueError('MALFORMED_EXPECTATION')
        if policy == 'EXTERNAL_STATE' and {'min', 'max'} & rule.keys() and rule['type'] != 'integer':
            raise ValueError('MALFORMED_EXPECTATION')
    if policy in {'ENUM', 'EXTERNAL_STATE'}:
        allowed.add('values')
        if policy == 'ENUM' and 'values' not in rule:
            raise ValueError('MALFORMED_EXPECTATION')
        if 'values' in rule and (not isinstance(rule['values'], list) or not rule['values'] or not all(scalar(v) for v in rule['values'])):
            raise ValueError('MALFORMED_EXPECTATION')
    if policy == 'EXTERNAL_STATE':
        allowed.add('unavailable')
        if not isinstance(rule.get('unavailable'), list):
            raise ValueError('MALFORMED_EXPECTATION')
        for v in rule['unavailable']:
            if not isinstance(v, dict) or set(v) != {'value', 'reason'} or not scalar(v['value']) or not isinstance(v['reason'], str) or not re.fullmatch('[A-Z][A-Z0-9_]*', v['reason']):
                raise ValueError('MALFORMED_EXPECTATION')
    if rule.keys() - allowed:
        raise ValueError('MALFORMED_EXPECTATION')


def evaluate(rule, actual):
    try:
        validate(rule)
    except (ValueError, TypeError):
        return dict(status='FAIL', reason='MALFORMED_EXPECTATION')
    policy = rule['policy']
    def result(status, reason):
        return dict(status=status, reason=reason)
    if policy == 'EXACT':
        matches = same(rule['value'], actual)
        return result('PASS' if matches else 'FAIL', 'EXACT_MATCH' if matches else 'EXACT_MISMATCH')
    if policy == 'EXTERNAL_STATE':
        for item in rule['unavailable']:
            if same(item['value'], actual):
                return result('OBSERVED', 'EXTERNAL_STATE_UNAVAILABLE:' + item['reason'])
        if actual is None or actual == '':
            return result('OBSERVED', 'EXTERNAL_STATE_UNAVAILABLE:NO_VALUE')
    elif actual is None or actual == '':
        return result('MISSING', 'EXPECTATION_VALUE_UNAVAILABLE')
    if policy == 'PRESENT':
        return result('PASS', 'VALUE_PRESENT')
    if 'type' in rule and not typed(rule['type'], actual):
        return result('FAIL', 'EXPECTATION_TYPE_MISMATCH')
    if policy == 'RANGE' or 'min' in rule or 'max' in rule:
        if not integer(actual):
            return result('FAIL', 'EXPECTATION_TYPE_MISMATCH')
        if ('min' in rule and actual < rule['min']) or ('max' in rule and actual > rule['max']):
            return result('FAIL', 'EXPECTATION_OUT_OF_RANGE')
    if 'values' in rule and not any(same(v, actual) for v in rule['values']):
        return result('FAIL', 'EXPECTATION_NOT_IN_ENUM')
    if policy == 'EXTERNAL_STATE':
        return result('OBSERVED', 'EXTERNAL_STATE_AVAILABLE')
    if policy == 'STABLE_ACROSS_SWITCH':
        return result('OBSERVED', 'STABILITY_BASELINE_REQUIRED')
    return result('PASS', {'TYPED': 'TYPE_VALID', 'ENUM': 'ENUM_VALID', 'RANGE': 'RANGE_VALID'}[policy])


def fixture(value, label):
    if (not isinstance(value, dict) or set(value) != {'schema_version', 'fixture', 'rows'}
            or type(value['schema_version']) is not int or value['schema_version'] != 1
            or value['fixture'] != label or not isinstance(value['rows'], dict)):
        raise ValueError('INVALID_EXPECTATION_FIXTURE')
    for rule in value['rows'].values():
        validate(rule)
    return value['rows']


def validate_row(row):
    """Report metadata is deliberately bounded; no rule values are copied here."""
    if 'expectation_policy' not in row:
        return
    if (not isinstance(row['expectation_policy'], str) or row['expectation_policy'] not in POLICIES or type(row.get('policy_version')) is not int
            or row['policy_version'] != 1 or row.get('has_expectation') is not True
            or not isinstance(row.get('expectation_rule_sha256'), str)
            or not re.fullmatch('[a-f0-9]{64}', row['expectation_rule_sha256'])
            or ('actual_available' in row and type(row['actual_available']) is not bool)
            or ('expected_change' in row and type(row['expected_change']) is not bool)):
        raise ValueError('INVALID_EXPECTATION_METADATA')
    for key in ('actual_sha256', 'expected_sha256'):
        if row.get(key) is not None and (not isinstance(row[key], str) or not re.fullmatch('[a-f0-9]{64}', row[key])):
            raise ValueError('INVALID_EXPECTATION_METADATA')
    policy = row['expectation_policy']
    if policy in {'EXTERNAL_STATE', 'STABLE_ACROSS_SWITCH'} and row['status'] == 'PASS':
        raise ValueError('UNSUPPORTED_POLICY_PROMOTION')
    if policy == 'EXTERNAL_STATE' and row['status'] == 'OBSERVED':
        reason = row.get('reason', '')
        if reason != 'EXTERNAL_STATE_AVAILABLE' and not re.fullmatch('EXTERNAL_STATE_UNAVAILABLE:[A-Z][A-Z0-9_]*', reason):
            raise ValueError('INVALID_EXPECTATION_METADATA')
    if policy == 'STABLE_ACROSS_SWITCH' and row['status'] == 'OBSERVED' and row.get('reason') != 'STABILITY_BASELINE_REQUIRED':
        raise ValueError('INVALID_EXPECTATION_METADATA')


def compare_stable(before, after, left, right, token):
    result = dict(id=left['id'], changed=None, expected_change=right.get('expected_change', False),
                  reason='STABILITY_BASELINE_REQUIRED', severity='WARNING', status='OBSERVED')
    if (left.get('expectation_policy') != right.get('expectation_policy')
            or left.get('expectation_rule_sha256') != right.get('expectation_rule_sha256')
            or not isinstance(before.get('artifact'), str) or not re.fullmatch('[a-f0-9]{64}', before['artifact'])
            or before.get('artifact') != after.get('artifact')
            or not isinstance(before.get('request_id'), str) or not before['request_id']
            or not isinstance(after.get('request_id'), str) or not after['request_id']
            or before['request_id'] == after['request_id']
            or not left.get('actual_available') or not right.get('actual_available')):
        return result
    # Hashed evidence needs a real value digest, not a digest synthesized from redacted null.
    for row in (left, right):
        if row.get('actual') is None and not re.fullmatch('[a-f0-9]{64}', row.get('actual_sha256', '')):
            return result
    changed = token(left, 'actual') != token(right, 'actual')
    passed = changed == result['expected_change']
    result.update(changed=changed, severity='INFO' if passed else 'FAIL', status='PASS' if passed else 'FAIL',
                  evidence_pair=sorted([before['request_id'], after['request_id']]),
                  reason=('EXPECTED_STABILITY_CHANGE' if changed else 'STABLE_MATCH') if passed
                  else ('UNEXPECTED_STABILITY_CHANGE' if changed else 'EXPECTED_STABILITY_CHANGE_MISSING'))
    return result


def pending_stability(reports, differences):
    captures = {}
    invalid = set()
    for report in reports:
        for row in report['rows']:
            if row.get('expectation_policy') != 'STABLE_ACROSS_SWITCH':
                continue
            ids = captures.setdefault(row['id'], set())
            request = report.get('request_id')
            if not isinstance(request, str) or not request or request in ids:
                invalid.add(row['id'])
            else:
                ids.add(request)
    pending = set(invalid)
    for key, ids in captures.items():
        edges = [d.get('evidence_pair', []) for d in differences if d['id'] == key and d.get('status') == 'PASS'
                 and d['reason'] in {'STABLE_MATCH', 'EXPECTED_STABILITY_CHANGE'}]
        reached = {next(iter(ids))} if ids else set()
        for _ in range(len(ids)):
            for pair in edges:
                if len(pair) == 2 and set(pair) <= ids and reached & set(pair):
                    reached.update(pair)
        if len(ids) < 2 or reached != ids:
            pending.add(key)
    return pending
