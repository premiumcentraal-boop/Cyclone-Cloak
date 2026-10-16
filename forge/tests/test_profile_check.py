"""Host-side evidence tests; simulated device results are never device proof."""
import copy
import json
from pathlib import Path
import sys
import time
from types import SimpleNamespace
from zipfile import ZipFile

import pytest

sys.path.insert(0, str(Path(__file__).resolve().parents[2] / 'tools'))
import profile_check as pc


def row(key='build.MODEL', actual='a', expected='a', status='PASS'):
    return dict(id=key, source='Android API', actual=actual, expected=expected,
                status=status, detail='MATCH', duration_ms=1)


def report(*rows):
    return dict(format=1, rows=list(rows) or [row()])


def manifest():
    return dict(format=1, run_id='a'*32, scenario='test', phases={}, user=0,
                target_user=17, full_profile=False, artifact='b'*64,
                started_epoch=time.time(), selections=[dict(kind='fixture', fixture='a', user=0, profile_id='a')])


@pytest.mark.parametrize('status', sorted(pc.STATUSES))
def test_all_probe_statuses_parse(status):
    assert pc.parse_report(json.dumps(report(row(status=status))))['rows'][0]['status'] == status


@pytest.mark.parametrize('value', ['{', '[]', '{}', '{"rows":[]}', '{"rows":[{}]}'])
def test_malformed_reports_are_rejected(value):
    with pytest.raises(ValueError):
        pc.parse_report(value)


def test_duplicate_ids_and_error_reports_are_rejected():
    with pytest.raises(ValueError, match='DUPLICATE'):
        pc.parse_report(report(row(), row()))
    with pytest.raises(ValueError, match='COLLECTION_FAILED'):
        pc.parse_report(dict(error='TEST', **report()))


@pytest.mark.parametrize('field,value', [('status','VERIFIED'), ('source',None), ('duration_ms',-1), ('duration_ms',True), ('detail',None)])
def test_invalid_row_fields_rejected(field, value):
    item = row()
    item[field] = value
    with pytest.raises(ValueError):
        pc.parse_report(report(item))


def test_redaction_preserves_comparison_and_removes_both_identifier_values():
    raw = report(row('settings.secure.androidId', 'PRIVATE-HOST-ID', 'PRIVATE-EXPECTED-ID', 'FAIL'))
    safe = pc.redact_report(raw)
    assert 'PRIVATE-' not in json.dumps(safe)
    assert safe['rows'][0]['actual_sha256'] != safe['rows'][0]['expected_sha256']
    assert raw['rows'][0]['actual'] == 'PRIVATE-HOST-ID'


def test_explicit_null_expectation_survives_redaction():
    item = row(actual=None, expected=None)
    item['has_expectation'] = True
    safe = pc.redact_report(report(item))['rows'][0]
    assert safe['has_expectation'] and safe['expected_sha256'] == pc.digest(None)


@pytest.mark.parametrize('a,b,expected,severity', [
    (row(), row(actual='b',expected='b'), 'EXPECTED_CHANGE_OBSERVED','INFO'),
    (row(), row(actual='a',expected='b',status='FAIL'), 'EXPECTED_CHANGE_NOT_OBSERVED','FAIL'),
    (row(), row(actual='b',status='FAIL'), 'UNEXPECTED_CHANGE','FAIL'),
    (row(expected=None,status='OBSERVED'), row(actual='b',expected=None,status='OBSERVED'), 'UNMAPPED_CHANGE','WARNING'),
    (row(), row(status='BLOCKED'), 'BLOCKED_OR_MISSING','WARNING'),
    (row(), row(status='MISSING'), 'BLOCKED_OR_MISSING','WARNING'),
    (row(status='FAIL'), row(), 'PROFILE_VALUE_MISMATCH','FAIL'),
])
def test_diff_classification_matches_raw_and_redacted(a,b,expected,severity):
    raw = pc.compare(report(a),report(b))
    safe = pc.compare(pc.redact_report(report(a)),pc.redact_report(report(b)))
    assert raw == safe
    assert raw[0]['reason'] == expected and raw[0]['severity'] == severity


def test_diff_uses_sorted_ids_and_missing_rows_are_warnings():
    result = pc.compare(report(row('z'),row('a')), report(row('a')))
    assert [r['id'] for r in result] == ['a','z']
    assert result[-1]['reason'] == 'BLOCKED_OR_MISSING'


def test_counts_distinct_rows_and_no_silent_promotion():
    for status, expected in [('PASS','PASS'), ('FAIL','FAIL'), ('OBSERVED','DEGRADED'), ('BLOCKED','DEGRADED'), ('MISSING','DEGRADED')]:
        result = pc.classify([report(row(status=status))]*3)
        assert result['result'] == expected
        assert result['critical_failures'] <= 1 and result['blocked_rows'] <= 1
    assert pc.classify([])['result'] != 'PASS'
    assert pc.classify([report()], [dict(id='test',severity='FAIL')])['result'] == 'FAIL'


def test_phase_and_artifact_names_are_stable():
    assert len(pc.PHASES) == len(set(pc.ARTIFACTS.values())) == 10
    assert pc.ARTIFACTS['post-switch-probes'] == 'phase-07-post-switch-probes.json'
    assert pc.ARTIFACTS['crash-log'] == 'phase-09-crash.log'
    assert pc.ARTIFACTS['summary'] == 'summary.md'
    wrapper = (pc.REPO/'tools/run-profile-check.ps1').read_text(encoding='utf-8')
    for phase in pc.PHASES:
        assert "'"+phase+"'" in wrapper
    assert '--cleanup' in wrapper


def test_missing_or_corrupt_prerequisite_blocks_before_device_work(tmp_path):
    h = pc.Harness(tmp_path,manifest(),None,Path('test.apk'))
    h.publish = lambda: pytest.fail('must not mutate')
    assert not h.phase('publish')
    assert h.m['phases']['publish']['reason'].startswith('PREREQUISITE_')
    h.bind = lambda: dict(selection='one')
    assert h.phase('bind')
    (tmp_path/pc.ARTIFACTS['bind']).write_text('{}', encoding='utf-8')
    assert not h.phase('publish')


def test_repeated_phase_invalidates_dependent_evidence(tmp_path):
    h = pc.Harness(tmp_path,manifest(),None,Path('test.apk'))
    h.bind = lambda: dict(selection='one')
    h.publish = lambda: dict(success=True)
    assert h.phase('bind') and h.phase('publish')
    assert h.phase('bind')
    assert h.m['phases']['publish']['status'] == 'STALE'
    assert h.phase('summary')
    assert h.m['result'] == 'INCOMPLETE'


def test_full_profile_selection_is_conservative():
    index = dict(schemaVersion=2,entries={f'17/com.app{i}':dict(key=f'{i:064x}',cloakProfileId='one') for i in range(327)})
    assert pc.selection(index,17,True)['target_count'] == 327
    index['entries']['17/com.other'] = dict(key='f'*64,cloakProfileId='two')
    with pytest.raises(ValueError,match='AMBIGUOUS'):
        pc.selection(index,17,True)
    assert pc.selection(index,0,False)['fixture'] == 'a'


def test_crash_collection_ignores_old_and_foreign_crashes():
    text = '\n'.join(['10.0 1 1 F DEBUG: >>> dev.cyclone.cloak <<<',
                      '25.0 2 2 F DEBUG: >>> other.app <<<',
                      '26.0 3 3 F DEBUG: >>> dev.cyclone.cloak.probe.diag <<<'])
    assert pc.crash_classes(text,20) == ['CLOAK_PROCESS_CRASH']


class FakeDevice:
    def __init__(self, conflicts=0):
        self.value = dict(schemaVersion=2, entries={'17/com.keep':dict(key='c'*64)})
        self.patched = None
        self.scripts = []
        self.conflicts = conflicts
    def index(self):
        return json.dumps(self.value), copy.deepcopy(self.value)
    def shell(self, script, **kwargs):
        self.scripts.append(script)
        return SimpleNamespace(stdout='',returncode=0)
    def run(self, *args, **kwargs):
        if args[0] == 'push' and Path(args[1]).name == 'index.json':
            self.patched = json.loads(Path(args[1]).read_text(encoding='utf-8'))
        return SimpleNamespace(stdout='',returncode=0)
    def root(self, script, **kwargs):
        self.scripts.append(script)
        if self.conflicts:
            self.conflicts -= 1
            self.value['entries']['12/com.concurrent'] = dict(key='d'*64)
            return SimpleNamespace(stdout='',returncode=74)
        self.value = self.patched
        return SimpleNamespace(stdout='',returncode=0)


def test_overlay_and_cleanup_preserve_other_publishers_and_lock_inode(tmp_path):
    device = FakeDevice(conflicts=1)
    h = pc.Harness(tmp_path,manifest(),device,Path('test.apk'))
    original = copy.deepcopy(device.value['entries']['17/com.keep'])
    h.overlay()
    assert device.value['entries']['17/com.keep'] == original
    assert '12/com.concurrent' in device.value['entries']
    assert '0/'+pc.PACKAGE in device.value['entries']
    h.overlay(remove=True)
    assert set(device.value['entries']) == {'17/com.keep','12/com.concurrent'}
    assert any('exec 0>>' in script and 'flock -n 0' in script for script in device.scripts)
    assert not any('rm' in script and 'publication.lock' in script.split('rm',1)[-1] for script in device.scripts)


def test_foreign_probe_binding_is_never_overwritten(tmp_path):
    d = FakeDevice()
    d.value['entries']['0/'+pc.PACKAGE] = dict(key='e'*64)
    original = copy.deepcopy(d.value)
    h = pc.Harness(tmp_path,manifest(),d,Path('test.apk'))
    with pytest.raises(ValueError,match='refusing'):
        h.overlay()
    assert d.value == original


class SimulatedHarness(pc.Harness):
    def environment(self):
        self.m.update(user=0,target_user=17,original_user=0,artifact='f'*64)
        return dict(simulated=True)
    def bind(self):
        self.m['selections'] = [dict(profile_id='fixture-a')]
        return dict(simulated=True)
    def publish(self): return dict(simulated=True)
    def launch(self): return dict(simulated=True)
    def probes(self): return report()
    def switch(self): return dict(android_user=17)
    def post_switch_probes(self): return dict(after_switch=report(row(actual='b',expected='b')),after_cold_restart=report(row(actual='b',expected='b')))
    def activate(self,user): return dict(android_user=user)
    def crash_log(self):
        (self.output/pc.ARTIFACTS['crash-log']).write_text('',encoding='utf-8')
        (self.output/'phase-10-logcat.log').write_text('',encoding='utf-8')
        return dict(crash_classes=[])


def test_full_fake_run_writes_named_bundle_and_compact_summary(tmp_path, monkeypatch):
    monkeypatch.setattr(pc,'Harness',SimulatedHarness)
    assert pc.main(['--scenario','simulated','--serial','test','--output',str(tmp_path)]) == 0
    with ZipFile(tmp_path/'evidence-bundle.zip') as z:
        assert set(z.namelist()) == {'manifest.json',*pc.ARTIFACTS.values(),'phase-10-logcat.log'}
    summary = (tmp_path/'summary.md').read_text(encoding='utf-8')
    assert 'Result: PASS' in summary and len(summary) < 1000
    assert (tmp_path/'.gitignore').read_text(encoding='utf-8') == '*\n'
    with pytest.raises(SystemExit):
        pc.main(['--scenario','simulated','--serial','test','--output',str(tmp_path)])


def test_failed_phase_still_produces_complete_evidence_names(tmp_path,monkeypatch):
    class Failed(SimulatedHarness):
        def launch(self): raise RuntimeError('LAUNCH_FAILED')
    monkeypatch.setattr(pc,'Harness',Failed)
    assert pc.main(['--scenario','simulated','--serial','test','--output',str(tmp_path)]) == 1
    for name in ['manifest.json',*pc.ARTIFACTS.values(),'phase-10-logcat.log','evidence-bundle.zip']:
        assert (tmp_path/name).is_file()
    assert 'LAUNCH_FAILED' in (tmp_path/'summary.md').read_text(encoding='utf-8')


def test_partial_environment_is_successful_without_other_device_phases(tmp_path,monkeypatch):
    monkeypatch.setattr(pc,'Harness',SimulatedHarness)
    assert pc.main(['--scenario','simulated','--serial','test','--output',str(tmp_path),'--phase','environment']) == 0
    assert 'INCOMPLETE' in (tmp_path/'summary.md').read_text(encoding='utf-8')
    assert not (tmp_path/'phase-09-crash.log').exists()


def test_probe_rows_keep_legacy_ids_and_add_metadata_without_permissions():
    source = (pc.REPO/'android/probe/src/main/java/dev/cyclone/cloak/probe/ProbeActivity.java').read_text(encoding='utf-8')
    for marker in ['"source"','"detail"','"duration_ms"','"request_id"','"profile_sha256"','"process.androidUser"','"packages.visible"','"runtime.loadedLibraries"']:
        assert marker in source
    assert '"settings.secure.androidId"' in source and '"build." + field[0]' in source
    assert 'ATOMIC_MOVE' in source and 'expected-profile.json' in source


def test_large_audit_bounds_commands_and_checks_every_profile(tmp_path):
    paths = []
    entries = {f'17/com.example.p{i}': dict(key=f'{i:064x}') for i in range(327)}
    class Device:
        def index(self): return '', dict(entries=entries)
        def root(self, command):
            assert len(command) < 5000
            chunk = command.split()[1:]
            paths.extend(chunk)
            return SimpleNamespace(stdout='\n'.join('f'*64 + '  ' + p for p in chunk))
    m = manifest()
    m.update(full_profile=True, selections=[dict(user=17, target_count=327)])
    h = pc.Harness(tmp_path, m, Device(), Path('test.apk'))
    h.overlay = lambda: {}
    assert h.publish()['production_audit']['target_count'] == 327
    assert len(paths) == len(set(paths)) == 327


def test_partial_snapshots_survive_cold_restart_failure(tmp_path):
    m = manifest()
    m['selections'] *= 2
    h = pc.Harness(tmp_path, m, None, Path('test.apk'))
    def capture(item, cold=False):
        if cold: raise RuntimeError('COLD_TEST_FAILURE')
        return report(row(status='FAIL'))
    h.capture = capture
    with pytest.raises(RuntimeError, match='COLD_TEST_FAILURE'):
        h.post_switch_probes()
    partial = json.loads((tmp_path/pc.ARTIFACTS['post-switch-probes']).read_text())
    assert partial['after_switch']['rows'][0]['status'] == 'FAIL'


def test_summary_counts_completed_snapshot_when_later_phase_failed(tmp_path):
    m = manifest()
    m['phases'] = dict(probes=dict(status='COMPLETED'), switch=dict(status='FAILED', reason='TIMEOUT'))
    pc.save(tmp_path/pc.ARTIFACTS['probes'], report(row(status='FAIL'), row('wifi.ssid', status='BLOCKED')))
    h = pc.Harness(tmp_path, m, None, Path('test.apk'))
    result = h.summary()
    assert result['result'] == 'FAIL' and result['blocked_rows'] == 1
    assert 'build.MODEL' in (tmp_path/'summary.md').read_text()


def test_rebuilt_summary_retains_original_run_duration(tmp_path):
    m = manifest()
    m.update(started_epoch=0, finished_at='1970-01-01T00:00:12+00:00')
    pc.Harness(tmp_path, m, None, Path('test.apk')).summary()
    assert 'Duration: 12.0s' in (tmp_path/'summary.md').read_text()


@pytest.mark.parametrize('key', ['build.BOOTLOADER', 'build.getRadioVersion',
                                 'packages.visible', 'settings.unrelatedControl'])
def test_device_dependent_changes_keep_stable_ids_without_inferred_pass(key):
    before = report(row(key, actual='user17-value', expected=None, status='OBSERVED'))
    after = report(row(key, actual='user0-value', expected=None, status='OBSERVED'))
    findings = pc.compare(pc.redact_report(before), pc.redact_report(after))
    assert findings == [dict(id=key, changed=True, expected_change=None,
                             reason='UNMAPPED_CHANGE', severity='WARNING')]
    assert pc.classify([before, after], findings)['result'] == 'DEGRADED'


@pytest.mark.parametrize('key', ['build.BOOTLOADER', 'build.getRadioVersion'])
def test_one_sided_profile_expectation_does_not_verify_transition(key):
    before = report(row(key, actual='a', expected=None, status='OBSERVED'))
    after = report(row(key, actual='b', expected='b', status='PASS'))
    finding = pc.compare(pc.redact_report(before), pc.redact_report(after))[0]
    assert finding['id'] == key and finding['expected_change'] is None
    assert finding['severity'] == 'WARNING'
