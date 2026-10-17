"""Pure diagnostics orchestration tests; no ADB or production writes."""
import copy
import hashlib
import json
from pathlib import Path
import sys
import pytest

sys.path.insert(0, str(Path(__file__).resolve().parents[2]/'tools'))
from run_probe_reversibility import (
    phases, restoration, cleanup_private, check_metadata, effective_permission_state, PACKAGE,
    cycle_plan, aggregate_summary, run_cycles, parse_args, snapshot_delta,
    ensure_diagnostics_apk, APK_INSTALL_POLICY)



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
    before=state_with_allowed_entries(expected)
    after=state_with_allowed_entries(expected)
    change(dict(before=before,after=after))
    assert effective_permission_state(before,after) is False





def state_with_allowed_entries(entries):
    return {
        PACKAGE: permission_fixture(appops={'0': list(entries), '17': list(entries)}),
        'dev.cyclone.cloak': permission_fixture(appops={'0': [], '17': []}),
    }

def test_identical_snapshot_with_existing_allowed_records_passes():
    allowed=sorted([('READ_DEVICE_IDENTIFIERS','deny'),('USE_ICC_AUTH_WITH_DEVICE_IDENTIFIER','default')])
    before=state_with_allowed_entries(allowed)
    assert effective_permission_state(before,copy.deepcopy(before)) is True

@pytest.mark.parametrize('added', [
    [('READ_DEVICE_IDENTIFIERS','deny')],
    [('USE_ICC_AUTH_WITH_DEVICE_IDENTIFIER','default')],
    sorted([('READ_DEVICE_IDENTIFIERS','deny'),('USE_ICC_AUTH_WITH_DEVICE_IDENTIFIER','default')]),
])
def test_allowed_subset_additions_pass(added):
    before=state_with_allowed_entries([])
    after=state_with_allowed_entries(sorted(added))
    assert effective_permission_state(before,after) is True

def test_allowed_mode_change_or_removal_is_rejected():
    allowed=[('READ_DEVICE_IDENTIFIERS','deny')]
    changed=state_with_allowed_entries([('READ_DEVICE_IDENTIFIERS','default')])
    assert effective_permission_state(state_with_allowed_entries(allowed),changed) is False
    assert effective_permission_state(state_with_allowed_entries(allowed),state_with_allowed_entries([])) is False

def test_json_round_trip_accepts_unchanged_snapshot():
    allowed=sorted([('READ_DEVICE_IDENTIFIERS','deny'),('USE_ICC_AUTH_WITH_DEVICE_IDENTIFIER','default')])
    state=state_with_allowed_entries(allowed)
    replay=json.loads(json.dumps(state))
    assert effective_permission_state(state,replay) is True

def test_duplicate_or_malformed_appop_entries_are_rejected():
    duplicate=state_with_allowed_entries([('READ_DEVICE_IDENTIFIERS','deny'),('READ_DEVICE_IDENTIFIERS','deny')])
    malformed=state_with_allowed_entries([('READ_DEVICE_IDENTIFIERS',)])
    assert effective_permission_state(state_with_allowed_entries([]),duplicate) is False
    with pytest.raises(ValueError,match='INVALID_APPOP_ENTRY'):
        effective_permission_state(state_with_allowed_entries([]),malformed)


def test_cycle_plan_repeats_complete_sequence_with_unique_directories():
    plan = cycle_plan([0,17], 3)
    assert [step['directory'] for step in plan] == ['cycle-001','cycle-002','cycle-003']
    assert [step['cycle'] for step in plan] == [1,2,3]
    assert all(step['phases'] == phases([0,17]) for step in plan)
    assert sum(len(step['phases']) for step in plan) == 24


@pytest.mark.parametrize('count', [0,-1,True,1.5])
def test_cycle_plan_rejects_invalid_counts(count):
    with pytest.raises(ValueError,match='INVALID_CYCLES'):
        cycle_plan([0,17],count)


def cycle_args(tmp_path, *extra):
    return parse_args(['--source-checkout',str(tmp_path/'source'),'--adb','adb',
                       '--serial','test','--output',str(tmp_path/'evidence'),*extra])


def fake_cycle(args, *, failure=None, permissions=None, audit=None):
    """Exercise orchestration using persisted evidence, without a device."""
    out = args.output
    out.mkdir(parents=True,exist_ok=False)
    def save(name,value):
        (out/name).write_text(json.dumps(value),encoding='utf-8')
    state = state_with_allowed_entries([('READ_DEVICE_IDENTIFIERS','deny')])
    after = copy.deepcopy(state)
    if permissions:
        after[PACKAGE]['appop_modes']['0'].append(('CAMERA','allow'))
    before_audit = {'index_sha256':'a'*64}
    save('audit-before.json',before_audit)
    save('audit-after.json',audit or before_audit)
    save('permissions-before.json',state)
    save('permissions-after.json',after)
    save('cleanup-proof.json',{'scratch_removed':True,**{str(u):dict(
        binding_removed=True,body_directory_removed=True,private_files_removed=True) for u in (0,17)}})
    save('manifest.json',dict(result='FAIL' if failure else 'RESTORED_WITH_EXPECTED_LIMITATIONS',
         apk_sha256='f'*64, apk_install=dict(apk_install_policy=APK_INSTALL_POLICY,
             installation_skipped=True, installed_apk_hashes={'0':'f'*64,'17':'f'*64}),
         errors=[failure] if failure else [],captures=4 if failure else 8,duration_seconds=1,
         failed_phase='user-17-bound-cold' if failure else None,
         restoration={} if failure else {'0':'RESTORED_WITH_LIMITATIONS','17':'RESTORED_WITH_LIMITATIONS'}))
    return 1 if failure else 0


def test_default_and_explicit_one_cycle_keep_existing_paths_and_phase_plan(tmp_path):
    default = cycle_args(tmp_path)
    explicit = cycle_args(tmp_path,'--cycles','1')
    assert default == explicit
    assert cycle_plan([0,17]) == [dict(cycle=1,directory='.',phases=phases([0,17]))]
    calls=[]
    def executor(args):
        calls.append(args.output)
        return fake_cycle(args)
    assert run_cycles(default,executor)==0
    assert calls==[default.output]
    assert (default.output/'manifest.json').is_file()
    assert not (default.output/'cycle-001').exists()


def test_three_cycles_aggregate_and_preserve_each_cycle_evidence(tmp_path):
    args=cycle_args(tmp_path,'--cycles','3')
    assert run_cycles(args,fake_cycle)==0
    result=json.loads((args.output/'aggregate-summary.json').read_text())
    assert result['passed_cycles']==3 and result['failed_cycle'] is None
    assert not result['permission_state_changed'] and not result['production_state_changed']
    assert result['cleanup_passed']
    assert all(row['restoration']=={'0':'RESTORED_WITH_LIMITATIONS','17':'RESTORED_WITH_LIMITATIONS'}
               for row in result['cycles'])
    from zipfile import ZipFile
    with ZipFile(args.output/'lifecycle-evidence-bundle.zip') as bundle:
        assert bundle.testzip() is None
        assert all(f'cycle-{n:03d}/cleanup-proof.json' in bundle.namelist() for n in (1,2,3))


@pytest.mark.parametrize('failure', ['PROBE_REPORT_TIMEOUT','BOUND_CAPTURE_FAILED'])
def test_failed_cycle_stops_before_next_and_keeps_phase_and_cleanup(tmp_path,failure):
    args=cycle_args(tmp_path,'--cycles','3')
    def executor(child):
        return fake_cycle(child,failure=failure if child.cycle==2 else None)
    assert run_cycles(args,executor)==1
    summary=json.loads((args.output/'aggregate-summary.json').read_text())
    assert summary['failed_cycle']==2 and summary['completed_cycles']==2
    assert summary['cycles'][1]['failed_phase']=='user-17-bound-cold'
    assert summary['cycles'][1]['cleanup_passed']
    assert not (args.output/'cycle-003').exists()


@pytest.mark.parametrize('kind', ['permissions','production','cleanup','boundary'])
def test_false_success_in_cycle_manifest_is_not_promoted(tmp_path,kind):
    args=cycle_args(tmp_path,'--cycles','3')
    def executor(child):
        code=fake_cycle(child,permissions=kind=='permissions',
                        audit={'index_sha256':'b'*64} if kind=='production' else None)
        if kind=='cleanup':
            (child.output/'cleanup-proof.json').unlink()
        if kind=='boundary' and child.cycle==2:
            for name in ['permissions-before.json','permissions-after.json']:
                path=child.output/name
                state=json.loads(path.read_text())
                state[PACKAGE]['appop_modes']['0'].append(['CAMERA','deny'])
                path.write_text(json.dumps(state))
        return code
    assert run_cycles(args,executor)==1
    summary=json.loads((args.output/'aggregate-summary.json').read_text())
    assert summary['failed_cycle']==(2 if kind=='boundary' else 1)
    if kind=='permissions': assert summary['permission_state_changed']
    if kind=='production': assert summary['production_state_changed']
    if kind=='cleanup': assert not summary['cleanup_passed']
    if kind=='boundary':
        assert summary['permission_state_changed']
        assert summary['cycles'][-1]['boundary_permission_delta']


def test_missing_cycle_evidence_cannot_pass(tmp_path):
    args=cycle_args(tmp_path,'--cycles','3')
    assert run_cycles(args,lambda child: 0)==1
    result=json.loads((args.output/'aggregate-summary.json').read_text())
    assert result['failed_cycle']==1 and not result['cleanup_passed']


def test_existing_output_is_never_overwritten(tmp_path):
    args=cycle_args(tmp_path,'--cycles','3')
    args.output.mkdir()
    with pytest.raises(FileExistsError):
        run_cycles(args,lambda child: pytest.fail('Must not start device work'))


def test_cli_rejects_zero_cycles(tmp_path):
    with pytest.raises(SystemExit): cycle_args(tmp_path,'--cycles','0')


def test_snapshot_delta_reports_added_removed_and_changed_records():
    assert snapshot_delta({'a':1,'b':2},{'a':3,'c':4})==[
        dict(path=['a'],change='changed',before=1,after=3),
        dict(path=['b'],change='removed',before=2),
        dict(path=['c'],change='added',after=4)]
    assert snapshot_delta({'a':1},{'a':1})==[]


def test_aggregate_incomplete_plan_never_reports_success():
    assert aggregate_summary([],3)['result']=='FAIL'


class ApkDevice:
    def __init__(self, installed=None):
        self.installed = dict(installed if installed is not None else {0:'f'*64,17:'f'*64})
        self.installs = []
        self.lookups = []
        self.install_error = None

    def shell(self, command, check=False):
        from types import SimpleNamespace
        user = int(command.split()[3])
        assert command == f'pm path --user {user} {PACKAGE}'
        self.lookups.append(user)
        return SimpleNamespace(stdout=f'package:/data/app/probe-{user}/base.apk\n' if user in self.installed else '',
                               stderr='',returncode=0 if user in self.installed else 1)

    def root(self, command, check=False):
        from types import SimpleNamespace
        assert command.startswith('sha256sum /data/app/probe-')
        user=int(command.split('probe-')[1].split('/')[0])
        return SimpleNamespace(stdout=self.installed[user]+'  /data/app/base.apk\n',returncode=0)

    def run(self, *command, timeout):
        from types import SimpleNamespace
        self.installs.append((command,timeout))
        assert command[0]=='install' and command[3]=='-r' and timeout==120
        if self.install_error: raise self.install_error
        self.installed[int(command[2])]='f'*64
        return SimpleNamespace(stdout='Success\n')


def test_same_hash_reuse_checks_both_users_every_cycle_without_install():
    device=ApkDevice(); state=dict(install_attempts=0)
    for _ in range(3):
        evidence={}
        assert ensure_diagnostics_apk(device,[0,17],Path('probe.apk'),'f'*64,state,evidence)=={'0':'f'*64,'17':'f'*64}
        assert evidence['apk_install_policy']==APK_INSTALL_POLICY
        assert evidence['installation_skipped'] and not evidence['install_attempted']
        assert [row['android_user'] for row in evidence['checks']]==[0,17]
        assert all(row['status']=='MATCH' and row['package_path'] for row in evidence['checks'])
    assert device.lookups==[0,17]*3 and device.installs==[] and state['install_attempts']==0


def test_missing_apk_installs_once_and_later_cycles_reuse_it():
    device=ApkDevice({17:'f'*64}); state=dict(install_attempts=0); evidence={}
    ensure_diagnostics_apk(device,[0,17],Path('probe.apk'),'f'*64,state,evidence)
    assert state['install_attempts']==1 and len(device.installs)==1
    assert not evidence['installation_skipped'] and evidence['install_attempted']
    assert [row['status'] for row in evidence['checks']]==['MISSING','MATCH','MATCH','MATCH']
    for _ in range(2):
        ensure_diagnostics_apk(device,[0,17],Path('probe.apk'),'f'*64,state,{})
    assert len(device.installs)==1
    del device.installed[17]
    with pytest.raises(ValueError,match='DIAGNOSTICS_INSTALL_ALREADY_ATTEMPTED'):
        ensure_diagnostics_apk(device,[0,17],Path('probe.apk'),'f'*64,state,{})
    assert len(device.installs)==1


@pytest.mark.parametrize('installed', [{0:'e'*64,17:'f'*64},{0:'f'*64,17:'e'*64},{17:'e'*64}])
def test_installed_hash_mismatch_fails_fast_without_reinstall(installed):
    device=ApkDevice(installed); state=dict(install_attempts=0); evidence={}
    with pytest.raises(ValueError,match='^INSTALLED_APK_HASH_MISMATCH$'):
        ensure_diagnostics_apk(device,[0,17],Path('probe.apk'),'f'*64,state,evidence)
    assert device.installs==[] and state['install_attempts']==0
    assert evidence['checks'][-1]['status']=='MISMATCH'


def test_timed_out_install_is_counted_and_cannot_be_retried():
    import subprocess
    device=ApkDevice({17:'f'*64}); state=dict(install_attempts=0); evidence={}
    device.install_error=subprocess.TimeoutExpired('adb install',120)
    with pytest.raises(subprocess.TimeoutExpired):
        ensure_diagnostics_apk(device,[0,17],Path('probe.apk'),'f'*64,state,evidence)
    assert state['install_attempts']==1 and evidence['install_attempted']
    with pytest.raises(ValueError,match='DIAGNOSTICS_INSTALL_ALREADY_ATTEMPTED'):
        ensure_diagnostics_apk(device,[0,17],Path('probe.apk'),'f'*64,state,{})
    assert len(device.installs)==1


@pytest.mark.parametrize('missing', [False,True])
def test_lifecycle_records_per_cycle_hash_checks_and_skipped_install(tmp_path,missing):
    args=cycle_args(tmp_path,'--cycles','3'); device=ApkDevice({17:'f'*64} if missing else None)
    def executor(child):
        code=fake_cycle(child)
        path=child.output/'manifest.json'
        m=json.loads(path.read_text())
        m['apk_install']={}
        ensure_diagnostics_apk(device,[0,17],Path('probe.apk'),'f'*64,child.apk_install_state,m['apk_install'])
        path.write_text(json.dumps(m))
        return code
    assert run_cycles(args,executor)==0
    summary=json.loads((args.output/'aggregate-summary.json').read_text())
    assert summary['apk_install_policy']==APK_INSTALL_POLICY
    assert summary['installation_skipped'] is not missing
    assert summary['install_attempts']==int(missing) and len(device.installs)==int(missing)
    assert len(device.lookups)==(8 if missing else 6)
    assert all(row['installed_apk_check_passed'] for row in summary['cycles'])
    assert all(len(row['apk_install']['checks'])==2 for row in summary['cycles'][1:])


def test_missing_hash_evidence_never_promotes_cycle_to_pass(tmp_path):
    args=cycle_args(tmp_path,'--cycles','3')
    def executor(child):
        code=fake_cycle(child)
        path=child.output/'manifest.json'
        m=json.loads(path.read_text()); m.pop('apk_install')
        path.write_text(json.dumps(m))
        return code
    assert run_cycles(args,executor)==1
    result=json.loads((args.output/'aggregate-summary.json').read_text())
    assert result['failed_cycle']==1 and not result['cycles'][0]['installed_apk_check_passed']


