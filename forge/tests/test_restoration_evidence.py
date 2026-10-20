"""No ADB: conservative restoration evidence and stable snapshot parsing."""
import copy
import sys
from pathlib import Path
import pytest

sys.path.insert(0, str(Path(__file__).resolve().parents[2] / 'tools'))
from restoration_evidence import (canonical_digest, comparable_values, value_difference,
                                  semantic_audit, audit_comparison, package_permissions, appop_modes)
from run_probe_reversibility import restoration, effective_permission_state, PACKAGE


def test_full_sorted_package_diff_and_digest_binding():
    def row(items):
        raw = dict(rows=[dict(id='packages.visible', actual=items)])
        safe = dict(rows=[dict(id='packages.visible', actual=None, actual_sha256=canonical_digest(items))])
        return comparable_values(raw, safe)['rows'][0]
    delta = value_difference(row(['com.z', 'com.a']), row(['com.b', 'com.a']))
    assert delta['before_value'] == ['com.z', 'com.a']
    assert delta['after_value'] == ['com.b', 'com.a']
    assert delta['added'] == ['com.b'] and delta['removed'] == ['com.z']
    assert '-com.z' in delta['line_diff'] and '+com.b' in delta['line_diff']


def test_android_framework_package_is_a_valid_full_inventory():
    packages = ['android', 'com.android.chrome']
    raw = dict(rows=[dict(id='packages.visible', actual=packages, status='OBSERVED')])
    safe = dict(rows=[dict(id='packages.visible', actual_sha256=canonical_digest(packages))])
    assert comparable_values(raw, safe)['rows'][0]['comparable_value'] == packages


@pytest.mark.parametrize('bad', [None, ['invalid label'], ['com.a', 'com.a']])
def test_successful_package_read_cannot_silently_lose_complete_values(bad):
    with pytest.raises(ValueError, match='COMPARABLE_PACKAGE_LIST_INVALID'):
        comparable_values(dict(rows=[dict(id='packages.visible', actual=bad, status='OBSERVED')]),
                          dict(rows=[dict(id='packages.visible')]))


@pytest.mark.parametrize('value', ['0', '1', None])
def test_control_value_is_exact_not_an_inferred_pass(value):
    raw = dict(rows=[dict(id='settings.unrelatedControl', actual=value)])
    safe = dict(rows=[dict(id='settings.unrelatedControl', actual_sha256=canonical_digest(value))])
    assert comparable_values(raw, safe)['rows'][0]['comparable_value'] == value


def test_private_readings_and_arbitrary_control_strings_stay_redacted():
    raw = dict(rows=[dict(id='android.id', actual='private'),
                     dict(id='settings.unrelatedControl', actual='private')])
    safe = dict(rows=[dict(id=x['id'], actual=None) for x in raw['rows']])
    assert all('comparable_value' not in r for r in comparable_values(raw, safe)['rows'])


def test_tampered_comparable_hash_is_rejected():
    with pytest.raises(ValueError, match='COMPARABLE_HASH_MISMATCH'):
        comparable_values(dict(rows=[dict(id='packages.visible', actual=['com.a'])]),
                          dict(rows=[dict(id='packages.visible', actual_sha256='f'*64)]))


def test_old_hash_only_reports_are_not_reconstructed():
    delta = value_difference(dict(id='packages.visible', actual_sha256='a'*64),
                             dict(id='packages.visible', actual_sha256='b'*64))
    assert not delta['values_available'] and 'before_value' not in delta


def test_only_raw_index_serialization_is_non_gating():
    before = dict(index_bytes_sha256='a'*64, index_bytes_size=30, index_sha256='c'*64,
                  published_bodies={'hash': 'unchanged'})
    after = dict(before, index_bytes_sha256='b'*64, index_bytes_size=33)
    assert semantic_audit(before) == semantic_audit(after)
    assert audit_comparison(before, after)['raw_index_changed']
    assert audit_comparison(before, after)['semantic_unchanged']
    after['published_bodies'] = {'hash': 'changed'}
    assert not audit_comparison(before, after)['semantic_unchanged']
    assert not audit_comparison(None, None)['semantic_unchanged']


PERMISSIONS = '''Packages:
    User 0: installed=true
      runtime permissions:
        android.permission.CAMERA: granted=true, flags=[ USER_SET|USER_FIXED]
    User 17: installed=true
      runtime permissions:
        android.permission.CAMERA: granted=false, flags=[ USER_SENSITIVE_WHEN_DENIED]
Queries:
    User 0:
    User 17:
'''


def test_visibility_sections_cannot_erase_runtime_grants():
    grants, _ = package_permissions(PERMISSIONS)
    assert grants['0']['android.permission.CAMERA'] == dict(granted=True, flags=['USER_FIXED', 'USER_SET'])
    assert grants['17']['android.permission.CAMERA']['granted'] is False


@pytest.mark.parametrize('bad', [PERMISSIONS.replace('granted=true', 'granted=maybe'),
                                PERMISSIONS + '    User 0: installed=true\n', ''])
def test_malformed_or_duplicate_permissions_fail_closed(bad):
    with pytest.raises(ValueError):
        package_permissions(bad)


def test_appop_timestamps_and_counters_are_excluded_uid_modes_preserved():
    first = 'Uid mode: CAMERA: foreground\n  CAMERA: allow; time=+1s ago; duration=3s\n'
    second = 'Uid mode: CAMERA: foreground\n  CAMERA: allow; time=+4h ago; duration=10s\n'
    assert appop_modes(first) == appop_modes(second) == [('CAMERA', 'allow'), ('Uid mode: CAMERA', 'foreground')]


@pytest.mark.parametrize('bad', ['  CAMERA: magic;', 'CAMERA: allow;\nCAMERA: deny;', 'CAMERA: 123;'])
def test_bad_or_duplicate_appops_fail_closed(bad):
    with pytest.raises(ValueError):
        appop_modes(bad)


def test_uid_mode_cannot_turn_into_package_mode():
    def state(entries):
        return {PACKAGE: dict(grants={}, install_grants={}, appop_modes={'0': entries}),
                'dev.cyclone.cloak': dict(grants={}, install_grants={}, appop_modes={'0': []})}
    assert not effective_permission_state(state([('Uid mode: CAMERA', 'allow')]),
                                          state([('CAMERA', 'allow')]))


def test_multiline_uid_prefix_does_not_conflict_with_package_modes():
    uid = 'Uid mode: COARSE_LOCATION: foreground\nFINE_LOCATION: foreground\n'
    package = uid + 'FINE_LOCATION: allow; time=+1s ago\nWIFI_SCAN: allow; time=+3s ago\n'
    assert appop_modes(package, uid) == [('FINE_LOCATION', 'allow'),
        ('Uid mode: COARSE_LOCATION', 'foreground'), ('Uid mode: FINE_LOCATION', 'foreground'),
        ('WIFI_SCAN', 'allow')]
    with pytest.raises(ValueError, match='APPOP_UID_PREFIX_CHANGED'):
        appop_modes(package, uid.replace('foreground', 'ignore'))


def test_no_uid_operations_can_still_have_package_modes():
    assert appop_modes('CAMERA: allow; time=+1s ago', 'No operations.\n') == [('CAMERA', 'allow')]


@pytest.mark.parametrize('row_id,left,right', [
    ('settings.unrelatedControl', '0', '1'),
    ('packages.visible', ['com.a'], ['com.a', 'com.b'])])
def test_unexplained_control_or_package_change_still_fails_with_full_evidence(row_id, left, right):
    def report(request, value):
        return dict(android_user=0, artifact='a'*64, profile_sha256='b'*64,
                    apk_sha256='c'*64, request_id=request, cold_restart_requested=True,
                    rows=[dict(id=row_id, status='OBSERVED', actual_sha256=canonical_digest(value),
                               comparable_value=value)])
    result = restoration(report('a', left), report('b', right))
    assert result['counts'] == {'FAIL': 1}
    row = result['rows'][0]
    assert row['before_value'] == left and row['after_value'] == right
    assert row['reason'] == 'COLD_VALUE_NOT_RESTORED'


@pytest.mark.parametrize('corruption', [None, 'wrong_user', 'wrong_request', 'wrong_value', 'missing'])
def test_only_independently_confirmed_host_control_changes_are_external(corruption):
    def report(request, value):
        return dict(android_user=17, artifact='a'*64, profile_sha256='b'*64,
                    apk_sha256='c'*64, request_id=request, cold_restart_requested=True,
                    host_control=dict(android_user=17, request_id=request, before=value, after=value,
                                      source='root_cli_settings_secure'),
                    rows=[dict(id='settings.unrelatedControl', status='OBSERVED',
                               actual_sha256=canonical_digest(value), comparable_value=value)])
    before, after = report('a', '0'), report('b', '1')
    if corruption == 'wrong_user': after['host_control']['android_user'] = 0
    if corruption == 'wrong_request': after['host_control']['request_id'] = 'foreign'
    if corruption == 'wrong_value': after['host_control'].update(before='0', after='0')
    if corruption == 'missing': after.pop('host_control')
    row = restoration(before, after)['rows'][0]
    assert row['status'] == ('OBSERVED' if corruption is None else 'FAIL')
    assert row['before_value'] == '0' and row['after_value'] == '1'
    if corruption is None:
        assert row['reason'] == 'EXTERNAL_STATE_HOST_SETTING'


def test_unrelated_package_snapshot_addition_is_rejected():
    state = {k: dict(grants={}, install_grants={}, appop_modes={'0': []})
             for k in (PACKAGE, 'dev.cyclone.cloak')}
    extra = copy.deepcopy(state)
    extra['foreign.package'] = copy.deepcopy(state[PACKAGE])
    assert not effective_permission_state(state, extra)

@pytest.mark.parametrize('value,status', [(72,'OBSERVED'),(-1,'OBSERVED'),(None,'OBSERVED'),('bad','FAIL')])
def test_shared_external_rule_never_promotes_to_pass(value,status):
    from restoration_evidence import attach_external_policies, confirmed_external_policy
    raw = dict(rows=[dict(id='wifi.linkSpeed', actual=value, expected=None)])
    safe = dict(rows=[dict(id='wifi.linkSpeed', status='OBSERVED', has_expectation=False, actual_sha256=canonical_digest(value))])
    row = attach_external_policies(raw,safe)['rows'][0]
    assert row['status'] == status
    assert confirmed_external_policy(row) == (status == 'OBSERVED')
    row['restoration_policy']['rule_sha256'] = '0'*64
    assert not confirmed_external_policy(row)

@pytest.mark.parametrize('status,expected', [('BLOCKED',None),('FAIL',None),('OBSERVED',42)])
def test_external_policy_preserves_denial_failure_and_profile_expectations(status,expected):
    from restoration_evidence import attach_external_policies
    raw = dict(rows=[dict(id='wifi.linkSpeed', actual=72, expected=expected)])
    safe = dict(rows=[dict(id='wifi.linkSpeed', status=status, has_expectation=expected is not None, actual_sha256=canonical_digest(72))])
    assert 'restoration_policy' not in attach_external_policies(raw,safe)['rows'][0]
