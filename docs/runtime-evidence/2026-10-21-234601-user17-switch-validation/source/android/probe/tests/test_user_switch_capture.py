import ast
import sys
import unittest
from pathlib import Path
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[3] / 'tools'))
import run_standalone_probe_validation as runner


class UserSwitchCaptureTest(unittest.TestCase):
    def test_switch_waits_for_active_user_before_launch(self):
        commands=[]
        users=iter(('RUNNING_UNLOCKED','0','0','17'))
        def shell(command, **kwargs):
            commands.append(command)
            return next(users)
        def adb(*args, **kwargs):
            commands.append(args[1]); return dict(stdout='',stderr='',exit_code=0)
        events=[]
        with patch.object(runner,'shell',side_effect=shell), patch.object(runner,'adb_result',side_effect=adb), patch.object(runner.time,'sleep') as sleep:
            result=runner.activate_user(17,events.append)
        self.assertTrue(result['completed'])
        self.assertEqual(result['after_user'],17)
        self.assertEqual(commands, ['am get-started-user-state 17','am get-current-user','am switch-user 17','am get-current-user','am get-current-user'])
        sleep.assert_called_once_with(.3)
        self.assertEqual(events,[result])

    def test_switch_force_stop_launch_read_sequence(self):
        order=[]
        def activate(*args): order.append('switch_wait'); return dict(completed=True)
        def prepare(*args): order.append('synthetic_expected_profile'); return {}
        def adb(*args,**kwargs):
            order.append(args[1]); return dict(stdout='{}',stderr='',exit_code=0)
        with patch.object(runner,'activate_user',side_effect=activate), patch.object(runner,'prepare_external_fixture',side_effect=prepare), patch.object(runner,'launch_snapshot',return_value={}), patch.object(runner,'shell',return_value='clock'), patch.object(runner,'adb_result',side_effect=adb):
            runner.launch_with_evidence(17,'request')
            runner.read_evidence(17,'report.json')
        self.assertEqual(order[:3],['switch_wait','synthetic_expected_profile',f'am force-stop --user 17 {runner.PACKAGE}'])
        self.assertEqual(order[3],runner.lifecycle_launch_command(17,'request'))
        self.assertEqual(order[4],f'run-as {runner.PACKAGE} --user 17 cat files/report.json')

    def test_launch_command_exactly_matches_lifecycle_sequence(self):
        command=runner.lifecycle_launch_command(17,'request')
        self.assertEqual(command,f'am start --user 17 -n {runner.PACKAGE}/dev.cyclone.cloak.probe.DiagnosticsProbeActivity --activity-clear-top --activity-single-top --ez external_profile true --ez no_permission_prompt true --ei expected_user 17 --es fixture a --es request_id request')

    def test_return_to_zero_uses_same_switch_and_wait(self):
        with patch.object(runner,'shell',side_effect=['RUNNING_UNLOCKED','17','0']), patch.object(runner,'adb_result',return_value=dict(stdout='',stderr='',exit_code=0)) as adb:
            event=runner.activate_user(0)
        adb.assert_called_once_with('shell','am switch-user 0')
        self.assertTrue(event['completed'])

    def test_active_user_does_not_receive_a_second_switch(self):
        with patch.object(runner,'shell',side_effect=['RUNNING_UNLOCKED','17','17']), patch.object(runner,'adb_result') as adb:
            self.assertTrue(runner.activate_user(17)['completed'])
        adb.assert_not_called()

    def test_locked_user_aborts_without_switch(self):
        with patch.object(runner,'shell',return_value='RUNNING_LOCKED'), patch.object(runner,'adb_result') as adb:
            with self.assertRaisesRegex(RuntimeError,'USER_UNAVAILABLE_OR_LOCKED'): runner.activate_user(17)
        adb.assert_not_called()

    def test_switch_timeout_preserves_failed_event(self):
        events=[]
        with patch.object(runner,'shell',side_effect=['RUNNING_UNLOCKED','0']), patch.object(runner,'adb_result',return_value=dict(exit_code=0)), patch.object(runner.time,'monotonic',side_effect=[0,61]):
            with self.assertRaisesRegex(RuntimeError,'USER_SWITCH_TIMEOUT'): runner.activate_user(17,events.append)
        self.assertFalse(events[0]['completed'])

    def test_finally_returns_to_zero_before_uninstall(self):
        tree=ast.parse(Path(runner.__file__).read_text())
        main=next(n for n in tree.body if isinstance(n,ast.FunctionDef) and n.name=='main')
        cleanup=next(n for n in main.body if isinstance(n,ast.Try)).finalbody
        source=ast.unparse(ast.Module(body=cleanup,type_ignores=[]))
        self.assertLess(source.index('activate_user(0, record_switch)'),source.index("adb('uninstall'"))


if __name__ == '__main__': unittest.main()
