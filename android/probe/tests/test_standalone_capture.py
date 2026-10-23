"""Cold-only standalone launch transport and bounded marker diagnosis."""
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[3] / 'tools'))
from run_standalone_probe_validation import cold_launch, marker_diagnosis, safe_marker, PACKAGE


def marker(phase, request='request-2', outcome=None):
    return dict(format=1, request_id=request, session_generation=1, uid=1712345, pid=42,
                phase=phase, outcome=outcome or ('STARTED' if phase == 'start' else 'COMPLETED'), timestamp_ms=1)


class StandaloneCaptureTest(unittest.TestCase):
    def test_each_launch_is_preceded_by_user_scoped_diagnostics_force_stop(self):
        calls = []
        for user in (0, 17):
            for repeat in (1, 2):
                cold_launch(calls.append, user, f'request-{user}-{repeat}')
        self.assertEqual(len(calls), 8)
        for i, user in enumerate((0, 0, 17, 17)):
            self.assertEqual(calls[i*2], f'am force-stop --user {user} {PACKAGE}')
            self.assertTrue(calls[i*2+1].startswith(f'am start --user {user} -n {PACKAGE}/'))
            self.assertIn('--ez no_permission_prompt true', calls[i*2+1])

    def test_failed_force_stop_aborts_before_launch(self):
        calls = []
        def denied(command):
            calls.append(command)
            raise RuntimeError('STOP_FAILED')
        with self.assertRaises(RuntimeError):
            cold_launch(denied, 17, 'request-2')
        self.assertEqual(len(calls), 1)

    def test_timeout_without_current_start_is_never_started(self):
        for start, end in ((None, None), (marker('start', 'old'), marker('end', 'old'))):
            self.assertEqual(marker_diagnosis('request-2', start, end),
                dict(start_seen=False, end_seen=False, reason='CAPTURE_NEVER_STARTED'))

    def test_timeout_with_current_start_and_stale_end_is_started_no_end(self):
        self.assertEqual(marker_diagnosis('request-2', marker('start'), marker('end', 'old')),
            dict(start_seen=True, end_seen=False, reason='CAPTURE_STARTED_NO_END'))

    def test_completed_and_failed_end_markers_are_distinct(self):
        for outcome in ('COMPLETED', 'FAILED'):
            self.assertEqual(marker_diagnosis('request-2', marker('start'), marker('end', outcome=outcome)),
                dict(start_seen=True, end_seen=True, reason='CAPTURE_ENDED_' + outcome))

    def test_different_process_marker_cannot_end_capture(self):
        end = marker('end'); end['pid'] = 99
        self.assertEqual(marker_diagnosis('request-2', marker('start'), end)['reason'],
                         'CAPTURE_MARKER_BINDING_MISMATCH')

    def test_malformed_markers_cannot_prove_capture_started(self):
        for malformed in (None, [], {}, dict(marker('start'), uid=True), dict(marker('start'), outcome='unknown')):
            self.assertFalse(marker_diagnosis('request-2', malformed, None)['start_seen'])

    def test_only_marker_metadata_survives_export(self):
        source = dict(marker('start'), token='private', actual='private')
        self.assertEqual(safe_marker(source), marker('start'))
        self.assertIsNone(safe_marker('invalid'))


if __name__ == '__main__':
    unittest.main()
