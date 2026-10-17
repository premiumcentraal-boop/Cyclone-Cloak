"""Invalid reports are evidence, never a bypass around identifier redaction."""
from pathlib import Path
import json
import sys
import pytest

sys.path.insert(0, str(Path(__file__).resolve().parents[2] / 'tools'))
import profile_check as pc


def context():
    return dict(request_id='a'*32, android_user=17, fixture='b',
                profile_sha256='b'*64, phase='owner-return-warm-primer')


def test_duplicate_rows_preserve_exact_id_without_private_material():
    secret = 'private-android-id-do-not-export'
    row = dict(id='build.MODEL', status='PASS', reason='MATCH', actual=secret,
               expected={'seed': secret}, token=secret, detail=secret)
    raw = dict(request_id='a'*32, fixture='b', rows=[row, row], error='InvalidCapture',
               reason='DUPLICATE_ROW_ID', failed_row_id='build.MODEL', private_key=secret)
    safe = pc.failed_report_evidence(raw, 'ValueError', context())
    assert safe['duplicate_row_ids'] == ['build.MODEL']
    assert safe['reason'] == 'DUPLICATE_ROW_ID'
    assert safe['failed_row_id'] == 'build.MODEL'
    assert safe['probe_error_type'] == 'InvalidCapture'
    assert secret not in json.dumps(safe)
    assert safe['rows'][0]['actual_sha256'] == pc.digest(secret)
    assert safe['request_metadata']['phase'] == 'owner-return-warm-primer'


@pytest.mark.parametrize('raw', ['private malformed text', [], None, 7,
    {'fixture': {}, 'reason': [], 'failed_row_id': {}, 'rows': [{'id': [], 'reason': 'private'}]}])
def test_malformed_shapes_are_bounded_and_safe(raw):
    safe = pc.failed_report_evidence(raw, 'ValueError', context())
    assert safe['status'] == 'FAIL'
    assert 'private' not in json.dumps(safe)


def test_row_limit_and_unknown_identifiers():
    safe = pc.failed_report_evidence({'rows': [{'id': 'secret-id'}]*1000}, 'ValueError', context())
    assert safe['row_count'] == 1000
    assert len(safe['rows']) == 256
    assert safe['truncated']
    assert 'secret-id' not in json.dumps(safe)
    assert len(json.dumps(safe)) < 40000


def test_permission_failure_reason_is_retained():
    reason = 'PERMISSION_DENIED:android.permission.BLUETOOTH_CONNECT'
    safe = pc.failed_report_evidence({'rows': [{'id': 'bluetooth.name', 'status': 'BLOCKED', 'reason': reason}]}, 'ValueError', context())
    assert safe['rows'][0]['reason'] == reason


def test_failed_capture_is_saved_before_exception_and_cleanup(tmp_path, monkeypatch):
    class Device:
        timeout = 1
        def root(self, command):
            return type('Output', (), dict(stdout='a'*64+' file\n'))()
        def index(self):
            return '', {'entries': {'17/'+pc.PACKAGE: {'key': pc.digest(['b'*32, 17])}}}
        def shell(self, command, **kwargs):
            if command == 'am get-current-user': output = '17'
            elif 'pidof' in command: output = ''
            elif 'cat files/report.json' in command:
                output = json.dumps(dict(request_id='c'*32, error='InvalidCapture',
                    reason='DUPLICATE_ROW_ID', failed_row_id='build.MODEL',
                    rows=[dict(id='build.MODEL'), dict(id='build.MODEL')]))
            else: output = ''
            return type('Output', (), dict(stdout=output, stderr='', returncode=0))()
    monkeypatch.setattr(pc.uuid, 'uuid4', lambda: type('UUID', (), {'hex': 'c'*32})())
    h = pc.Harness(tmp_path, dict(run_id='b'*32, artifact='a'*64, active_capture='secondary-warm'), Device(), 'unused.apk')
    monkeypatch.setattr(h, 'body', lambda item: {})
    with pytest.raises(ValueError, match='PROBE_COLLECTION_FAILED'):
        h.capture(dict(user=17, fixture='b', kind='fixture', sha256='d'*64))
    saved = json.loads(next(tmp_path.glob('failed-capture-*.json')).read_text())
    assert saved['duplicate_row_ids'] == ['build.MODEL']
    assert saved['request_metadata']['phase'] == 'secondary-warm'
