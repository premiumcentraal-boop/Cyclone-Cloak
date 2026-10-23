import sys
import unittest
from unittest.mock import patch
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parents[3] / 'tools'))
from run_standalone_probe_validation import evidence_paths, read_state, visibility_diagnosis, marker_diagnosis
import run_standalone_probe_validation as runner


class UserVisibilityTest(unittest.TestCase):
    def test_paths_are_absolute_and_user_scoped(self):
        for user in (0,17):
            external, private = evidence_paths(user, 'report.json')
            self.assertIn(f'/emulated/{user}/Android/data/', external)
            self.assertIn(f'/data/user/{user}/', private)
            self.assertTrue(external.endswith('/standalone-evidence/report.json'))

    def test_invalid_paths_are_rejected(self):
        for user, name in ((-1,'report.json'),(True,'report.json'),(17,'../secret')):
            with self.assertRaises(ValueError): evidence_paths(user, name)

    def test_absent_and_unreadable_are_distinct(self):
        self.assertEqual(read_state(dict(exit_code=1,stdout='',stderr='No such file or directory')), 'ABSENT')
        self.assertEqual(read_state(dict(exit_code=1,stdout='',stderr='Permission denied')), 'ACCESS_DENIED_EXISTENCE_UNKNOWN')

    def test_access_denial_does_not_claim_capture_never_started(self):
        diagnosis=marker_diagnosis('new',None,None)
        self.assertEqual(visibility_diagnosis(diagnosis,[dict(state='ACCESS_DENIED_EXISTENCE_UNKNOWN')])['reason'], 'CAPTURE_START_UNOBSERVABLE')
        self.assertEqual(visibility_diagnosis(diagnosis,[dict(state='ABSENT')])['reason'], 'CAPTURE_NEVER_STARTED')

    def test_readable_marker_is_not_hidden_by_another_denied_transport(self):
        diagnosis=dict(start_seen=True,end_seen=False,reason='CAPTURE_STARTED_NO_END')
        self.assertEqual(visibility_diagnosis(diagnosis,[dict(state='ACCESS_DENIED_EXISTENCE_UNKNOWN')])['reason'], 'CAPTURE_STARTED_NO_END')

    def test_launch_preserves_verbatim_streams_exit_and_user(self):
        start=dict(stdout='Starting: Intent\n',stderr='Warning: example\n',exit_code=0)
        with patch.object(runner,'activate_user'), patch.object(runner,'prepare_external_fixture',return_value={}), patch.object(runner,'launch_snapshot',return_value={}), patch.object(runner,'shell',return_value='10-21 12:00:00.000'), patch.object(runner,'adb_result',side_effect=[dict(stdout='',stderr='',exit_code=0),start]) as adb:
            evidence=runner.launch_with_evidence(17,'request')
        self.assertEqual(evidence['am_start'],start)
        self.assertIn('am start --user 17 -n '+runner.PACKAGE,adb.call_args.args[1])

    def test_report_read_tries_explicit_per_user_paths_and_preserves_denial(self):
        responses=[dict(stdout='',stderr='Permission denied',exit_code=1)]*3+[dict(stdout='{"request_id":"r"}',stderr='',exit_code=0)]
        with patch.object(runner,'adb_result',side_effect=responses) as adb:
            report,visibility=runner.read_evidence(17,'report.json')
        self.assertEqual(report['request_id'],'r')
        self.assertEqual(visibility[0]['state'],'ACCESS_DENIED_EXISTENCE_UNKNOWN')
        self.assertIn('/data/user/17/',adb.call_args.args[1])


if __name__ == '__main__': unittest.main()
