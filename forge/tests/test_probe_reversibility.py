"""Pure diagnostics orchestration tests; no ADB or production writes."""
import copy
import hashlib
from pathlib import Path
import sys
import pytest

sys.path.insert(0, str(Path(__file__).resolve().parents[2]/'tools'))
from run_probe_reversibility import (
    phases, restoration, cleanup_private, check_metadata, effective_permission_state, PACKAGE)



def report(request='a', **row_overrides):
    row=dict(id='build.MODEL',status='FAIL',actual_sha256='a'*64)
    row.update(row_overrides)
    return dict(android_user=0,artifact='a'*64,profile_sha256='b'*64,apk_sha256='c'*64,
                request_id=request,cold_restart_requested=True,rows=[row])


def test_phase_order_preserves_both_warm_observations_before_cold_stops():
    plan=phases([0,17])
    assert len(plan)==8
    assert [u for _,u,_ in plan]==[0,17]*4
    assert plan[4:6]==[('post-removal-warm',0,False),('post-removal-warm',17,False)]


@pytest.mark.parametrize('users', [[0,0],[17,0],[0,-1],[0,True]])
def test_invalid_users(users):
    with pytest.raises(ValueError): phases(users)


def test_fixture_mismatch_does_not_mask_actual_restoration():
    result=restoration(report(),report('b'))
    assert result['counts']=={'PASS':1}
    assert result['rows'][0]['reason']=='BASELINE_RESTORED'


def test_stale_fixture_after_cold_restart_is_failure():
    result=restoration(report(),report('b',actual_sha256='d'*64))
    assert result['counts']=={'FAIL':1}
    assert result['rows'][0]['reason']=='COLD_VALUE_NOT_RESTORED'


@pytest.mark.parametrize('override,expected', [
    ({'status':'BLOCKED'},'BLOCKED'), ({'status':'MISSING'},'OBSERVED'),
    ({'actual_sha256':None},'OBSERVED'), ({'actual_available':False},'OBSERVED'),
    ({'actual_sha256':hashlib.sha256(b'null').hexdigest()},'OBSERVED'),
    ({'expectation_policy':'EXTERNAL_STATE'},'OBSERVED')])
def test_insufficient_or_external_evidence_never_passes(override,expected):
    result=restoration(report(),report('b',**override))
    assert result['counts']=={expected:1}


@pytest.mark.parametrize('key,value', [('android_user',17),('artifact','d'*64),
    ('profile_sha256','d'*64),('apk_sha256','d'*64),('request_id','a'),('cold_restart_requested',False)])
def test_restoration_requires_same_user_artifact_profile_apk_and_distinct_cold_requests(key,value):
    after=report('b'); after[key]=value
    with pytest.raises(ValueError,match='RESTORATION_METADATA'): restoration(report(),after)


def test_missing_row_cannot_pass():
    after=report('b'); after['rows']=[]
    assert restoration(report(),after)['counts']=={'FAIL':1}


def test_dynamic_local_typed_change_is_observation():
    before=report(expectation_policy='TYPED')
    after=report('b',expectation_policy='TYPED',actual_sha256='d'*64)
    assert restoration(before,after)['counts']=={'OBSERVED':1}


def test_cleanup_is_scoped_to_diagnostics_for_each_user():
    calls=[]
    class Device:
        def shell(self,command): calls.append(command)
    cleanup_private(Device(),[0,17])
    assert len(calls)==4
    assert all(PACKAGE in c for c in calls)
    assert all('grant' not in c and 'clear' not in c for c in calls)
    assert 'am force-stop --user 17 '+PACKAGE in calls
    assert 'expected-profile.json' in calls[-1] and 'report.json.*.tmp' in calls[-1]


def test_capture_metadata_requires_user_fixture_hash_and_complete_rows():
    item=dict(user=17,fixture='b',sha256='a'*64)
    r=dict(package=PACKAGE,uid=1700001,fixture='b',profile_sha256='a'*64,
           process_reused=False,rows=[dict(id='a'),dict(id='b')])
    check_metadata(r,item,True,['a','b'])
    for change in [dict(uid=1),dict(fixture='a'),dict(profile_sha256='b'*64),
                   dict(rows=[dict(id='a'),dict(id='a')]),dict(process_reused=True)]:
        invalid=copy.deepcopy(r); invalid.update(change)
        with pytest.raises(ValueError): check_metadata(invalid,item,True,['a','b'])

def permission_fixture(grants=None, appops=None, install=None):
    return dict(grants=grants if grants is not None else {'0': {}},
                appop_modes=appops if appops is not None else {'0': []},
                install_grants=install if install is not None else {})


def test_exact_read_created_diagnostic_appops_are_expected_only():
    expected=sorted([('READ_DEVICE_IDENTIFIERS','deny'),('USE_ICC_AUTH_WITH_DEVICE_IDENTIFIER','default')])
    before={PACKAGE:permission_fixture(appops={'0': [], '17': []}), 'dev.cyclone.cloak':permission_fixture(appops={'0': [], '17': []})}
    after={PACKAGE:permission_fixture(appops={'0': expected, '17': expected}),
           'dev.cyclone.cloak':permission_fixture(appops={'0': [], '17': []})}
    assert effective_permission_state(before,after) is True


@pytest.mark.parametrize('change', [
    lambda state: state['before'][PACKAGE].update(grants={'0': {'android.permission.CAMERA': dict(granted=False, flags='')}}),
    lambda state: state['before'][PACKAGE]['appop_modes']['0'].append(('CAMERA_TOGGLE','default')),
    lambda state: state['after'][PACKAGE]['appop_modes']['0'].__setitem__(0, ('READ_DEVICE_IDENTIFIERS','default')),
    lambda state: state['after'][PACKAGE]['appop_modes']['0'].pop(0),
    lambda state: state['before'][PACKAGE]['appop_modes']['17'].append(('READ_DEVICE_IDENTIFIERS','deny')),
    lambda state: state['before'].update({'other.package': permission_fixture()}),
    lambda state: state['before']['dev.cyclone.cloak']['appop_modes']['0'].append(('CAMERA_TOGGLE','default')),
])
def test_other_permission_or_appop_changes_fail(change):
    expected=sorted([('READ_DEVICE_IDENTIFIERS','deny'),('USE_ICC_AUTH_WITH_DEVICE_IDENTIFIER','default')])
    before={PACKAGE:permission_fixture(appops={'0': [], '17': []}), 'dev.cyclone.cloak':permission_fixture(appops={'0': [], '17': []})}
    after={PACKAGE:permission_fixture(appops={'0': expected, '17': expected}),
           'dev.cyclone.cloak':permission_fixture(appops={'0': [], '17': []})}
    change(dict(before=before,after=after))
    assert effective_permission_state(before,after) is False



